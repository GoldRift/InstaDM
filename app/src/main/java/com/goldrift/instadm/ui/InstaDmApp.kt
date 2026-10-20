package com.goldrift.instadm.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.goldrift.instadm.data.ProtocolRepository
import com.goldrift.instadm.data.RealtimeBus
import com.goldrift.instadm.data.SessionStore
import com.goldrift.instadm.service.RealtimeService
import com.goldrift.instadm.ui.chat.ChatScreen
import com.goldrift.instadm.ui.chat.InboxState
import com.goldrift.instadm.ui.inbox.InboxScreen

private object Routes {
    const val Inbox = "inbox"
    const val Chat = "chat"
    const val ThreadIdArg = "threadId"
    const val ChatRoute = "$Chat/{$ThreadIdArg}"

    fun chat(threadId: String): String = "$Chat/${Uri.encode(threadId)}"
}

@Composable
fun InstaDmApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionStore = remember(context) { SessionStore(context) }
    val repository = remember(sessionStore) { ProtocolRepository(sessionStore) }
    val inboxState = remember(repository, scope) { InboxState(repository, scope) }
    val navController = rememberNavController()
    val realtimeStatus by RealtimeBus.status.collectAsState()

    DisposableEffect(repository) {
        onDispose { repository.close() }
    }
    LaunchedEffect(inboxState) {
        inboxState.refresh()
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Inbox,
    ) {
        composable(Routes.Inbox) {
            InboxScreen(
                conversations = inboxState.conversations,
                status = inboxState.status,
                errorMessage = inboxState.errorMessage,
                cookiePath = sessionStore.cookieFile.absolutePath,
                realtimeStatus = realtimeStatus,
                onRefresh = {
                    inboxState.refresh()
                    RealtimeService.restart(context)
                },
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
                    isLoading = inboxState.isLoading(threadId),
                    errorMessage = inboxState.errorMessage,
                    onLoadMedia = { mediaThreadId, messageId, partIndex, mediaType ->
                        inboxState.downloadMedia(mediaThreadId, messageId, partIndex, mediaType)
                    },
                    onMarkRead = { messageId -> inboxState.markRead(threadId, messageId) },
                    onLoadOlder = { inboxState.loadOlder(threadId) },
                    onRetryOlder = { inboxState.retryOlder(threadId) },
                )
            }
        }
    }
}
