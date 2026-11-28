package com.goldrift.instadm.data

import androidx.room.withTransaction
import com.goldrift.instadm.data.db.AttachmentEntity
import com.goldrift.instadm.data.db.InstaDmDatabase
import com.goldrift.instadm.data.db.MessageEntity
import com.goldrift.instadm.data.db.MessageStatus
import com.goldrift.instadm.data.db.OutboxEntity
import com.goldrift.instadm.data.db.OutboxState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val MAX_ATTEMPTS = 3
private const val RETRY_BACKOFF_MS = 2_000L

data class ShareEnqueueResult(
    val queuedThreadIds: List<String>,
    val failedMediaCount: Int = 0,
    val lastMediaError: String? = null,
)

/**
 * Stages one independent file per OTID in [outboxDir] while invoking [writePrimary]
 * once. Independent files ensure completing one thread's upload never moves or deletes
 * the copy still queued for another thread.
 */
internal suspend fun stageMediaCopies(
    outboxDir: File,
    otids: List<String>,
    extension: String,
    writePrimary: suspend (File) -> Unit,
): List<File> = withContext(Dispatchers.IO) {
    if (otids.isEmpty()) return@withContext emptyList()
    outboxDir.mkdirs()
    val primary = File(outboxDir, "${otids.first()}.$extension")
    val created = mutableListOf(primary)
    try {
        writePrimary(primary)
        for (otid in otids.drop(1)) {
            val copy = File(outboxDir, "$otid.$extension")
            created += copy
            primary.copyTo(copy, overwrite = true)
        }
        created.toList()
    } catch (error: Throwable) {
        created.forEach { runCatching { it.delete() } }
        throw error
    }
}

/** Sends an outbox row through the Go core. Kept behind an interface for tests. */
interface OutboxSender {
    suspend fun sendText(
        threadId: String,
        text: String,
        replyTo: String,
        otid: String,
        mentionsJson: String = "",
    ): JSONObject

    suspend fun sendMedia(
        threadId: String,
        filePath: String,
        fileName: String,
        mimeType: String,
        otid: String,
        voice: Boolean = false,
        waveformJson: String = "",
    ): JSONObject
}

class ProtocolOutboxSender(private val repository: ProtocolApi) : OutboxSender {
    override suspend fun sendText(
        threadId: String,
        text: String,
        replyTo: String,
        otid: String,
        mentionsJson: String,
    ): JSONObject = repository.sendText(threadId, text, replyTo, otid, mentionsJson)

    override suspend fun sendMedia(
        threadId: String,
        filePath: String,
        fileName: String,
        mimeType: String,
        otid: String,
        voice: Boolean,
        waveformJson: String,
    ): JSONObject = repository.sendMedia(
        threadId = threadId,
        filePath = filePath,
        fileName = fileName,
        mimeType = mimeType,
        otid = otid,
        voice = voice,
        waveformJson = waveformJson,
    )
}

/**
 * Durable send queue. A message is written to Room before the network call, so
 * it survives process death; retries reuse the same OTID so Instagram can
 * deduplicate them.
 */
class Outbox(
    private val db: InstaDmDatabase,
    private val chatStore: ChatStore,
    private val sender: OutboxSender,
    private val mediaReader: MediaReader,
    private val outboxDir: File,
    private val mediaCacheDir: File,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val retryBackoffMs: Long = RETRY_BACKOFF_MS,
) {
    private val threadDao = db.threads()
    private val messageDao = db.messages()
    private val outboxDao = db.outbox()

    private val started = AtomicBoolean(false)
    private val wake = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            outboxDao.resetInFlight()
            drain()
            for (signal in wake) {
                drain()
            }
        }
    }

    /** Queues a text message and returns the local message ID. */
    suspend fun enqueueText(
        threadId: String,
        text: String,
        reply: ReplySnapshot? = null,
        mentions: List<MessageMention> = emptyList(),
        timestampMs: Long = clock(),
    ): String {
        val otid = OfflineThreadingIds.next()
        val localId = db.withTransaction {
            insertTextRow(
                threadId = threadId,
                text = text,
                reply = reply,
                mentions = mentions,
                otid = otid,
                timestampMs = timestampMs,
                viewerFbid = chatStore.viewerFbid(),
            )
        }
        wake.trySend(Unit)
        return localId
    }

    /**
     * Copies the picked bytes into app-private storage, then queues the upload.
     * The copy is what survives process death; the content URI may not.
     */
    suspend fun enqueueMedia(
        threadId: String,
        picked: PickedMedia,
        timestampMs: Long = clock(),
    ): String {
        val otid = OfflineThreadingIds.next()
        val extension = extensionFor(picked.mimeType, picked.kind)
        val file = stageMediaCopies(outboxDir, listOf(otid), extension) { target ->
            mediaReader.copyTo(picked, target)
        }
            .single()

        val localId = try {
            db.withTransaction {
                insertMediaRow(
                    threadId = threadId,
                    picked = picked,
                    file = file,
                    otid = otid,
                    timestampMs = timestampMs,
                    viewerFbid = chatStore.viewerFbid(),
                )
            }
        } catch (error: Throwable) {
            runCatching { file.delete() }
            throw error
        }
        wake.trySend(Unit)
        return localId
    }

    /**
     * Stages shared attachments once per file and queues independent messages
     * across [threadIds] in one database transaction.
     */
    suspend fun enqueueShare(
        threadIds: List<String>,
        text: String,
        media: List<PickedMedia>,
    ): ShareEnqueueResult {
        val targets = threadIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val trimmedText = text.trim()
        if (targets.isEmpty() || (trimmedText.isEmpty() && media.isEmpty())) {
            return ShareEnqueueResult(emptyList())
        }

        data class StagedShareMedia(
            val picked: PickedMedia,
            val otids: List<String>,
            val files: List<File>,
        )

        val staged = mutableListOf<StagedShareMedia>()
        var failedMediaCount = 0
        var lastMediaError: String? = null
        try {
            for (picked in media) {
                val otids = List(targets.size) { OfflineThreadingIds.next() }
                val extension = extensionFor(picked.mimeType, picked.kind)
                try {
                    val files = stageMediaCopies(outboxDir, otids, extension) { target ->
                        mediaReader.copyTo(picked, target)
                    }
                    staged += StagedShareMedia(picked = picked, otids = otids, files = files)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    failedMediaCount++
                    lastMediaError = error.message?.takeIf { it.isNotBlank() }
                        ?: error.javaClass.simpleName
                }
            }
        } catch (cancellation: CancellationException) {
            staged.forEach { item -> item.files.forEach { runCatching { it.delete() } } }
            throw cancellation
        }

        if (staged.isEmpty() && trimmedText.isEmpty()) {
            return ShareEnqueueResult(
                queuedThreadIds = emptyList(),
                failedMediaCount = failedMediaCount,
                lastMediaError = lastMediaError,
            )
        }

        withContext(NonCancellable) {
            try {
                db.withTransaction {
                    val viewerFbid = chatStore.viewerFbid()
                    // Keep timestamps monotonic across threads so OutboxDao.nextPending
                    // drains each thread's media and note together in order.
                    var stamp = clock()
                    targets.forEachIndexed { threadIndex, threadId ->
                        staged.forEach { item ->
                            insertMediaRow(
                                threadId = threadId,
                                picked = item.picked,
                                file = item.files[threadIndex],
                                otid = item.otids[threadIndex],
                                timestampMs = stamp++,
                                viewerFbid = viewerFbid,
                            )
                        }
                        if (trimmedText.isNotEmpty()) {
                            insertTextRow(
                                threadId = threadId,
                                text = trimmedText,
                                reply = null,
                                otid = OfflineThreadingIds.next(),
                                timestampMs = stamp++,
                                viewerFbid = viewerFbid,
                            )
                        }
                    }
                }
            } catch (error: Throwable) {
                staged.forEach { item -> item.files.forEach { runCatching { it.delete() } } }
                throw error
            }
        }
        wake.trySend(Unit)
        return ShareEnqueueResult(
            queuedThreadIds = targets,
            failedMediaCount = failedMediaCount,
            lastMediaError = lastMediaError,
        )
    }

    private suspend fun insertTextRow(
        threadId: String,
        text: String,
        reply: ReplySnapshot?,
        mentions: List<MessageMention> = emptyList(),
        otid: String,
        timestampMs: Long,
        viewerFbid: Long,
    ): String {
        val localId = "local:${UUID.randomUUID()}"
        val encodedMentions = mentionsToJson(mentions)
        messageDao.upsertMessage(
            MessageEntity(
                id = localId,
                localId = localId,
                threadId = threadId,
                timestampMs = timestampMs,
                kind = "text",
                text = text,
                mentionsJson = encodedMentions,
                replyToId = reply?.id.orEmpty(),
                replyToText = reply?.text.orEmpty(),
                replyToSender = reply?.senderName.orEmpty(),
                replyToIsFromViewer = reply?.isFromViewer ?: false,
                replyToKind = reply?.kind.orEmpty(),
                replyToMediaCount = reply?.mediaCount ?: 0,
                otid = otid,
                isFromViewer = true,
                status = MessageStatus.PENDING,
            ),
        )
        outboxDao.insert(
            OutboxEntity(
                threadId = threadId,
                kind = "text",
                payloadJson = JSONObject()
                    .put("text", text)
                    .put("reply_to", reply?.id.orEmpty())
                    .apply { if (encodedMentions.isNotEmpty()) put("mentions", encodedMentions) }
                    .toString(),
                localMessageId = localId,
                otid = otid,
                state = OutboxState.PENDING,
                createdAt = timestampMs,
                updatedAt = timestampMs,
            ),
        )
        threadDao.updateLastMessage(
            threadId = threadId,
            messageId = localId,
            timestampMs = timestampMs,
            text = text,
            kind = "text",
            fromViewer = true,
            senderFbid = viewerFbid,
            now = timestampMs,
        )
        return localId
    }

    private suspend fun insertMediaRow(
        threadId: String,
        picked: PickedMedia,
        file: File,
        otid: String,
        timestampMs: Long,
        viewerFbid: Long,
    ): String {
        val localId = "local:${UUID.randomUUID()}"
        val localUri = file.toURI().toString()
        messageDao.upsertMessage(
            MessageEntity(
                id = localId,
                localId = localId,
                threadId = threadId,
                timestampMs = timestampMs,
                kind = picked.kind,
                otid = otid,
                isFromViewer = true,
                status = MessageStatus.PENDING,
                mediaCount = 1,
            ),
        )
        messageDao.upsertAttachments(
            listOf(
                AttachmentEntity(
                    messageId = localId,
                    partIndex = 0,
                    kind = picked.kind,
                    url = localUri,
                    previewUrl = localUri,
                    width = picked.width,
                    height = picked.height,
                    durationMs = picked.durationMs.toLong(),
                    mimeType = picked.mimeType,
                    localPath = file.absolutePath,
                    downloadState = AttachmentEntity.DOWNLOAD_READY,
                ),
            ),
        )
        outboxDao.insert(
            OutboxEntity(
                threadId = threadId,
                kind = "media",
                payloadJson = JSONObject()
                    .put("file_path", file.absolutePath)
                    .put("file_name", picked.fileName)
                    .put("mime_type", picked.mimeType)
                    .put("kind", picked.kind)
                    .put("width", picked.width)
                    .put("height", picked.height)
                    .put("duration_ms", picked.durationMs)
                    .toString(),
                localMessageId = localId,
                otid = otid,
                state = OutboxState.PENDING,
                createdAt = timestampMs,
                updatedAt = timestampMs,
            ),
        )
        threadDao.updateLastMessage(
            threadId = threadId,
            messageId = localId,
            timestampMs = timestampMs,
            text = "",
            kind = picked.kind,
            fromViewer = true,
            senderFbid = viewerFbid,
            now = timestampMs,
        )
        return localId
    }

    /**
     * Copies a finished voice recording into app-private storage and queues it
     * as a voice note. The recorder writes to the cache, which the system may
     * evict; the copy is what survives process death and retries.
     */
    suspend fun enqueueVoiceNote(threadId: String, recording: RecordedVoice): String {
        val otid = OfflineThreadingIds.next()
        val extension = extensionFor(recording.mimeType, "audio")
        val file = withContext(Dispatchers.IO) {
            if (!recording.file.isFile) error("recording file is missing")
            if (recording.file.length() > MediaPolicy.MAX_MEDIA_BYTES) {
                error("recording is larger than the voice-note limit")
            }
            outboxDir.mkdirs()
            val target = File(outboxDir, "$otid.$extension")
            recording.file.copyTo(target, overwrite = true)
            recording.file.delete()
            target
        }
        val waveform = waveformJson(recording.waveform)

        val now = clock()
        val localId = "local:${UUID.randomUUID()}"
        val localUri = file.toURI().toString()
        db.withTransaction {
            messageDao.upsertMessage(
                MessageEntity(
                    id = localId,
                    localId = localId,
                    threadId = threadId,
                    timestampMs = now,
                    kind = "audio",
                    otid = otid,
                    isFromViewer = true,
                    status = MessageStatus.PENDING,
                    mediaCount = 1,
                ),
            )
            messageDao.upsertAttachments(
                listOf(
                    AttachmentEntity(
                        messageId = localId,
                        partIndex = 0,
                        kind = "audio",
                        url = localUri,
                        previewUrl = localUri,
                        durationMs = recording.durationMs.toLong(),
                        mimeType = recording.mimeType,
                        localPath = file.absolutePath,
                        waveform = waveform.takeIf { it.isNotBlank() },
                        downloadState = AttachmentEntity.DOWNLOAD_READY,
                    ),
                ),
            )
            outboxDao.insert(
                OutboxEntity(
                    threadId = threadId,
                    kind = "media",
                    payloadJson = JSONObject()
                        .put("file_path", file.absolutePath)
                        .put("file_name", recording.fileName)
                        .put("mime_type", recording.mimeType)
                        .put("kind", "audio")
                        .put("duration_ms", recording.durationMs)
                        .put("voice", true)
                        .put("waveform", waveform)
                        .toString(),
                    localMessageId = localId,
                    otid = otid,
                    state = OutboxState.PENDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            threadDao.updateLastMessage(
                threadId = threadId,
                messageId = localId,
                timestampMs = now,
                text = "",
                kind = "audio",
                fromViewer = true,
                senderFbid = chatStore.viewerFbid(),
                now = now,
            )
        }
        wake.trySend(Unit)
        return localId
    }

    /** Re-queues a failed outgoing message. */
    suspend fun retry(localMessageId: String) {
        val entry = outboxDao.byLocalMessage(localMessageId) ?: return
        outboxDao.updateState(entry.id, OutboxState.PENDING, 0, "", clock())
        messageDao.setStatus(localMessageId, MessageStatus.PENDING)
        wake.trySend(Unit)
    }

    /** Processes queued rows until none are left. */
    suspend fun drain() {
        while (scope.isActive) {
            val entry = outboxDao.nextPending() ?: return
            process(entry)
        }
    }

    private suspend fun process(entry: OutboxEntity) {
        val now = clock()
        outboxDao.updateState(entry.id, OutboxState.SENDING, entry.attempts, "", now)

        val result = runCatching { send(entry) }
        result.onSuccess { response ->
            complete(entry, response)
            outboxDao.delete(entry.id)
        }.onFailure { error ->
            val attempts = entry.attempts + 1
            val message = (error.message ?: error.javaClass.simpleName).take(500)
            val localId = entry.localMessageId
            if (attempts >= MAX_ATTEMPTS || error is MediaPickException) {
                outboxDao.updateState(entry.id, OutboxState.FAILED, attempts, message, clock())
                if (localId != null) messageDao.setStatus(localId, MessageStatus.FAILED)
            } else {
                outboxDao.updateState(entry.id, OutboxState.PENDING, attempts, message, clock())
                if (localId != null) messageDao.setStatus(localId, MessageStatus.PENDING)
                delay(retryBackoffMs * attempts)
            }
        }
    }

    private suspend fun send(entry: OutboxEntity): JSONObject {
        val payload = JSONObject(entry.payloadJson)
        return when (entry.kind) {
            "text" -> sender.sendText(
                threadId = entry.threadId,
                text = payload.optString("text"),
                replyTo = payload.optString("reply_to"),
                otid = entry.otid,
                mentionsJson = payload.optString("mentions"),
            )

            "media" -> {
                val path = payload.optString("file_path")
                val exists = withContext(Dispatchers.IO) { File(path).isFile }
                if (!exists) error("attachment file is missing")
                sender.sendMedia(
                    threadId = entry.threadId,
                    filePath = path,
                    fileName = payload.optString("file_name"),
                    mimeType = payload.optString("mime_type"),
                    otid = entry.otid,
                    voice = payload.optBoolean("voice"),
                    waveformJson = payload.optString("waveform"),
                )
            }

            else -> error("unsupported outbox kind ${entry.kind}")
        }
    }

    private suspend fun complete(entry: OutboxEntity, result: JSONObject) {
        val localId = entry.localMessageId ?: return
        val payload = JSONObject(entry.payloadJson)
        if (entry.kind == "text") {
            chatStore.completeOutgoingText(
                localMessageId = localId,
                threadId = entry.threadId,
                result = result,
                fallbackText = payload.optString("text"),
            )
        } else {
            val path = payload.optString("file_path")
            val localPath = moveToMediaCache(entry.otid, path)
            chatStore.completeOutgoingMedia(
                localMessageId = localId,
                threadId = entry.threadId,
                result = result,
                kind = payload.optString("kind"),
                localPath = localPath,
                width = payload.optInt("width"),
                height = payload.optInt("height"),
                durationMs = payload.optLong("duration_ms"),
                waveform = payload.optString("waveform"),
            )
            withContext(Dispatchers.IO) {
                pruneMediaCache(mediaCacheDir, MEDIA_CACHE_MAX_BYTES, keep = File(localPath))
            }
        }
    }

    /**
     * Moves a sent attachment out of the outbox into the bounded media cache so
     * the durable queue directory does not grow forever. The attachment row is
     * then updated with the cache path.
     */
    private suspend fun moveToMediaCache(otid: String, path: String): String =
        withContext(Dispatchers.IO) {
            val source = File(path)
            if (!source.isFile) return@withContext path
            mediaCacheDir.mkdirs()
            val target = File(mediaCacheDir, "outbox_$otid.${source.extension.ifBlank { "bin" }}")
            val moved = runCatching { source.renameTo(target) }.getOrDefault(false)
            if (!moved) {
                runCatching {
                    source.copyTo(target, overwrite = true)
                    source.delete()
                }
            }
            if (target.isFile) target.absolutePath else path
        }

    /** Removes queued work and its copied attachments (used on logout). */
    suspend fun clear() {
        outboxDao.clear()
        withContext(Dispatchers.IO) {
            outboxDir.listFiles()?.forEach { runCatching { it.delete() } }
        }
    }

    private fun extensionFor(mimeType: String, kind: String): String = when {
        mimeType == "image/jpeg" -> "jpg"
        mimeType == "image/png" -> "png"
        mimeType == "image/gif" -> "gif"
        mimeType == "image/webp" -> "webp"
        mimeType.startsWith("video/") || kind == "video" -> "mp4"
        mimeType.startsWith("audio/") || kind == "audio" -> "m4a"
        else -> "bin"
    }
}
