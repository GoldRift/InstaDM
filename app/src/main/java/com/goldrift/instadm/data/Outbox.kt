package com.goldrift.instadm.data

import androidx.room.withTransaction
import com.goldrift.instadm.data.db.AttachmentEntity
import com.goldrift.instadm.data.db.InstaDmDatabase
import com.goldrift.instadm.data.db.MessageEntity
import com.goldrift.instadm.data.db.MessageStatus
import com.goldrift.instadm.data.db.OutboxEntity
import com.goldrift.instadm.data.db.OutboxState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/** Sends an outbox row through the Go core. Kept behind an interface for tests. */
interface OutboxSender {
    suspend fun sendText(
        threadId: String,
        text: String,
        replyTo: String,
        otid: String,
    ): JSONObject

    suspend fun sendMedia(
        threadId: String,
        fileName: String,
        mimeType: String,
        data: ByteArray,
        otid: String,
    ): JSONObject
}

class ProtocolOutboxSender(private val repository: ProtocolRepository) : OutboxSender {
    override suspend fun sendText(
        threadId: String,
        text: String,
        replyTo: String,
        otid: String,
    ): JSONObject = repository.sendText(threadId, text, replyTo, otid)

    override suspend fun sendMedia(
        threadId: String,
        fileName: String,
        mimeType: String,
        data: ByteArray,
        otid: String,
    ): JSONObject = repository.sendMedia(threadId, fileName, mimeType, data, otid)
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
    suspend fun enqueueText(threadId: String, text: String, replyTo: String = ""): String {
        val now = clock()
        val localId = "local:${UUID.randomUUID()}"
        val otid = OfflineThreadingIds.next()
        db.withTransaction {
            messageDao.upsertMessage(
                MessageEntity(
                    id = localId,
                    localId = localId,
                    threadId = threadId,
                    timestampMs = now,
                    kind = "text",
                    text = text,
                    replyToId = replyTo,
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
                        .put("reply_to", replyTo)
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
                text = text,
                kind = "text",
                fromViewer = true,
                senderFbid = chatStore.viewerFbid(),
                now = now,
            )
        }
        wake.trySend(Unit)
        return localId
    }

    /**
     * Copies the picked bytes into app-private storage, then queues the upload.
     * The copy is what survives process death; the content URI may not.
     */
    suspend fun enqueueMedia(threadId: String, picked: PickedMedia): String {
        val otid = OfflineThreadingIds.next()
        val extension = extensionFor(picked.mimeType, picked.kind)
        val file = withContext(Dispatchers.IO) {
            val data = mediaReader.read(picked)
            outboxDir.mkdirs()
            File(outboxDir, "$otid.$extension").apply { writeBytes(data) }
        }

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
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            threadDao.updateLastMessage(
                threadId = threadId,
                messageId = localId,
                timestampMs = now,
                text = "",
                kind = picked.kind,
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
            )

            "media" -> {
                val path = payload.optString("file_path")
                val file = File(path)
                if (!file.isFile) error("attachment file is missing")
                sender.sendMedia(
                    threadId = entry.threadId,
                    fileName = payload.optString("file_name"),
                    mimeType = payload.optString("mime_type"),
                    data = withContext(Dispatchers.IO) { file.readBytes() },
                    otid = entry.otid,
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
            )
            pruneMediaCache(mediaCacheDir, MEDIA_CACHE_MAX_BYTES, keep = File(localPath))
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
