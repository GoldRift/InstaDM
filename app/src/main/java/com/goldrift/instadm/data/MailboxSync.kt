package com.goldrift.instadm.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Threads fetched per reconcile pass; one first page, not the whole inbox. */
private const val READ_RECONCILE_LIMIT = 20

/**
 * Syncs the inbox into Room: mailbox pages, then the read state of threads the
 * inbox still counts as unread. Mailbox fetches are serialized so a manual
 * refresh and a realtime-triggered refresh cannot issue overlapping requests.
 */
class MailboxSync(
    private val repository: ProtocolApi,
    private val chatStore: ChatStore,
) {
    private val mutex = Mutex()

    // Separate from [mutex] so a reconcile pass does not block pull-to-refresh.
    private val readSyncMutex = Mutex()

    suspend fun fetchAndStore(): JSONObject = mutex.withLock {
        repository.loadMailbox().also { chatStore.ingestMailbox(it) }
    }

    /** Appends the older inbox page after [cursor] to Room. */
    suspend fun fetchOlder(cursor: String): JSONObject = mutex.withLock {
        repository.loadOlderConversations(cursor).also { chatStore.ingestMailbox(it) }
    }

    suspend fun reconcileUnreadThreads(limit: Int = READ_RECONCILE_LIMIT) {
        if (!readSyncMutex.tryLock()) return
        try {
            for (threadId in chatStore.unreadThreadIds(limit)) {
                // A read made while the watch was down is not replayed and the
                // mailbox has no read state, so the viewer's receipt on the
                // thread is the only record. A failed thread stays unread and
                // the next refresh retries it.
                try {
                    chatStore.ingestThread(repository.loadThread(threadId))
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Best effort; the badge stays until the next refresh.
                }
            }
        } finally {
            readSyncMutex.unlock()
        }
    }
}
