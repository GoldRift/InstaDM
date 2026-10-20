package com.goldrift.instadm.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

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

    private suspend fun <T> withClient(block: (GoClient) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cookies = sessionStore.readCookies()
                ?: error("cookies.json not found at ${sessionStore.cookieFile.absolutePath}")
            val active = client ?: GoClient(cookies).also { client = it }
            block(active)
        }
    }
}
