package com.goldrift.instadm.data

import androidx.room.withTransaction
import com.goldrift.instadm.data.db.AppMetaEntity
import com.goldrift.instadm.data.db.AttachmentEntity
import com.goldrift.instadm.data.db.InstaDmDatabase
import com.goldrift.instadm.data.db.MessageEntity
import com.goldrift.instadm.data.db.MessageStatus
import com.goldrift.instadm.data.db.MessageWithMedia
import com.goldrift.instadm.data.db.ParticipantEntity
import com.goldrift.instadm.data.db.ReactionEntity
import com.goldrift.instadm.data.db.ThreadEntity
import com.goldrift.instadm.data.db.ThreadWithParticipants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Messages kept per thread; older rows are pruned after each ingest. */
private const val MESSAGE_RETENTION = 1000

private const val META_INBOX_SYNCED_AT = "inbox_synced_at"
private const val META_VIEWER_ID = "viewer_ig_id"
private const val META_VIEWER_USERNAME = "viewer_username"
private const val META_VIEWER_NAME = "viewer_name"
private const val META_VIEWER_FBID = "viewer_fbid"

/**
 * Owns the Room database and turns normalized Go JSON into entities. The UI
 * observes flows from here; nothing in this class talks to the network.
 *
 * Ingesting is idempotent: the same mailbox, thread page, or realtime message
 * can be applied repeatedly without duplicating rows. Messages arriving from
 * Instagram never resurrect a tombstone.
 */
class ChatStore(
    private val db: InstaDmDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val threadDao = db.threads()
    private val messageDao = db.messages()
    private val metaDao = db.appMeta()

    fun observeInbox(): Flow<List<ThreadWithParticipants>> = threadDao.observeInbox()

    fun observeThread(threadId: String): Flow<List<MessageWithMedia>> =
        messageDao.observeThread(threadId)

    fun observeThreadMeta(threadId: String): Flow<ThreadWithParticipants?> =
        threadDao.observeThread(threadId)

    suspend fun viewer(key: String): String? = metaDao.get(key)

    suspend fun inboxSyncedAt(): Long? = metaDao.get(META_INBOX_SYNCED_AT)?.toLongOrNull()

    /** Applies `PolarisDirectInboxQuery` output: thread metadata and previews. */
    suspend fun ingestMailbox(json: JSONObject) {
        val threads = json.optJSONArray("threads") ?: return
        val now = clock()

        db.withTransaction {
            val ids = (0 until threads.length()).mapNotNull { index ->
                threads.optJSONObject(index)
                    ?.optString("id")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
            val existing = if (ids.isEmpty()) {
                emptyMap()
            } else {
                threadDao.threads(ids).associateBy { it.id }
            }

            val rows = ArrayList<ThreadEntity>(threads.length())
            for (index in 0 until threads.length()) {
                val thread = threads.optJSONObject(index) ?: continue
                val id = thread.optString("id").trim()
                if (id.isEmpty()) continue
                rows += threadEntity(thread, existing[id], now) ?: continue
            }
            if (rows.isNotEmpty()) threadDao.upsertAll(rows)

            // Participants reference the thread row, so insert them afterwards.
            for (index in 0 until threads.length()) {
                val thread = threads.optJSONObject(index) ?: continue
                val id = thread.optString("id").trim()
                if (id.isEmpty()) continue
                threadDao.deleteParticipants(id)
                val participants = participants(thread, id)
                if (participants.isNotEmpty()) threadDao.upsertParticipants(participants)
            }

            json.optJSONObject("viewer")?.let { saveViewer(it) }
            metaDao.put(AppMetaEntity(META_INBOX_SYNCED_AT, now.toString()))
        }
    }

    /** Applies `IGDThreadDetailQuery` output: thread metadata, messages, receipts. */
    suspend fun ingestThread(json: JSONObject) {
        val threadJson = json.optJSONObject("thread")
        val threadId = threadJson?.optString("id")?.trim().orEmpty()
            .ifBlank { json.optString("thread_id").trim() }
        if (threadId.isEmpty()) return

        val now = clock()
        db.withTransaction {
            val previous = threadDao.thread(threadId)
            if (threadJson != null) {
                val entity = threadEntity(threadJson, previous, now) ?: return@withTransaction
                threadDao.upsert(entity)
                threadDao.deleteParticipants(threadId)
                val participants = participants(threadJson, threadId)
                if (participants.isNotEmpty()) threadDao.upsertParticipants(participants)
            } else if (previous == null) {
                threadDao.upsert(ThreadEntity(id = threadId, updatedAt = now))
            }

            upsertServerMessages(threadId, json.optJSONArray("messages") ?: JSONArray(), now)
            threadDao.updateNewestPage(
                threadId = threadId,
                cursor = json.optString("oldest_cursor"),
                hasMore = json.optBoolean("has_more_older"),
                syncedAt = now,
                now = now,
            )
            applyReadReceipts(threadId, json.optJSONArray("read_receipts"), now)
            updatePreviewFromNewest(threadId, now)
            messageDao.prune(threadId, MESSAGE_RETENTION)
        }
    }

    /** Applies an `IGDMessageListOffMsysQuery` page: older messages only. */
    suspend fun ingestOlderPage(json: JSONObject) {
        val threadId = json.optString("thread_id").trim()
        if (threadId.isEmpty()) return

        val now = clock()
        db.withTransaction {
            if (threadDao.thread(threadId) == null) {
                threadDao.upsert(ThreadEntity(id = threadId, updatedAt = now))
            }
            upsertServerMessages(threadId, json.optJSONArray("messages") ?: JSONArray(), now)
            threadDao.updateOlderPage(
                threadId = threadId,
                cursor = json.optString("oldest_cursor"),
                hasMore = json.optBoolean("has_more_older"),
                now = now,
            )
            messageDao.prune(threadId, MESSAGE_RETENTION)
        }
    }

    /** Records the viewer's own read watermark for a thread. */
    suspend fun markThreadRead(threadId: String, watermarkMs: Long) {
        if (threadId.isBlank() || watermarkMs <= 0) return
        threadDao.updateReadWatermark(threadId, watermarkMs, clock())
    }

    /** Removes every cached thread, message, and queue entry. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        db.clearAllTables()
    }

    private suspend fun upsertServerMessages(
        threadId: String,
        messages: JSONArray,
        now: Long,
    ) {
        if (messages.length() == 0) return

        val rows = ArrayList<MessageEntity>(messages.length())
        val payloads = ArrayList<Pair<String, JSONObject>>(messages.length())
        for (index in 0 until messages.length()) {
            val message = messages.optJSONObject(index) ?: continue
            val id = message.optString("id").trim()
            if (id.isEmpty()) continue

            val existing = messageDao.message(id)
            if (existing?.status == MessageStatus.DELETED) continue
            val entity = messageEntity(message, threadId, existing, now) ?: continue
            rows += entity
            payloads += id to message
        }
        if (rows.isEmpty()) return

        // Messages first: attachments and reactions reference the message row.
        messageDao.upsertMessages(rows)

        for ((id, message) in payloads) {
            val previousMedia = messageDao.attachments(id).associateBy { it.partIndex }
            val media = attachments(message, id).map { item ->
                previousMedia[item.partIndex]?.let { cached ->
                    item.copy(localPath = cached.localPath, downloadState = cached.downloadState)
                } ?: item
            }
            messageDao.deleteAttachments(id)
            if (media.isNotEmpty()) messageDao.upsertAttachments(media)

            val reactions = reactions(message, id)
            messageDao.deleteReactions(id)
            if (reactions.isNotEmpty()) messageDao.upsertReactions(reactions)
        }
    }

    private suspend fun updatePreviewFromNewest(threadId: String, now: Long) {
        val newest = messageDao.newest(threadId) ?: return
        threadDao.updateLastMessage(
            threadId = threadId,
            messageId = newest.id,
            timestampMs = newest.timestampMs,
            text = newest.text,
            kind = newest.kind,
            fromViewer = newest.isFromViewer,
            senderFbid = newest.senderFbid,
            now = now,
        )
    }

    private suspend fun applyReadReceipts(
        threadId: String,
        receipts: JSONArray?,
        now: Long,
    ) {
        if (receipts == null) return

        var viewerWatermark = 0L
        var otherWatermark = 0L
        for (index in 0 until receipts.length()) {
            val receipt = receipts.optJSONObject(index) ?: continue
            val watermark = receipt.optLong("watermark_ms")
            if (watermark <= 0) continue
            if (receipt.optBoolean("from_viewer")) {
                viewerWatermark = maxOf(viewerWatermark, watermark)
            } else {
                otherWatermark = maxOf(otherWatermark, watermark)
            }
        }
        if (viewerWatermark > 0) {
            threadDao.updateReadWatermark(threadId, viewerWatermark, now)
        }
        if (otherWatermark > 0) {
            messageDao.markSeen(threadId, otherWatermark)
        }
    }

    private suspend fun saveViewer(viewer: JSONObject) {
        val entries = buildList {
            add(AppMetaEntity(META_VIEWER_ID, viewer.optString("ig_id")))
            add(AppMetaEntity(META_VIEWER_USERNAME, viewer.optString("username")))
            add(AppMetaEntity(META_VIEWER_NAME, viewer.optString("name")))
            add(AppMetaEntity(META_VIEWER_FBID, viewer.optLong("fbid").toString()))
        }
        entries.filter { it.value.isNotBlank() && it.value != "0" }.forEach { metaDao.put(it) }
    }
}

internal fun threadEntity(
    json: JSONObject,
    previous: ThreadEntity?,
    now: Long,
): ThreadEntity? {
    val id = json.optString("id").trim()
    if (id.isEmpty()) return null

    val preview = json.optJSONObject("last_message")
    val previewTs = preview?.optLong("timestamp_ms") ?: 0L
    val mailboxActivity = json.optLong("last_activity_ms")
    val lastActivity = when {
        mailboxActivity > 0 -> mailboxActivity
        previewTs > 0 -> previewTs
        else -> previous?.lastActivityMs ?: 0L
    }

    return ThreadEntity(
        id = id,
        longThreadId = json.optString("thread_id").ifBlank { previous?.longThreadId.orEmpty() },
        threadFbid = json.optString("thread_fbid").ifBlank { previous?.threadFbid.orEmpty() },
        threadKey = json.optLong("thread_key").takeIf { it != 0L } ?: previous?.threadKey ?: 0L,
        isGroup = if (json.has("is_group")) json.optBoolean("is_group") else previous?.isGroup ?: false,
        subtype = json.optString("subtype").ifBlank { previous?.subtype.orEmpty() },
        title = json.optString("title").ifBlank { previous?.title.orEmpty() },
        avatarUrl = json.optString("avatar_url").ifBlank { previous?.avatarUrl.orEmpty() },
        folder = json.optString("folder").ifBlank { previous?.folder.orEmpty() },
        systemFolder = json.optString("system_folder").ifBlank { previous?.systemFolder.orEmpty() },
        lastActivityMs = lastActivity,
        isMuted = if (json.has("is_muted")) json.optBoolean("is_muted") else previous?.isMuted ?: false,
        isPinned = if (json.has("is_pinned")) json.optBoolean("is_pinned") else previous?.isPinned ?: false,
        markedUnread = if (json.has("marked_as_unread")) {
            json.optBoolean("marked_as_unread")
        } else {
            previous?.markedUnread ?: false
        },
        readWatermarkMs = previous?.readWatermarkMs ?: 0,
        oldestCursor = previous?.oldestCursor.orEmpty(),
        hasMoreOlder = previous?.hasMoreOlder ?: false,
        messagesSyncedAt = previous?.messagesSyncedAt ?: 0,
        lastMessageId = if (preview != null) {
            preview.optString("id")
        } else {
            previous?.lastMessageId.orEmpty()
        },
        lastMessageTs = if (preview != null) previewTs else previous?.lastMessageTs ?: 0,
        lastMessageText = if (preview != null) {
            preview.optString("text")
        } else {
            previous?.lastMessageText.orEmpty()
        },
        lastMessageKind = if (preview != null) {
            preview.optString("kind")
        } else {
            previous?.lastMessageKind.orEmpty()
        },
        lastMessageFromViewer = if (preview != null) {
            preview.optBoolean("is_from_viewer")
        } else {
            previous?.lastMessageFromViewer ?: false
        },
        lastMessageSenderFbid = if (preview != null) {
            preview.optLong("sender_fbid")
        } else {
            previous?.lastMessageSenderFbid ?: 0
        },
        updatedAt = now,
    )
}

internal fun participants(json: JSONObject, threadId: String): List<ParticipantEntity> {
    val array = json.optJSONArray("participants") ?: return emptyList()
    val result = ArrayList<ParticipantEntity>(array.length())
    for (index in 0 until array.length()) {
        val participant = array.optJSONObject(index) ?: continue
        val igId = participant.optString("ig_id").trim()
        if (igId.isEmpty()) continue
        result += ParticipantEntity(
            threadId = threadId,
            igUserId = igId,
            fbid = participant.optLong("fbid"),
            username = participant.optString("username"),
            name = participant.optString("name"),
            avatarUrl = participant.optString("avatar_url"),
            isViewer = participant.optBoolean("is_viewer"),
        )
    }
    return result
}

internal fun messageEntity(
    json: JSONObject,
    threadId: String,
    previous: MessageEntity?,
    now: Long,
): MessageEntity? {
    val id = json.optString("id").trim()
    if (id.isEmpty()) return null

    val status = previous?.status?.takeIf { it == MessageStatus.DELETED } ?: MessageStatus.SENT
    return MessageEntity(
        id = id,
        localId = previous?.localId,
        threadId = threadId,
        senderFbid = json.optLong("sender_fbid"),
        senderName = json.optString("sender_name"),
        timestampMs = json.optLong("timestamp_ms"),
        kind = json.optString("kind").ifBlank { "unknown" },
        text = json.optString("text"),
        replyToId = json.optString("replied_to_message_id"),
        otid = json.optString("offline_threading_id"),
        isFromViewer = json.optBoolean("is_from_viewer"),
        status = status,
        seen = json.optBoolean("seen"),
        mediaCount = json.optInt("media_count"),
        editedAt = previous?.editedAt ?: 0,
        deletedAt = previous?.deletedAt ?: 0,
    )
}

internal fun attachments(json: JSONObject, messageId: String): List<AttachmentEntity> {
    val array = json.optJSONArray("media") ?: return emptyList()
    val result = ArrayList<AttachmentEntity>(array.length())
    for (index in 0 until array.length()) {
        val media = array.optJSONObject(index) ?: continue
        result += AttachmentEntity(
            messageId = messageId,
            partIndex = index,
            kind = media.optString("type").ifBlank { "media" },
            remoteFbid = media.optString("attachment_fbid"),
            url = media.optString("url").ifBlank { media.optString("fallback_url") },
            previewUrl = media.optString("preview_url").ifBlank {
                media.optString("preview_fallback_url")
            },
            width = media.optInt("width"),
            height = media.optInt("height"),
            durationMs = media.optLong("duration_ms"),
        )
    }
    return result
}

internal fun reactions(json: JSONObject, messageId: String): List<ReactionEntity> {
    val array = json.optJSONArray("reactions") ?: return emptyList()
    val result = ArrayList<ReactionEntity>(array.length())
    for (index in 0 until array.length()) {
        val reaction = array.optJSONObject(index) ?: continue
        val emoji = reaction.optString("emoji")
        val sender = reaction.optLong("sender_fbid")
        if (emoji.isEmpty() || sender == 0L) continue
        result += ReactionEntity(
            messageId = messageId,
            senderFbid = sender,
            emoji = emoji,
            isFromViewer = reaction.optBoolean("is_from_viewer"),
            timestampMs = reaction.optLong("timestamp_ms"),
        )
    }
    return result
}
