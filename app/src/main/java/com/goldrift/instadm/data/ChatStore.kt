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
import com.goldrift.instadm.data.db.ThreadUnreadCount
import com.goldrift.instadm.data.db.ThreadWithParticipants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Messages kept per thread; older rows are pruned after each ingest. */
private const val MESSAGE_RETENTION = 10_000

/** SQLite caps bound parameters per statement at 999 on API 24; stay well under. */
internal const val SQLITE_BIND_CHUNK = 500

/** Kind the Go core assigns to like/reaction activity entries. */
internal const val REACTION_LOG_KIND = "reaction-log"

/**
 * Watermark a read of the chat records: the visible message a receipt
 * names, and hidden activity rows cleared by the same write. The max
 * keeps the watermark monotonic when the hidden row is older.
 */
internal fun coveredReadWatermark(visibleWatermarkMs: Long, activityWatermarkMs: Long): Long =
    maxOf(visibleWatermarkMs, activityWatermarkMs)

private const val META_INBOX_SYNCED_AT = "inbox_synced_at"
private const val META_VIEWER_ID = "viewer_ig_id"
private const val META_VIEWER_USERNAME = "viewer_username"
private const val META_VIEWER_NAME = "viewer_name"
private const val META_VIEWER_FBID = "viewer_fbid"
private const val META_VIEWER_AVATAR = "viewer_avatar"

/** The logged-in account's display data, used by the inbox header. */
data class ViewerProfile(
    val name: String = "",
    val avatarUrl: String = "",
)

/** The persisted paging state after an older page, for the UI state to apply. */
data class OlderPageResult(
    /** Messages that survived the retention prune. */
    val added: Int,
    val oldestCursor: String,
    val hasMoreOlder: Boolean,
)

/**
 * Whether an older page leaves history behind it.
 *
 * Messages the page stored prove the history continues, because Instagram
 * sometimes ends a page early with `has_next_page = false` while older
 * messages remain. Trusting that flag there stranded the reader at the page's
 * start for good. A page that stored nothing (an overlap) only continues while
 * the server still claims more, and a page whose rows the retention prune
 * removed ends it because the history can no longer be shown.
 */
internal fun olderPageHasMore(
    cursorAdvanced: Boolean,
    pageMessageCount: Int,
    added: Int,
    retained: Int,
    serverHasMore: Boolean,
): Boolean {
    if (!cursorAdvanced) return false
    return when {
        retained > 0 -> true
        added > 0 -> false
        else -> serverHasMore && pageMessageCount > 0
    }
}

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
    /** Per-thread message cap; tests lower it to exercise the prune path. */
    private val retention: Int = MESSAGE_RETENTION,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val threadDao = db.threads()
    private val messageDao = db.messages()
    private val metaDao = db.appMeta()

    @Volatile
    private var cachedViewerFbid: Long? = null

    fun observeInbox(): Flow<List<ThreadWithParticipants>> = threadDao.observeInbox()

    /** Unread incoming messages per thread, used by the inbox badges. */
    fun observeUnreadCounts(): Flow<List<ThreadUnreadCount>> = threadDao.observeUnreadCounts()

    /** Incoming messages newer than the viewer's read watermark in one thread. */
    suspend fun unreadCount(threadId: String): Int = threadDao.unreadCount(threadId)

    /**
     * Threads the inbox counts as unread, newest activity first. The inbox
     * reconcile pass fetches these to learn reads made outside this app.
     */
    suspend fun unreadThreadIds(limit: Int): List<String> = threadDao.unreadThreadIds(limit)

    fun observeThread(threadId: String): Flow<List<MessageWithMedia>> =
        messageDao.observeThread(threadId)

    /** Observes the newest [limit] visible messages in descending order. */
    fun observeThreadWindow(threadId: String, limit: Int): Flow<List<MessageWithMedia>> =
        messageDao.observeThreadWindowDesc(threadId, limit)

    fun observeThreadMeta(threadId: String): Flow<ThreadWithParticipants?> =
        threadDao.observeThread(threadId)

    suspend fun threadWithParticipants(threadId: String): ThreadWithParticipants? =
        threadDao.threadWithParticipants(threadId)

    suspend fun viewer(key: String): String? = metaDao.get(key)

    /** Name and profile picture of the logged-in account. */
    suspend fun viewerProfile(): ViewerProfile = ViewerProfile(
        name = metaDao.get(META_VIEWER_NAME).orEmpty(),
        avatarUrl = metaDao.get(META_VIEWER_AVATAR).orEmpty(),
    )

    suspend fun threadExists(threadId: String): Boolean =
        threadId.isNotBlank() && threadDao.thread(threadId) != null

    suspend fun message(messageId: String): MessageEntity? =
        if (messageId.isBlank()) null else messageDao.message(messageId)

    /**
     * The activity entry Instagram wrote for a reaction, matched by the
     * reaction's own timestamp because the realtime delta carries no entry ID.
     */
    suspend fun reactionLog(threadId: String, timestampMs: Long): MessageEntity? =
        messageDao.activityLog(threadId, REACTION_LOG_KIND, timestampMs)

    suspend fun inboxSyncedAt(): Long? = metaDao.get(META_INBOX_SYNCED_AT)?.toLongOrNull()

    /** Applies `PolarisDirectInboxQuery` output: thread metadata and previews. */
    suspend fun ingestMailbox(json: JSONObject) {
        val parsed = withContext(Dispatchers.Default) { parseMailboxPayload(json) } ?: return
        val now = clock()

        db.withTransaction {
            val ids = parsed.threads.map { it.id }
            val existing = chunkedQuery(ids) { threadDao.threads(it) }.associateBy { it.id }

            val rows = parsed.threads.map { it.toEntity(existing[it.id], now) }
            if (rows.isNotEmpty()) threadDao.upsertAll(rows)

            // Participants reference the thread row, so replace them afterwards.
            if (ids.isNotEmpty()) {
                chunkedForEach(ids) { threadDao.deleteParticipantsForThreads(it) }
            }
            if (parsed.participants.isNotEmpty()) {
                threadDao.upsertParticipants(parsed.participants)
            }

            parsed.viewerFbid?.let { cachedViewerFbid = it.takeIf { id -> id > 0 } }
            val metaEntries = parsed.viewerMeta + AppMetaEntity(META_INBOX_SYNCED_AT, now.toString())
            metaDao.putAll(metaEntries)
        }
    }

    /** Applies `IGDThreadDetailQuery` output: thread metadata, messages, receipts. */
    suspend fun ingestThread(json: JSONObject) {
        val parsed = withContext(Dispatchers.Default) { parseThreadPayload(json) } ?: return
        val threadId = parsed.threadId

        val now = clock()
        db.withTransaction {
            val previous = threadDao.thread(threadId)
            if (parsed.thread != null) {
                threadDao.upsert(parsed.thread.toEntity(previous, now))
                threadDao.deleteParticipants(threadId)
                if (parsed.participants.isNotEmpty()) {
                    threadDao.upsertParticipants(parsed.participants)
                }
            } else if (previous == null) {
                threadDao.upsert(ThreadEntity(id = threadId, updatedAt = now))
            }

            upsertServerMessages(threadId, parsed.messages)
            if (previous == null || previous.oldestCursor.isBlank()) {
                threadDao.updateNewestPage(
                    threadId = threadId,
                    // A cursor names where the page stopped, so older messages
                    // may follow even when the server ends this page early; only
                    // an older page that returns nothing proves the start.
                    cursor = parsed.oldestCursor,
                    hasMore = parsed.hasMoreOlder || parsed.oldestCursor.isNotBlank(),
                    syncedAt = now,
                    now = now,
                )
            } else {
                // Refreshing the newest page must not rewind a cursor that older
                // pages have already advanced; only the sync time changes.
                threadDao.markSynced(threadId = threadId, syncedAt = now, now = now)
            }
            applyReadReceipts(threadId, parsed.readReceipts, now)
            updatePreviewFromNewest(threadId, now)
            messageDao.prune(threadId, retention)
        }
    }

    /**
     * Applies an `IGDMessageListOffMsysQuery` page: older messages only.
     * Returns null when the page does not name a thread. A page can overlap
     * what is cached and add nothing while its cursor still moves.
     */
    suspend fun ingestOlderPage(json: JSONObject): OlderPageResult? {
        val parsed = withContext(Dispatchers.Default) { parseOlderPagePayload(json) } ?: return null
        val threadId = parsed.threadId

        val now = clock()
        return db.withTransaction {
            val previous = threadDao.thread(threadId)
            if (previous == null) {
                threadDao.upsert(ThreadEntity(id = threadId, updatedAt = now))
            }
            val requestedCursor = previous?.oldestCursor.orEmpty()
            val before = messageDao.count(threadId)
            upsertServerMessages(threadId, parsed.messages)
            val added = messageDao.count(threadId) - before
            val cursor = parsed.oldestCursor
            // A page can overlap what is cached and add nothing while the cursor
            // still moves; only a cursor that fails to advance would make the
            // scroll loader retry the same page forever.
            val cursorAdvanced = cursor.isNotBlank() &&
                (requestedCursor.isBlank() || cursor != requestedCursor)
            messageDao.prune(threadId, retention)
            val retained = (messageDao.count(threadId) - before).coerceAtLeast(0)
            val hasMore = olderPageHasMore(
                cursorAdvanced = cursorAdvanced,
                pageMessageCount = parsed.messages.size,
                added = added,
                retained = retained,
                serverHasMore = parsed.hasMoreOlder,
            )
            // An empty last page names no cursor; keeping the one that was asked
            // for leaves the thread readable as "history ends here" instead of
            // blanking it, which would re-arm paging from the newest page.
            val nextCursor = cursor.ifBlank { requestedCursor }
            threadDao.updateOlderPage(
                threadId = threadId,
                cursor = nextCursor,
                hasMore = hasMore,
                now = now,
            )
            OlderPageResult(added = retained, oldestCursor = nextCursor, hasMoreOlder = hasMore)
        }
    }

    /** Records the viewer's own read watermark for a thread. */
    suspend fun markThreadRead(threadId: String, watermarkMs: Long) {
        if (threadId.isBlank() || watermarkMs <= 0) return
        threadDao.updateReadWatermark(threadId, watermarkMs, clock())
    }

    /**
     * Advances the read watermark to [visibleWatermarkMs] and over reaction-log
     * rows already unread at call time. Called where the viewer's read is
     * already established (an accepted receipt, or a folder Instagram will not
     * take a receipt for), so the write lands with that read, not before it.
     */
    suspend fun markThreadReadIncludingActivity(threadId: String, visibleWatermarkMs: Long) {
        if (threadId.isBlank()) return
        db.withTransaction {
            val activityMs = messageDao.newestUnreadActivity(threadId, REACTION_LOG_KIND) ?: 0L
            val watermark = coveredReadWatermark(visibleWatermarkMs, activityMs)
            if (watermark > 0) threadDao.updateReadWatermark(threadId, watermark, clock())
        }
    }

    /**
     * Clears reaction-log rows already unread at call time, but only when no
     * visible incoming message is unread: while a receipt is pending, that
     * receipt's own covering write clears the activity, so the badge keeps
     * matching the server until the receipt is accepted.
     */
    suspend fun markActivityReadIfSettled(threadId: String) {
        if (threadId.isBlank()) return
        db.withTransaction {
            if (threadDao.unreadMessageCount(threadId, REACTION_LOG_KIND) > 0) return@withTransaction
            val activityMs = messageDao.newestUnreadActivity(threadId, REACTION_LOG_KIND) ?: 0L
            if (activityMs > 0) threadDao.updateReadWatermark(threadId, activityMs, clock())
        }
    }

    /** The viewer's own FBID, used to key optimistic reactions. */
    suspend fun viewerFbid(): Long {
        cachedViewerFbid?.let { return it }
        return (metaDao.get(META_VIEWER_FBID)?.toLongOrNull() ?: 0L)
            .also { cachedViewerFbid = it }
    }

    /** Applies a `message.new` realtime event. */
    suspend fun ingestRealtimeMessage(threadId: String, message: RealtimeMessage) {
        if (threadId.isBlank() || message.id.isBlank()) return

        val now = clock()
        val serverTimestamp = message.timestampMs.takeIf { it > 0 } ?: now
        db.withTransaction {
            if (threadDao.thread(threadId) == null) {
                threadDao.upsert(ThreadEntity(id = threadId, updatedAt = now))
            }
            val existing = messageDao.message(message.id)
            if (existing?.status == MessageStatus.DELETED) return@withTransaction

            val duplicate = message.offlineThreadingId
                .takeIf { it.isNotBlank() }
                ?.let { messageDao.byOtid(it) }
            // Read the pending row's attachments before deleting it: ON DELETE CASCADE
            // would otherwise drop locally recorded voice waveforms and cached paths.
            val duplicateMedia = duplicate
                ?.takeIf { it.id != message.id }
                ?.let { messageDao.attachments(it.id).associateBy { attachment -> attachment.partIndex } }
                .orEmpty()
            if (duplicate != null && duplicate.id != message.id) {
                messageDao.delete(duplicate.id)
            }

            // The echo of an optimistic reply may omit the quoted snapshot, so
            // fall back to the pending row that was just matched by OTID.
            val previous = existing?.withReplySnapshotFrom(duplicate) ?: duplicate
            val timestamp = sentTimestamp(previous, message.isFromViewer, serverTimestamp)
            val replyToId = message.replyToId.ifBlank { previous?.replyToId.orEmpty() }
            val storedReply = previous?.takeIf { it.replyToId == replyToId }
            val reply = message.reply
            val entity = MessageEntity(
                id = message.id,
                localId = previous?.localId,
                threadId = threadId,
                senderFbid = message.senderFbid,
                senderName = message.senderName,
                timestampMs = timestamp,
                kind = message.kind.ifBlank {
                    if (message.media.isEmpty()) "text" else "media"
                },
                text = message.text,
                replyToId = replyToId,
                replyToText = reply?.text ?: storedReply?.replyToText.orEmpty(),
                replyToSender = reply?.senderName ?: storedReply?.replyToSender.orEmpty(),
                replyToIsFromViewer = reply?.isFromViewer
                    ?: storedReply?.replyToIsFromViewer
                    ?: false,
                replyToKind = reply?.kind ?: storedReply?.replyToKind.orEmpty(),
                replyToMediaCount = reply?.mediaCount ?: storedReply?.replyToMediaCount ?: 0,
                otid = message.offlineThreadingId,
                isFromViewer = message.isFromViewer,
                status = MessageStatus.SENT,
                seen = previous?.seen ?: false,
                mediaCount = message.media.size,
            )
            messageDao.upsertMessage(entity)

            if (message.media.isNotEmpty()) {
                val existingMedia = messageDao.attachments(message.id).associateBy { it.partIndex }
                messageDao.deleteAttachments(message.id)
                messageDao.upsertAttachments(
                    message.media.mapIndexed { index, media ->
                        val item = media.toAttachmentEntity(message.id, index)
                        mergeAttachmentCache(duplicateMedia, existingMedia, index)?.let { cached ->
                            item.copy(
                                localPath = cached.localPath,
                                downloadState = cached.downloadState,
                                waveform = item.waveform ?: cached.waveform?.takeIf { it.isNotBlank() },
                            )
                        } ?: item
                    },
                )
            }

            threadDao.updateLastMessage(
                threadId = threadId,
                messageId = entity.id,
                timestampMs = timestamp,
                text = entity.text,
                kind = entity.kind,
                fromViewer = entity.isFromViewer,
                senderFbid = entity.senderFbid,
                now = now,
            )
            messageDao.prune(threadId, retention)
        }
    }

    /** Stores the server message returned by a successful text send. */
    suspend fun ingestSentText(threadId: String, result: JSONObject, fallbackText: String) {
        val messageId = result.optString("message_id").ifBlank { result.optString("id") }
        if (threadId.isBlank() || messageId.isBlank()) return

        val now = clock()
        val timestamp = result.optLong("timestamp_ms").takeIf { it > 0 } ?: now
        val text = result.optString("text").ifBlank { fallbackText }
        db.withTransaction {
            if (threadDao.thread(threadId) == null) {
                threadDao.upsert(ThreadEntity(id = threadId, updatedAt = now))
            }
            messageDao.upsertMessage(
                MessageEntity(
                    id = messageId,
                    threadId = threadId,
                    timestampMs = timestamp,
                    kind = "text",
                    text = text,
                    otid = result.optString("offline_threading_id"),
                    isFromViewer = true,
                    status = MessageStatus.SENT,
                ),
            )
            threadDao.updateLastMessage(
                threadId = threadId,
                messageId = messageId,
                timestampMs = timestamp,
                text = text,
                kind = "text",
                fromViewer = true,
                senderFbid = viewerFbid(),
                now = now,
            )
            messageDao.prune(threadId, retention)
        }
    }

    /** Replaces a pending text message with the server message after a send. */
    suspend fun completeOutgoingText(
        localMessageId: String,
        threadId: String,
        result: JSONObject,
        fallbackText: String,
    ) {
        val messageId = result.optString("message_id").ifBlank { result.optString("id") }
        val now = clock()
        val serverTimestamp = result.optLong("timestamp_ms").takeIf { it > 0 } ?: now
        val text = result.optString("text").ifBlank { fallbackText }
        db.withTransaction {
            if (messageId.isBlank()) {
                // Without a server ID the row stays local but becomes a normal
                // sent message; a later refresh reconciles it by OTID.
                messageDao.setStatus(localMessageId, MessageStatus.SENT)
                return@withTransaction
            }
            // A realtime echo can land before the HTTP response and replace the
            // pending row with the server one. Reuse whichever row still holds
            // the reply snapshot so the quote never drops out of the bubble.
            val pending = messageDao.message(localMessageId)
            val previous = pending
                ?: messageDao.message(messageId)
                ?: result.optString("offline_threading_id").trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { messageDao.byOtid(it) }
            val timestamp = sentTimestamp(previous, fromViewer = true, serverMs = serverTimestamp)
            if (previous != null && previous.id != messageId) {
                messageDao.delete(previous.id)
            }
            messageDao.upsertMessage(
                MessageEntity(
                    id = messageId,
                    threadId = threadId,
                    timestampMs = timestamp,
                    kind = "text",
                    text = text,
                    replyToId = previous?.replyToId.orEmpty(),
                    replyToText = previous?.replyToText.orEmpty(),
                    replyToSender = previous?.replyToSender.orEmpty(),
                    replyToIsFromViewer = previous?.replyToIsFromViewer ?: false,
                    replyToKind = previous?.replyToKind.orEmpty(),
                    replyToMediaCount = previous?.replyToMediaCount ?: 0,
                    otid = result.optString("offline_threading_id"),
                    isFromViewer = true,
                    status = MessageStatus.SENT,
                ),
            )
            threadDao.updateLastMessage(
                threadId = threadId,
                messageId = messageId,
                timestampMs = timestamp,
                text = text,
                kind = "text",
                fromViewer = true,
                senderFbid = viewerFbid(),
                now = now,
            )
            messageDao.prune(threadId, retention)
        }
    }

    /**
     * Replaces a pending attachment with the server message. The local file is
     * kept until the realtime echo or the next refresh supplies CDN URLs, so
     * the bubble keeps its image instead of flashing empty.
     */
    suspend fun completeOutgoingMedia(
        localMessageId: String,
        threadId: String,
        result: JSONObject,
        kind: String,
        localPath: String,
        width: Int,
        height: Int,
        durationMs: Long,
        waveform: String = "",
    ) {
        val messageId = result.optString("message_id").ifBlank { result.optString("id") }
        val now = clock()
        val serverTimestamp = result.optLong("timestamp_ms").takeIf { it > 0 } ?: now
        db.withTransaction {
            if (messageId.isBlank()) {
                messageDao.setStatus(localMessageId, MessageStatus.SENT)
                val localUri = File(localPath).toURI().toString()
                messageDao.setAttachmentFile(localMessageId, 0, localPath, localUri)
                return@withTransaction
            }
            // An echo may already have swapped the pending row for the server
            // one, so take the send time from whichever row still exists.
            val previous = messageDao.message(localMessageId) ?: messageDao.message(messageId)
            val timestamp = sentTimestamp(previous, fromViewer = true, serverMs = serverTimestamp)
            messageDao.delete(localMessageId)
            messageDao.upsertMessage(
                MessageEntity(
                    id = messageId,
                    threadId = threadId,
                    timestampMs = timestamp,
                    kind = kind,
                    otid = result.optString("offline_threading_id"),
                    isFromViewer = true,
                    status = MessageStatus.SENT,
                    mediaCount = 1,
                ),
            )
            val localUri = File(localPath).toURI().toString()
            messageDao.upsertAttachments(
                listOf(
                    AttachmentEntity(
                        messageId = messageId,
                        partIndex = 0,
                        kind = kind,
                        url = localUri,
                        previewUrl = localUri,
                        width = width,
                        height = height,
                        durationMs = durationMs,
                        waveform = waveform.takeIf { it.isNotBlank() },
                        localPath = localPath,
                        downloadState = AttachmentEntity.DOWNLOAD_READY,
                    ),
                ),
            )
            threadDao.updateLastMessage(
                threadId = threadId,
                messageId = messageId,
                timestampMs = timestamp,
                text = "",
                kind = kind,
                fromViewer = true,
                senderFbid = viewerFbid(),
                now = now,
            )
            messageDao.prune(threadId, retention)
        }
    }

    /** Applies an edit (local action or realtime `message.edit`). */    suspend fun editMessageText(messageId: String, text: String) {
        val now = clock()
        db.withTransaction {
            messageDao.editText(messageId, text, now)
            val message = messageDao.message(messageId) ?: return@withTransaction
            threadDao.setLastMessage(
                threadId = message.threadId,
                messageId = message.id,
                timestampMs = message.timestampMs,
                text = text,
                kind = message.kind,
                fromViewer = message.isFromViewer,
                senderFbid = message.senderFbid,
                now = now,
            )
        }
    }

    /** Applies a local unsend or a realtime `message.delete`. */
    suspend fun tombstoneMessage(messageId: String) {
        val now = clock()
        db.withTransaction {
            val message = messageDao.message(messageId) ?: return@withTransaction
            messageDao.tombstone(messageId, now)
            val newest = messageDao.newest(message.threadId)
            if (newest != null) {
                threadDao.setLastMessage(
                    threadId = message.threadId,
                    messageId = newest.id,
                    timestampMs = newest.timestampMs,
                    text = newest.text,
                    kind = newest.kind,
                    fromViewer = newest.isFromViewer,
                    senderFbid = newest.senderFbid,
                    now = now,
                )
            } else {
                threadDao.clearLastMessage(message.threadId, now)
            }
        }
    }

    /** Upserts one reaction, replacing the sender's previous emoji. */
    suspend fun setReaction(
        messageId: String,
        senderFbid: Long,
        emoji: String,
        isFromViewer: Boolean,
    ) {
        if (messageId.isBlank() || emoji.isBlank()) return
        if (senderFbid == 0L && !isFromViewer) return
        messageDao.upsertReactions(
            listOf(
                ReactionEntity(
                    messageId = messageId,
                    senderFbid = senderFbid,
                    emoji = emoji,
                    isFromViewer = isFromViewer,
                    timestampMs = clock(),
                ),
            ),
        )
    }

    suspend fun removeReaction(messageId: String, senderFbid: Long) {
        if (messageId.isBlank()) return
        messageDao.deleteReaction(messageId, senderFbid)
    }

    suspend fun removeViewerReaction(messageId: String) {
        if (messageId.isBlank()) return
        messageDao.deleteViewerReaction(messageId)
    }

    /** Applies another participant's read receipt to the viewer's messages. */
    suspend fun markMessagesSeen(threadId: String, watermarkMs: Long) {
        if (threadId.isBlank() || watermarkMs <= 0) return
        messageDao.markSeen(threadId, watermarkMs)
    }

    /** Records the local file for an attachment downloaded on demand. */
    suspend fun markAttachmentReady(messageId: String, partIndex: Int, path: String) {
        if (messageId.isBlank() || path.isBlank()) return
        messageDao.markAttachmentReady(messageId, partIndex, path)
    }

    /** Persists a locally extracted waveform for a voice attachment. */
    suspend fun setAttachmentWaveform(messageId: String, partIndex: Int, waveform: List<Float>) {
        if (messageId.isBlank() || waveform.isEmpty()) return
        val encoded = waveformJson(waveform)
        if (encoded.isBlank()) return
        messageDao.setAttachmentWaveform(messageId, partIndex, encoded)
    }

    /** Drops delivered attachments' local paths after the media cache is cleared. */
    suspend fun clearDownloadedMediaFiles() = withContext(Dispatchers.IO) {
        messageDao.clearAttachmentFiles()
    }

    /** Removes every cached thread, message, and queue entry. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        cachedViewerFbid = null
        db.clearAllTables()
    }

    private suspend fun upsertServerMessages(
        threadId: String,
        messages: List<ParsedServerMessage>,
    ) {
        if (messages.isEmpty()) return

        val ids = messages.map { it.id }
        val existingById = chunkedQuery(ids) { messageDao.messagesByIds(it) }.associateBy { it.id }

        val activeMessages = messages.filter { existingById[it.id]?.status != MessageStatus.DELETED }
        if (activeMessages.isEmpty()) return

        val otids = activeMessages.asSequence()
            .mapNotNull { it.otid.takeIf { otid -> otid.isNotEmpty() } }
            .distinct()
            .toList()
        val existingByOtid = if (otids.isEmpty()) {
            emptyMap()
        } else {
            chunkedQuery(otids) { messageDao.messagesByOtids(it) }.groupBy { it.otid }
        }

        val duplicateIds = activeMessages.asSequence()
            .mapNotNull { msg ->
                msg.otid.takeIf { it.isNotEmpty() }
                    ?.let { otid -> existingByOtid[otid]?.firstOrNull { it.id != msg.id }?.id }
            }
            .distinct()
            .toList()
        val duplicateAttachmentsByMsgId = if (duplicateIds.isEmpty()) {
            emptyMap()
        } else {
            chunkedQuery(duplicateIds) { messageDao.attachmentsForMessages(it) }
                .groupBy { it.messageId }
        }

        val activeIds = activeMessages.map { it.id }
        val existingAttachmentsByMsgId = chunkedQuery(activeIds) { messageDao.attachmentsForMessages(it) }
            .groupBy { it.messageId }
        val existingReactionMsgIds = chunkedQuery(activeIds) { messageDao.reactionMessageIds(it) }
            .toHashSet()

        val plan = planServerMessageUpsert(
            threadId = threadId,
            parsed = activeMessages,
            existingById = existingById,
            existingByOtid = existingByOtid,
            duplicateAttachmentsByMsgId = duplicateAttachmentsByMsgId,
            existingAttachmentsByMsgId = existingAttachmentsByMsgId,
            existingReactionMsgIds = existingReactionMsgIds,
        )
        if (plan.messages.isEmpty()) return

        // Delete superseded pending rows after reading their cached attachments
        // and before upserting so ON DELETE CASCADE cannot touch the new rows.
        if (plan.duplicateIdsToDelete.isNotEmpty()) {
            chunkedForEach(plan.duplicateIdsToDelete) { messageDao.deleteMessages(it) }
        }

        // Messages first: attachments and reactions reference the message row.
        messageDao.upsertMessages(plan.messages)

        if (plan.attachmentIdsToDelete.isNotEmpty()) {
            chunkedForEach(plan.attachmentIdsToDelete) { messageDao.deleteAttachmentsForMessages(it) }
        }
        if (plan.attachments.isNotEmpty()) {
            messageDao.upsertAttachments(plan.attachments)
        }

        if (plan.reactionIdsToDelete.isNotEmpty()) {
            chunkedForEach(plan.reactionIdsToDelete) { messageDao.deleteReactionsForMessages(it) }
        }
        if (plan.reactions.isNotEmpty()) {
            messageDao.upsertReactions(plan.reactions)
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
        receipts: ParsedReadReceipts,
        now: Long,
    ) {
        if (receipts.viewerWatermark > 0) {
            threadDao.applyViewerReceipt(threadId, receipts.viewerWatermark, now)
        }
        if (receipts.otherWatermark > 0) {
            messageDao.markSeen(threadId, receipts.otherWatermark)
        }
    }
}

internal inline fun <T, R> chunkedQuery(
    items: List<T>,
    chunkSize: Int = SQLITE_BIND_CHUNK,
    query: (List<T>) -> List<R>,
): List<R> {
    if (items.isEmpty()) return emptyList()
    if (items.size <= chunkSize) return query(items)
    val result = ArrayList<R>(items.size)
    var start = 0
    while (start < items.size) {
        val end = minOf(start + chunkSize, items.size)
        result += query(items.subList(start, end))
        start = end
    }
    return result
}

internal inline fun <T> chunkedForEach(
    items: List<T>,
    chunkSize: Int = SQLITE_BIND_CHUNK,
    action: (List<T>) -> Unit,
) {
    if (items.isEmpty()) return
    if (items.size <= chunkSize) {
        action(items)
        return
    }
    var start = 0
    while (start < items.size) {
        val end = minOf(start + chunkSize, items.size)
        action(items.subList(start, end))
        start = end
    }
}

internal data class ParsedReadReceipts(
    val viewerWatermark: Long = 0L,
    val otherWatermark: Long = 0L,
)

private data class ParsedMailboxPayload(
    val threads: List<ParsedThreadFields>,
    val participants: List<ParticipantEntity>,
    val viewerFbid: Long?,
    val viewerMeta: List<AppMetaEntity>,
)

private data class ParsedThreadPayload(
    val threadId: String,
    val thread: ParsedThreadFields?,
    val participants: List<ParticipantEntity>,
    val messages: List<ParsedServerMessage>,
    val oldestCursor: String,
    val hasMoreOlder: Boolean,
    val readReceipts: ParsedReadReceipts,
)

private data class ParsedOlderPagePayload(
    val threadId: String,
    val messages: List<ParsedServerMessage>,
    val oldestCursor: String,
    val hasMoreOlder: Boolean,
)

private data class ParsedThreadFields(
    val id: String,
    val longThreadId: String,
    val threadFbid: String,
    val threadKey: Long,
    val hasIsGroup: Boolean,
    val isGroup: Boolean,
    val subtype: String,
    val title: String,
    val avatarUrl: String,
    val folder: String,
    val systemFolder: String,
    val mailboxActivityMs: Long,
    val hasIsMuted: Boolean,
    val isMuted: Boolean,
    val hasIsPinned: Boolean,
    val isPinned: Boolean,
    val hasMarkedUnread: Boolean,
    val markedUnread: Boolean,
    val hasPreview: Boolean,
    val previewId: String,
    val previewTs: Long,
    val previewText: String,
    val previewKind: String,
    val previewFromViewer: Boolean,
    val previewSenderFbid: Long,
) {
    fun toEntity(previous: ThreadEntity?, now: Long): ThreadEntity {
        val lastActivity = when {
            mailboxActivityMs > 0 -> mailboxActivityMs
            previewTs > 0 -> previewTs
            else -> previous?.lastActivityMs ?: 0L
        }
        // Only adopt the inbox stub when it is at least as new as the stored
        // preview; a loaded page can be ahead of the mailbox snapshot.
        val adoptPreview = hasPreview &&
            (previous == null || previewTs >= previous.lastMessageTs)
        // A newer inbox stub means the cached page no longer ends at the newest
        // message, so the thread is refetched when it is opened.
        val previewAdvanced = adoptPreview && (
            previous == null ||
                previewTs > previous.lastMessageTs ||
                (previewId.isNotBlank() && previewId != previous.lastMessageId)
            )

        return ThreadEntity(
            id = id,
            longThreadId = longThreadId.ifBlank { previous?.longThreadId.orEmpty() },
            threadFbid = threadFbid.ifBlank { previous?.threadFbid.orEmpty() },
            threadKey = threadKey.takeIf { it != 0L } ?: previous?.threadKey ?: 0L,
            isGroup = if (hasIsGroup) isGroup else previous?.isGroup ?: false,
            subtype = subtype.ifBlank { previous?.subtype.orEmpty() },
            title = title.ifBlank { previous?.title.orEmpty() },
            avatarUrl = avatarUrl.ifBlank { previous?.avatarUrl.orEmpty() },
            folder = folder.ifBlank { previous?.folder.orEmpty() },
            systemFolder = systemFolder.ifBlank { previous?.systemFolder.orEmpty() },
            lastActivityMs = lastActivity,
            isMuted = if (hasIsMuted) isMuted else previous?.isMuted ?: false,
            isPinned = if (hasIsPinned) isPinned else previous?.isPinned ?: false,
            markedUnread = if (hasMarkedUnread) markedUnread else previous?.markedUnread ?: false,
            readWatermarkMs = previous?.readWatermarkMs ?: 0,
            oldestCursor = previous?.oldestCursor.orEmpty(),
            hasMoreOlder = previous?.hasMoreOlder ?: false,
            messagesSyncedAt = if (previewAdvanced) 0 else previous?.messagesSyncedAt ?: 0,
            lastMessageId = if (adoptPreview) previewId else previous?.lastMessageId.orEmpty(),
            lastMessageTs = if (adoptPreview) previewTs else previous?.lastMessageTs ?: 0,
            lastMessageText = if (adoptPreview) previewText else previous?.lastMessageText.orEmpty(),
            lastMessageKind = if (adoptPreview) previewKind else previous?.lastMessageKind.orEmpty(),
            lastMessageFromViewer = if (adoptPreview) {
                previewFromViewer
            } else {
                previous?.lastMessageFromViewer ?: false
            },
            lastMessageSenderFbid = if (adoptPreview) {
                previewSenderFbid
            } else {
                previous?.lastMessageSenderFbid ?: 0
            },
            updatedAt = now,
        )
    }
}

internal data class ParsedServerMessage(
    val id: String,
    val otid: String,
    val senderFbid: Long,
    val senderName: String,
    val timestampMs: Long,
    val kind: String,
    val text: String,
    val replyToId: String,
    val hasQuoted: Boolean,
    val quotedText: String,
    val quotedSender: String,
    val quotedFromViewer: Boolean,
    val quotedKind: String,
    val quotedMediaCount: Int,
    val isFromViewer: Boolean,
    val seen: Boolean,
    val mediaCount: Int,
    val attachments: List<AttachmentEntity>,
    val reactions: List<ReactionEntity>,
) {
    fun toEntity(threadId: String, previous: MessageEntity?): MessageEntity {
        val status = previous?.status?.takeIf { it == MessageStatus.DELETED } ?: MessageStatus.SENT
        val resolvedReplyToId = replyToId.ifBlank { previous?.replyToId.orEmpty() }
        // A page refresh may omit the nested snapshot; keep the one already stored
        // as long as it still describes the same quoted message.
        val storedReply = previous?.takeIf { it.replyToId == resolvedReplyToId }
        return MessageEntity(
            id = id,
            localId = previous?.localId,
            threadId = threadId,
            senderFbid = senderFbid,
            senderName = senderName,
            timestampMs = sentTimestamp(previous, isFromViewer, timestampMs),
            kind = kind,
            text = text,
            replyToId = resolvedReplyToId,
            replyToText = if (hasQuoted) quotedText else storedReply?.replyToText.orEmpty(),
            replyToSender = if (hasQuoted) quotedSender else storedReply?.replyToSender.orEmpty(),
            replyToIsFromViewer = if (hasQuoted) {
                quotedFromViewer
            } else {
                storedReply?.replyToIsFromViewer ?: false
            },
            replyToKind = if (hasQuoted) quotedKind else storedReply?.replyToKind.orEmpty(),
            replyToMediaCount = if (hasQuoted) {
                quotedMediaCount
            } else {
                storedReply?.replyToMediaCount ?: 0
            },
            otid = otid,
            isFromViewer = isFromViewer,
            status = status,
            seen = seen,
            mediaCount = mediaCount,
            editedAt = previous?.editedAt ?: 0,
            deletedAt = previous?.deletedAt ?: 0,
        )
    }
}

internal data class ServerMessageUpsertPlan(
    val messages: List<MessageEntity>,
    val duplicateIdsToDelete: List<String>,
    val attachmentIdsToDelete: List<String>,
    val attachments: List<AttachmentEntity>,
    val reactionIdsToDelete: List<String>,
    val reactions: List<ReactionEntity>,
)

internal fun planServerMessageUpsert(
    threadId: String,
    parsed: List<ParsedServerMessage>,
    existingById: Map<String, MessageEntity>,
    existingByOtid: Map<String, List<MessageEntity>>,
    duplicateAttachmentsByMsgId: Map<String, List<AttachmentEntity>>,
    existingAttachmentsByMsgId: Map<String, List<AttachmentEntity>>,
    existingReactionMsgIds: Set<String>,
): ServerMessageUpsertPlan {
    val messages = ArrayList<MessageEntity>(parsed.size)
    val duplicateIdsToDelete = ArrayList<String>()
    val seenDuplicateIds = HashSet<String>()
    val attachmentIdsToDelete = ArrayList<String>()
    val attachments = ArrayList<AttachmentEntity>()
    val reactionIdsToDelete = ArrayList<String>()
    val reactions = ArrayList<ReactionEntity>()

    for (msg in parsed) {
        val existing = existingById[msg.id]
        if (existing?.status == MessageStatus.DELETED) continue

        val duplicate = msg.otid.takeIf { it.isNotEmpty() }
            ?.let { otid -> existingByOtid[otid]?.firstOrNull { it.id != msg.id } }
        val previous = existing?.withReplySnapshotFrom(duplicate) ?: duplicate
        messages += msg.toEntity(threadId, previous)

        val duplicateMedia = if (duplicate != null) {
            if (seenDuplicateIds.add(duplicate.id)) {
                duplicateIdsToDelete += duplicate.id
            }
            duplicateAttachmentsByMsgId[duplicate.id]
                ?.associateBy { it.partIndex }
                .orEmpty()
        } else {
            emptyMap()
        }

        val existingMediaList = existingAttachmentsByMsgId[msg.id]
        if (!existingMediaList.isNullOrEmpty()) {
            attachmentIdsToDelete += msg.id
        }
        val existingMedia = existingMediaList?.associateBy { it.partIndex }.orEmpty()
        for (item in msg.attachments) {
            attachments += mergeAttachmentCache(duplicateMedia, existingMedia, item.partIndex)
                ?.let { cached ->
                    item.copy(
                        localPath = cached.localPath,
                        downloadState = cached.downloadState,
                        waveform = item.waveform ?: cached.waveform?.takeIf { it.isNotBlank() },
                    )
                }
                ?: item
        }

        if (msg.id in existingReactionMsgIds) {
            reactionIdsToDelete += msg.id
        }
        if (msg.reactions.isNotEmpty()) {
            reactions += msg.reactions
        }
    }

    return ServerMessageUpsertPlan(
        messages = messages,
        duplicateIdsToDelete = duplicateIdsToDelete,
        attachmentIdsToDelete = attachmentIdsToDelete,
        attachments = attachments,
        reactionIdsToDelete = reactionIdsToDelete,
        reactions = reactions,
    )
}

private fun mergeAttachmentCache(
    duplicateMedia: Map<Int, AttachmentEntity>,
    existingMedia: Map<Int, AttachmentEntity>,
    partIndex: Int,
): AttachmentEntity? {
    val existing = existingMedia[partIndex]
    val duplicate = duplicateMedia[partIndex]
    if (existing == null) return duplicate
    if (duplicate == null) return existing
    val downloadState = if (existing.downloadState != AttachmentEntity.DOWNLOAD_NONE) {
        existing.downloadState
    } else {
        duplicate.downloadState
    }
    return existing.copy(
        localPath = existing.localPath.ifBlank { duplicate.localPath },
        downloadState = downloadState,
        waveform = existing.waveform?.takeIf { it.isNotBlank() }
            ?: duplicate.waveform?.takeIf { it.isNotBlank() },
    )
}

private fun parseMailboxPayload(json: JSONObject): ParsedMailboxPayload? {
    val threadsArray = json.optJSONArray("threads") ?: return null
    val threadsById = LinkedHashMap<String, ParsedThreadFields>(threadsArray.length())
    val participantsByThread = LinkedHashMap<String, List<ParticipantEntity>>(threadsArray.length())
    for (index in 0 until threadsArray.length()) {
        val threadJson = threadsArray.optJSONObject(index) ?: continue
        val parsed = parseThreadFields(threadJson) ?: continue
        threadsById[parsed.id] = parsed
        participantsByThread[parsed.id] = participants(threadJson, parsed.id)
    }

    val allParticipants = ArrayList<ParticipantEntity>()
    for (list in participantsByThread.values) {
        allParticipants += list
    }

    val viewerJson = json.optJSONObject("viewer")
    val viewerFbid = viewerJson?.optLong("fbid")
    val viewerMeta = if (viewerJson != null) {
        listOf(
            AppMetaEntity(META_VIEWER_ID, viewerJson.optString("ig_id")),
            AppMetaEntity(META_VIEWER_USERNAME, viewerJson.optString("username")),
            AppMetaEntity(META_VIEWER_NAME, viewerJson.optString("name")),
            AppMetaEntity(META_VIEWER_AVATAR, viewerJson.optString("avatar_url")),
            AppMetaEntity(META_VIEWER_FBID, (viewerFbid ?: 0L).toString()),
        ).filter { it.value.isNotBlank() && it.value != "0" }
    } else {
        emptyList()
    }

    return ParsedMailboxPayload(
        threads = threadsById.values.toList(),
        participants = allParticipants,
        viewerFbid = viewerFbid,
        viewerMeta = viewerMeta,
    )
}

private fun parseThreadPayload(json: JSONObject): ParsedThreadPayload? {
    val threadJson = json.optJSONObject("thread")
    val threadId = threadJson?.optString("id")?.trim().orEmpty()
        .ifBlank { json.optString("thread_id").trim() }
    if (threadId.isEmpty()) return null

    val parsedThread = threadJson?.let(::parseThreadFields)
    val parsedParticipants = if (parsedThread != null) {
        participants(threadJson, threadId)
    } else {
        emptyList()
    }
    return ParsedThreadPayload(
        threadId = threadId,
        thread = parsedThread,
        participants = parsedParticipants,
        messages = parseThreadMessages(json.optJSONArray("messages")),
        oldestCursor = json.optString("oldest_cursor"),
        hasMoreOlder = json.optBoolean("has_more_older"),
        readReceipts = parseReadReceipts(json.optJSONArray("read_receipts")),
    )
}

private fun parseOlderPagePayload(json: JSONObject): ParsedOlderPagePayload? {
    val threadId = json.optString("thread_id").trim()
    if (threadId.isEmpty()) return null
    return ParsedOlderPagePayload(
        threadId = threadId,
        messages = parseThreadMessages(json.optJSONArray("messages")),
        oldestCursor = json.optString("oldest_cursor").trim(),
        hasMoreOlder = json.optBoolean("has_more_older"),
    )
}

private fun parseThreadFields(json: JSONObject): ParsedThreadFields? {
    val id = json.optString("id").trim()
    if (id.isEmpty()) return null
    val preview = json.optJSONObject("last_message")
    return ParsedThreadFields(
        id = id,
        longThreadId = json.optString("thread_id"),
        threadFbid = json.optString("thread_fbid"),
        threadKey = json.optLong("thread_key"),
        hasIsGroup = json.has("is_group"),
        isGroup = json.optBoolean("is_group"),
        subtype = json.optString("subtype"),
        title = json.optString("title"),
        avatarUrl = json.optString("avatar_url"),
        folder = json.optString("folder"),
        systemFolder = json.optString("system_folder"),
        mailboxActivityMs = json.optLong("last_activity_ms"),
        hasIsMuted = json.has("is_muted"),
        isMuted = json.optBoolean("is_muted"),
        hasIsPinned = json.has("is_pinned"),
        isPinned = json.optBoolean("is_pinned"),
        hasMarkedUnread = json.has("marked_as_unread"),
        markedUnread = json.optBoolean("marked_as_unread"),
        hasPreview = preview != null,
        previewId = preview?.optString("id").orEmpty(),
        previewTs = preview?.optLong("timestamp_ms") ?: 0L,
        previewText = preview?.optString("text").orEmpty(),
        previewKind = preview?.optString("kind").orEmpty(),
        previewFromViewer = preview?.optBoolean("is_from_viewer") ?: false,
        previewSenderFbid = preview?.optLong("sender_fbid") ?: 0L,
    )
}

internal fun parseReadReceipts(receipts: JSONArray?): ParsedReadReceipts {
    if (receipts == null) return ParsedReadReceipts()
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
    return ParsedReadReceipts(viewerWatermark = viewerWatermark, otherWatermark = otherWatermark)
}

internal fun parseThreadMessages(messages: JSONArray?): List<ParsedServerMessage> {
    if (messages == null || messages.length() == 0) return emptyList()
    val byId = LinkedHashMap<String, ParsedServerMessage>(messages.length())
    for (index in 0 until messages.length()) {
        val message = messages.optJSONObject(index) ?: continue
        val id = message.optString("id").trim()
        if (id.isEmpty()) continue
        val quoted = message.optJSONObject("replied_to_message")
        byId[id] = ParsedServerMessage(
            id = id,
            otid = message.optString("offline_threading_id").trim(),
            senderFbid = message.optLong("sender_fbid"),
            senderName = message.optString("sender_name"),
            timestampMs = message.optLong("timestamp_ms"),
            kind = message.optString("kind").ifBlank { "unknown" },
            text = message.optString("text"),
            replyToId = message.optString("replied_to_message_id")
                .ifBlank { quoted?.optString("id").orEmpty() },
            hasQuoted = quoted != null,
            quotedText = quoted?.optString("text").orEmpty(),
            quotedSender = quoted?.optString("sender_name").orEmpty(),
            quotedFromViewer = quoted?.optBoolean("is_from_viewer") ?: false,
            quotedKind = quoted?.optString("kind").orEmpty(),
            quotedMediaCount = quoted?.optInt("media_count") ?: 0,
            isFromViewer = message.optBoolean("is_from_viewer"),
            seen = message.optBoolean("seen"),
            mediaCount = message.optInt("media_count"),
            attachments = attachments(message, id),
            reactions = reactions(message, id),
        )
    }
    return byId.values.toList()
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

/**
 * Fills a missing reply snapshot from a superseded row, so a page or echo that
 * omits the nested quote cannot blank a reply that is already queued locally.
 */
private fun MessageEntity.withReplySnapshotFrom(fallback: MessageEntity?): MessageEntity {
    if (fallback == null || replyToId.isNotBlank() || fallback.replyToId.isBlank()) return this
    return copy(
        replyToId = fallback.replyToId,
        replyToText = fallback.replyToText,
        replyToSender = fallback.replyToSender,
        replyToIsFromViewer = fallback.replyToIsFromViewer,
        replyToKind = fallback.replyToKind,
        replyToMediaCount = fallback.replyToMediaCount,
    )
}

/**
 * The viewer's own send time wins over Instagram's, so a staged batch keeps the
 * order it was sent in while each attachment is acked. Other senders have no
 * stored row, so their server time stands.
 */
internal fun sentTimestamp(previous: MessageEntity?, fromViewer: Boolean, serverMs: Long): Long =
    if (fromViewer) previous?.timestampMs?.takeIf { it > 0 } ?: serverMs else serverMs

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
            waveform = media.optJSONArray("waveform")
                ?.let { waveformJson(parseWaveform(it)) }
                ?.takeIf { it.isNotBlank() },
            viewOnce = media.optBoolean("view_once"),
            viewMode = media.optString("view_mode"),
            targetId = media.optString("target_id"),
            shortcode = media.optString("shortcode"),
            targetUrl = media.optString("target_url"),
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

/** Maps a normalized media item into its cache row. */
internal fun MessageMedia.toAttachmentEntity(messageId: String, partIndex: Int): AttachmentEntity =
    AttachmentEntity(
        messageId = messageId,
        partIndex = partIndex,
        kind = type.ifBlank { "media" },
        url = url.ifBlank { fallbackUrl },
        previewUrl = previewUrl.ifBlank { previewFallbackUrl },
        width = width,
        height = height,
        durationMs = durationMs.toLong(),
        waveform = waveformJson(waveform).takeIf { it.isNotBlank() },
        viewOnce = viewOnce,
        viewMode = viewMode,
        targetId = targetId,
        shortcode = shortcode,
        targetUrl = targetUrl,
    )
