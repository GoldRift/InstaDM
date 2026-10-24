package com.goldrift.instadm.ui.chat

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.goldrift.instadm.data.ChatStore
import com.goldrift.instadm.data.MailboxSync
import com.goldrift.instadm.data.MediaReader
import com.goldrift.instadm.data.Outbox
import com.goldrift.instadm.data.ProtocolRepository
import com.goldrift.instadm.data.RecordedVoice
import com.goldrift.instadm.data.ReplySnapshot
import com.goldrift.instadm.data.db.MessageWithMedia
import com.goldrift.instadm.data.db.ThreadWithParticipants
import com.goldrift.instadm.service.AppVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

enum class InboxStatus { Loading, Ready, NoSession, Error }

/** Message requests must be accepted before they are marked read. */
private val READ_REQUEST_FOLDERS = setOf("PENDING", "SPAM")

/** Wait for a burst of incoming messages to settle before sending a receipt. */
private const val READ_DEBOUNCE_MS = 1_000L

/** Never send more than one read receipt per thread in this window. */
private const val READ_MIN_INTERVAL_MS = 3_000L

/** Re-fetch the newest page when it is older than this. */
private const val THREAD_STALE_MS = 30_000L

/**
 * Compose state for the inbox and open threads. Network and Go calls run in
 * [ProtocolRepository]; this class only applies JSON and realtime results to UI
 * state.
 */
@Stable
class InboxState(
    private val repository: ProtocolRepository,
    private val chatStore: ChatStore,
    private val mailboxSync: MailboxSync,
    private val outbox: Outbox,
    private val scope: CoroutineScope,
    private val mediaReader: MediaReader,
) {
    private val items = mutableStateListOf<Conversation>()
    private val loadingThreads = mutableStateMapOf<String, Boolean>()
    private val markedRead = mutableMapOf<String, String>()
    private val readJobs = mutableMapOf<String, Job>()
    private val lastReadAt = mutableMapOf<String, Long>()
    private val messageCollectors = mutableSetOf<String>()
    private var refreshInFlight = false

    var status by mutableStateOf(InboxStatus.Loading)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var viewerName by mutableStateOf("")
        private set
    var viewerAvatarUrl by mutableStateOf("")
        private set

    init {
        scope.launch { loadViewer() }
        scope.launch {
            // Thread rows and unread counts come from two tables, so an
            // incoming message (or an accepted read receipt) re-emits one of
            // these before the row shows the other.
            combine(
                chatStore.observeInbox(),
                chatStore.observeUnreadCounts(),
            ) { rows, counts ->
                rows to counts.associate { it.threadId to it.unreadCount }
            }.collect { (rows, counts) -> applyInboxRows(rows, counts) }
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

    private suspend fun loadViewer() {
        val profile = chatStore.viewerProfile()
        viewerName = profile.name
        viewerAvatarUrl = profile.avatarUrl
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
                    runCatching {
                        chatStore.clear()
                        outbox.clear()
                        repository.clearMediaCache()
                    }
                    items.clear()
                    status = InboxStatus.NoSession
                    return@launch
                }

                runCatching {
                    mailboxSync.fetchAndStore()
                }
                    .onSuccess {
                        loadViewer()
                        status = InboxStatus.Ready
                    }
                    .onFailure { error ->
                        errorMessage = describeError(error)
                        status = if (items.isEmpty()) InboxStatus.Error else InboxStatus.Ready
                    }
            } finally {
                refreshInFlight = false
            }
        }
    }

    /** Applies the mailbox returned by login validation without another fetch. */
    fun applyMailbox(mailbox: JSONObject) {
        scope.launch {
            runCatching { chatStore.ingestMailbox(mailbox) }
                .onSuccess {
                    loadViewer()
                    errorMessage = null
                    status = InboxStatus.Ready
                }
                .onFailure { error ->
                    errorMessage = describeError(error)
                    status = if (items.isEmpty()) InboxStatus.Error else InboxStatus.Ready
                }
        }
    }

    /** Rebuilds the inbox list from the database, keeping in-memory messages. */
    private fun applyInboxRows(
        rows: List<ThreadWithParticipants>,
        unreadCounts: Map<String, Int>,
    ) {
        val existing = items.associateBy { it.id }
        val mapped = rows.map { row ->
            row.toConversation(existing[row.thread.id], unreadCounts[row.thread.id] ?: 0)
        }
        items.clear()
        items.addAll(mapped)
        if (mapped.isNotEmpty() && status == InboxStatus.Loading) {
            status = InboxStatus.Ready
        }
    }

    fun loadThread(threadId: String) {
        val conversation = conversation(threadId) ?: return
        startMessageCollector(threadId)
        if (isLoading(threadId)) return

        val now = System.currentTimeMillis()
        val syncedAt = conversation.messagesSyncedAt
        if (syncedAt > 0 && now - syncedAt < THREAD_STALE_MS) return

        loadingThreads[threadId] = true
        scope.launch {
            runCatching { repository.loadThread(threadId) }
                .onSuccess { json -> chatStore.ingestThread(json) }
                .onFailure { error ->
                    errorMessage = describeError(error)
                    // Cached messages stay visible; only a thread with nothing
                    // to show falls back to the empty/loading state.
                    if (conversation(threadId)?.messages.isNullOrEmpty()) {
                        update(threadId) { it.copy(messagesLoaded = false) }
                    }
                }
            loadingThreads.remove(threadId)
        }
    }

    /** Starts the database collector for a thread once per process. */
    private fun startMessageCollector(threadId: String) {
        if (!messageCollectors.add(threadId)) return
        scope.launch {
            chatStore.observeThread(threadId).collect { rows ->
                applyThreadMessages(threadId, rows)
            }
        }
    }

    /**
     * Rebuilds an open thread from the database. Optimistic sends are still
     * memory-only until the outbox lands, so they are carried over here.
     */
    private fun applyThreadMessages(threadId: String, rows: List<MessageWithMedia>) {
        update(threadId) { current ->
            val stored = rows.map { it.toChatMessage() }
            current.copy(
                messages = stored,
                messagesLoaded = stored.isNotEmpty() || current.messagesLoaded,
                isLoadingOlder = false,
                olderError = false,
            )
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
        update(threadId) { it.copy(isLoadingOlder = true, olderError = false) }
        scope.launch {
            runCatching { repository.loadOlder(threadId, cursor) }
                .onSuccess { json -> chatStore.ingestOlderPage(json) }
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

    fun send(threadId: String, rawText: String, reply: ReplySnapshot? = null) {
        val text = rawText.trim()
        if (text.isEmpty() || conversation(threadId) == null) return
        errorMessage = null
        scope.launch {
            runCatching { outbox.enqueueText(threadId, text, reply) }
                .onFailure { error -> errorMessage = describeError(error) }
        }
    }

    /**
     * Copies a picked attachment into the durable outbox and shows it as a
     * pending bubble. The upload runs from [Outbox], so it survives a restart.
     */
    fun sendMedia(threadId: String, uri: Uri) {
        if (conversation(threadId) == null) return
        errorMessage = null
        scope.launch {
            runCatching {
                outbox.enqueueMedia(threadId, mediaReader.prepare(uri))
            }.onFailure { error -> errorMessage = describeError(error) }
        }
    }

    /**
     * Queues a finished voice recording. The outbox writes the optimistic
     * bubble first, so the note is playable before the upload completes.
     */
    fun sendVoiceNote(threadId: String, recording: RecordedVoice) {
        if (conversation(threadId) == null) return
        errorMessage = null
        scope.launch {
            runCatching { outbox.enqueueVoiceNote(threadId, recording) }
                .onFailure { error -> errorMessage = describeError(error) }
        }
    }

    /** Re-queues a failed outgoing message with the same OTID. */
    fun retryMedia(threadId: String, messageId: String) {
        if (conversation(threadId) == null) return
        errorMessage = null
        scope.launch {
            runCatching { outbox.retry(messageId) }
                .onFailure { error -> errorMessage = describeError(error) }
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
                .onSuccess {
                    val viewer = chatStore.viewerFbid()
                    if (remove) {
                        chatStore.removeReaction(messageId, viewer)
                    } else {
                        chatStore.setReaction(messageId, viewer, emoji, isFromViewer = true)
                    }
                }
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
                    chatStore.editMessageText(messageId, serverText)
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
        update(threadId) { it.copy(messages = remaining) }
        errorMessage = null

        scope.launch {
            runCatching { repository.unsend(threadId, messageId) }
                .onSuccess { chatStore.tombstoneMessage(messageId) }
                .onFailure { error ->
                    update(threadId) { current ->
                        if (current.messages.any { it.id == messageId }) {
                            current
                        } else {
                            val restored = current.messages.toMutableList()
                            restored.add(index.coerceIn(0, restored.size), message)
                            current.copy(messages = restored)
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
        if (conversation.systemFolder in READ_REQUEST_FOLDERS) {
            // Instagram rejects a viewer receipt until the request is accepted,
            // but the message has still been seen; clear the local badge.
            val watermark = requestReadWatermark(conversation.messages)
            if (watermark > 0) {
                scope.launch { chatStore.markThreadRead(threadId, watermark) }
            }
            return
        }

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
                val targetTimestamp = currentTarget?.timestampMs ?: 0L
                lastReadAt[threadId] = System.currentTimeMillis()
                sent = true
                runCatching {
                    repository.markRead(
                        threadId = threadId,
                        messageId = target,
                        longThreadId = current.longThreadId,
                        timestampMs = targetTimestamp,
                    )
                }.onSuccess {
                    chatStore.markThreadRead(threadId, targetTimestamp)
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

    /** Downloads an attachment through the Go client into the app cache. */
    suspend fun downloadMedia(
        threadId: String,
        messageId: String,
        partIndex: Int,
        mediaType: String,
    ): File {
        val file = repository.downloadMedia(threadId, messageId, partIndex, mediaType)
        chatStore.markAttachmentReady(messageId, partIndex, file.absolutePath)
        return file
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
            conversation.copy(messages = messages)
        }
    }

    private fun describeError(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
}
