package com.goldrift.instadm.ui.chat

import android.net.Uri
import androidx.core.net.toUri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.goldrift.instadm.data.MediaReader
import com.goldrift.instadm.data.OfflineThreadingIds
import com.goldrift.instadm.data.PickedMedia
import com.goldrift.instadm.data.ProtocolRepository
import com.goldrift.instadm.data.RealtimeBus
import com.goldrift.instadm.data.RealtimeEvent
import com.goldrift.instadm.data.messagePlaceholder
import com.goldrift.instadm.service.AppVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class InboxStatus { Loading, Ready, NoSession, Error }

/** Message requests must be accepted before they are marked read. */
private val READ_REQUEST_FOLDERS = setOf("PENDING", "SPAM")

/** Wait for a burst of incoming messages to settle before sending a receipt. */
private const val READ_DEBOUNCE_MS = 1_000L

/** Never send more than one read receipt per thread in this window. */
private const val READ_MIN_INTERVAL_MS = 3_000L

/** Avoid a mailbox refresh per message for a thread outside the first page. */
private const val UNKNOWN_THREAD_REFRESH_MS = 60_000L

/**
 * Compose state for the inbox and open threads. Network and Go calls run in
 * [ProtocolRepository]; this class only applies JSON and realtime results to UI
 * state.
 */
@Stable
class InboxState(
    private val repository: ProtocolRepository,
    private val scope: CoroutineScope,
    private val mediaReader: MediaReader,
) {
    private val items = mutableStateListOf<Conversation>()
    private val loadingThreads = mutableStateMapOf<String, Boolean>()
    private val markedRead = mutableMapOf<String, String>()
    private val readJobs = mutableMapOf<String, Job>()
    private val lastReadAt = mutableMapOf<String, Long>()
    private val unknownThreadRefreshAt = mutableMapOf<String, Long>()
    private val inFlightSends = mutableSetOf<String>()
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

    /** Loads the inbox once per process; rotations reuse the retained state. */
    fun refreshIfNeeded() {
        if (status == InboxStatus.Loading && items.isEmpty()) refresh()
    }

    fun refresh() {
        if (refreshInFlight) return
        refreshInFlight = true
        scope.launch {
            try {
                status = InboxStatus.Loading
                errorMessage = null

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
                        val loadedOtids = loaded.messages
                            .mapNotNullTo(HashSet()) { message ->
                                message.offlineThreadingId.takeIf { it.isNotBlank() }
                            }
                        val inFlight = current.messages.filter { message ->
                            when {
                                message.id in loadedIDs -> false
                                message.id in inFlightSends -> true
                                message.offlineThreadingId.isNotBlank() ->
                                    message.offlineThreadingId !in loadedOtids
                                message.pending && message.text.isNotBlank() ->
                                    loaded.messages.none { loadedMessage ->
                                        loadedMessage.sender == message.sender &&
                                            loadedMessage.text == message.text
                                    }
                                else -> true
                            }
                        }
                        loaded.copy(messages = loaded.messages + inFlight)
                    }
                }
                .onFailure { error ->
                    errorMessage = describeError(error)
                }
            loadingThreads.remove(threadId)
        }
    }

    /**
     * Fetches the next page of older messages. The Go core addresses pages by
     * the `oldest_cursor` of the previously loaded page and returns a new
     * cursor plus its own `has_more_older` flag.
     */
    fun loadOlder(threadId: String) {
        val conversation = conversation(threadId) ?: return
        if (!conversation.messagesLoaded || conversation.isLoadingOlder) return
        if (!conversation.hasMoreOlder || conversation.oldestCursor.isBlank()) return

        val cursor = conversation.oldestCursor
        update(threadId) { it.copy(isLoadingOlder = true) }
        scope.launch {
            runCatching { repository.loadOlder(threadId, cursor) }
                .onSuccess { json ->
                    update(threadId) { current ->
                        val page = mapOlderPage(json, current)
                        // A page that adds nothing while still claiming more
                        // would retrigger the scroll loader forever.
                        if (page.messages.size == current.messages.size) {
                            page.copy(hasMoreOlder = false)
                        } else {
                            page
                        }
                    }
                }
                .onFailure { error ->
                    errorMessage = describeError(error)
                    update(threadId) { current ->
                        current.copy(isLoadingOlder = false, olderError = true)
                    }
                }
        }
    }

    /** Retries a failed older page; automatic paging stays paused until then. */
    fun retryOlder(threadId: String) {
        update(threadId) { it.copy(olderError = false) }
        loadOlder(threadId)
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
     * Sends a picked attachment. The bubble appears immediately with a local
     * preview; the bytes are read and uploaded afterwards. A failed upload keeps
     * the bubble so it can be retried from the same content URI.
     */
    fun sendMedia(threadId: String, uri: Uri) {
        if (conversation(threadId) == null) return

        scope.launch {
            val picked = runCatching { mediaReader.prepare(uri) }
                .getOrElse { error ->
                    errorMessage = describeError(error)
                    return@launch
                }

            val localID = "local-${UUID.randomUUID()}"
            val localTimestamp = System.currentTimeMillis()
            val otid = OfflineThreadingIds.next()
            val local = ChatMessage(
                id = localID,
                text = "",
                sender = MessageSender.Me,
                timestamp = formatMessageTime(localTimestamp),
                timestampMs = localTimestamp,
                pending = true,
                localMedia = PendingMedia(
                    uri = uri.toString(),
                    kind = picked.kind,
                    fileName = picked.fileName,
                    width = picked.width,
                    height = picked.height,
                    durationMs = picked.durationMs,
                ),
                offlineThreadingId = otid,
            )
            update(threadId) { conversation ->
                conversation.copy(
                    messages = conversation.messages + local,
                    lastActivityMs = maxOf(conversation.lastActivityMs, localTimestamp),
                    preview = previewFor(local),
                )
            }
            errorMessage = null

            uploadMedia(threadId, localID, picked, otid)
        }
    }

    /** Retries a failed attachment upload from the URI kept on the bubble. */
    fun retryMedia(threadId: String, messageId: String) {
        val message = conversation(threadId)
            ?.messages
            ?.firstOrNull { it.id == messageId }
            ?: return
        if (message.pending || !message.failed) return
        val local = message.localMedia ?: return
        val uri = runCatching { local.uri.toUri() }.getOrNull() ?: return

        val otid = message.offlineThreadingId.ifBlank { OfflineThreadingIds.next() }
        updateMessage(threadId, messageId) {
            it.copy(pending = true, failed = false, offlineThreadingId = otid)
        }
        errorMessage = null

        scope.launch {
            val picked = runCatching { mediaReader.prepare(uri) }
                .getOrElse { error ->
                    markMediaFailed(threadId, messageId)
                    errorMessage = describeError(error)
                    return@launch
                }
            uploadMedia(threadId, messageId, picked, otid)
        }
    }

    private suspend fun uploadMedia(
        threadId: String,
        localID: String,
        picked: PickedMedia,
        otid: String,
    ) {
        inFlightSends += localID
        val result = runCatching {
            val data = mediaReader.read(picked)
            repository.sendMedia(threadId, picked.fileName, picked.mimeType, data, otid)
        }
        inFlightSends -= localID

        result
            .onSuccess { reconcileMediaSend(threadId, localID, it) }
            .onFailure { error ->
                markMediaFailed(threadId, localID)
                errorMessage = describeError(error)
            }
        resortConversations()
    }

    /**
     * Replaces the optimistic bubble with the server message. If the realtime
     * echo already produced that message, the local duplicate is dropped.
     */
    private fun reconcileMediaSend(threadId: String, localID: String, result: JSONObject) {
        val serverID = result.optString("message_id")
            .ifBlank { result.optString("id") }
        val serverTimestamp = result.optLong("timestamp_ms").takeIf { it > 0 }

        update(threadId) { conversation ->
            val echoArrived = serverID.isNotBlank() &&
                conversation.messages.any { it.id == serverID && it.id != localID }
            val messages = if (echoArrived) {
                conversation.messages.filterNot { it.id == localID }
            } else {
                conversation.messages.map { message ->
                    if (message.id != localID) return@map message
                    val timestampMs = serverTimestamp ?: message.timestampMs
                    message.copy(
                        id = serverID.ifBlank { localID },
                        timestamp = formatMessageTime(timestampMs),
                        timestampMs = timestampMs,
                        pending = false,
                        failed = false,
                    )
                }
            }
            conversation.copy(
                messages = messages,
                preview = previewFor(messages.lastOrNull()),
            )
        }
    }

    private fun markMediaFailed(threadId: String, messageId: String) {
        update(threadId) { conversation ->
            conversation.copy(
                messages = conversation.messages.map { message ->
                    if (message.id == messageId) {
                        message.copy(pending = false, failed = true)
                    } else {
                        message
                    }
                },
            )
        }
    }

    /**
     * Toggles the viewer's reaction optimistically. Instagram allows one
     * reaction per sender, so choosing a different emoji replaces the old one.
     */
    fun react(threadId: String, messageId: String, emoji: String) {
        val message = conversation(threadId)
            ?.messages
            ?.firstOrNull { it.id == messageId }
            ?: return
        if (!message.canReact()) return

        val original = message.reactions.firstOrNull { it.isFromViewer }
        val (optimistic, remove) = toggleViewerReaction(message, emoji)
        if (optimistic === message.reactions) return

        updateMessage(threadId, messageId) { it.copy(reactions = optimistic) }
        errorMessage = null

        scope.launch {
            runCatching { repository.react(threadId, messageId, emoji, remove) }
                .onFailure { error ->
                    updateMessage(threadId, messageId) { current ->
                        current.copy(
                            reactions = restoreViewerReaction(current.reactions, original),
                        )
                    }
                    errorMessage = describeError(error)
                }
        }
    }

    /** Edits one of the viewer's own messages, reverting on failure. */
    fun edit(threadId: String, messageId: String, rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return

        val message = conversation(threadId)
            ?.messages
            ?.firstOrNull { it.id == messageId }
            ?: return
        if (!message.canEdit() || message.text == text) return

        val originalText = message.text
        updateMessage(threadId, messageId) { it.copy(text = text) }
        errorMessage = null

        scope.launch {
            runCatching { repository.edit(threadId, messageId, text) }
                .onSuccess { result ->
                    val serverText = result.optString("text").ifBlank { text }
                    updateMessage(threadId, messageId) { it.copy(text = serverText) }
                }
                .onFailure { error ->
                    updateMessage(threadId, messageId) { current ->
                        if (current.text == text) current.copy(text = originalText) else current
                    }
                    errorMessage = describeError(error)
                }
        }
    }

    /**
     * Removes one of the viewer's own messages for everyone. The bubble
     * disappears immediately and is restored at its old position on failure.
     */
    fun unsend(threadId: String, messageId: String) {
        val conversation = conversation(threadId) ?: return
        val index = conversation.messages.indexOfFirst { it.id == messageId }
        if (index < 0) return

        val message = conversation.messages[index]
        if (!message.canUnsend()) return

        val remaining = conversation.messages.filterNot { it.id == messageId }
        update(threadId) {
            it.copy(
                messages = remaining,
                preview = previewFor(remaining.lastOrNull()),
            )
        }
        errorMessage = null

        scope.launch {
            runCatching { repository.unsend(threadId, messageId) }
                .onFailure { error ->
                    update(threadId) { current ->
                        if (current.messages.any { it.id == messageId }) {
                            current
                        } else {
                            val restored = current.messages.toMutableList()
                            restored.add(index.coerceIn(0, restored.size), message)
                            current.copy(
                                messages = restored,
                                preview = previewFor(restored.lastOrNull()),
                            )
                        }
                    }
                    errorMessage = describeError(error)
                }
        }
    }

    /**
     * Marks the thread read up to [messageId], or the newest incoming server
     * message when blank. Bursts are coalesced: a newer target replaces the
     * pending receipt, the receipt is sent after the burst settles, and one
     * thread is not read more than once per [READ_MIN_INTERVAL_MS].
     * Failures clear the marker so the next view or incoming message can retry.
     */
    fun markRead(threadId: String, messageId: String = "") {
        if (!AppVisibility.isForeground) return
        val conversation = conversation(threadId) ?: return
        if (conversation.systemFolder in READ_REQUEST_FOLDERS) return

        val explicit = messageId.trim()
        val targetMessage = if (explicit.isNotBlank()) {
            conversation.messages.firstOrNull { it.id == explicit }
        } else {
            conversation.messages.lastOrNull { it.canMarkRead() }
        }
        val target = targetMessage?.id?.takeIf { it.isNotBlank() } ?: explicit
        if (target.isBlank() || markedRead[threadId] == target) return

        markedRead[threadId] = target
        readJobs.remove(threadId)?.cancel()

        var sent = false
        val job = scope.launch {
            try {
                delay(READ_DEBOUNCE_MS)
                if (markedRead[threadId] != target) return@launch

                val sinceLast = System.currentTimeMillis() - (lastReadAt[threadId] ?: 0L)
                if (sinceLast < READ_MIN_INTERVAL_MS) {
                    delay(READ_MIN_INTERVAL_MS - sinceLast)
                }
                if (!AppVisibility.isForeground || markedRead[threadId] != target) return@launch

                val current = conversation(threadId) ?: return@launch
                val currentTarget = current.messages.firstOrNull { it.id == target }
                lastReadAt[threadId] = System.currentTimeMillis()
                sent = true
                runCatching {
                    repository.markRead(
                        threadId = threadId,
                        messageId = target,
                        longThreadId = current.longThreadId,
                        timestampMs = currentTarget?.timestampMs ?: 0L,
                    )
                }.onFailure {
                    if (markedRead[threadId] == target) {
                        markedRead.remove(threadId)
                    }
                }
            } finally {
                // A coalesced or deferred target was never sent, so leave it
                // eligible for the next view or foreground transition.
                if (!sent && markedRead[threadId] == target) {
                    markedRead.remove(threadId)
                }
            }
        }
        readJobs[threadId] = job
    }

    private fun applyRealtimeEvent(event: RealtimeEvent) {
        when (event.type) {
            "message.new", "message.admin" -> applyNewMessage(event)
            "message.edit" -> applyEdit(event)
            "message.delete" -> applyDelete(event)
            "read.receipt" -> applyReadReceipt(event)
            "reaction.add", "reaction.remove" -> applyReactionEvent(event)
        }
    }

    private fun applyNewMessage(event: RealtimeEvent) {
        val message = event.message ?: return
        if (event.threadId.isBlank() || message.id.isBlank()) return

        val index = indexOfThread(event.threadId)
        if (index < 0) {
            // A thread outside the loaded page should not trigger a mailbox
            // query for every message it receives.
            val now = System.currentTimeMillis()
            val last = unknownThreadRefreshAt[event.threadId] ?: 0L
            if (now - last >= UNKNOWN_THREAD_REFRESH_MS) {
                unknownThreadRefreshAt[event.threadId] = now
                refresh()
            }
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
            offlineThreadingId = message.offlineThreadingId,
        )

        val conversation = items[index]
        val messages = conversation.messages.toMutableList()
        val existingIndex = messages.indexOfFirst { it.id == incoming.id }
        if (existingIndex >= 0) {
            messages[existingIndex] = incoming.copy(seen = messages[existingIndex].seen)
        } else {
            val pendingIndex = if (message.isFromViewer) {
                messages.indexOfFirst { candidate -> matchesPendingSend(candidate, message) }
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

    private fun applyReactionEvent(event: RealtimeEvent) {
        val messageID = event.message?.id.orEmpty()
        if (event.threadId.isBlank() || messageID.isBlank() || event.reaction.isBlank()) return

        val index = indexOfThread(event.threadId)
        if (index < 0) return
        val conversation = items[index]
        val messages = applyReactionToMessages(
            conversation.messages,
            messageID,
            event.reaction,
            event.participantFbid,
            event.fromViewer,
            event.type == "reaction.remove",
        )
        if (messages !== conversation.messages) {
            items[index] = conversation.copy(messages = messages)
        }
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

    private fun updateMessage(
        threadId: String,
        messageId: String,
        transform: (ChatMessage) -> ChatMessage,
    ) {
        update(threadId) { conversation ->
            val index = conversation.messages.indexOfFirst { it.id == messageId }
            if (index < 0) return@update conversation

            val message = conversation.messages[index]
            val updated = transform(message)
            if (updated == message) return@update conversation

            val messages = conversation.messages.toMutableList()
            messages[index] = updated
            conversation.copy(
                messages = messages,
                preview = previewFor(messages.lastOrNull()),
            )
        }
    }

    private fun describeError(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
}
