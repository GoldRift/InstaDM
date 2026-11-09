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
import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.data.Outbox
import com.goldrift.instadm.data.ProtocolApi
import com.goldrift.instadm.data.RealtimeBus
import com.goldrift.instadm.data.RealtimeEvent
import com.goldrift.instadm.data.RecordedVoice
import com.goldrift.instadm.data.ReplySnapshot
import com.goldrift.instadm.data.db.MessageWithMedia
import com.goldrift.instadm.data.db.ThreadWithParticipants
import com.goldrift.instadm.service.AppVisibility
import com.goldrift.instadm.service.RealtimeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
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

/** How long a started conversation waits for its inbox row before opening. */
private const val NEW_THREAD_WAIT_MS = 5_000L

/** Thread ID prefix of a chat opened from search before its first message. */
private const val DRAFT_THREAD_PREFIX = "draft:"

/** Instagram stops showing a typing signal about this long after the last one. */
private const val TYPING_TTL_MS = 6_000L

/** Send at most one typing signal per thread in this window. */
private const val TYPING_PING_MS = 5_000L

/**
 * Compose state for the inbox and open threads. Network and Go calls run in
 * [ProtocolRepository]; this class only applies JSON and realtime results to UI
 * state.
 */
@Stable
class InboxState(
    private val repository: ProtocolApi,
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
    private val typingSenders = mutableStateMapOf<String, List<Long>>()
    private val typingExpiry = mutableMapOf<String, Job>()
    private val typingPingedAt = mutableMapOf<String, Long>()
    private val typingIdleJobs = mutableMapOf<String, Job>()
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

    /** True while Instagram reports more conversations older than [inboxCursor]. */
    var hasMoreConversations by mutableStateOf(false)
        private set
    var isLoadingMoreConversations by mutableStateOf(false)
        private set
    /** A failed older page; automatic paging pauses until the user retries. */
    var moreConversationsError by mutableStateOf(false)
        private set

    private var inboxCursor = ""

    init {
        scope.launch { loadViewer() }
        scope.launch {
            RealtimeBus.events.collect { event ->
                if (event.type == "typing") applyTyping(event)
            }
        }
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

    /**
     * A chat opened from search that has no thread yet. It is deliberately not
     * part of [conversations]: the inbox only lists conversations that exist on
     * Instagram, which is true only once the first message is sent.
     */
    private var draftThread: Conversation? by mutableStateOf(null)

    fun conversation(threadId: String): Conversation? =
        items.firstOrNull { it.id == threadId } ?: draftThread?.takeIf { it.id == threadId }

    fun isLoading(threadId: String): Boolean = loadingThreads[threadId] == true

    /** True while another participant is typing in [threadId]. */
    fun isTyping(threadId: String): Boolean = typingSenders.containsKey(threadId)

    /**
     * Members whose picture belongs beside the typing bubble. Direct chats have
     * one other person and no picture, like the reaction bubbles.
     */
    fun typingParticipants(threadId: String): List<ConversationParticipant> {
        val senders = typingSenders[threadId].orEmpty()
        if (senders.isEmpty()) return emptyList()
        val conversation = conversation(threadId) ?: return emptyList()
        if (!conversation.isGroup) return emptyList()
        return senders.mapNotNull { igId ->
            conversation.participants.firstOrNull { it.igId == igId.toString() }
        }
    }

    fun clearError() {
        errorMessage = null
    }

    /**
     * Opens a chat for a search result with no existing thread and returns its
     * draft thread ID. Nothing is written until the first message is sent.
     */
    fun startDraft(
        recipientFbid: String,
        name: String,
        username: String,
        avatarUrl: String,
    ): String {
        val displayName = name.ifBlank { username }
        val draft = Conversation(
            id = "$DRAFT_THREAD_PREFIX$recipientFbid",
            contact = Contact(
                id = recipientFbid,
                name = displayName,
                initials = initialsOf(displayName),
                avatarUrl = avatarUrl,
            ),
            draftRecipientFbid = recipientFbid,
        )
        draftThread = draft
        return draft.id
    }

    /** The inbox conversation that already contains the account, if any. */
    fun conversationForRecipient(recipientFbid: String, igId: String = ""): Conversation? =
        items.firstOrNull { conversation ->
            conversation.participants.any { participant ->
                (recipientFbid.isNotBlank() && participant.fbid.toString() == recipientFbid) ||
                    (igId.isNotBlank() && participant.igId == igId)
            }
        }

    private suspend fun loadViewer() {
        val profile = chatStore.viewerProfile()
        viewerName = profile.name
        viewerAvatarUrl = profile.avatarUrl
    }

    /** Forgets the session and empties every screen backed by it. */
    fun logout() {
        scope.launch {
            runCatching { repository.signOut() }
            viewerName = ""
            viewerAvatarUrl = ""
            draftThread = null
            // refresh() wipes the cached inbox, outbox, and media of the old account.
            refresh()
        }
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
                    hasMoreConversations = false
                    inboxCursor = ""
                    status = InboxStatus.NoSession
                    return@launch
                }

                runCatching {
                    mailboxSync.fetchAndStore()
                }
                    .onSuccess { mailbox ->
                        applyInboxPage(mailbox)
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
                    applyInboxPage(mailbox)
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

    /**
     * Fetches the next older inbox page. The Go core addresses pages by the
     * `end_cursor` of the previous one and reports its own `has_next_page`.
     */
    fun loadMoreConversations() {
        if (isLoadingMoreConversations || !hasMoreConversations) return
        if (inboxCursor.isBlank()) return

        isLoadingMoreConversations = true
        moreConversationsError = false
        scope.launch {
            runCatching { mailboxSync.fetchOlder(inboxCursor) }
                .onSuccess { page -> applyInboxPage(page) }
                .onFailure { error ->
                    errorMessage = describeError(error)
                    moreConversationsError = true
                }
            isLoadingMoreConversations = false
        }
    }

    /** Retries a failed older inbox page; automatic paging stays paused until then. */
    fun retryMoreConversations() {
        moreConversationsError = false
        loadMoreConversations()
    }

    private fun applyInboxPage(page: JSONObject) {
        inboxCursor = page.optString("end_cursor").trim()
        hasMoreConversations = hasOlderConversations(inboxCursor, page.optBoolean("has_next_page"))
    }

    fun send(threadId: String, rawText: String, reply: ReplySnapshot? = null) {
        val text = rawText.trim()
        if (text.isEmpty() || conversation(threadId) == null) return
        stopTyping(threadId)
        errorMessage = null
        scope.launch {
            runCatching { outbox.enqueueText(threadId, text, reply) }
                .onFailure { error -> errorMessage = describeError(error) }
        }
    }

    /**
     * Starts a conversation with a search result: Instagram creates the thread
     * with the first message, so this cannot go through the outbox and needs a
     * live connection. [onStarted] receives the thread once the inbox row is
     * there and the chat route can open it.
     */
    fun startConversation(
        recipientFbid: String,
        rawText: String,
        onStarted: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        val text = rawText.trim()
        if (text.isEmpty() || recipientFbid.isBlank()) return
        scope.launch {
            runCatching {
                val result = repository.sendNewDm(recipientFbid, text)
                mailboxSync.fetchAndStore()
                // The Go core resolves the thread from the mailbox it queries
                // after the send; a participant match covers a late reorder.
                val threadId = result.optString("thread_id").trim()
                    .ifEmpty { inboxThreadFor(recipientFbid) }
                // shortcut: a delivered message whose thread Instagram does not
                // report cannot be opened from here, revisit if that happens.
                if (threadId.isEmpty()) {
                    error("The message was sent, but Instagram did not report the conversation.")
                }
                // Shows the bubble before the thread page loads and creates the
                // row, so the chat route finds a thread missing from the inbox.
                chatStore.ingestSentText(threadId, result, text)
                // The inbox list is fed by a Room flow, so wait for the row.
                withTimeoutOrNull(NEW_THREAD_WAIT_MS) {
                    while (conversation(threadId) == null) delay(50)
                }
                threadId
            }
                .onSuccess { threadId -> onStarted(threadId) }
                .onFailure { error -> onError(describeError(error)) }
        }
    }

    private fun inboxThreadFor(recipientFbid: String): String =
        conversationForRecipient(recipientFbid)?.id.orEmpty()

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

    /**
     * Applies an incoming typing signal. Instagram sends one while the other
     * person types and a stop signal when they send, but either can be missed,
     * so each sender expires on its own.
     */
    private fun applyTyping(event: RealtimeEvent) {
        val threadId = items
            .firstOrNull { conversation ->
                (event.threadId.isNotBlank() && conversation.id == event.threadId) ||
                    (event.longThreadId.isNotBlank() &&
                        conversation.longThreadId == event.longThreadId)
            }
            ?.id
            ?: return

        val igId = event.participantIgId
        // A group can have several people typing at once, so each sender keeps
        // its own expiry instead of one timer per thread.
        val key = "$threadId/$igId"
        typingExpiry.remove(key)?.cancel()

        if (event.activityStatus == 0) {
            setTypingSenders(threadId, typingSenders[threadId].orEmpty().withoutTypingSender(igId))
            return
        }

        setTypingSenders(threadId, typingSenders[threadId].orEmpty().withTypingSender(igId))
        typingExpiry[key] = scope.launch {
            delay(TYPING_TTL_MS)
            typingExpiry.remove(key)
            setTypingSenders(threadId, typingSenders[threadId].orEmpty().withoutTypingSender(igId))
        }
    }

    private fun setTypingSenders(threadId: String, senders: List<Long>) {
        if (senders.isEmpty()) {
            typingSenders.remove(threadId)
        } else {
            typingSenders[threadId] = senders
        }
    }

    /**
     * Reports that the viewer is typing. The signal is rate limited to one ping
     * per [TYPING_PING_MS] and cleared after [TYPING_TTL_MS] without a keystroke,
     * which is roughly when Instagram forgets it anyway.
     */
    fun notifyTyping(threadId: String) {
        if (conversation(threadId) == null) return

        typingIdleJobs.remove(threadId)?.cancel()
        typingIdleJobs[threadId] = scope.launch {
            delay(TYPING_TTL_MS)
            typingIdleJobs.remove(threadId)
            clearTyping(threadId)
        }

        val now = System.currentTimeMillis()
        if (now - (typingPingedAt[threadId] ?: 0L) < TYPING_PING_MS) return
        typingPingedAt[threadId] = now
        RealtimeService.setTyping(threadId, true)
    }

    /** Clears a typing signal that was already sent, if any. */
    private fun clearTyping(threadId: String) {
        if (typingPingedAt.remove(threadId) == null) return
        RealtimeService.setTyping(threadId, false)
    }

    private fun stopTyping(threadId: String) {
        typingIdleJobs.remove(threadId)?.cancel()
        clearTyping(threadId)
    }

    /** Downloads an attachment through the Go client into the app cache. */
    suspend fun downloadMedia(
        threadId: String,
        messageId: String,
        media: MessageMedia,
    ): File {
        val file = repository.downloadMedia(threadId, messageId, media)
        chatStore.markAttachmentReady(messageId, media.partIndex, file.absolutePath)
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

/** Typing senders in arrival order, without duplicates. */
internal fun List<Long>.withTypingSender(igId: Long): List<Long> =
    if (igId in this) this else this + igId

internal fun List<Long>.withoutTypingSender(igId: Long): List<Long> = filterNot { it == igId }
