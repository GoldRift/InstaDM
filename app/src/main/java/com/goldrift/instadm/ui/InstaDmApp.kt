package com.goldrift.instadm.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.goldrift.instadm.InstaDmApplication
import com.goldrift.instadm.data.RealtimeBus
import com.goldrift.instadm.service.RealtimeService
import com.goldrift.instadm.ui.chat.ChatScreen
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

@Composable
fun InstaDmApp() {
    val context = LocalContext.current
    val application = context.applicationContext as InstaDmApplication
    val inboxState = application.inboxState
    val navController = rememberNavController()
    val realtimeStatus by RealtimeBus.status.collectAsState()
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

    NavHost(
        navController = navController,
        startDestination = resolvedStart,
    ) {
        composable(Routes.Login) {
            LoginScreen(
                signIn = { cookies -> application.repository.signIn(cookies) },
                onSignedIn = { mailbox ->
                    inboxState.applyMailbox(mailbox)
                    RealtimeService.start(context)
                    navController.navigate(Routes.Inbox) {
                        popUpTo(Routes.Login) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.Inbox) {
            InboxScreen(
                conversations = inboxState.conversations,
                status = inboxState.status,
                errorMessage = inboxState.errorMessage,
                realtimeStatus = realtimeStatus,
                onRefresh = {
                    inboxState.refresh()
                    RealtimeService.restart(context)
                },
                onLogin = { navController.navigate(Routes.Login) },
                onConversationClick = { threadId ->
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
                LaunchedEffect(threadId) {
                    inboxState.loadThread(threadId)
                }
                ChatScreen(
                    conversation = conversation,
                    onSend = { text -> inboxState.send(threadId, text) },
                    onBack = {
                        inboxState.clearError()
                        navController.popBackStack()
                    },
                    onSendMedia = { uri -> inboxState.sendMedia(threadId, uri) },
                    onRetryMedia = { messageId -> inboxState.retryMedia(threadId, messageId) },
                    isLoading = inboxState.isLoading(threadId),
                    errorMessage = inboxState.errorMessage,
                    onLoadMedia = { mediaThreadId, messageId, partIndex, mediaType ->
                        inboxState.downloadMedia(mediaThreadId, messageId, partIndex, mediaType)
                    },
                    onMarkRead = { messageId -> inboxState.markRead(threadId, messageId) },
                    onLoadOlder = { inboxState.loadOlder(threadId) },
                    onRetryOlder = { inboxState.retryOlder(threadId) },
                    onReact = { messageId, emoji ->
                        inboxState.react(threadId, messageId, emoji)
                    },
                    onEdit = { messageId, text ->
                        inboxState.edit(threadId, messageId, text)
                    },
                    onUnsend = { messageId ->
                        inboxState.unsend(threadId, messageId)
                    },
                )
            }
        }
    }
}
