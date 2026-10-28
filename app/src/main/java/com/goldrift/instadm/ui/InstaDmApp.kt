package com.goldrift.instadm.ui

import android.content.Context
import android.net.Uri
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

private object Routes {
    const val Login = "login"
    const val Inbox = "inbox"
    const val Chat = "chat"
    const val ThreadIdArg = "threadId"
    const val ChatRoute = "$Chat/{$ThreadIdArg}"

    fun chat(threadId: String): String = "$Chat/${Uri.encode(threadId)}"
}

/** Material 3 emphasized easing for screen transitions. */
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/** Expanded-width breakpoint for the side-by-side inbox and chat panes. */
private val TwoPaneMinWidth = 840.dp
private val InboxPaneWidth = 380.dp

@Composable
fun InstaDmApp(
    pendingThreadId: String? = null,
    onPendingThreadHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val application = context.applicationContext as InstaDmApplication
    val inboxState = application.inboxState
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
            CircularProgressIndicator()
        }
        return
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentChatThreadId = if (currentRoute == Routes.ChatRoute) {
        backStackEntry?.arguments?.getString(Routes.ThreadIdArg)
    } else {
        null
    }
    val pendingConversation = pendingThreadId?.let(inboxState::conversation)

    NavHost(
        navController = navController,
        startDestination = resolvedStart,
        enterTransition = {
            fadeIn(tween(400, easing = EmphasizedDecelerate)) +
                slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { it / 10 }
        },
        exitTransition = {
            fadeOut(tween(200, easing = EmphasizedAccelerate)) +
                slideOutHorizontally(tween(200, easing = EmphasizedAccelerate)) { -it / 10 }
        },
        popEnterTransition = {
            fadeIn(tween(400, easing = EmphasizedDecelerate)) +
                slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { -it / 10 }
        },
        popExitTransition = {
            fadeOut(tween(200, easing = EmphasizedAccelerate)) +
                slideOutHorizontally(tween(200, easing = EmphasizedAccelerate)) { it / 10 }
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
                onRefresh = {
                    inboxState.refresh()
                    RealtimeService.restart(context)
                },
                onLogin = { navController.navigate(Routes.Login) },
                onOpenChat = { threadId ->
                    inboxState.clearError()
                    navController.navigate(Routes.chat(threadId))
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
                    onBack = {
                        inboxState.clearError()
                        navController.popBackStack()
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
    onRefresh: () -> Unit,
    onLogin: () -> Unit,
    onOpenChat: (String) -> Unit,
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
            )
            return@BoxWithConstraints
        }

        var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
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
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val threadId = conversation.id
    LaunchedEffect(threadId) {
        inboxState.loadThread(threadId)
    }
    ChatScreen(
        conversation = conversation,
        onSend = { text, reply ->
            inboxState.send(threadId, text, reply?.toReplySnapshot())
        },
        onBack = onBack,
        onSendMedia = { uri -> inboxState.sendMedia(threadId, uri) },
        onSendVoice = { recording -> inboxState.sendVoiceNote(threadId, recording) },
        onRetryMedia = { messageId -> inboxState.retryMedia(threadId, messageId) },
        isLoading = inboxState.isLoading(threadId),
        errorMessage = inboxState.errorMessage,
        onLoadMedia = { mediaThreadId, messageId, media ->
            inboxState.downloadMedia(mediaThreadId, messageId, media)
        },
        onMarkRead = { messageId -> inboxState.markRead(threadId, messageId) },
        onLoadOlder = { inboxState.loadOlder(threadId) },
        onRetryOlder = { inboxState.retryOlder(threadId) },
        onReact = { messageId, emoji -> inboxState.react(threadId, messageId, emoji) },
        onEdit = { messageId, text -> inboxState.edit(threadId, messageId, text) },
        onUnsend = { messageId -> inboxState.unsend(threadId, messageId) },
        modifier = modifier,
    )
}
