package com.goldrift.instadm.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.goldrift.instadm.InstaDmApplication
import com.goldrift.instadm.R
import com.goldrift.instadm.service.RealtimeService
import com.goldrift.instadm.ui.chat.ChatScreen
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.InboxState
import com.goldrift.instadm.ui.chat.InboxStatus
import com.goldrift.instadm.ui.chat.toReplySnapshot
import com.goldrift.instadm.ui.inbox.InboxScreen
import com.goldrift.instadm.ui.login.LoginScreen
import com.goldrift.instadm.ui.search.SearchScreen
import com.goldrift.instadm.ui.search.toSearchUsers
import com.goldrift.instadm.ui.settings.SettingsScreen

private object Routes {
    const val Login = "login"
    const val Inbox = "inbox"
    const val Chat = "chat"
    const val Search = "search"
    const val Settings = "settings"
    const val ThreadIdArg = "threadId"
    const val ChatRoute = "$Chat/{$ThreadIdArg}"

    fun chat(threadId: String): String = "$Chat/${Uri.encode(threadId)}"
}

/** Expanded-width breakpoint for the side-by-side inbox and chat panes. */
private val TwoPaneMinWidth = 840.dp
private val InboxPaneWidth = 380.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalCoilApi::class)
@Composable
fun InstaDmApp(
    pendingThreadId: String? = null,
    onPendingThreadHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as InstaDmApplication
    val inboxState = application.inboxState
    val settingsState = application.settings
    val settings = settingsState.current
    val navController = rememberNavController()
    var startRoute by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(application.repository) {
        startRoute = if (application.repository.hasCookies()) Routes.Inbox else Routes.Login
    }

    LaunchedEffect(inboxState) {
        inboxState.refreshIfNeeded()
    }

    val resolvedStart = startRoute
    if (resolvedStart == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val loading = stringResource(R.string.app_loading)
            LoadingIndicator(
                modifier = Modifier.semantics { contentDescription = loading },
            )
        }
        return
    }

    val motion = MaterialTheme.motionScheme
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentChatThreadId = if (currentRoute == Routes.ChatRoute) {
        backStackEntry?.arguments?.getString(Routes.ThreadIdArg)
    } else {
        null
    }
    val pendingConversation = pendingThreadId?.let(inboxState::conversation)

    // Logging out discards the session and leaves the inbox without one.
    LaunchedEffect(inboxState.status, currentRoute) {
        if (inboxState.status == InboxStatus.NoSession && currentRoute == Routes.Inbox) {
            navController.navigate(Routes.Login) {
                popUpTo(Routes.Inbox) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = resolvedStart,
        enterTransition = {
            fadeIn(motion.defaultEffectsSpec<Float>()) +
                slideInHorizontally(motion.defaultSpatialSpec<IntOffset>()) { it / 10 }
        },
        exitTransition = {
            fadeOut(motion.fastEffectsSpec<Float>()) +
                slideOutHorizontally(motion.fastSpatialSpec<IntOffset>()) { -it / 10 }
        },
        popEnterTransition = {
            fadeIn(motion.fastEffectsSpec<Float>()) +
                slideInHorizontally(motion.fastSpatialSpec<IntOffset>()) { -it / 10 }
        },
        popExitTransition = {
            fadeOut(motion.fastEffectsSpec<Float>()) +
                slideOutHorizontally(motion.fastSpatialSpec<IntOffset>()) { it / 10 }
        },
    ) {
        composable(Routes.Login) {
            LoginScreen(
                signIn = { cookies -> application.repository.signIn(cookies) },
                onSignedIn = { mailbox ->
                    inboxState.applyMailbox(mailbox)
                    application.realtimeRecorder.start()
                    application.outbox.start()
                    RealtimeService.restart(context)
                    navController.navigate(Routes.Inbox) {
                        popUpTo(Routes.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.Inbox) {
            MainPane(
                inboxState = inboxState,
                likeEmoji = settings.doubleTapEmoji,
                readReceiptsEnabled = settings.sendReadReceipts,
                onRefresh = {
                    inboxState.refresh()
                    RealtimeService.restart(context)
                },
                onLogin = { navController.navigate(Routes.Login) },
                onOpenChat = { threadId ->
                    inboxState.clearError()
                    navController.navigate(Routes.chat(threadId))
                },
                onSearch = { navController.navigate(Routes.Search) },
                onSettings = { navController.navigate(Routes.Settings) },
                onLogout = {
                    inboxState.logout()
                    RealtimeService.stop(context)
                },
            )
        }

        composable(Routes.Settings) {
            SettingsScreen(
                settings = settings,
                onThemeModeChange = settingsState::setThemeMode,
                onDynamicColorChange = settingsState::setDynamicColor,
                onDoubleTapEmojiChange = settingsState::setDoubleTapEmoji,
                onSendTypingChange = { enabled ->
                    settingsState.setSendTypingIndicators(enabled)
                    if (!enabled) inboxState.stopSendingTypingIndicators()
                },
                onSendReadReceiptsChange = { enabled ->
                    settingsState.setSendReadReceipts(enabled)
                    if (!enabled) inboxState.cancelPendingReadReceipts()
                },
                onShowNotificationPreviewsChange = { enabled ->
                    settingsState.setShowNotificationPreviews(enabled)
                    if (!enabled) application.messageNotifier.dropMessageNotifications()
                },
                onClearMediaCache = {
                    application.chatStore.clearDownloadedMediaFiles()
                    application.repository.clearMediaCache()
                    runCatching { context.imageLoader.memoryCache?.clear() }
                    runCatching { context.imageLoader.diskCache?.clear() }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.Search) {
            SearchScreen(
                search = { query -> application.repository.searchUsers(query).toSearchUsers() },
                onBack = { navController.popBackStack() },
                onPick = { user ->
                    // The first message creates the thread, so an account the
                    // inbox does not have yet gets a draft chat instead.
                    val existing = inboxState.conversationForRecipient(user.fbid, user.igId)
                    val threadId = existing?.id ?: inboxState.startDraft(
                        recipientFbid = user.fbid,
                        name = user.name,
                        username = user.username,
                        avatarUrl = user.avatarUrl,
                    )
                    navController.navigate(Routes.chat(threadId)) {
                        popUpTo(Routes.Inbox)
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(
            route = Routes.ChatRoute,
            arguments = listOf(
                navArgument(Routes.ThreadIdArg) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments
                ?.getString(Routes.ThreadIdArg)
                .orEmpty()
            val conversation = inboxState.conversation(threadId)

            if (conversation == null) {
                // Unknown thread (for example after a process restart); return to the inbox.
                LaunchedEffect(threadId) { navController.popBackStack() }
            } else {
                ChatDestination(
                    conversation = conversation,
                    inboxState = inboxState,
                    likeEmoji = settings.doubleTapEmoji,
                    readReceiptsEnabled = settings.sendReadReceipts,
                    onBack = {
                        inboxState.clearError()
                        navController.popBackStack()
                    },
                    onStarted = { startedThreadId ->
                        navController.navigate(Routes.chat(startedThreadId)) {
                            popUpTo(Routes.Inbox)
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }

    LaunchedEffect(
        pendingThreadId,
        pendingConversation?.id,
        inboxState.status,
        currentRoute,
        currentChatThreadId,
    ) {
        val threadId = pendingThreadId ?: return@LaunchedEffect
        if (currentRoute == null) return@LaunchedEffect
        if (currentRoute == Routes.Login) {
            onPendingThreadHandled()
            return@LaunchedEffect
        }

        val conversation = pendingConversation
        when {
            conversation != null -> {
                onPendingThreadHandled()
                if (currentChatThreadId != threadId) {
                    navController.navigate(Routes.chat(threadId)) {
                        popUpTo(Routes.Inbox)
                        launchSingleTop = true
                    }
                }
            }
            inboxState.status != InboxStatus.Loading -> onPendingThreadHandled()
        }
    }
}

/**
 * Inbox shell that becomes a list-detail layout at expanded widths. The chat
 * pane keeps its own selection instead of pushing a route, so the list stays
 * visible while reading a thread.
 */
@Composable
private fun MainPane(
    inboxState: InboxState,
    likeEmoji: String,
    readReceiptsEnabled: Boolean,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    onOpenChat: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < TwoPaneMinWidth) {
            InboxScreen(
                conversations = inboxState.conversations,
                status = inboxState.status,
                errorMessage = inboxState.errorMessage,
                onRefresh = onRefresh,
                onLogin = onLogin,
                onConversationClick = onOpenChat,
                viewerName = inboxState.viewerName,
                viewerAvatarUrl = inboxState.viewerAvatarUrl,
                hasMoreConversations = inboxState.hasMoreConversations,
                isLoadingMore = inboxState.isLoadingMoreConversations,
                moreError = inboxState.moreConversationsError,
                onLoadMore = inboxState::loadMoreConversations,
                onRetryMore = inboxState::retryMoreConversations,
                onSearch = onSearch,
                onSettings = onSettings,
                onLogout = onLogout,
            )
            return@BoxWithConstraints
        }

        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
        // Back closes the detail pane before it leaves the inbox.
        BackHandler(enabled = selectedId != null) { selectedId = null }
        val selected = selectedId?.let { inboxState.conversation(it) }

        Row(Modifier.fillMaxSize()) {
            InboxScreen(
                conversations = inboxState.conversations,
                status = inboxState.status,
                errorMessage = inboxState.errorMessage,
                onRefresh = onRefresh,
                onLogin = onLogin,
                selectedConversationId = selectedId,
                onConversationClick = { threadId ->
                    inboxState.clearError()
                    selectedId = threadId
                },
                modifier = Modifier.width(InboxPaneWidth),
                viewerName = inboxState.viewerName,
                viewerAvatarUrl = inboxState.viewerAvatarUrl,
                hasMoreConversations = inboxState.hasMoreConversations,
                isLoadingMore = inboxState.isLoadingMoreConversations,
                moreError = inboxState.moreConversationsError,
                onLoadMore = inboxState::loadMoreConversations,
                onRetryMore = inboxState::retryMoreConversations,
                onSearch = onSearch,
                onSettings = onSettings,
                onLogout = onLogout,
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (selected == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.inbox_select_conversation),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                ChatDestination(
                    conversation = selected,
                    inboxState = inboxState,
                    likeEmoji = likeEmoji,
                    readReceiptsEnabled = readReceiptsEnabled,
                    onBack = { selectedId = null },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Loads the thread and wires the chat surface to the shared inbox state. */
@Composable
private fun ChatDestination(
    conversation: Conversation,
    inboxState: InboxState,
    likeEmoji: String,
    readReceiptsEnabled: Boolean,
    onBack: () -> Unit,
    onStarted: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val threadId = conversation.id
    val draftRecipient = conversation.draftRecipientFbid
    val isDraft = draftRecipient.isNotBlank()
    var draftError by remember(threadId) { mutableStateOf<String?>(null) }
    var sendingDraft by remember(threadId) { mutableStateOf(false) }

    LaunchedEffect(threadId) {
        if (!isDraft) inboxState.loadThread(threadId)
    }
    // A late onDispose from the previous two-pane selection must not stop the
    // observer for the thread now on screen.
    DisposableEffect(threadId) {
        if (!isDraft) inboxState.openThread(threadId)
        onDispose { if (!isDraft) inboxState.closeThread(threadId) }
    }
    ChatScreen(
        conversation = conversation,
        onSend = { text, reply ->
            if (isDraft) {
                sendingDraft = true
                draftError = null
                inboxState.startConversation(
                    recipientFbid = draftRecipient,
                    rawText = text,
                    onStarted = { startedThreadId ->
                        sendingDraft = false
                        onStarted(startedThreadId)
                    },
                    onError = { message ->
                        sendingDraft = false
                        draftError = message
                    },
                )
            } else {
                inboxState.send(threadId, text, reply?.toReplySnapshot())
            }
        },
        onBack = onBack,
        onSendMedia = { uri -> inboxState.sendMedia(threadId, uri) },
        onSendVoice = { recording -> inboxState.sendVoiceNote(threadId, recording) },
        onRetryMedia = { messageId -> inboxState.retryMedia(threadId, messageId) },
        isTyping = inboxState.isTyping(threadId),
        typingSenders = inboxState.typingParticipants(threadId),
        onTyping = { if (!isDraft) inboxState.notifyTyping(threadId) },
        isLoading = sendingDraft || inboxState.isLoading(threadId),
        errorMessage = draftError ?: inboxState.errorMessage,
        onLoadMedia = { mediaThreadId, messageId, media ->
            inboxState.downloadMedia(mediaThreadId, messageId, media)
        },
        onMarkRead = { messageId -> inboxState.markRead(threadId, messageId) },
        onLoadOlder = { inboxState.loadOlder(threadId) },
        onRetryOlder = { inboxState.retryOlder(threadId) },
        onReact = { messageId, emoji -> inboxState.react(threadId, messageId, emoji) },
        onEdit = { messageId, text -> inboxState.edit(threadId, messageId, text) },
        onUnsend = { messageId -> inboxState.unsend(threadId, messageId) },
        attachmentsEnabled = !isDraft,
        viewerName = inboxState.viewerName,
        viewerAvatarUrl = inboxState.viewerAvatarUrl,
        likeEmoji = likeEmoji,
        readReceiptsEnabled = readReceiptsEnabled,
        modifier = modifier,
    )
}
