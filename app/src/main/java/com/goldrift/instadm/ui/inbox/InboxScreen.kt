package com.goldrift.instadm.ui.inbox

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.goldrift.instadm.R
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.sampleConversations
import com.goldrift.instadm.ui.components.Avatar
import com.goldrift.instadm.ui.theme.InstaDMTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    conversations: List<Conversation>,
    onConversationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            items(
                items = conversations,
                key = { it.contact.id },
            ) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    onClick = { onConversationClick(conversation.contact.id) },
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 78.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
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
        lastMessage == null -> stringResource(R.string.inbox_no_messages)
        lastMessage.sender == MessageSender.Me ->
            stringResource(R.string.inbox_you_prefix, lastMessage.text)
        else -> lastMessage.text
    }

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
            text = lastMessage?.timestamp.orEmpty(),
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
