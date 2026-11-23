package com.goldrift.instadm.ui.chat

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.goldrift.instadm.R
import com.goldrift.instadm.data.ChatStore
import com.goldrift.instadm.data.MailboxSync
import com.goldrift.instadm.data.MediaReader
import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.data.Outbox
import com.goldrift.instadm.data.PickedMedia
import com.goldrift.instadm.data.ProtocolApi
import com.goldrift.instadm.data.RealtimeBus
import com.goldrift.instadm.data.RealtimeEvent
import com.goldrift.instadm.data.RealtimeMessage
import com.goldrift.instadm.data.RecordedVoice
import com.goldrift.instadm.data.ReplySnapshot
import com.goldrift.instadm.data.SettingsStore
import com.goldrift.instadm.data.extractAudioWaveform
import com.goldrift.instadm.service.AppVisibility
import com.goldrift.instadm.service.RealtimeService
import com.goldrift.instadm.service.suppressesNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.util.Collections

enum class InboxStatus { Loading, Ready, NoSession, Error }

/** Active chat transcript state, isolated from inbox summary rows. */
@Stable
data class ActiveThreadState(
    val threadId: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val loaded: Boolean = false,
    val hasMoreLocal: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val olderError: Boolean = false,
    /** Live unread count for the open thread, or null to keep the row's. */
    val unreadCount: Int? = null,
) {
    fun mergeInto(conversation: Conversation): Conversation {
        if (threadId != conversation.id) return conversation
        return conversation.copy(
            messages = messages,
            messagesLoaded = loaded,
            hasMoreOlder = hasMoreLocal || conversation.hasMoreOlder,
            isLoadingOlder = isLoadingOlder,
            olderError = olderError,
            unreadCount = unreadCount ?: conversation.unreadCount,
        )
    }
}

/**
 * Turns a `LIMIT windowSize + 1` newest-first Room snapshot into chronological
 * order plus whether older local rows remain outside the window.
 */
internal fun <T> sliceThreadWindow(
    descRows: List<T>,
    windowSize: Int,
): Pair<List<T>, Boolean> {
    val hasMoreLocal = descRows.size > windowSize
    val count = if (hasMoreLocal) windowSize else descRows.size
    val chronological = ArrayList<T>(count)
    for (index in count - 1 downTo 0) {
        chronological += descRows[index]
    }
    return chronological to hasMoreLocal
}

/** A message that arrived in another chat while the reader was in one. */
data class ChatBanner(
    val threadId: String,
    val title: String,
    val preview: String,
    val initials: String = initialsOf(title),
    val avatarUrl: String = "",
    val isGroup: Boolean = false,
    val participants: List<ConversationParticipant> = emptyList(),
)

/** Wait for a burst of incoming messages to settle before sending a receipt. */
private const val READ_DEBOUNCE_MS = 1_000L

/** Never send more than one read receipt per thread in this window. */
private const val READ_MIN_INTERVAL_MS = 3_000L

/** Re-fetch the newest page when it is older than this. */
private const val THREAD_STALE_MS = 30_000L

/** Pause after an older page that added no rows, so overlap walks back gently. */
private const val OVERLAP_PAGE_DELAY_MS = 400L

/** How long a started conversation waits for its inbox row before opening. */
private const val NEW_THREAD_WAIT_MS = 5_000L

/** Thread ID prefix of a chat opened from search before its first message. */
private const val DRAFT_THREAD_PREFIX = "draft:"

/** Instagram stops showing a typing signal about this long after the last one. */
private const val TYPING_TTL_MS = 6_000L

/** Send at most one typing signal per thread in this window. */
private const val TYPING_PING_MS = 5_000L

/** How long the in-app banner for another chat stays before it hides itself. */
private const val BANNER_TTL_MS = 5_000L

/** Initial local Room window for an open thread, and step per older expansion. */
private const val INITIAL_THREAD_WINDOW = 100
private const val THREAD_WINDOW_STEP = 100

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
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val mediaReader: MediaReader,
    private val appContext: Context,
) {
    private var conversationsById: Map<String, Conversation> = emptyMap()
    private val loadingThreads = mutableStateMapOf<String, Boolean>()
    private val markedRead = mutableMapOf<String, String>()
    private val readJobs = mutableMapOf<String, Job>()
    private val activityJobs = mutableMapOf<String, Job>()
    private val lastReadAt = mutableMapOf<String, Long>()
    private val typingSenders = mutableStateMapOf<String, List<Long>>()
    private val typingExpiry = mutableMapOf<String, Job>()
    private val typingPingedAt = mutableMapOf<String, Long>()
    private val typingIdleJobs = mutableMapOf<String, Job>()
    private val waveformRequested = Collections.synchronizedSet(mutableSetOf<String>())
    private val waveformExtracting = Collections.synchronizedSet(mutableSetOf<String>())
    private var openThreadId: String? = null
    private var messageCollector: Job? = null
    private var unreadCollector: Job? = null
    private var threadWindowSize = MutableStateFlow(INITIAL_THREAD_WINDOW)
    private var networkLoadOlderInFlight = false
    private var refreshInFlight = false

    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set
    private var activeThread by mutableStateOf(ActiveThreadState())

    var status by mutableStateOf(InboxStatus.Loading)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var viewerName by mutableStateOf("")
        private set
    var viewerAvatarUrl by mutableStateOf("")
        private set

    /** Message in another chat, shown as a banner over the screen; null when none. */
    var banner by mutableStateOf<ChatBanner?>(null)
        private set
    private var bannerJob: Job? = null

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
                if (event.type == "message.new" || event.type == "message.admin") {
                    showBannerFor(event)
                }
            }
        }
        scope.launch {
            // Thread rows and unread counts come from two tables, so an
            // incoming message (or an accepted read receipt) re-emits one of
            // these before the row shows the other.
            combine(
                chatStore.observeInbox().distinctUntilChanged(),
                chatStore.observeUnreadCounts().distinctUntilChanged(),
            ) { rows, counts ->
                val unreadByThread = counts.associate { it.threadId to it.unreadCount }
                val mapped = rows.map { row ->
                    row.toConversation(
                        previous = null,
                        unreadCount = unreadByThread[row.thread.id] ?: 0,
                    )
                }
                mapped to mapped.associateBy { it.id }
            }
                .flowOn(Dispatchers.Default)
                .collect { (mapped, byId) -> applyInboxRows(mapped, byId) }
        }
    }

    /**
     * A chat opened from search that has no thread yet. It is deliberately not
     * part of [conversations]: the inbox only lists conversations that exist on
     * Instagram, which is true only once the first message is sent.
     */
    private var draftThread: Conversation? by mutableStateOf(null)

    fun conversation(threadId: String): Conversation? =
        conversationsById[threadId] ?: draftThread?.takeIf { it.id == threadId }

    /** Combines a summary conversation with the open thread's messages. */
    fun withActiveMessages(conversation: Conversation): Conversation =
        activeThread.mergeInto(conversation)

    private fun messagesFor(threadId: String): List<ChatMessage> =
        if (activeThread.threadId == threadId) activeThread.messages else emptyList()

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
        conversations.firstOrNull { conversation ->
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
            dismissBanner()
            refresh()
        }
    }

    /** Loads the inbox once per process; rotations reuse the retained state. */
    fun refreshIfNeeded() {
        if (status == InboxStatus.Loading && conversations.isEmpty()) refresh()
    }

    /**
     * Announces a message that arrived in a chat the reader is not in. The
     * notification rule decides whether the open chat already shows it, and a
     * muted thread stays as quiet here as it does in the shade; everything else
     * is the notification's in-app counterpart while the app is in front.
     */
    internal fun showBannerFor(event: RealtimeEvent) {
        val message = event.message ?: return
        val foreground = AppVisibility.isForeground
        if (!foreground || message.isFromViewer) return
        if (event.threadId.isBlank() || message.id.isBlank()) return
        if (suppressesNotification(foreground, AppVisibility.openThreadId, event.threadId)) return

        scope.launch {
            val thread = chatStore.threadWithParticipants(event.threadId)
            if (thread?.thread?.isMuted == true) return@launch
            val conversation = thread?.toConversation(previous = null)
            val isGroup = conversation?.isGroup == true
            val preview = ConversationPreview(
                kind = message.kind,
                text = message.text,
                fromViewer = false,
                senderName = message.senderName,
            ).label(isGroup)
                ?.render { id, args -> appContext.getString(id, *args.toTypedArray()) }
                ?: appContext.getString(R.string.notification_message)

            val title = bannerTitle(message, conversation, isGroup)
            val senderParticipant = if (!isGroup) {
                conversation?.participants?.firstOrNull()
            } else {
                null
            }
            val avatarUrl = conversation?.contact?.avatarUrl?.takeIf { it.isNotBlank() }
                ?: senderParticipant?.avatarUrl.orEmpty()
            banner = ChatBanner(
                threadId = event.threadId,
                title = title,
                preview = preview,
                initials = initialsOf(title),
                avatarUrl = avatarUrl,
                isGroup = isGroup,
                participants = conversation?.participants.orEmpty(),
            )
            bannerJob?.cancel()
            bannerJob = scope.launch {
                delay(BANNER_TTL_MS)
                banner = null
            }
        }
    }

    private fun bannerTitle(
        message: RealtimeMessage,
        conversation: Conversation?,
        isGroup: Boolean,
    ): String {
        val threadName = conversation?.contact?.name
            ?.takeIf { it.isNotBlank() && it != UNKNOWN_CONVERSATION_TITLE }
        return when {
            isGroup -> threadName ?: appContext.getString(R.string.notification_group_chat)
            else -> message.senderName.trim()
                .ifBlank { threadName.orEmpty() }
                .ifBlank { appContext.getString(R.string.chat_someone) }
        }
    }

    fun dismissBanner() {
        bannerJob?.cancel()
        banner = null
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
                    conversations = emptyList()
                    conversationsById = emptyMap()
                    activeThread = ActiveThreadState()
                    waveformRequested.clear()
                    waveformExtracting.clear()
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
                        scope.launch { mailboxSync.reconcileUnreadThreads() }
                    }
                    .onFailure { error ->
                        errorMessage = describeError(error)
                        status = if (conversations.isEmpty()) InboxStatus.Error else InboxStatus.Ready
                    }
            } finally {
                refreshInFlight = false
            }
        }
    }

    /** Applies the mailbox returned by login validation without another fetch. */
    fun applyMailbox(mailbox: JSONObject) {
        // A mailbox means the session is live. Leaving the inbox on NoSession
        // until the ingest finishes would bounce the reader back to login.
        status = InboxStatus.Loading
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
                    status = if (conversations.isEmpty()) InboxStatus.Error else InboxStatus.Ready
                }
        }
    }

    /** Applies mapped inbox summary rows without touching open-thread messages. */
    private fun applyInboxRows(
        mapped: List<Conversation>,
        byId: Map<String, Conversation>,
    ) {
        if (mapped != conversations) {
            conversations = mapped
            conversationsById = byId
        }
        if (mapped.isNotEmpty() && status == InboxStatus.Loading) {
            status = InboxStatus.Ready
        }
    }

    fun loadThread(threadId: String) {
        val conversation = conversation(threadId) ?: return
        openThread(threadId)
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
                    if (messagesFor(threadId).isEmpty()) {
                        updateActiveThread(threadId) { it.copy(loaded = false) }
                    }
                }
            loadingThreads.remove(threadId)
        }
    }

    /**
     * Makes [threadId] the one open chat fed by the database. Opening another
     * thread releases the previous observer, so chats do not accumulate.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun openThread(threadId: String) {
        if (banner?.threadId == threadId) dismissBanner()
        if (openThreadId == threadId &&
            messageCollector?.isActive == true &&
            unreadCollector?.isActive == true
        ) {
            return
        }
        messageCollector?.cancel()
        unreadCollector?.cancel()
        openThreadId = threadId
        networkLoadOlderInFlight = false
        threadWindowSize = MutableStateFlow(INITIAL_THREAD_WINDOW)
        if (activeThread.threadId != threadId) {
            activeThread = ActiveThreadState(threadId = threadId)
        }
        val windowFlow = threadWindowSize
        messageCollector = scope.launch {
            windowFlow
                .flatMapLatest { limit ->
                    chatStore.observeThreadWindow(threadId, limit + 1)
                        .map { rows ->
                            val (windowed, hasMoreLocal) = sliceThreadWindow(rows, limit)
                            windowed.map { it.toChatMessage() } to hasMoreLocal
                        }
                }
                .flowOn(Dispatchers.Default)
                .collect { (stored, hasMoreLocal) ->
                    applyThreadMessages(threadId, stored, hasMoreLocal)
                }
        }
        unreadCollector = scope.launch {
            chatStore.observeUnreadCounts()
                .map { counts -> counts.firstOrNull { it.threadId == threadId }?.unreadCount ?: 0 }
                .distinctUntilChanged()
                .collect { count ->
                    updateActiveThread(threadId) { it.copy(unreadCount = count) }
                }
        }
    }

    /** Stops the database observer, unless the reader already moved to another thread. */
    fun closeThread(threadId: String) {
        if (openThreadId != threadId) return
        openThreadId = null
        networkLoadOlderInFlight = false
        messageCollector?.cancel()
        messageCollector = null
        unreadCollector?.cancel()
        unreadCollector = null
        if (activeThread.threadId == threadId) {
            activeThread = ActiveThreadState()
        }
    }

    /**
     * Applies mapped messages for the open thread on the UI thread.
     */
    private fun applyThreadMessages(
        threadId: String,
        stored: List<ChatMessage>,
        hasMoreLocal: Boolean,
    ) {
        updateActiveThread(threadId) { current ->
            current.copy(
                messages = stored,
                loaded = stored.isNotEmpty() || current.loaded,
                hasMoreLocal = hasMoreLocal,
                isLoadingOlder = if (networkLoadOlderInFlight) current.isLoadingOlder else false,
            )
        }
    }

    /**
     * Fetches the next page of older messages, expanding the local Room window
     * first before pulling another page from Instagram.
     */
    fun loadOlder(threadId: String) {
        val conversation = conversation(threadId) ?: return
        val threadState = activeThread.takeIf { it.threadId == threadId } ?: return
        if (!threadState.loaded || threadState.isLoadingOlder) return

        if (threadState.hasMoreLocal) {
            updateActiveThread(threadId) { it.copy(isLoadingOlder = true, olderError = false) }
            threadWindowSize.value += THREAD_WINDOW_STEP
            return
        }
        if (!conversation.hasMoreOlder || conversation.oldestCursor.isBlank()) return

        val cursor = conversation.oldestCursor
        networkLoadOlderInFlight = true
        updateActiveThread(threadId) { it.copy(isLoadingOlder = true, olderError = false) }
        scope.launch {
            try {
                runCatching {
                    val json = repository.loadOlder(threadId, cursor)
                    // Expand the local window before ingesting so the new page lands in one emission.
                    if (openThreadId == threadId) {
                        threadWindowSize.value += THREAD_WINDOW_STEP
                    }
                    chatStore.ingestOlderPage(json)
                }.onSuccess { page ->
                    if (page != null && page.added > THREAD_WINDOW_STEP && openThreadId == threadId) {
                        threadWindowSize.value += page.added - THREAD_WINDOW_STEP
                    }
                    // Apply the page's cursor before clearing the in-flight flag: the
                    // list re-arms as soon as it clears, and a stale cursor would
                    // request the same page again.
                    if (page != null && page.added == 0) delay(OVERLAP_PAGE_DELAY_MS)
                    updateConversation(threadId) { current ->
                        current.copy(
                            oldestCursor = page?.oldestCursor ?: current.oldestCursor,
                            hasMoreOlder = page?.hasMoreOlder ?: false,
                        )
                    }
                    networkLoadOlderInFlight = false
                    updateActiveThread(threadId) { it.copy(isLoadingOlder = false) }
                }.onFailure { error ->
                    errorMessage = describeError(error)
                    networkLoadOlderInFlight = false
                    updateActiveThread(threadId) {
                        it.copy(isLoadingOlder = false, olderError = true)
                    }
                }
            } finally {
                networkLoadOlderInFlight = false
            }
        }
    }

    /** Retries a failed older page; automatic paging stays paused until then. */
    fun retryOlder(threadId: String) {
        updateActiveThread(threadId) { it.copy(olderError = false) }
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
                .onSuccess { page ->
                    applyInboxPage(page)
                    scope.launch { mailboxSync.reconcileUnreadThreads() }
                }
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
                .onSuccess { markRead(threadId, fromSend = true) }
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
     * Reads a picked attachment's metadata so the composer can stage it. A file
     * the policy rejects reports through [errorMessage] and stages nothing.
     */
    suspend fun prepareMedia(uri: Uri): PickedMedia? =
        runCatching { mediaReader.prepare(uri) }
            .onFailure { error -> errorMessage = describeError(error) }
            .getOrNull()

    /**
     * Copies picked attachments into the durable outbox and shows them as
     * pending bubbles, in the order the viewer arranged them. One failed file
     * does not drop the rest of the batch.
     */
    fun sendMedia(threadId: String, media: List<PickedMedia>) {
        if (conversation(threadId) == null || media.isEmpty()) return
        errorMessage = null
        scope.launch {
            var stamp = System.currentTimeMillis()
            var enqueued = false
            media.forEach { picked ->
                runCatching { outbox.enqueueMedia(threadId, picked, stamp++) }
                    .onSuccess { enqueued = true }
                    .onFailure { error -> errorMessage = describeError(error) }
            }
            if (enqueued) markRead(threadId, fromSend = true)
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
                .onSuccess { markRead(threadId, fromSend = true) }
                .onFailure { error -> errorMessage = describeError(error) }
        }
    }

    /** Re-queues a failed outgoing message with the same OTID. */
    fun retryMedia(threadId: String, messageId: String) {
        if (conversation(threadId) == null) return
        errorMessage = null
        scope.launch {
            runCatching { outbox.retry(messageId) }
                .onSuccess { markRead(threadId, fromSend = true) }
                .onFailure { error -> errorMessage = describeError(error) }
        }
    }

    /**
     * Toggles the viewer's reaction optimistically. Instagram allows one
     * reaction per sender, so choosing a different emoji replaces the old one.
     */
    fun react(threadId: String, messageId: String, emoji: String) {
        val message = messagesFor(threadId)
            .firstOrNull { it.id == messageId }
            ?: return
        if (!message.canReact()) return

        val original = message.reactions.firstOrNull { it.isFromViewer }
        val (optimistic, remove) = toggleViewerReaction(message, emoji)
        if (optimistic === message.reactions) return

        updateActiveMessage(threadId, messageId) { it.copy(reactions = optimistic) }
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
                    updateActiveMessage(threadId, messageId) { current ->
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

        val message = messagesFor(threadId)
            .firstOrNull { it.id == messageId }
            ?: return
        if (!message.canEdit() || message.text == text) return

        val originalText = message.text
        updateActiveMessage(threadId, messageId) { it.copy(text = text) }
        errorMessage = null

        scope.launch {
            runCatching { repository.edit(threadId, messageId, text) }
                .onSuccess { result ->
                    val serverText = result.optString("text").ifBlank { text }
                    updateActiveMessage(threadId, messageId) { it.copy(text = serverText) }
                    chatStore.editMessageText(messageId, serverText)
                }
                .onFailure { error ->
                    updateActiveMessage(threadId, messageId) { current ->
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
        val currentMessages = messagesFor(threadId)
        val index = currentMessages.indexOfFirst { it.id == messageId }
        if (index < 0) return

        val message = currentMessages[index]
        if (!message.canUnsend()) return

        val remaining = currentMessages.filterNot { it.id == messageId }
        updateActiveThread(threadId) { it.copy(messages = remaining) }
        errorMessage = null

        scope.launch {
            runCatching { repository.unsend(threadId, messageId) }
                .onSuccess { chatStore.tombstoneMessage(messageId) }
                .onFailure { error ->
                    updateActiveThread(threadId) { current ->
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
     *
     * [fromSend] keeps the server's read state consistent with a reply: a
     * thread the viewer replies to is marked read even when the viewer hides
     * receipts, because replying would otherwise leave an unseen message with
     * a sent message after it.
     *
     * Hidden reaction-log rows clear locally, immediately when no visible
     * incoming message is unread and otherwise with the accepted receipt;
     * they never name a receipt.
     */
    fun markRead(threadId: String, messageId: String = "", fromSend: Boolean = false) {
        if (!AppVisibility.isForeground) return
        val conversation = conversation(threadId) ?: return
        if (conversation.systemFolder in READ_REQUEST_FOLDERS) {
            // Instagram rejects a viewer receipt until the request is accepted,
            // but the message has still been seen; clear the local badge.
            markLocallyRead(threadId)
            return
        }
        if (!shouldPublishReadReceipt(settings.current.sendReadReceipts, fromSend)) {
            markLocallyRead(threadId)
            return
        }
        // Hidden reaction-log rows have no receipt Instagram accepts, so the
        // viewer's look at the chat is the only signal that clears them; with
        // a visible message still unread, the accepted receipt clears them.
        markActivityRead(threadId)

        val target = readReceiptTarget(messagesFor(threadId), messageId)
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
                if (!shouldPublishReadReceipt(settings.current.sendReadReceipts, fromSend)) {
                    return@launch
                }

                val current = conversation(threadId) ?: return@launch
                val currentTarget = messagesFor(threadId).firstOrNull { it.id == target }
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
                    chatStore.markThreadReadIncludingActivity(threadId, targetTimestamp)
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

    /** Cancels a debounced receipt and lets the next view send it once receipts return. */
    fun cancelPendingReadReceipts() {
        readJobs.values.forEach { it.cancel() }
        readJobs.clear()
        markedRead.clear()
    }

    /** Clears the inbox badge without asking Instagram to publish a receipt. */
    private fun markLocallyRead(threadId: String) {
        val watermark = requestReadWatermark(messagesFor(threadId))
        scope.launch { chatStore.markThreadReadIncludingActivity(threadId, watermark) }
    }

    /** Clears settled hidden activity; see [ChatStore.markActivityReadIfSettled]. */
    private fun markActivityRead(threadId: String) {
        activityJobs.remove(threadId)?.cancel()
        activityJobs[threadId] = scope.launch {
            if (!AppVisibility.isForeground) return@launch
            chatStore.markActivityReadIfSettled(threadId)
        }
    }

    /**
     * Applies an incoming typing signal. Instagram sends one while the other
     * person types and a stop signal when they send, but either can be missed,
     * so each sender expires on its own.
     */
    private fun applyTyping(event: RealtimeEvent) {
        val threadId = conversations
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
        if (!settings.current.sendTypingIndicators) return
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

    /** Clears typing signals that were already sent when the viewer turns them off. */
    fun stopSendingTypingIndicators() {
        typingIdleJobs.values.forEach { it.cancel() }
        typingIdleJobs.clear()
        typingPingedAt.keys.toList().forEach { clearTyping(it) }
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
        if (media.isVoiceNote && media.waveform.isEmpty()) {
            launchWaveformExtraction(messageId, media.partIndex, file)
        }
        return file
    }

    /** Ensures a visible voice note without a server waveform gets one decoded on device. */
    fun ensureVoiceWaveform(
        threadId: String,
        messageId: String,
        media: MessageMedia,
    ) {
        if (!media.isVoiceNote || media.waveform.isNotEmpty()) return
        val key = "$messageId:${media.partIndex}"
        if (!waveformRequested.add(key)) return

        scope.launch {
            val localFile = withContext(Dispatchers.IO) { localVoiceFile(media) }
            if (localFile != null) {
                launchWaveformExtraction(messageId, media.partIndex, localFile)
            } else if (!messageId.startsWith("local:")) {
                runCatching { downloadMedia(threadId, messageId, media) }
                    .onFailure { waveformRequested.remove(key) }
            } else {
                waveformRequested.remove(key)
            }
        }
    }

    private fun launchWaveformExtraction(
        messageId: String,
        partIndex: Int,
        file: File,
    ) {
        val key = "$messageId:$partIndex"
        if (!waveformExtracting.add(key)) return
        scope.launch {
            try {
                val bars = extractAudioWaveform(file)
                if (bars.isNotEmpty()) {
                    chatStore.setAttachmentWaveform(messageId, partIndex, bars)
                }
            } finally {
                waveformExtracting.remove(key)
            }
        }
    }

    private fun updateConversation(threadId: String, transform: (Conversation) -> Conversation) {
        val current = conversations
        val index = current.indexOfFirst { it.id == threadId }
        if (index < 0) return
        val updated = transform(current[index])
        if (updated == current[index]) return
        val next = current.toMutableList()
        next[index] = updated
        conversations = next
        conversationsById = next.associateBy { it.id }
    }

    private fun updateActiveThread(
        threadId: String,
        transform: (ActiveThreadState) -> ActiveThreadState,
    ) {
        val current = activeThread
        if (current.threadId != threadId) return
        val updated = transform(current)
        if (updated != current) {
            activeThread = updated
        }
    }

    private fun updateActiveMessage(
        threadId: String,
        messageId: String,
        transform: (ChatMessage) -> ChatMessage,
    ) {
        updateActiveThread(threadId) { current ->
            val index = current.messages.indexOfFirst { it.id == messageId }
            if (index < 0) return@updateActiveThread current

            val message = current.messages[index]
            val updated = transform(message)
            if (updated == message) return@updateActiveThread current

            val messages = current.messages.toMutableList()
            messages[index] = updated
            current.copy(messages = messages)
        }
    }

    private fun describeError(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
}

/** Typing senders in arrival order, without duplicates. */
internal fun List<Long>.withTypingSender(igId: Long): List<Long> =
    if (igId in this) this else this + igId

internal fun List<Long>.withoutTypingSender(igId: Long): List<Long> = filterNot { it == igId }
