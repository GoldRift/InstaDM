package com.goldrift.instadm.ui.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.goldrift.instadm.R
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.InboxStatus
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.formatInboxTime
import com.goldrift.instadm.ui.chat.sampleConversations
import com.goldrift.instadm.ui.components.Avatar
import com.goldrift.instadm.ui.theme.InstaDMTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    conversations: List<Conversation>,
    onConversationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    status: InboxStatus = InboxStatus.Ready,
    errorMessage: String? = null,
    cookiePath: String = "",
    onRefresh: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.inbox_refresh),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                status == InboxStatus.Loading && conversations.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                status == InboxStatus.NoSession && conversations.isEmpty() -> {
                    InboxMessage(
                        title = stringResource(R.string.inbox_no_session_title),
                        body = stringResource(R.string.inbox_no_session_body, cookiePath),
                        actionLabel = stringResource(R.string.inbox_retry),
                        onAction = onRefresh,
                    )
                }

                status == InboxStatus.Error && conversations.isEmpty() -> {
                    InboxMessage(
                        title = stringResource(R.string.inbox_error_title),
                        body = errorMessage ?: stringResource(R.string.inbox_error_body),
                        actionLabel = stringResource(R.string.inbox_retry),
                        onAction = onRefresh,
                    )
                }

                conversations.isEmpty() -> {
                    InboxMessage(
                        title = stringResource(R.string.inbox_empty_title),
                        body = stringResource(R.string.inbox_empty_body),
                        actionLabel = stringResource(R.string.inbox_retry),
                        onAction = onRefresh,
                    )
                }

                else -> {
                    Column(Modifier.fillMaxSize()) {
                        if (errorMessage != null) {
                            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                                Text(
                                    text = errorMessage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(
                                items = conversations,
                                key = { it.id },
                            ) { conversation ->
                                ConversationRow(
                                    conversation = conversation,
                                    onClick = { onConversationClick(conversation.id) },
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 78.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxMessage(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    onClick: () -> Unit,
) {
    val lastMessage = conversation.messages.lastOrNull()
    val preview = when {
        lastMessage == null && conversation.preview.isNotBlank() -> conversation.preview
        lastMessage == null -> stringResource(R.string.inbox_tap_to_open)
        lastMessage.sender == MessageSender.Me ->
            stringResource(R.string.inbox_you_prefix, lastMessage.text)
        else -> lastMessage.text
    }
    val timestamp = lastMessage?.timestamp ?: formatInboxTime(conversation.lastActivityMs)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            initials = conversation.contact.initials,
            size = 50.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = conversation.contact.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = timestamp,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(name = "Inbox light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun InboxLightPreview() {
    InstaDMTheme(darkTheme = false) {
        InboxScreen(
            conversations = sampleConversations(),
            onConversationClick = {},
        )
    }
}

@Preview(
    name = "Inbox dark",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    backgroundColor = 0xFF111318,
)
@Composable
private fun InboxDarkPreview() {
    InstaDMTheme(darkTheme = true) {
        InboxScreen(
            conversations = sampleConversations(),
            onConversationClick = {},
        )
    }
}
