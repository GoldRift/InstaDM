package com.goldrift.instadm.demo

import com.goldrift.instadm.data.ChatStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Builds the demo database once per process. The seeder is the single source
 * for both Room and the fake protocol, so an inbox refresh and a thread open
 * always describe the same conversations.
 */
internal class DemoSeeder(private val chatStore: ChatStore) {
    private val mutex = Mutex()
    private var seed: DemoSeed? = null

    suspend fun reset() {
        mutex.withLock {
            chatStore.clear()
            seedLocked()
        }
    }

    suspend fun ensureSeeded() {
        if (seed != null) return
        mutex.withLock {
            if (seed == null) {
                chatStore.clear()
                seedLocked()
            }
        }
    }

    suspend fun mailbox(): JSONObject {
        ensureSeeded()
        return checkNotNull(seed).mailbox
    }

    suspend fun thread(threadId: String): JSONObject {
        ensureSeeded()
        return checkNotNull(seed).metaThreads[threadId]
            ?: throw IllegalArgumentException("unknown demo thread $threadId")
    }

    suspend fun olderPage(threadId: String): JSONObject? {
        ensureSeeded()
        return checkNotNull(seed).olderPages[threadId]
    }

    suspend fun replyPerson(threadId: String): DemoPerson? {
        ensureSeeded()
        return checkNotNull(seed).replies[threadId]
    }

    private suspend fun seedLocked() {
        val built = DemoData.build(System.currentTimeMillis())
        chatStore.ingestMailbox(built.mailbox)
        built.fullThreads.values.forEach { chatStore.ingestThread(it) }
        seed = built
    }
}
