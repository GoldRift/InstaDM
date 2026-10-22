package com.goldrift.instadm.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.goldrift.instadm.R
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.InboxStatus
import com.goldrift.instadm.ui.chat.formatInboxTime
import com.goldrift.instadm.ui.chat.initialsOf
import com.goldrift.instadm.ui.chat.label
import com.goldrift.instadm.ui.chat.render
import com.goldrift.instadm.ui.chat.sampleConversations
import com.goldrift.instadm.ui.chat.toPreview
import com.goldrift.instadm.ui.components.Avatar
import com.goldrift.instadm.ui.components.ConversationAvatar
import com.goldrift.instadm.ui.theme.InstaDMTheme

private val ListTopShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    conversations: List<Conversation>,
    onConversationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    status: InboxStatus = InboxStatus.Ready,
    errorMessage: String? = null,
    onRefresh: () -> Unit = {},
    onLogin: () -> Unit = {},
    selectedConversationId: String? = null,
    viewerName: String = "",
    viewerAvatarUrl: String = "",
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    // Placeholder until in-app search exists.
                    IconButton(onClick = {}) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.inbox_search),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    if (viewerAvatarUrl.isBlank() && viewerName.isBlank()) {
                        Box(
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = stringResource(R.string.inbox_profile_photo),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        Avatar(
                            initials = initialsOf(viewerName),
                            size = 32.dp,
                            imageUrl = viewerAvatarUrl,
                            contentDescription = stringResource(R.string.inbox_profile_photo),
                            colorSeed = viewerName,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            shape = ListTopShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            PullToRefreshBox(
                isRefreshing = status == InboxStatus.Loading && conversations.isNotEmpty(),
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    status == InboxStatus.Loading && conversations.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    status == InboxStatus.NoSession && conversations.isEmpty() -> {
                        InboxMessage(
                            title = stringResource(R.string.inbox_no_session_title),
                            body = stringResource(R.string.inbox_no_session_body),
                            actionLabel = stringResource(R.string.inbox_login),
                            onAction = onLogin,
                        )
                    }

                    status == InboxStatus.Error && conversations.isEmpty() -> {
                        InboxMessage(
                            title = stringResource(R.string.inbox_error_title),
                            body = errorMessage ?: stringResource(R.string.inbox_error_body),
                            actionLabel = stringResource(R.string.inbox_retry),
                            onAction = onRefresh,
                            secondaryActionLabel = stringResource(R.string.inbox_error_login),
                            onSecondaryAction = onLogin,
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

                    else -> Column(Modifier.fillMaxSize()) {
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
                        val navBarBottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding()
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = navBarBottom + 8.dp,
                            ),
                        ) {
                            items(
                                items = conversations,
                                key = { it.id },
                            ) { conversation ->
                                ConversationRow(
                                    conversation = conversation,
                                    selected = conversation.id == selectedConversationId,
                                    onClick = { onConversationClick(conversation.id) },
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
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
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
        if (secondaryActionLabel != null) {
            TextButton(onClick = onSecondaryAction) {
                Text(secondaryActionLabel)
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val lastMessage = conversation.messages.lastOrNull()
    val resources = LocalContext.current.resources
    val preview = ((lastMessage?.toPreview() ?: conversation.preview)
        .label(conversation.isGroup)
        ?.render { id, args -> resources.getString(id, *args.toTypedArray()) })
        ?: stringResource(R.string.inbox_tap_to_open)
    val timestamp = lastMessage?.timestamp ?: formatInboxTime(conversation.lastActivityMs)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainer)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .heightIn(min = 80.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ConversationAvatar(
            contact = conversation.contact,
            isGroup = conversation.isGroup,
            participants = conversation.participants,
            size = 48.dp,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = conversation.contact.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = timestamp,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Preview(name = "Inbox light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun InboxLightPreview() {
    InstaDMTheme(darkTheme = false, dynamicColor = false) {
        InboxScreen(
            conversations = sampleConversations(),
            onConversationClick = {},
            viewerName = "Alex Rivera",
        )
    }
}

@Preview(
    name = "Inbox dark",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    backgroundColor = 0xFF10131C,
)
@Composable
private fun InboxDarkPreview() {
    InstaDMTheme(darkTheme = true, dynamicColor = false) {
        InboxScreen(
            conversations = sampleConversations(),
            onConversationClick = {},
            viewerName = "Alex Rivera",
        )
    }
}
