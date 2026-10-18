package com.goldrift.instadm.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.goldrift.instadm.ui.chat.ChatScreen
import com.goldrift.instadm.ui.chat.InboxState
import com.goldrift.instadm.ui.chat.sampleConversations
import com.goldrift.instadm.ui.inbox.InboxScreen

private object Routes {
    const val Inbox = "inbox"
    const val Chat = "chat"
    const val ContactIdArg = "contactId"
    const val ChatRoute = "$Chat/{$ContactIdArg}"

    fun chat(contactId: String): String = "$Chat/${Uri.encode(contactId)}"
}

@Composable
fun InstaDmApp() {
    val scope = rememberCoroutineScope()
    val inboxState = remember(scope) { InboxState(sampleConversations(), scope) }
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.Inbox,
    ) {
        composable(Routes.Inbox) {
            InboxScreen(
                conversations = inboxState.conversations,
                onConversationClick = { contactId ->
                    navController.navigate(Routes.chat(contactId))
                },
            )
        }

        composable(
            route = Routes.ChatRoute,
            arguments = listOf(
                navArgument(Routes.ContactIdArg) { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments
                ?.getString(Routes.ContactIdArg)
                .orEmpty()
            val conversation = inboxState.conversation(contactId)

            if (conversation == null) {
                // Unknown contact (for example after process restoration); return to the inbox.
                LaunchedEffect(contactId) { navController.popBackStack() }
            } else {
                ChatScreen(
                    conversation = conversation,
                    isTyping = inboxState.isTyping(contactId),
                    onSend = { text -> inboxState.send(contactId, text) },
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
