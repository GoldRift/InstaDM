package com.goldrift.instadm.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Fetches the mailbox and stores it in Room. Serialized so a manual refresh and
 * a realtime-triggered refresh cannot issue overlapping requests.
 */
class MailboxSync(
    private val repository: ProtocolApi,
    private val chatStore: ChatStore,
) {
    private val mutex = Mutex()

    suspend fun fetchAndStore(): JSONObject = mutex.withLock {
        repository.loadMailbox().also { chatStore.ingestMailbox(it) }
    }
}
