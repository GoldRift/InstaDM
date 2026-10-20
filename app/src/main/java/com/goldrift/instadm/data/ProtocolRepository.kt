package com.goldrift.instadm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Serializes calls into the Go client and owns its lifetime. The Go side keeps
 * the session and socket; this repository only moves JSON across the boundary.
 */
class ProtocolRepository(private val sessionStore: SessionStore) {
    private val mutex = Mutex()
    private var client: GoClient? = null

    suspend fun hasCookies(): Boolean = withContext(Dispatchers.IO) {
        sessionStore.readCookies() != null
    }

    suspend fun reloadSession() {
        mutex.withLock {
            client?.close()
            client = null
        }
    }

    fun close() {
        client?.close()
        client = null
    }

    suspend fun loadMailbox(): JSONObject = withClient { it.mailbox(90) }

    suspend fun loadThread(threadId: String): JSONObject = withClient {
        it.thread(threadId, 20, "", 90)
    }

    suspend fun sendText(threadId: String, text: String): JSONObject = withClient {
        it.sendText(threadId, text)
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
        cachedMedia(cacheDir, baseName)?.let { return@withClient it }

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
        target
    }

    private suspend fun <T> withClient(block: (GoClient) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cookies = sessionStore.readCookies()
                ?: error("cookies.json not found at ${sessionStore.cookieFile.absolutePath}")
            val active = client ?: GoClient(cookies).also { client = it }
            block(active)
        }
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
