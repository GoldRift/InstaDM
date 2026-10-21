package com.goldrift.instadm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Messages per thread page, for both the newest page and older pages. */
private const val THREAD_PAGE_SIZE = 30

/** Downloaded attachments are pruned LRU so the cache stays bounded. */
internal const val MEDIA_CACHE_MAX_BYTES = 250L * 1024 * 1024

/**
 * Serializes calls into the Go client and owns its lifetime. The Go side keeps
 * the session and socket; this repository only moves JSON across the boundary.
 */
class ProtocolRepository(private val sessionStore: SessionStore) {
    private val mutex = Mutex()
    private var client: GoClient? = null
    private var clientCookies: String? = null

    suspend fun hasCookies(): Boolean = withContext(Dispatchers.IO) {
        sessionStore.readCookies() != null
    }

    /**
     * Validates [cookiesJson] through the inbox bootstrap and only then persists
     * it. The returned mailbox feeds the first inbox render without another
     * fetch. A failed candidate leaves any existing session untouched.
     */
    suspend fun signIn(cookiesJson: String): JSONObject = withContext(Dispatchers.IO) {
        mutex.withLock {
            val candidate = GoClient(cookiesJson)
            try {
                val mailbox = candidate.mailbox()
                val persisted = runCatching { candidate.exportCookiesJson() }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: cookiesJson
                sessionStore.saveCookies(persisted)
                client?.close()
                client = candidate
                clientCookies = persisted
                mailbox
            } catch (error: Throwable) {
                candidate.close()
                throw error
            }
        }
    }

    /** Drops the client even when the cookie file is unchanged. */
    suspend fun reloadSession() {
        mutex.withLock {
            client?.close()
            client = null
            clientCookies = null
        }
    }

    fun close() {
        client?.close()
        client = null
        clientCookies = null
    }

    suspend fun loadMailbox(): JSONObject = withClient { it.mailbox(90) }

    suspend fun loadThread(threadId: String): JSONObject = withClient {
        it.thread(threadId, THREAD_PAGE_SIZE)
    }

    /** Fetches the page of messages older than [cursor] (an `oldest_cursor` value). */
    suspend fun loadOlder(threadId: String, cursor: String): JSONObject = withClient {
        it.thread(threadId, THREAD_PAGE_SIZE, cursor)
    }

    suspend fun sendText(
        threadId: String,
        text: String,
        replyTo: String = "",
        otid: String = "",
    ): JSONObject = withClient {
        it.sendText(threadId, text, replyTo, otid)
    }

    /** Uploads one attachment and sends it to an existing thread. */
    suspend fun sendMedia(
        threadId: String,
        fileName: String,
        mimeType: String,
        data: ByteArray,
        otid: String,
        voice: Boolean = false,
        waveformJson: String = "",
    ): JSONObject = withClient {
        it.sendMedia(
            threadId,
            fileName,
            mimeType,
            data,
            otid = otid,
            voice = voice,
            waveformJson = waveformJson,
        )
    }

    /**
     * Marks the thread read up to [messageId]. The long thread ID and target
     * timestamp come from the loaded conversation, so the Go core does not have
     * to fetch the whole thread page again.
     */
    suspend fun markRead(
        threadId: String,
        messageId: String = "",
        longThreadId: String = "",
        timestampMs: Long = 0,
    ): JSONObject = withClient {
        it.markRead(threadId, messageId, longThreadId, timestampMs)
    }

    /** Adds or removes one emoji reaction on [messageId]. */
    suspend fun react(
        threadId: String,
        messageId: String,
        emoji: String,
        remove: Boolean,
    ): JSONObject = withClient {
        it.react(threadId, messageId, emoji, remove)
    }

    /** Replaces the text of one of the viewer's own messages. */
    suspend fun edit(threadId: String, messageId: String, text: String): JSONObject = withClient {
        it.edit(threadId, messageId, text)
    }

    /** Removes one of the viewer's own messages for everyone. */
    suspend fun unsend(threadId: String, messageId: String): JSONObject = withClient {
        it.unsend(threadId, messageId)
    }

    /**
     * Downloads one attachment into the app cache and returns the local file.
     * The Go downloader re-queries the thread, so it uses a fresh signed URL
     * even when the inline thumbnail URL has expired.
     */
    suspend fun downloadMedia(
        threadId: String,
        messageId: String,
        partIndex: Int,
        mediaType: String,
    ): File = withClient {
        val cacheDir = sessionStore.mediaCacheDir
        val baseName = "${safeName(threadId)}_${safeName(messageId)}_$partIndex"
        cachedMedia(cacheDir, baseName)?.let {
            it.setLastModified(System.currentTimeMillis())
            return@withClient it
        }

        val temp = File(cacheDir, "$baseName.download")
        temp.delete()
        val result = it.downloadMedia(threadId, messageId, partIndex, temp.absolutePath)
        val target = File(
            cacheDir,
            "$baseName.${extensionFor(result.optString("mime_type"), mediaType)}",
        )
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        pruneMediaCache(cacheDir, MEDIA_CACHE_MAX_BYTES, keep = target)
        target
    }

    /** Removes every downloaded attachment; safe to call from any screen. */
    suspend fun clearMediaCache() = withContext(Dispatchers.IO) {
        sessionStore.mediaCacheDir.listFiles()?.forEach { runCatching { it.delete() } }
    }

    private suspend fun <T> withClient(block: (GoClient) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cookies = sessionStore.readCookies()
                ?: error("cookies.json not found at ${sessionStore.cookieFile.absolutePath}")
            // Reuse the bootstrapped client until the cookie file changes. Each
            // new client costs a page load and mailbox query.
            val active = client?.takeIf { clientCookies == cookies } ?: run {
                client?.close()
                GoClient(cookies).also {
                    client = it
                    clientCookies = cookies
                }
            }
            val result = block(active)
            persistRotatedCookies(active)
            result
        }
    }

    /**
     * Instagram rotates routing cookies on responses. Persist a changed jar so
     * the next process uses the same values; an incomplete export is refused by
     * the Go side and never replaces the stored session.
     */
    private fun persistRotatedCookies(active: GoClient) {
        val exported = runCatching { active.exportCookiesJson() }.getOrNull() ?: return
        if (exported.isBlank() || exported == clientCookies) return
        runCatching { sessionStore.saveCookies(exported) }
            .onSuccess { clientCookies = exported }
    }

    private fun cachedMedia(directory: File, baseName: String): File? =
        directory.listFiles()
            ?.firstOrNull { file ->
                file.isFile &&
                    file.length() > 0 &&
                    file.name.startsWith("$baseName.") &&
                    !file.name.endsWith(".download")
            }

    private fun extensionFor(mimeType: String, mediaType: String): String = when {
        mimeType.startsWith("audio/mp4") || mimeType == "audio/aac" -> "m4a"
        mimeType.startsWith("audio/mpeg") || mimeType == "audio/mp3" -> "mp3"
        mimeType.startsWith("audio/wav") || mimeType == "audio/x-wav" -> "wav"
        mimeType.startsWith("audio/ogg") || mimeType == "audio/opus" -> "ogg"
        mimeType.startsWith("audio/") || mediaType == "audio" -> "m4a"
        mimeType.startsWith("video/") || mediaType == "video" -> "mp4"
        mimeType == "image/gif" -> "gif"
        mimeType == "image/webp" -> "webp"
        mimeType == "image/png" -> "png"
        mimeType.startsWith("image/") || mediaType == "image" -> "jpg"
        else -> "bin"
    }

    private fun safeName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120)
}

/**
 * Deletes the least recently used files until the cache fits [maxBytes].
 * [keep] (the file just downloaded) is never deleted.
 */
internal fun pruneMediaCache(directory: File, maxBytes: Long, keep: File?) {
    val files = directory.listFiles()?.filter { it.isFile } ?: return
    var total = files.sumOf { it.length() }
    if (total <= maxBytes) return

    val keepPath = keep?.absolutePath
    val oldestFirst = files
        .filter { it.absolutePath != keepPath }
        .sortedBy { it.lastModified() }
    for (file in oldestFirst) {
        if (total <= maxBytes) break
        val size = file.length()
        if (runCatching { file.delete() }.getOrDefault(false)) {
            total -= size
        }
    }
}
