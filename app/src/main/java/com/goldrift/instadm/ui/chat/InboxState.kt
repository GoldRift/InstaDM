package com.goldrift.instadm.ui.chat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.goldrift.instadm.data.ProtocolRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

enum class InboxStatus { Loading, Ready, NoSession, Error }

/**
 * Compose state for the inbox and open threads. Network and Go calls run in
 * [ProtocolRepository]; this class only applies the JSON results to UI state.
 */
@Stable
class InboxState(
    private val repository: ProtocolRepository,
    private val scope: CoroutineScope,
) {
    private val items = mutableStateListOf<Conversation>()
    private val loadingThreads = mutableStateMapOf<String, Boolean>()

    var status by mutableStateOf(InboxStatus.Loading)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    val conversations: List<Conversation>
        get() = items

    fun conversation(threadId: String): Conversation? =
        items.firstOrNull { it.id == threadId }

    fun isLoading(threadId: String): Boolean = loadingThreads[threadId] == true

    fun clearError() {
        errorMessage = null
    }

    fun refresh() {
        scope.launch {
            status = InboxStatus.Loading
            errorMessage = null
            repository.reloadSession()

            if (!repository.hasCookies()) {
                items.clear()
                status = InboxStatus.NoSession
                return@launch
            }

            runCatching { repository.loadMailbox() }
                .onSuccess { mailbox ->
                    val existing = items.associateBy { it.id }
                    val updated = mapMailbox(mailbox, existing)
                    items.clear()
                    items.addAll(updated)
                    status = InboxStatus.Ready
                }
                .onFailure { error ->
                    status = InboxStatus.Error
                    errorMessage = describeError(error)
                }
        }
    }

    fun loadThread(threadId: String) {
        val conversation = conversation(threadId) ?: return
        if (conversation.messages.isNotEmpty() || isLoading(threadId)) return

        loadingThreads[threadId] = true
        scope.launch {
            runCatching { repository.loadThread(threadId) }
                .onSuccess { json ->
                    update(threadId) { conversation ->
                        val loaded = mapThread(json, conversation)
                        val loadedIDs = loaded.messages.mapTo(HashSet()) { it.id }
                        val inFlight = conversation.messages.filter { it.id !in loadedIDs }
                        loaded.copy(messages = loaded.messages + inFlight)
                    }
                }
                .onFailure { error ->
                    errorMessage = describeError(error)
                }
            loadingThreads.remove(threadId)
        }
    }

    fun send(threadId: String, rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty() || conversation(threadId) == null) return

        val localID = "local-${UUID.randomUUID()}"
        val local = ChatMessage(
            id = localID,
            text = text,
            sender = MessageSender.Me,
            timestamp = currentTimeLabel(),
            pending = true,
        )
        update(threadId) { it.copy(messages = it.messages + local) }
        errorMessage = null

        scope.launch {
            runCatching { repository.sendText(threadId, text) }
                .onSuccess { result ->
                    val serverID = result.optString("message_id")
                        .ifBlank { result.optString("id") }
                    val timestamp = formatMessageTime(result.optLong("timestamp_ms"))
                    update(threadId) { conversation ->
                        conversation.copy(
                            messages = conversation.messages.map { message ->
                                if (message.id == localID) {
                                    message.copy(
                                        id = serverID.ifBlank { localID },
                                        timestamp = timestamp,
                                        pending = false,
                                    )
                                } else {
                                    message
                                }
                            },
                        )
                    }
                }
                .onFailure { error ->
                    update(threadId) { conversation ->
                        conversation.copy(
                            messages = conversation.messages.filterNot { it.id == localID },
                        )
                    }
                    errorMessage = describeError(error)
                }
        }
    }

    private fun update(threadId: String, transform: (Conversation) -> Conversation) {
        val index = items.indexOfFirst { it.id == threadId }
        if (index >= 0) {
            items[index] = transform(items[index])
        }
    }

    private fun describeError(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
}
