package com.goldrift.instadm.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.goldrift.instadm.data.PendingShare
import com.goldrift.instadm.service.RealtimeService
import com.goldrift.instadm.ui.chat.CameraCaptureOverlay
import com.goldrift.instadm.ui.chat.CameraCaptureRequest
import com.goldrift.instadm.ui.chat.ChatBanner
import com.goldrift.instadm.ui.chat.ChatScreen
import com.goldrift.instadm.ui.chat.Contact
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.InboxState
import com.goldrift.instadm.ui.chat.InboxStatus
import com.goldrift.instadm.ui.chat.MediaPickerOverlay
import com.goldrift.instadm.ui.chat.MediaPickerRequest
import com.goldrift.instadm.ui.chat.MediaViewerOverlay
import com.goldrift.instadm.ui.chat.MediaViewerRequest
import com.goldrift.instadm.ui.chat.toReplySnapshot
import com.goldrift.instadm.ui.chat.VoicePlayer
import com.goldrift.instadm.ui.components.Avatar
import com.goldrift.instadm.ui.components.ConversationAvatar
import com.goldrift.instadm.ui.components.ExpressiveStateBadge
import com.goldrift.instadm.ui.components.MiniVoicePlayer
import com.goldrift.instadm.ui.components.MiniVoicePlayerInset
import com.goldrift.instadm.ui.components.showVoiceMiniPlayer
import com.goldrift.instadm.ui.components.thresholdActivated
import com.goldrift.instadm.ui.inbox.InboxScreen
import com.goldrift.instadm.ui.login.LoginScreen
import com.goldrift.instadm.ui.search.SearchScreen
import com.goldrift.instadm.ui.search.toSearchUsers
import com.goldrift.instadm.ui.settings.SettingsScreen
import com.goldrift.instadm.ui.share.ShareCompletion
import com.goldrift.instadm.ui.share.SharePickerScreen
import com.goldrift.instadm.ui.share.resolveShareCompletion
import com.goldrift.instadm.update.UpdateState
import com.goldrift.instadm.update.showUpdateBadge
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

internal fun resolveStartRoute(hasInitialSession: Boolean): String =
    if (hasInitialSession) Routes.Inbox else Routes.Login

internal data class ShareArrivalAction(
    val markHandledId: Long,
    val clearUiForPicker: Boolean,
    val popToInbox: Boolean,
)

internal fun resolveShareArrival(
    pendingShareId: Long?,
    handledShareId: Long,
    currentRoute: String?,
): ShareArrivalAction? {
    if (pendingShareId == null || pendingShareId == handledShareId) return null
    if (currentRoute == null) return null
    return ShareArrivalAction(
        markHandledId = pendingShareId,
        clearUiForPicker = true,
        popToInbox = currentRoute != Routes.Inbox && currentRoute != Routes.Login,
    )
}

/** Expanded-width breakpoint for the side-by-side inbox and chat panes. */
private val TwoPaneMinWidth = 840.dp
private val InboxPaneWidth = 380.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalCoilApi::class)
@Composable
fun InstaDmApp(
    pendingThreadId: String? = null,
    onPendingThreadHandled: () -> Unit = {},
    pendingShare: PendingShare? = null,
    onShareHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as InstaDmApplication
    val inboxState = application.inboxState
    val settingsState = application.settings
    val settings = settingsState.current
    val voicePlayer = application.voicePlayer
    val updates = application.updates
    val updateState = updates.state
    val hasUpdateBadge = showUpdateBadge(updates.pendingUpdate, updates.badgeSeenVersionCode)
    val navController = rememberNavController()
    val startRoute = remember(application) {
        resolveStartRoute(hasInitialSession = application.hasInitialSession())
    }
    // The two-pane detail selection lives here so the player bar can tell which
    // chat is on screen and open the one it belongs to.
    var selectedChatId by rememberSaveable { mutableStateOf<String?>(null) }
    // The note the player bar opened, waiting for its chat to show it.
    var focusMessage by remember { mutableStateOf<Pair<String, String>?>(null) }
    // Reported by the open chat, so the bar can float just above its composer.
    var composerHeight by remember { mutableStateOf(0.dp) }
    var mediaViewerRequest by remember { mutableStateOf<MediaViewerRequest?>(null) }
    var cameraCaptureRequest by remember { mutableStateOf<CameraCaptureRequest?>(null) }
    var mediaPickerRequest by remember { mutableStateOf<MediaPickerRequest?>(null) }
    var handledShareId by rememberSaveable { mutableLongStateOf(0L) }

    // A message in another chat lands as a banner over the screen. The exit
    // animation keeps drawing the last one after the state clears.
    val banner = inboxState.banner
    var shownBanner by remember { mutableStateOf<ChatBanner?>(null) }
    LaunchedEffect(banner) { if (banner != null) shownBanner = banner }

    LaunchedEffect(inboxState) {
        inboxState.refreshIfNeeded()
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
    val nowPlaying = voicePlayer.nowPlaying

    // Logging out discards the session and leaves the inbox without one.
    LaunchedEffect(inboxState.status, currentRoute) {
        if (inboxState.status == InboxStatus.NoSession && currentRoute == Routes.Inbox) {
            navController.navigate(Routes.Login) {
                popUpTo(Routes.Inbox) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val wide = LocalWindowInfo.current.containerDpSize.width >= TwoPaneMinWidth
    val sharePickerOpen = pendingShare != null &&
        currentRoute == Routes.Inbox &&
        inboxState.status != InboxStatus.NoSession
    Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
        // The bar floats over the screens so the surface shows around it: the
        // inbox keeps its own colour and the note sits on top like a player.
        // Only the detail pane of a wide inbox counts as "reading" a chat.
        val visibleThreadId = currentChatThreadId
            ?: selectedChatId?.takeIf { currentRoute == Routes.Inbox && wide && !sharePickerOpen }
        val showPlayerBar = showVoiceMiniPlayer(
            nowPlaying = nowPlaying,
            visibleThreadId = visibleThreadId,
            enabled = settings.voiceBackgroundPlayback,
            finished = voicePlayer.finished,
            mediaViewerOpen = mediaViewerRequest != null,
            cameraOpen = cameraCaptureRequest != null || mediaPickerRequest != null,
            sharePickerOpen = sharePickerOpen,
        )
        // The bar floats over the list, so the list keeps its last row clear.
        val listBottomInset =
            if (showPlayerBar && visibleThreadId == null) MiniVoicePlayerInset else 0.dp
        // One bar throughout: it rises from the window bottom to just above a
        // chat's composer instead of a second one appearing there.
        val lift by animateDpAsState(
            targetValue = if (visibleThreadId != null) composerHeight else 0.dp,
            animationSpec = motion.defaultSpatialSpec(),
            label = "miniPlayerLift",
        )
        // Opening the bar lands on the note it plays, from wherever it shows.
        val openPlayingNote: () -> Unit = {
            val target = nowPlaying
            if (target != null) {
                focusMessage = target.threadId to target.messageId
                if (wide && currentRoute == Routes.Inbox && !sharePickerOpen) {
                    selectedChatId = target.threadId
                } else {
                    navController.navigate(Routes.chat(target.threadId)) {
                        popUpTo(Routes.Inbox)
                        launchSingleTop = true
                    }
                }
            }
        }
        NavHost(
            navController = navController,
            startDestination = startRoute,
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
                if (pendingShare != null && inboxState.status != InboxStatus.NoSession) {
                    key(pendingShare.id) {
                        SharePickerScreen(
                            pendingShare = pendingShare,
                            conversations = inboxState.conversations,
                            status = inboxState.status,
                            errorMessage = inboxState.errorMessage,
                            hasMoreConversations = inboxState.hasMoreConversations,
                            isLoadingMore = inboxState.isLoadingMoreConversations,
                            moreError = inboxState.moreConversationsError,
                            onLoadMore = inboxState::loadMoreConversations,
                            onRetryMore = inboxState::retryMoreConversations,
                            onRefresh = {
                                inboxState.refresh()
                                RealtimeService.restart(context)
                            },
                            onPrepareMedia = inboxState::prepareShareMedia,
                            onSendShare = inboxState::sendShare,
                            onShareSent = { result ->
                                onShareHandled()
                                if (result.failedMediaCount > 0 &&
                                    !result.lastMediaError.isNullOrBlank()
                                ) {
                                    Toast.makeText(
                                        context,
                                        result.lastMediaError,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                                when (val completion = resolveShareCompletion(result.queuedThreadIds)) {
                                    is ShareCompletion.OpenChat -> {
                                        inboxState.clearError()
                                        if (wide) {
                                            selectedChatId = completion.threadId
                                        } else {
                                            navController.navigate(Routes.chat(completion.threadId))
                                        }
                                    }

                                    is ShareCompletion.ReturnToInbox -> {
                                        Toast.makeText(
                                            context,
                                            context.resources.getQuantityString(
                                                R.plurals.share_sending_to,
                                                completion.count,
                                                completion.count,
                                            ),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }

                                    ShareCompletion.None -> Unit
                                }
                            },
                            onDismiss = onShareHandled,
                        )
                    }
                } else {
                    MainPane(
                        wide = wide,
                        inboxState = inboxState,
                        likeEmoji = settings.doubleTapEmoji,
                        readReceiptsEnabled = settings.sendReadReceipts,
                        voicePlayer = voicePlayer,
                        voiceBackgroundPlayback = settings.voiceBackgroundPlayback,
                        focusMessage = focusMessage,
                        onFocusHandled = { focusMessage = null },
                        onComposerHeight = { composerHeight = it },
                        listBottomInset = listBottomInset,
                        selectedChatId = selectedChatId,
                        onSelectChat = { selectedChatId = it },
                        pendingUpdate = updates.pendingUpdate,
                        showUpdateBadge = hasUpdateBadge,
                        updateState = updateState,
                        onUpdateSeen = updates::markBadgeSeen,
                        onInstallUpdate = updates::install,
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
                        onOpenMedia = { mediaViewerRequest = it },
                        onOpenCamera = { cameraCaptureRequest = it },
                        onOpenMediaPicker = { mediaPickerRequest = it },
                        onLogout = {
                            inboxState.logout()
                            voicePlayer.stop()
                            RealtimeService.stop(context)
                        },
                    )
                }
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
                    onVoiceBackgroundPlaybackChange = { enabled ->
                        settingsState.setVoiceBackgroundPlayback(enabled)
                        if (!enabled) voicePlayer.stop()
                    },
                    onAutoCheckUpdatesChange = settingsState::setAutoCheckUpdates,
                    updateState = updateState,
                    installedVersion = updates.installedVersionName,
                    onCheckUpdates = updates::checkNow,
                    onInstallUpdate = updates::install,
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
                    LaunchedEffect(threadId) {
                        navController.popBackStack()
                    }
                } else {
                    ChatDestination(
                        conversation = conversation,
                        inboxState = inboxState,
                        likeEmoji = settings.doubleTapEmoji,
                        readReceiptsEnabled = settings.sendReadReceipts,
                        voicePlayer = voicePlayer,
                        voiceBackgroundPlayback = settings.voiceBackgroundPlayback,
                        focusMessageId = focusMessage
                            ?.takeIf { it.first == conversation.id }?.second,
                        onFocusHandled = { focusMessage = null },
                        onComposerHeight = { composerHeight = it },
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
                        onOpenMedia = { mediaViewerRequest = it },
                        onOpenCamera = { cameraCaptureRequest = it },
                        onOpenMediaPicker = { mediaPickerRequest = it },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showPlayerBar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // A spring may dip past its target; padding cannot.
                .padding(bottom = lift.coerceAtLeast(0.dp))
                .then(
                    // A chat's composer already carries the navigation bar.
                    if (visibleThreadId == null) Modifier.navigationBarsPadding() else Modifier,
                ),
            enter = fadeIn(motion.defaultEffectsSpec()) +
                slideInVertically(motion.defaultSpatialSpec()) { it },
            exit = fadeOut(motion.fastEffectsSpec()) +
                slideOutVertically(motion.fastSpatialSpec()) { it },
        ) {
            MiniVoicePlayer(player = voicePlayer, onOpen = openPlayingNote)
        }

        AnimatedVisibility(
            visible = banner != null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(
                    start = 12.dp,
                    top = SmallAppBarHeight + 8.dp,
                    end = 12.dp,
                    bottom = 8.dp,
                ),
            enter = fadeIn(motion.defaultEffectsSpec()) +
                slideInVertically(motion.defaultSpatialSpec()) { -it },
            exit = fadeOut(motion.fastEffectsSpec()) +
                slideOutVertically(motion.fastSpatialSpec()) { -it },
        ) {
            val incoming = shownBanner ?: return@AnimatedVisibility
            IncomingChatBanner(
                banner = incoming,
                onOpen = {
                    inboxState.dismissBanner()
                    if (wide && currentRoute == Routes.Inbox && !sharePickerOpen) {
                        selectedChatId = incoming.threadId
                    } else {
                        navController.navigate(Routes.chat(incoming.threadId)) {
                            popUpTo(Routes.Inbox)
                            launchSingleTop = true
                        }
                    }
                },
                onDismiss = inboxState::dismissBanner,
            )
        }

        MediaViewerOverlay(
            request = mediaViewerRequest,
            loadMedia = { threadId, messageId, media ->
                inboxState.downloadMedia(threadId, messageId, media)
            },
            voicePlayer = voicePlayer,
            dynamicColor = settings.dynamicColor,
            onDismiss = { mediaViewerRequest = null },
        )

        CameraCaptureOverlay(
            request = cameraCaptureRequest,
            voicePlayer = voicePlayer,
            dynamicColor = settings.dynamicColor,
            onDismiss = { cameraCaptureRequest = null },
        )

        MediaPickerOverlay(
            request = mediaPickerRequest,
            onDismiss = { mediaPickerRequest = null },
        )
    }

    LaunchedEffect(pendingShare?.id, currentRoute) {
        val action = resolveShareArrival(
            pendingShareId = pendingShare?.id,
            handledShareId = handledShareId,
            currentRoute = currentRoute,
        ) ?: return@LaunchedEffect
        handledShareId = action.markHandledId
        if (action.clearUiForPicker) {
            selectedChatId = null
            mediaViewerRequest = null
            cameraCaptureRequest = null
            mediaPickerRequest = null
        }
        if (action.popToInbox && !navController.popBackStack(Routes.Inbox, inclusive = false)) {
            navController.navigate(Routes.Inbox) {
                launchSingleTop = true
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MainPane(
    wide: Boolean,
    inboxState: InboxState,
    likeEmoji: String,
    readReceiptsEnabled: Boolean,
    voicePlayer: VoicePlayer,
    voiceBackgroundPlayback: Boolean,
    focusMessage: Pair<String, String>? = null,
    onFocusHandled: () -> Unit = {},
    onComposerHeight: (Dp) -> Unit = {},
    listBottomInset: Dp = 0.dp,
    selectedChatId: String?,
    onSelectChat: (String?) -> Unit,
    pendingUpdate: UpdateState.Available?,
    showUpdateBadge: Boolean,
    updateState: UpdateState,
    onUpdateSeen: () -> Unit,
    onInstallUpdate: () -> Unit,
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    onOpenChat: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenMedia: (MediaViewerRequest) -> Unit,
    onOpenCamera: (CameraCaptureRequest) -> Unit,
    onOpenMediaPicker: (MediaPickerRequest) -> Unit,
    onLogout: () -> Unit,
) {
    if (!wide) {
        InboxScreen(
            conversations = inboxState.conversations,
            status = inboxState.status,
            errorMessage = inboxState.errorMessage,
            pendingUpdate = pendingUpdate,
            showUpdateBadge = showUpdateBadge,
            updateState = updateState,
            onUpdateSeen = onUpdateSeen,
            onInstallUpdate = onInstallUpdate,
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
            isTyping = inboxState::isTyping,
            typingParticipants = inboxState::typingParticipants,
            onDismissError = inboxState::clearError,
            listBottomInset = listBottomInset,
        )
        return
    }

    // Back closes the detail pane before it leaves the inbox.
    BackHandler(enabled = selectedChatId != null) { onSelectChat(null) }
    val selected = selectedChatId?.let { inboxState.conversation(it) }

    Row(Modifier.fillMaxSize()) {
            InboxScreen(
                conversations = inboxState.conversations,
                status = inboxState.status,
                errorMessage = inboxState.errorMessage,
                onRefresh = onRefresh,
                onLogin = onLogin,
                selectedConversationId = selectedChatId,
                pendingUpdate = pendingUpdate,
                showUpdateBadge = showUpdateBadge,
                updateState = updateState,
                onUpdateSeen = onUpdateSeen,
                onInstallUpdate = onInstallUpdate,
                onConversationClick = { threadId ->
                    inboxState.clearError()
                    onSelectChat(threadId)
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
                isTyping = inboxState::isTyping,
                typingParticipants = inboxState::typingParticipants,
                onDismissError = inboxState::clearError,
                listBottomInset = listBottomInset,
            )
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (selected == null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    ExpressiveStateBadge(
                        shape = MaterialShapes.Cookie7Sided.toShape(),
                        icon = Icons.Rounded.Forum,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.inbox_select_conversation),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                ChatDestination(
                    conversation = selected,
                    inboxState = inboxState,
                    likeEmoji = likeEmoji,
                    readReceiptsEnabled = readReceiptsEnabled,
                    voicePlayer = voicePlayer,
                    voiceBackgroundPlayback = voiceBackgroundPlayback,
                    focusMessageId = focusMessage?.takeIf { it.first == selected.id }?.second,
                    onFocusHandled = onFocusHandled,
                    onComposerHeight = onComposerHeight,
                    onBack = { onSelectChat(null) },
                    onOpenMedia = onOpenMedia,
                    onOpenCamera = onOpenCamera,
                    onOpenMediaPicker = onOpenMediaPicker,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** M3 small app bar height; the banner parks just below it instead of over the title. */
private val SmallAppBarHeight = 64.dp

/**
 * Banner for a message in a chat other than the open one. It reads as one
 * control that opens the chat, with its own dismiss button; the text is a live
 * region so TalkBack announces the arrival, and both lines wrap instead of
 * clipping at large font sizes. Swiping it up dismisses it the way the voice
 * player swipes down.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IncomingChatBanner(
    banner: ChatBanner,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val pull = remember { Animatable(0f) }
    var heightPx by remember { mutableIntStateOf(0) }
    // A new message can arrive mid-exit; start that banner undragged.
    LaunchedEffect(banner) { pull.snapTo(0f) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, pull.value.roundToInt()) }
            .graphicsLayer {
                alpha = 1f - (-pull.value / (heightPx * 1.05f).coerceAtLeast(1f)).coerceIn(0f, 1f)
            }
            .onSizeChanged { heightPx = it.height }
            .pointerInput(Unit) {
                // Swiping up dismisses; swiping down does nothing.
                fun release() = scope.launch { pull.animateTo(0f, motion.defaultSpatialSpec()) }
                var armed = false
                detectVerticalDragGestures(
                    onDragStart = { armed = false },
                    onVerticalDrag = { change, delta ->
                        change.consume()
                        val next = (pull.value + delta).coerceAtMost(0f)
                        // One tick when the pull first passes the dismiss point,
                        // not one per dragged pixel past it.
                        if (!armed && -next > heightPx * 0.4f) {
                            armed = true
                            haptic.thresholdActivated()
                        }
                        scope.launch {
                            pull.snapTo(next)
                        }
                    },
                    onDragEnd = {
                        armed = false
                        if (-pull.value > heightPx * 0.4f) {
                            scope.launch {
                                pull.animateTo(
                                    targetValue = -heightPx * 1.05f,
                                    animationSpec = motion.defaultSpatialSpec(),
                                )
                                onDismiss()
                            }
                        } else {
                            release()
                        }
                    },
                    onDragCancel = {
                        armed = false
                        release()
                    },
                )
            }
            // Same capsule silhouette as the floating voice player.
            .clip(RoundedCornerShape(percent = 50))
            .clickable(
                onClickLabel = stringResource(R.string.banner_open),
                role = Role.Button,
                onClick = onOpen,
            )
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (banner.isGroup) {
                ConversationAvatar(
                    contact = Contact(
                        id = banner.threadId,
                        name = banner.title,
                        initials = banner.initials,
                        avatarUrl = banner.avatarUrl,
                    ),
                    isGroup = true,
                    participants = banner.participants,
                    size = 44.dp,
                )
            } else {
                Avatar(
                    initials = banner.initials,
                    size = 44.dp,
                    imageUrl = banner.avatarUrl,
                    colorSeed = banner.threadId,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = banner.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = banner.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onDismiss,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.banner_dismiss),
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
    voicePlayer: VoicePlayer,
    voiceBackgroundPlayback: Boolean,
    focusMessageId: String? = null,
    onFocusHandled: () -> Unit = {},
    onComposerHeight: (Dp) -> Unit = {},
    onBack: () -> Unit,
    onStarted: (String) -> Unit = {},
    onOpenMedia: (MediaViewerRequest) -> Unit = {},
    onOpenCamera: (CameraCaptureRequest) -> Unit = {},
    onOpenMediaPicker: (MediaPickerRequest) -> Unit = {},
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
    val activeConversation = inboxState.withActiveMessages(conversation)
    ChatScreen(
        conversation = activeConversation,
        onSend = { text, reply, mentions ->
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
                inboxState.send(threadId, text, reply?.toReplySnapshot(), mentions)
            }
        },
        onBack = onBack,
        onPrepareMedia = { uri -> inboxState.prepareMedia(uri) },
        onSendMedia = { media -> inboxState.sendMedia(threadId, media) },
        onSendVoice = { recording -> inboxState.sendVoiceNote(threadId, recording) },
        onRetryMedia = { messageId -> inboxState.retryMedia(threadId, messageId) },
        isTyping = inboxState.isTyping(threadId),
        typingSenders = inboxState.typingParticipants(threadId),
        onTyping = { if (!isDraft) inboxState.notifyTyping(threadId) },
        isLoading = sendingDraft || inboxState.isLoading(threadId),
        errorMessage = draftError ?: inboxState.errorMessage,
        onDismissError = {
            draftError = null
            inboxState.clearError()
        },
        onLoadMedia = { mediaThreadId, messageId, media ->
            inboxState.downloadMedia(mediaThreadId, messageId, media)
        },
        onEnsureVoiceWaveform = { messageId, media ->
            inboxState.ensureVoiceWaveform(threadId, messageId, media)
        },
        onMarkRead = { messageId -> inboxState.markRead(threadId, messageId) },
        onLoadOlder = { inboxState.loadOlder(threadId) },
        onRetryOlder = { inboxState.retryOlder(threadId) },
        onReact = { messageId, emoji -> inboxState.react(threadId, messageId, emoji) },
        onEdit = { messageId, text -> inboxState.edit(threadId, messageId, text) },
        onUnsend = { messageId -> inboxState.unsend(threadId, messageId) },
        onPin = { messageId -> inboxState.pinMessage(threadId, messageId) },
        onUnpin = { messageId -> inboxState.unpinMessage(threadId, messageId) },
        attachmentsEnabled = !isDraft,
        viewerName = inboxState.viewerName,
        viewerAvatarUrl = inboxState.viewerAvatarUrl,
        likeEmoji = likeEmoji,
        readReceiptsEnabled = readReceiptsEnabled,
        voicePlayer = voicePlayer,
        voiceBackgroundPlayback = voiceBackgroundPlayback,
        focusMessageId = focusMessageId,
        onFocusHandled = onFocusHandled,
        onComposerHeight = onComposerHeight,
        onOpenMediaViewer = onOpenMedia,
        onOpenCamera = onOpenCamera,
        onOpenMediaPicker = onOpenMediaPicker,
        modifier = modifier,
    )
}
