package com.goldrift.instadm.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.Closeable
import java.io.File

/** Messages per thread page, for both the newest page and older pages. */
private const val THREAD_PAGE_SIZE = 30

/** Conversations fetched for the first inbox render and for each older page. */
internal const val INBOX_PAGE_SIZE = 20

/** Downloaded attachments are pruned LRU so the cache stays bounded. */
internal const val MEDIA_CACHE_MAX_BYTES = 250L * 1024 * 1024

internal interface SessionBacking {
    val mediaCacheDir: File
    val cookieLocation: String
    fun readCookies(): String?
    fun saveCookies(cookiesJson: String)
    fun clearCookies()
}

internal interface ProtocolBackend : Closeable {
    fun exportCookiesJson(): String
    fun mailbox(limit: Int): JSONObject
    fun mailboxPage(cursor: String, limit: Int): JSONObject
    fun thread(threadId: String, limit: Int, afterCursor: String = ""): JSONObject
    fun searchUsers(query: String): JSONObject
    fun sendNewDM(recipientFbid: String, text: String, otid: String): JSONObject
    fun sendText(threadId: String, text: String, replyTo: String, otid: String): JSONObject
    fun sendMedia(
        threadId: String,
        filePath: String,
        filename: String,
        mimeType: String,
        otid: String = "",
        voice: Boolean = false,
        waveformJson: String = "",
    ): JSONObject
    fun markRead(
        threadId: String,
        messageId: String,
        longThreadId: String,
        timestampMs: Long,
    ): JSONObject
    fun react(threadId: String, messageId: String, emoji: String, remove: Boolean): JSONObject
    fun edit(threadId: String, messageId: String, text: String): JSONObject
    fun unsend(threadId: String, messageId: String): JSONObject
    fun pinMessage(threadId: String, messageId: String, replaceOldest: Boolean): JSONObject
    fun unpinMessage(threadId: String, messageId: String): JSONObject
    fun downloadMedia(
        threadId: String,
        messageId: String,
        part: Int,
        destPath: String,
    ): JSONObject
    fun downloadSharedMedia(
        mediaId: String,
        shortcode: String,
        destPath: String,
    ): JSONObject
}

private class GoClientBackend(private val inner: GoClient) : ProtocolBackend {
    override fun exportCookiesJson(): String = inner.exportCookiesJson()
    override fun mailbox(limit: Int): JSONObject = inner.mailbox(limit)
    override fun mailboxPage(cursor: String, limit: Int): JSONObject = inner.mailboxPage(cursor, limit)
    override fun thread(threadId: String, limit: Int, afterCursor: String): JSONObject =
        inner.thread(threadId, limit, afterCursor)
    override fun searchUsers(query: String): JSONObject = inner.searchUsers(query)
    override fun sendNewDM(recipientFbid: String, text: String, otid: String): JSONObject =
        inner.sendNewDM(recipientFbid, text, otid)
    override fun sendText(threadId: String, text: String, replyTo: String, otid: String): JSONObject =
        inner.sendText(threadId, text, replyTo, otid)
    override fun sendMedia(
        threadId: String,
        filePath: String,
        filename: String,
        mimeType: String,
        otid: String,
        voice: Boolean,
        waveformJson: String,
    ): JSONObject = inner.sendMedia(
        threadId = threadId,
        filePath = filePath,
        filename = filename,
        mimeType = mimeType,
        otid = otid,
        voice = voice,
        waveformJson = waveformJson,
    )
    override fun markRead(
        threadId: String,
        messageId: String,
        longThreadId: String,
        timestampMs: Long,
    ): JSONObject = inner.markRead(threadId, messageId, longThreadId, timestampMs)
    override fun react(threadId: String, messageId: String, emoji: String, remove: Boolean): JSONObject =
        inner.react(threadId, messageId, emoji, remove)
    override fun edit(threadId: String, messageId: String, text: String): JSONObject =
        inner.edit(threadId, messageId, text)
    override fun unsend(threadId: String, messageId: String): JSONObject =
        inner.unsend(threadId, messageId)
    override fun pinMessage(threadId: String, messageId: String, replaceOldest: Boolean): JSONObject =
        inner.pinMessage(threadId, messageId, replaceOldest)
    override fun unpinMessage(threadId: String, messageId: String): JSONObject =
        inner.unpinMessage(threadId, messageId)
    override fun downloadMedia(
        threadId: String,
        messageId: String,
        part: Int,
        destPath: String,
    ): JSONObject = inner.downloadMedia(threadId, messageId, part, destPath)
    override fun downloadSharedMedia(
        mediaId: String,
        shortcode: String,
        destPath: String,
    ): JSONObject = inner.downloadSharedMedia(mediaId, shortcode, destPath)
    override fun close() = inner.close()
}

/**
 * Owns the Go clients and moves JSON across the boundary.
 *
 * Interactive RPCs and CDN downloads share [rpcClient] (so downloads hit the
 * warm thread cache without re-bootstrapping, while Go's internal RPC lock
 * releases before CDN streaming). Large uploads run on a separate lazy
 * [uploadClient] so a 25 MiB upload never stalls text sends, thread loads,
 * reactions, or read receipts.
 */
class ProtocolRepository internal constructor(
    private val sessionStore: SessionBacking,
    private val clientFactory: (String) -> ProtocolBackend = { GoClientBackend(GoClient(it)) },
) : ProtocolApi {
    constructor(sessionStore: SessionStore) : this(sessionStore as SessionBacking)

    private val stateMutex = Mutex()
    private val uploadMutex = Mutex()
    private val downloadMutex = Mutex()
    private val inFlightDownloads = HashMap<String, CompletableDeferred<File>>()

    private var sessionGeneration = 0L
    private var rpcClient: ProtocolBackend? = null
    private var rpcCookies: String? = null
    private var uploadClient: ProtocolBackend? = null
    private var uploadCookies: String? = null
    private var lastPersistedCookies: String? = null

    override suspend fun hasCookies(): Boolean = withContext(Dispatchers.IO) {
        sessionStore.readCookies() != null
    }

    /**
     * Validates [cookiesJson] through the inbox bootstrap and only then persists
     * it. The returned mailbox feeds the first inbox render without another
     * fetch. A failed candidate leaves any existing session untouched.
     */
    override suspend fun signIn(cookiesJson: String): JSONObject = withContext(Dispatchers.IO) {
        val candidate = clientFactory(cookiesJson)
        try {
            val mailbox = candidate.mailbox(INBOX_PAGE_SIZE)
            val persisted = runCatching { candidate.exportCookiesJson() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: cookiesJson
            stateMutex.withLock {
                sessionStore.saveCookies(persisted)
                sessionGeneration++
                rpcClient?.close()
                uploadClient?.close()
                rpcClient = candidate
                rpcCookies = persisted
                uploadClient = null
                uploadCookies = null
                lastPersistedCookies = persisted
            }
            mailbox
        } catch (error: Throwable) {
            candidate.close()
            throw error
        }
    }

    override suspend fun signOut(): Unit = withContext(Dispatchers.IO) {
        stateMutex.withLock {
            sessionGeneration++
            rpcClient?.close()
            uploadClient?.close()
            rpcClient = null
            rpcCookies = null
            uploadClient = null
            uploadCookies = null
            lastPersistedCookies = null
            sessionStore.clearCookies()
        }
    }

    /** Drops the clients even when the cookie file is unchanged. */
    suspend fun reloadSession() {
        stateMutex.withLock {
            sessionGeneration++
            rpcClient?.close()
            uploadClient?.close()
            rpcClient = null
            rpcCookies = null
            uploadClient = null
            uploadCookies = null
            lastPersistedCookies = null
        }
    }

    fun close() {
        rpcClient?.close()
        uploadClient?.close()
        rpcClient = null
        rpcCookies = null
        uploadClient = null
        uploadCookies = null
        lastPersistedCookies = null
    }

    override suspend fun loadMailbox(): JSONObject = withRpcClient { it.mailbox(INBOX_PAGE_SIZE) }

    /** Fetches the older inbox page after [cursor] (an `end_cursor` value). */
    override suspend fun loadOlderConversations(cursor: String): JSONObject = withRpcClient {
        it.mailboxPage(cursor, INBOX_PAGE_SIZE)
    }

    override suspend fun loadThread(threadId: String): JSONObject = withRpcClient {
        it.thread(threadId, THREAD_PAGE_SIZE)
    }

    override suspend fun searchUsers(query: String): JSONObject = withRpcClient {
        it.searchUsers(query)
    }

    override suspend fun sendNewDm(recipientFbid: String, text: String, otid: String): JSONObject =
        withRpcClient { it.sendNewDM(recipientFbid, text, otid) }

    /** Fetches the page of messages older than [cursor] (an `oldest_cursor` value). */
    override suspend fun loadOlder(threadId: String, cursor: String): JSONObject = withRpcClient {
        it.thread(threadId, THREAD_PAGE_SIZE, cursor)
    }

    override suspend fun sendText(
        threadId: String,
        text: String,
        replyTo: String,
        otid: String,
    ): JSONObject = withRpcClient {
        it.sendText(threadId, text, replyTo, otid)
    }

    /** Uploads one attachment and sends it to an existing thread on the upload lane. */
    override suspend fun sendMedia(
        threadId: String,
        filePath: String,
        fileName: String,
        mimeType: String,
        otid: String,
        voice: Boolean,
        waveformJson: String,
    ): JSONObject = withUploadClient {
        it.sendMedia(
            threadId = threadId,
            filePath = filePath,
            filename = fileName,
            mimeType = mimeType,
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
    override suspend fun markRead(
        threadId: String,
        messageId: String,
        longThreadId: String,
        timestampMs: Long,
    ): JSONObject = withRpcClient {
        it.markRead(threadId, messageId, longThreadId, timestampMs)
    }

    /** Adds or removes one emoji reaction on [messageId]. */
    override suspend fun react(
        threadId: String,
        messageId: String,
        emoji: String,
        remove: Boolean,
    ): JSONObject = withRpcClient {
        it.react(threadId, messageId, emoji, remove)
    }

    /** Replaces the text of one of the viewer's own messages. */
    override suspend fun edit(threadId: String, messageId: String, text: String): JSONObject =
        withRpcClient { it.edit(threadId, messageId, text) }

    /** Removes one of the viewer's own messages for everyone. */
    override suspend fun unsend(threadId: String, messageId: String): JSONObject = withRpcClient {
        it.unsend(threadId, messageId)
    }

    override suspend fun pinMessage(
        threadId: String,
        messageId: String,
        replaceOldest: Boolean,
    ): JSONObject = withRpcClient {
        it.pinMessage(threadId, messageId, replaceOldest)
    }

    override suspend fun unpinMessage(threadId: String, messageId: String): JSONObject =
        withRpcClient { it.unpinMessage(threadId, messageId) }

    /**
     * Downloads one attachment into the app cache and returns the local file.
     * Cache hits return without touching the Go client; concurrent requests for
     * the same attachment share one transfer; distinct attachments stream in
     * parallel without stalling interactive RPCs.
     */
    override suspend fun downloadMedia(
        threadId: String,
        messageId: String,
        media: MessageMedia,
    ): File = withContext(Dispatchers.IO) {
        val cacheDir = sessionStore.mediaCacheDir
        // The same reel can be shared in several messages; key its cache entry
        // by the media ID so it is downloaded only once.
        val baseName = if (media.targetId.isNotBlank()) {
            "reel_${safeName(media.targetId)}"
        } else {
            "${safeName(threadId)}_${safeName(messageId)}_${media.partIndex}"
        }
        cachedMedia(cacheDir, baseName)?.let {
            it.setLastModified(System.currentTimeMillis())
            return@withContext it
        }

        var isOwner = false
        val deferred = downloadMutex.withLock {
            inFlightDownloads[baseName] ?: CompletableDeferred<File>().also {
                inFlightDownloads[baseName] = it
                isOwner = true
            }
        }
        if (!isOwner) return@withContext deferred.await()

        try {
            val file = cachedMedia(cacheDir, baseName)?.also {
                it.setLastModified(System.currentTimeMillis())
            } ?: performMediaDownload(cacheDir, baseName, threadId, messageId, media)
            deferred.complete(file)
            file
        } catch (error: Throwable) {
            deferred.completeExceptionally(error)
            throw error
        } finally {
            downloadMutex.withLock {
                if (inFlightDownloads[baseName] === deferred) {
                    inFlightDownloads.remove(baseName)
                }
            }
        }
    }

    private suspend fun performMediaDownload(
        cacheDir: File,
        baseName: String,
        threadId: String,
        messageId: String,
        media: MessageMedia,
    ): File {
        val (active, generation) = acquireRpcClient()
        val temp = File(cacheDir, "$baseName.download")
        temp.delete()
        val result = if (media.targetId.isNotBlank()) {
            active.downloadSharedMedia(media.targetId, media.shortcode, temp.absolutePath)
        } else {
            active.downloadMedia(threadId, messageId, media.partIndex, temp.absolutePath)
        }
        persistRotatedCookies(active, generation)
        val target = File(
            cacheDir,
            "$baseName.${extensionFor(result.optString("mime_type"), media.type)}",
        )
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        pruneMediaCache(cacheDir, MEDIA_CACHE_MAX_BYTES, keep = target)
        return target
    }

    /** Removes every downloaded attachment; safe to call from any screen. */
    override suspend fun clearMediaCache(): Unit = withContext(Dispatchers.IO) {
        sessionStore.mediaCacheDir.listFiles()?.forEach { runCatching { it.delete() } }
        Unit
    }

    private suspend fun <T> withRpcClient(block: (ProtocolBackend) -> T): T =
        withContext(Dispatchers.IO) {
            val (active, generation) = acquireRpcClient()
            val result = block(active)
            persistRotatedCookies(active, generation)
            result
        }

    private suspend fun <T> withUploadClient(block: (ProtocolBackend) -> T): T =
        withContext(Dispatchers.IO) {
            uploadMutex.withLock {
                val (active, generation) = acquireUploadClient()
                val result = block(active)
                persistRotatedCookies(active, generation)
                result
            }
        }

    private suspend fun acquireRpcClient(): Pair<ProtocolBackend, Long> = stateMutex.withLock {
        val cookies = sessionStore.readCookies()
            ?: error("cookies.json not found at ${sessionStore.cookieLocation}")
        if (lastPersistedCookies != null && cookies != lastPersistedCookies) {
            rpcClient?.close()
            uploadClient?.close()
            rpcClient = null
            uploadClient = null
            rpcCookies = null
            uploadCookies = null
        }
        // Reuse the bootstrapped client until the stored session changes. Each
        // new client costs a page load and mailbox query.
        val active = rpcClient ?: clientFactory(cookies).also {
            rpcClient = it
            rpcCookies = cookies
            lastPersistedCookies = cookies
        }
        active to sessionGeneration
    }

    private suspend fun acquireUploadClient(): Pair<ProtocolBackend, Long> = stateMutex.withLock {
        val cookies = sessionStore.readCookies()
            ?: error("cookies.json not found at ${sessionStore.cookieLocation}")
        if (lastPersistedCookies != null && cookies != lastPersistedCookies) {
            rpcClient?.close()
            uploadClient?.close()
            rpcClient = null
            uploadClient = null
            rpcCookies = null
            uploadCookies = null
        }
        val active = uploadClient ?: clientFactory(cookies).also {
            uploadClient = it
            uploadCookies = cookies
            lastPersistedCookies = cookies
        }
        active to sessionGeneration
    }

    /**
     * Instagram rotates routing cookies on responses. Persist a changed jar so
     * the next process uses the same values; an incomplete export is refused by
     * the Go side and never replaces the stored session.
     */
    private suspend fun persistRotatedCookies(active: ProtocolBackend, generation: Long) {
        val exported = runCatching { active.exportCookiesJson() }.getOrNull() ?: return
        if (exported.isBlank()) return
        stateMutex.withLock {
            if (generation != sessionGeneration || exported == lastPersistedCookies) return
            runCatching { sessionStore.saveCookies(exported) }
                .onSuccess {
                    lastPersistedCookies = exported
                    if (rpcClient === active) rpcCookies = exported
                    if (uploadClient === active) uploadCookies = exported
                }
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
        mimeType.startsWith("audio/mp4") || mimeType == "audio/aac" -> "m4a"
        mimeType.startsWith("audio/mpeg") || mimeType == "audio/mp3" -> "mp3"
        mimeType.startsWith("audio/wav") || mimeType == "audio/x-wav" -> "wav"
        mimeType.startsWith("audio/ogg") || mimeType == "audio/opus" -> "ogg"
        mimeType.startsWith("audio/") || mediaType == "audio" -> "m4a"
        mimeType.startsWith("video/") || mediaType == "video" || mediaType == "reel" -> "mp4"
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
