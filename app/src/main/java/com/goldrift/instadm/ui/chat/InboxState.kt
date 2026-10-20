package com.goldrift.instadm.ui.chat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.goldrift.instadm.data.ProtocolRepository
import com.goldrift.instadm.data.RealtimeBus
import com.goldrift.instadm.data.RealtimeEvent
import com.goldrift.instadm.data.messagePlaceholder
import com.goldrift.instadm.service.AppVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class InboxStatus { Loading, Ready, NoSession, Error }

/** Message requests must be accepted before they are marked read. */
private val READ_REQUEST_FOLDERS = setOf("PENDING", "SPAM")

/**
 * Compose state for the inbox and open threads. Network and Go calls run in
 * [ProtocolRepository]; this class only applies JSON and realtime results to UI
 * state.
 */
@Stable
class InboxState(
    private val repository: ProtocolRepository,
    private val scope: CoroutineScope,
) {
    private val items = mutableStateListOf<Conversation>()
    private val loadingThreads = mutableStateMapOf<String, Boolean>()
    private val markedRead = mutableMapOf<String, String>()
    private var refreshInFlight = false

    var status by mutableStateOf(InboxStatus.Loading)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    init {
        scope.launch {
            RealtimeBus.events.collect { event -> applyRealtimeEvent(event) }
        }
    }

    val conversations: List<Conversation>
        get() = items

    fun conversation(threadId: String): Conversation? =
        items.firstOrNull { it.id == threadId }

    fun isLoading(threadId: String): Boolean = loadingThreads[threadId] == true

    fun clearError() {
        errorMessage = null
    }

    fun refresh() {
        if (refreshInFlight) return
        refreshInFlight = true
        scope.launch {
            try {
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
            } finally {
                refreshInFlight = false
            }
        }
    }

    fun loadThread(threadId: String) {
        val conversation = conversation(threadId) ?: return
        if (conversation.messagesLoaded || isLoading(threadId)) return

        loadingThreads[threadId] = true
        scope.launch {
            runCatching { repository.loadThread(threadId) }
                .onSuccess { json ->
                    update(threadId) { current ->
                        val loaded = mapThread(json, current)
                        val loadedIDs = loaded.messages.mapTo(HashSet()) { it.id }
                        val inFlight = current.messages.filter { message ->
                            message.id !in loadedIDs &&
                                loaded.messages.none { loadedMessage ->
                                    message.pending &&
                                        loadedMessage.sender == message.sender &&
                                        loadedMessage.text == message.text
                                }
                        }
                        loaded.copy(messages = loaded.messages + inFlight)
                    }
                    markRead(threadId)
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
        val localTimestamp = System.currentTimeMillis()
        val local = ChatMessage(
            id = localID,
            text = text,
            sender = MessageSender.Me,
            timestamp = formatMessageTime(localTimestamp),
            timestampMs = localTimestamp,
            pending = true,
        )
        update(threadId) { it.copy(messages = it.messages + local) }
        errorMessage = null

        scope.launch {
            runCatching { repository.sendText(threadId, text) }
                .onSuccess { result ->
                    val serverID = result.optString("message_id")
                        .ifBlank { result.optString("id") }
                    val serverTimestamp = result.optLong("timestamp_ms")
                    update(threadId) { conversation ->
                        conversation.copy(
                            messages = conversation.messages.map { message ->
                                if (message.id == localID) {
                                    val timestampMs = serverTimestamp
                                        .takeIf { it > 0 }
                                        ?: message.timestampMs
                                    message.copy(
                                        id = serverID.ifBlank { localID },
                                        timestamp = formatMessageTime(timestampMs),
                                        timestampMs = timestampMs,
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

    /**
     * Marks the thread read up to [messageId], or the newest server message when
     * blank. Each target is sent once; failures clear the marker so the next
     * view or incoming message can retry. Only runs while the app is visible.
     */
    fun markRead(threadId: String, messageId: String = "") {
        if (!AppVisibility.isForeground) return
        val conversation = conversation(threadId) ?: return
        if (conversation.systemFolder in READ_REQUEST_FOLDERS) return

        val target = messageId.trim().ifBlank {
            conversation.messages.lastOrNull { it.canMarkRead() }?.id.orEmpty()
        }
        if (target.isBlank() || markedRead[threadId] == target) return

        markedRead[threadId] = target
        scope.launch {
            runCatching { repository.markRead(threadId, target) }
                .onFailure {
                    if (markedRead[threadId] == target) {
                        markedRead.remove(threadId)
                    }
                }
        }
    }

    private fun applyRealtimeEvent(event: RealtimeEvent) {
        when (event.type) {
            "message.new", "message.admin" -> applyNewMessage(event)
            "message.edit" -> applyEdit(event)
            "message.delete" -> applyDelete(event)
            "read.receipt" -> applyReadReceipt(event)
        }
    }

    private fun applyNewMessage(event: RealtimeEvent) {
        val message = event.message ?: return
        if (event.threadId.isBlank() || message.id.isBlank()) return

        val index = indexOfThread(event.threadId)
        if (index < 0) {
            refresh()
            return
        }

        val timestamp = message.timestampMs.takeIf { it > 0 }
            ?: event.timestampMs.takeIf { it > 0 }
            ?: System.currentTimeMillis()
        val incoming = ChatMessage(
            id = message.id,
            text = message.text.ifBlank {
                if (message.media.isEmpty()) messagePlaceholder(message.kind) else ""
            },
            sender = if (message.isFromViewer) MessageSender.Me else MessageSender.Them,
            timestamp = formatMessageTime(timestamp),
            timestampMs = timestamp,
            media = message.media,
        )

        val conversation = items[index]
        val messages = conversation.messages.toMutableList()
        val existingIndex = messages.indexOfFirst { it.id == incoming.id }
        if (existingIndex >= 0) {
            messages[existingIndex] = incoming.copy(seen = messages[existingIndex].seen)
        } else {
            val pendingIndex = if (message.isFromViewer) {
                messages.indexOfFirst {
                    it.pending && it.sender == MessageSender.Me && it.text == incoming.text
                }
            } else {
                -1
            }
            if (pendingIndex >= 0) {
                messages[pendingIndex] = incoming
            } else {
                messages += incoming
            }
        }

        items[index] = conversation.copy(
            messages = messages,
            lastActivityMs = maxOf(conversation.lastActivityMs, timestamp),
            preview = previewFor(incoming),
        )
        resortConversations()
    }

    private fun applyEdit(event: RealtimeEvent) {
        val message = event.message ?: return
        if (event.threadId.isBlank() || message.id.isBlank()) return

        val index = indexOfThread(event.threadId)
        if (index < 0) return
        val conversation = items[index]
        val messageIndex = conversation.messages.indexOfFirst { it.id == message.id }
        if (messageIndex < 0) return

        val messages = conversation.messages.toMutableList()
        messages[messageIndex] = messages[messageIndex].copy(
            text = message.text.ifBlank { messages[messageIndex].text },
        )
        items[index] = conversation.copy(
            messages = messages,
            preview = previewFor(messages.lastOrNull()),
        )
    }

    private fun applyDelete(event: RealtimeEvent) {
        val message = event.message ?: return
        if (event.threadId.isBlank() || message.id.isBlank()) return

        val index = indexOfThread(event.threadId)
        if (index < 0) return
        val conversation = items[index]
        if (conversation.messages.none { it.id == message.id }) return

        val messages = conversation.messages.filterNot { it.id == message.id }
        items[index] = conversation.copy(
            messages = messages,
            preview = previewFor(messages.lastOrNull()),
        )
    }

    private fun applyReadReceipt(event: RealtimeEvent) {
        val watermark = event.timestampMs
        if (watermark <= 0 || event.threadId.isBlank()) return

        val index = indexOfThread(event.threadId)
        if (index < 0) return
        val conversation = items[index]
        val messages = applyReadReceiptToMessages(
            conversation.messages,
            watermark,
            event.fromViewer,
        )
        if (messages !== conversation.messages) {
            items[index] = conversation.copy(messages = messages)
        }
    }

    /** Downloads an attachment through the Go client into the app cache. */
    suspend fun downloadMedia(
        threadId: String,
        messageId: String,
        partIndex: Int,
        mediaType: String,
    ): File = repository.downloadMedia(threadId, messageId, partIndex, mediaType)

    private fun previewFor(message: ChatMessage?): String = when {
        message == null -> ""
        message.sender == MessageSender.Me -> "You: ${message.previewLabel()}"
        else -> message.previewLabel()
    }

    private fun resortConversations() {
        val sorted = items.sortedByDescending { it.lastActivityMs }
        if (sorted == items) return
        items.clear()
        items.addAll(sorted)
    }

    private fun indexOfThread(threadId: String): Int {
        if (threadId.isBlank()) return -1
        return items.indexOfFirst { conversation ->
            conversation.id == threadId ||
                (conversation.threadFbid.isNotBlank() && conversation.threadFbid == threadId)
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
