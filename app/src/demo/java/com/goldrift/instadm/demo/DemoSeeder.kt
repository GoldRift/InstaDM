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

    /** Threads the demo created on demand for a search result, by thread ID. */
    private val created = mutableMapOf<String, DemoPerson>()

    suspend fun reset() {
        mutex.withLock {
            created.clear()
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

    suspend fun olderConversations(): JSONObject {
        ensureSeeded()
        return checkNotNull(seed).olderConversations
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
        return created[threadId] ?: checkNotNull(seed).replies[threadId]
    }

    /** Other members of a thread, in participant order. */
    suspend fun otherPeople(threadId: String): List<DemoPerson> {
        ensureSeeded()
        return created[threadId]?.let { listOf(it) } ?: checkNotNull(seed).others[threadId].orEmpty()
    }

    /** Demo accounts matching [query], for the offline search screen. */
    suspend fun searchUsers(query: String): List<DemoPerson> {
        ensureSeeded()
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        return (checkNotNull(seed).others.values.flatten() + DemoData.searchOnly)
            .filter { person ->
                person.name.lowercase().contains(needle) ||
                    person.username.lowercase().contains(needle)
            }
            .distinctBy { it.fbid }
            .sortedBy { it.name }
    }

    /** The demo thread that lists [fbid] as a participant, if any. */
    suspend fun threadForPerson(fbid: Long): String? {
        ensureSeeded()
        return checkNotNull(seed).others.entries
            .firstOrNull { (_, people) -> people.any { it.fbid == fbid } }
            ?.key
    }

    /** A search result, whether or not the demo already has a conversation. */
    suspend fun personForFbid(fbid: Long): DemoPerson? {
        ensureSeeded()
        return (checkNotNull(seed).others.values.flatten() + DemoData.searchOnly)
            .firstOrNull { it.fbid == fbid }
    }

    /** Creates the thread for a first message to an account the demo has not messaged. */
    suspend fun createThread(person: DemoPerson): String {
        ensureSeeded()
        val threadId = "demo_new_${person.username}"
        chatStore.ingestThread(DemoData.newThread(threadId, person))
        created[threadId] = person
        return threadId
    }

    private suspend fun seedLocked() {
        val built = DemoData.build(System.currentTimeMillis())
        chatStore.ingestMailbox(built.mailbox)
        built.fullThreads.values.forEach { chatStore.ingestThread(it) }
        seed = built
    }
}
