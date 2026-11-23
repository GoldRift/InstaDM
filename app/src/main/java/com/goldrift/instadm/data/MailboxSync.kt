package com.goldrift.instadm.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** Threads fetched per reconcile pass; one first page, not the whole inbox. */
private const val READ_RECONCILE_LIMIT = 20

/**
 * Syncs the inbox into Room: mailbox pages, then the badge and read state of
 * threads the inbox cannot settle from the cache. Mailbox fetches are serialized
 * so a manual refresh and a realtime-triggered refresh cannot issue overlapping
 * requests.
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
            // Missing badges take the slots first: with a backlog of already
            // unread threads at the head of the activity order they would
            // otherwise use every slot and the cold-start previews would never
            // be fetched.
            val threadIds = (
                chatStore.threadIdsWithUnseenPreview(limit) +
                    chatStore.unreadThreadIds(limit)
            ).distinct().take(limit)
            for (threadId in threadIds) {
                // A read made while the watch was down is not replayed and the
                // mailbox has no read state, so the viewer's receipt on the
                // thread is the only record. An unfetched incoming preview newer
                // than the cache hides a cold-start badge the same way. A failed
                // thread stays unread and the next refresh retries it.
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
