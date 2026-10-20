package com.goldrift.instadm.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goldrift.instadm.R
import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.ui.components.Avatar
import com.goldrift.instadm.ui.theme.InstaDMTheme

private val OutgoingBubbleShape = RoundedCornerShape(
    topStart = 24.dp,
    topEnd = 24.dp,
    bottomEnd = 6.dp,
    bottomStart = 24.dp,
)

private val IncomingBubbleShape = RoundedCornerShape(
    topStart = 24.dp,
    topEnd = 24.dp,
    bottomEnd = 24.dp,
    bottomStart = 6.dp,
)

@Composable
fun ChatScreen(
    conversation: Conversation,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isTyping: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onLoadMedia: MediaLoader? = null,
    onMarkRead: (String) -> Unit = {},
) {
    val messages = conversation.messages
    var draft by remember { mutableStateOf("") }
    var viewerTarget by remember { mutableStateOf<MediaViewerTarget?>(null) }
    val listState = rememberLazyListState()

    fun sendMessage() {
        val text = draft.trim()
        if (text.isEmpty()) return

        onSend(text)
        draft = ""
    }

    // Keep the newest bubble (bottom, index 0 in a reverse layout) in view.
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Mark the thread read whenever the newest server message changes while the
    // chat is composed. InboxState deduplicates targets and ignores backgrounding.
    val readTarget = messages.lastOrNull { it.canMarkRead() }?.id.orEmpty()
    LaunchedEffect(readTarget) {
        if (readTarget.isNotBlank()) {
            onMarkRead(readTarget)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatTopSection(
                contact = conversation.contact,
                onBack = onBack,
            )
        },
        bottomBar = {
            MessageComposer(
                draft = draft,
                onDraftChange = { draft = it },
                onSend = ::sendMessage,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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

            when {
                isLoading && messages.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                messages.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_no_messages),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                else -> {
                    MessageList(
                        messages = messages,
                        contactInitials = conversation.contact.initials,
                        isTyping = isTyping,
                        listState = listState,
                        threadId = conversation.id,
                        onOpenMedia = { messageId, media ->
                            viewerTarget = MediaViewerTarget(messageId, media)
                        },
                        onLoadMedia = onLoadMedia,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    viewerTarget?.let { target ->
        MediaViewerDialog(
            threadId = conversation.id,
            target = target,
            loadMedia = onLoadMedia,
            onDismiss = { viewerTarget = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopSection(
    contact: Contact,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(
                        initials = contact.initials,
                        size = 38.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = contact.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.chat_direct_message),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.chat_back),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onBackground,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    contactInitials: String,
    isTyping: Boolean,
    listState: LazyListState,
    threadId: String,
    onOpenMedia: (messageId: String, media: MessageMedia) -> Unit,
    onLoadMedia: MediaLoader?,
    modifier: Modifier = Modifier,
) {
    val reversed = messages.asReversed()
    val visibleMessageIds by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.mapNotNullTo(HashSet()) { it.key as? String }
        }
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isTyping) {
            item(key = "typing", contentType = "typing") {
                TypingBubble(
                    contactInitials = contactInitials,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        itemsIndexed(
            items = reversed,
            key = { _, message -> message.id },
            contentType = { _, message -> message.sender },
        ) { reverseIndex, message ->
            val originalIndex = messages.lastIndex - reverseIndex
            val previousSender = messages.getOrNull(originalIndex - 1)?.sender
            val showAvatar =
                message.sender == MessageSender.Them && previousSender != MessageSender.Them

            MessageRow(
                message = message,
                contactInitials = contactInitials,
                showAvatar = showAvatar,
                threadId = threadId,
                isVisible = message.id in visibleMessageIds,
                onOpenMedia = { media -> onOpenMedia(message.id, media) },
                onLoadMedia = onLoadMedia,
                modifier = Modifier.animateItem(),
            )
        }

        item(key = "date", contentType = "date") {
            DatePill()
        }
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    contactInitials: String,
    showAvatar: Boolean,
    threadId: String,
    isVisible: Boolean,
    onOpenMedia: (MessageMedia) -> Unit,
    onLoadMedia: MediaLoader?,
    modifier: Modifier = Modifier,
) {
    val isMine = message.sender == MessageSender.Me

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isMine) {
            if (showAvatar) {
                Avatar(
                    initials = contactInitials,
                    size = 28.dp,
                )
            } else {
                Spacer(Modifier.width(28.dp))
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Surface(
                shape = if (isMine) OutgoingBubbleShape else IncomingBubbleShape,
                color = if (isMine) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (isMine) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ) {
                MessageBubbleContent(
                    message = message,
                    threadId = threadId,
                    isVisible = isVisible,
                    onOpenMedia = onOpenMedia,
                    onLoadMedia = onLoadMedia,
                )
            }

            if (isMine) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    if (message.pending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = stringResource(
                                if (message.seen) R.string.chat_seen else R.string.chat_sent,
                            ),
                            tint = if (message.seen) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubbleContent(
    message: ChatMessage,
    threadId: String,
    isVisible: Boolean,
    onOpenMedia: (MessageMedia) -> Unit,
    onLoadMedia: MediaLoader?,
) {
    val media = message.media.filter { it.isVisual }
    Column(
        modifier = Modifier.padding(
            horizontal = if (media.isEmpty()) 14.dp else 4.dp,
            vertical = if (media.isEmpty()) 9.dp else 4.dp,
        ),
    ) {
        media.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(4.dp))
            MediaThumbnail(
                media = item,
                threadId = threadId,
                messageId = message.id,
                active = isVisible,
                loadMedia = onLoadMedia,
                onClick = { onOpenMedia(item) },
            )
        }

        if (message.text.isNotBlank()) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(
                    horizontal = if (media.isEmpty()) 0.dp else 10.dp,
                    vertical = if (media.isEmpty()) 0.dp else 6.dp,
                ),
            )
        }
    }
}

@Composable
private fun TypingBubble(
    contactInitials: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Avatar(
            initials = contactInitials,
            size = 28.dp,
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = IncomingBubbleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            TypingDots(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
    }
}

@Composable
private fun TypingDots(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 550,
                        delayMillis = index * 120,
                        easing = LinearEasing,
                    ),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .size(7.dp)
                    .alpha(alpha)
                    .background(color, CircleShape),
            )
        }
    }
}

@Composable
private fun DatePill() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Text(
                text = stringResource(R.string.chat_today),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun MessageComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 46.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        maxLines = 4,
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draft.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.chat_message_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                innerTextField()
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                // A Column gives AnimatedVisibility its own scope, avoiding the RowScope overload.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AnimatedVisibility(
                        visible = draft.isNotBlank(),
                        enter = scaleIn(
                            initialScale = 0.5f,
                            animationSpec = motion.fastSpatialSpec(),
                        ),
                        exit = scaleOut(
                            targetScale = 0.5f,
                            animationSpec = motion.fastEffectsSpec(),
                        ),
                    ) {
                        FilledIconButton(
                            onClick = onSend,
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.chat_send),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Chat light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ChatScreenLightPreview() {
    InstaDMTheme(darkTheme = false) {
        ChatScreen(
            conversation = sampleConversations().first(),
            isTyping = false,
            onSend = {},
            onBack = {},
        )
    }
}

@Preview(
    name = "Chat dark",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    backgroundColor = 0xFF111318,
)
@Composable
private fun ChatScreenDarkPreview() {
    InstaDMTheme(darkTheme = true) {
        ChatScreen(
            conversation = sampleConversations().first(),
            isTyping = true,
            onSend = {},
            onBack = {},
        )
    }
}
