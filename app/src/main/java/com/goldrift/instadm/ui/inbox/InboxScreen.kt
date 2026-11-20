package com.goldrift.instadm.ui.inbox

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.goldrift.instadm.R
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.ConversationParticipant
import com.goldrift.instadm.ui.chat.InboxStatus
import com.goldrift.instadm.ui.chat.formatInboxTime
import com.goldrift.instadm.ui.chat.initialsOf
import com.goldrift.instadm.ui.chat.label
import com.goldrift.instadm.ui.chat.render
import com.goldrift.instadm.ui.chat.sampleConversations
import com.goldrift.instadm.ui.chat.toPreview
import com.goldrift.instadm.ui.chat.unreadBadgeLabel
import com.goldrift.instadm.ui.components.Avatar
import com.goldrift.instadm.ui.components.ConversationAvatar
import com.goldrift.instadm.ui.components.ExpressiveStateBadge
import com.goldrift.instadm.ui.components.InlineBanner
import com.goldrift.instadm.ui.components.confirm
import com.goldrift.instadm.ui.components.thresholdActivated
import com.goldrift.instadm.ui.theme.InstaDMTheme
import com.goldrift.instadm.ui.update.UpdateDialog
import com.goldrift.instadm.update.UpdateInstaller
import com.goldrift.instadm.update.UpdateState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    hasMoreConversations: Boolean = false,
    isLoadingMore: Boolean = false,
    moreError: Boolean = false,
    onLoadMore: () -> Unit = {},
    onRetryMore: () -> Unit = {},
    shareHint: String? = null,
    pendingUpdate: UpdateState.Available? = null,
    showUpdateBadge: Boolean = false,
    updateState: UpdateState = UpdateState.Idle,
    onUpdateSeen: () -> Unit = {},
    onInstallUpdate: () -> Unit = {},
    onSearch: () -> Unit = {},
    onSettings: () -> Unit = {},
    onLogout: () -> Unit = {},
    isTyping: (String) -> Boolean = { false },
    typingParticipants: (String) -> List<ConversationParticipant> = { emptyList() },
    onDismissError: (() -> Unit)? = null,
    onDismissShare: (() -> Unit)? = null,
    /** Space for a player bar floating over the bottom of the list. */
    listBottomInset: Dp = 0.dp,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Matches the TopAppBar so the sheet's rounded top corners read as
        // cut into the bar instead of showing a background-colored sliver.
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                },
                actions = {
                    IconButton(
                        onClick = onSearch,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = stringResource(R.string.inbox_search),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    AccountMenu(
                        viewerName = viewerName,
                        viewerAvatarUrl = viewerAvatarUrl,
                        pendingUpdate = pendingUpdate,
                        showUpdateBadge = showUpdateBadge,
                        updateState = updateState,
                        onUpdateSeen = onUpdateSeen,
                        onInstallUpdate = onInstallUpdate,
                        onSettings = onSettings,
                        onLogout = onLogout,
                    )
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
        val listTopShape = RoundedCornerShape(
            topStart = MaterialTheme.shapes.extraLarge.topStart,
            topEnd = MaterialTheme.shapes.extraLarge.topEnd,
            bottomEnd = CornerSize(0.dp),
            bottomStart = CornerSize(0.dp),
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            shape = listTopShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            val motion = MaterialTheme.motionScheme
            val refreshing = status == InboxStatus.Loading && conversations.isNotEmpty()
            val pullState = rememberPullToRefreshState()
            val haptic = LocalHapticFeedback.current
            val currentRefreshing by rememberUpdatedState(refreshing)
            LaunchedEffect(pullState) {
                var armed = false
                snapshotFlow {
                    when {
                        currentRefreshing -> -1f
                        pullState.isAnimating -> -2f
                        else -> pullState.distanceFraction
                    }
                }.collect { raw ->
                    val step = when (raw) {
                        -1f -> stepPullRefreshThreshold(
                            armed = armed,
                            distanceFraction = 0f,
                            refreshing = true,
                        )
                        -2f -> stepPullRefreshThreshold(
                            armed = armed,
                            distanceFraction = pullState.distanceFraction,
                            refreshing = false,
                            isAnimating = true,
                        )
                        else -> stepPullRefreshThreshold(
                            armed = armed,
                            distanceFraction = raw,
                            refreshing = false,
                            isAnimating = false,
                        )
                    }
                    armed = step.armed
                    if (step.fireHaptic) haptic.thresholdActivated()
                }
            }
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
                state = pullState,
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = pullState,
                        isRefreshing = refreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
            ) {
                when {
                    status == InboxStatus.Loading && conversations.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val loading = stringResource(R.string.app_loading)
                            LoadingIndicator(
                                modifier = Modifier.semantics { contentDescription = loading },
                            )
                        }
                    }

                    status == InboxStatus.NoSession && conversations.isEmpty() -> {
                        InboxMessage(
                            badgeShape = MaterialShapes.Cookie7Sided.toShape(),
                            badgeIcon = Icons.Rounded.Lock,
                            badgeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            badgeIconTint = MaterialTheme.colorScheme.onPrimaryContainer,
                            title = stringResource(R.string.inbox_no_session_title),
                            body = stringResource(R.string.inbox_no_session_body),
                            actionLabel = stringResource(R.string.inbox_login),
                            onAction = onLogin,
                        )
                    }

                    status == InboxStatus.Error && conversations.isEmpty() -> {
                        InboxMessage(
                            badgeShape = MaterialShapes.SoftBurst.toShape(),
                            badgeIcon = Icons.Rounded.CloudOff,
                            badgeContainerColor = MaterialTheme.colorScheme.errorContainer,
                            badgeIconTint = MaterialTheme.colorScheme.onErrorContainer,
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
                            badgeShape = MaterialShapes.Clover4Leaf.toShape(),
                            badgeIcon = Icons.Rounded.Forum,
                            badgeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            badgeIconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                            title = stringResource(R.string.inbox_empty_title),
                            body = stringResource(R.string.inbox_empty_body),
                            actionLabel = stringResource(R.string.inbox_retry),
                            onAction = onRefresh,
                        )
                    }

                    else -> Column(Modifier.fillMaxSize()) {
                        var lastShareHint by remember { mutableStateOf(shareHint) }
                        if (shareHint != null && shareHint != lastShareHint) {
                            SideEffect { lastShareHint = shareHint }
                        }
                        var lastErrorMessage by remember { mutableStateOf(errorMessage) }
                        if (errorMessage != null && errorMessage != lastErrorMessage) {
                            SideEffect { lastErrorMessage = errorMessage }
                        }
                        AnimatedVisibility(
                            visible = shareHint != null,
                            enter = expandVertically(motion.defaultSpatialSpec()) +
                                fadeIn(motion.defaultEffectsSpec()),
                            exit = shrinkVertically(motion.defaultSpatialSpec()) +
                                fadeOut(motion.defaultEffectsSpec()),
                        ) {
                            val hint = shareHint ?: lastShareHint ?: return@AnimatedVisibility
                            InlineBanner(
                                text = hint,
                                icon = Icons.Rounded.Share,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                onDismiss = onDismissShare,
                            )
                        }
                        AnimatedVisibility(
                            visible = errorMessage != null,
                            enter = expandVertically(motion.defaultSpatialSpec()) +
                                fadeIn(motion.defaultEffectsSpec()),
                            exit = shrinkVertically(motion.defaultSpatialSpec()) +
                                fadeOut(motion.defaultEffectsSpec()),
                        ) {
                            val message =
                                errorMessage ?: lastErrorMessage ?: return@AnimatedVisibility
                            InlineBanner(
                                text = message,
                                icon = Icons.Rounded.ErrorOutline,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                actionLabel = stringResource(R.string.inbox_retry),
                                onAction = onRefresh,
                                onDismiss = onDismissError,
                            )
                        }
                        val navBarBottom = WindowInsets.navigationBars
                            .asPaddingValues()
                            .calculateBottomPadding()
                        val listState = rememberLazyListState()
                        // Ask for the next page once the oldest rows are on
                        // screen, so scrolling to the bottom keeps going.
                        val loadMoreNow by remember(
                            listState,
                            hasMoreConversations,
                            isLoadingMore,
                            moreError,
                        ) {
                            derivedStateOf {
                                if (!hasMoreConversations || isLoadingMore || moreError) {
                                    false
                                } else {
                                    val info = listState.layoutInfo
                                    val lastVisible = info.visibleItemsInfo.lastOrNull()?.index
                                    lastVisible != null &&
                                        lastVisible >= info.totalItemsCount - 3
                                }
                            }
                        }
                        LaunchedEffect(loadMoreNow, conversations.size) {
                            if (loadMoreNow) onLoadMore()
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                top = 8.dp,
                                bottom = navBarBottom + 8.dp + listBottomInset,
                            ),
                        ) {
                            items(
                                items = conversations,
                                key = { it.id },
                                contentType = { "conversation" },
                            ) { conversation ->
                                ConversationRow(
                                    conversation = conversation,
                                    selected = conversation.id == selectedConversationId,
                                    typing = isTyping(conversation.id),
                                    typingSenders = typingParticipants(conversation.id),
                                    onClick = { onConversationClick(conversation.id) },
                                )
                            }
                            if (hasMoreConversations) {
                                item(
                                    key = "older",
                                    contentType = "footer",
                                ) {
                                    OlderConversationsFooter(
                                        isLoading = isLoadingMore,
                                        hasError = moreError,
                                        onRetry = onRetryMore,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OlderConversationsFooter(
    isLoading: Boolean,
    hasError: Boolean,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            hasError -> TextButton(onClick = onRetry) {
                Text(stringResource(R.string.inbox_retry))
            }

            isLoading -> {
                val description = stringResource(R.string.inbox_loading_older)
                LoadingIndicator(
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = description },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InboxMessage(
    badgeShape: Shape,
    badgeIcon: ImageVector,
    badgeContainerColor: Color,
    badgeIconTint: Color,
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
        ExpressiveStateBadge(
            shape = badgeShape,
            icon = badgeIcon,
            containerColor = badgeContainerColor,
            iconTint = badgeIconTint,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAction, shapes = ButtonDefaults.shapes()) {
            Text(actionLabel)
        }
        if (secondaryActionLabel != null) {
            TextButton(onClick = onSecondaryAction, shapes = ButtonDefaults.shapes()) {
                Text(secondaryActionLabel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConversationRow(
    conversation: Conversation,
    selected: Boolean,
    typing: Boolean,
    typingSenders: List<ConversationParticipant>,
    onClick: () -> Unit,
) {
    val lastMessage = conversation.messages.lastOrNull()
    val resources = LocalContext.current.resources
    val preview = ((lastMessage?.toPreview() ?: conversation.preview)
        .label(conversation.isGroup)
        ?.render { id, args -> resources.getString(id, *args.toTypedArray()) })
        ?: stringResource(R.string.inbox_tap_to_open)
    val typingText = when {
        !typing -> null
        !conversation.isGroup || typingSenders.isEmpty() -> stringResource(R.string.chat_typing)
        typingSenders.size == 1 -> stringResource(
            R.string.chat_typing_named,
            typingSenders.first().name,
        )
        else -> stringResource(
            R.string.chat_typing_and_others,
            typingSenders.first().name,
        )
    }
    val timestamp = lastMessage?.timestamp ?: formatInboxTime(conversation.lastActivityMs)
    // Unread rows use the same bold-name, accent-time, badge treatment as the
    // other messengers people already read, so the contrast alone says "new".
    val unread = conversation.hasUnread
    val motion = MaterialTheme.motionScheme
    val rowShape = RoundedCornerShape(20.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(rowShape)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .heightIn(min = 76.dp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
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
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                ),
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            AnimatedContent(
                targetState = (typingText ?: preview) to (typingText != null),
                transitionSpec = {
                    fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                },
                label = "conversation_preview",
            ) { (lineText, isTypingLine) ->
                Text(
                    text = lineText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isTypingLine || unread) FontWeight.Medium else null,
                    ),
                    color = when {
                        isTypingLine -> MaterialTheme.colorScheme.primary
                        selected -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                        unread -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (unread) FontWeight.SemiBold else null,
                ),
                color = when {
                    unread -> MaterialTheme.colorScheme.primary
                    selected -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (unread) {
                Spacer(Modifier.height(6.dp))
                if (conversation.unreadCount > 0) {
                    UnreadBadge(conversation.unreadCount)
                } else {
                    UnreadDot()
                }
            }
        }
    }
}

/**
 * Profile picture in the top bar. Tapping it opens the account sheet: a
 * signed-in header that identifies the account, then grouped action rows.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountMenu(
    viewerName: String,
    viewerAvatarUrl: String,
    pendingUpdate: UpdateState.Available?,
    showUpdateBadge: Boolean,
    updateState: UpdateState,
    onUpdateSeen: () -> Unit,
    onInstallUpdate: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var updateDialog by remember { mutableStateOf<UpdateState.Available?>(null) }
    IconButton(
        onClick = {
            onUpdateSeen()
            expanded = true
        },
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.padding(end = 8.dp),
    ) {
        BadgedBox(
            badge = {
                if (showUpdateBadge) {
                    val description = stringResource(R.string.inbox_update_badge)
                    // Inset from the icon button's own corner: its circular clip
                    // cuts a badge that sits further out.
                    Badge(
                        modifier = Modifier
                            .offset(x = (-2).dp, y = 2.dp)
                            .semantics {
                                contentDescription = description
                                liveRegion = LiveRegionMode.Polite
                            },
                    )
                }
            },
        ) {
            if (viewerAvatarUrl.isBlank() && viewerName.isBlank()) {
                Box(
                    modifier = Modifier
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
                )
            }
        }
    }
    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            AccountSheetContent(
                viewerName = viewerName,
                viewerAvatarUrl = viewerAvatarUrl,
                pendingUpdate = pendingUpdate,
                onUpdate = {
                    expanded = false
                    updateDialog = pendingUpdate
                },
                onSettings = {
                    expanded = false
                    onSettings()
                },
                onLogout = {
                    expanded = false
                    onLogout()
                },
            )
        }
    }

    updateDialog?.let { available ->
        val context = LocalContext.current
        var canInstall by remember { mutableStateOf(UpdateInstaller.canInstall(context)) }
        val lifecycleOwner = LocalLifecycleOwner.current
        // Granting install permission happens on a system page, so the dialog
        // re-reads it when the screen comes back instead of staying stale.
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    canInstall = UpdateInstaller.canInstall(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        UpdateDialog(
            update = available.update,
            apk = available.apk,
            state = updateState,
            canInstall = canInstall,
            onInstall = onInstallUpdate,
            onOpenInstallPermission = { UpdateInstaller.openInstallPermission(context) },
            onDismiss = { updateDialog = null },
        )
    }
}

/** Sheet body: who is signed in, then the grouped account actions. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountSheetContent(
    viewerName: String,
    viewerAvatarUrl: String,
    pendingUpdate: UpdateState.Available?,
    onUpdate: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        val haptic = LocalHapticFeedback.current
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                initials = initialsOf(viewerName),
                size = 48.dp,
                imageUrl = viewerAvatarUrl,
                colorSeed = viewerName,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = viewerName.ifBlank {
                        stringResource(R.string.account_default_name)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.account_signed_in),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        val actionCount = if (pendingUpdate != null) 3 else 2
        var actionIndex = 0
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            if (pendingUpdate != null) {
                AccountAction(
                    icon = Icons.Rounded.SystemUpdate,
                    label = stringResource(
                        R.string.account_update,
                        pendingUpdate.update.version,
                    ),
                    supporting = stringResource(R.string.account_update_supporting),
                    iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shapes = ListItemDefaults.segmentedShapes(
                        index = actionIndex++,
                        count = actionCount,
                    ),
                    onClick = onUpdate,
                )
            }
            AccountAction(
                icon = Icons.Rounded.Settings,
                label = stringResource(R.string.account_settings),
                supporting = stringResource(R.string.account_settings_supporting),
                iconContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                iconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shapes = ListItemDefaults.segmentedShapes(
                    index = actionIndex++,
                    count = actionCount,
                ),
                onClick = onSettings,
            )
            AccountAction(
                icon = Icons.AutoMirrored.Rounded.Logout,
                label = stringResource(R.string.account_logout),
                supporting = stringResource(R.string.account_logout_supporting),
                iconContainerColor = MaterialTheme.colorScheme.errorContainer,
                iconContentColor = MaterialTheme.colorScheme.onErrorContainer,
                shapes = ListItemDefaults.segmentedShapes(
                    index = actionIndex,
                    count = actionCount,
                ),
                onClick = {
                    haptic.confirm()
                    onLogout()
                },
            )
        }
    }
}

/** One grouped action row: start icon, title, and a consequence line. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountAction(
    icon: ImageVector,
    label: String,
    supporting: String,
    iconContainerColor: Color,
    iconContentColor: Color,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconContentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        supportingContent = { Text(supporting) },
        colors = ListItemDefaults.segmentedColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            supportingContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        content = {
            Text(text = label, style = MaterialTheme.typography.titleMedium)
        },
    )
}

/** The WhatsApp-style count badge: accent circle, count capped at "99+". */
@Composable
private fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.inbox_unread_messages, count, count)
    val motion = MaterialTheme.motionScheme
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = count,
            transitionSpec = {
                val enter = fadeIn(motion.fastEffectsSpec()) +
                    scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f)
                val exit = fadeOut(motion.fastEffectsSpec()) +
                    scaleOut(motion.fastSpatialSpec(), targetScale = 0.8f)
                enter togetherWith exit
            },
            // Counts past the cap all read "99+", so only a change in digits
            // replays the swap.
            contentKey = { unreadBadgeLabel(it) },
            label = "unreadBadgeCount",
        ) { value ->
            Text(
                text = unreadBadgeLabel(value),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Marks a thread the user flagged as unread when no newer message is cached. */
@Composable
private fun UnreadDot(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.inbox_unread)
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clearAndSetSemantics { contentDescription = description },
    )
}

internal data class PullThresholdStep(
    val armed: Boolean,
    val fireHaptic: Boolean,
)

/**
 * Hysteresis around pull-to-refresh's release threshold so boundary jitter
 * and post-refresh settle animations only emit one haptic per pull.
 */
internal fun stepPullRefreshThreshold(
    armed: Boolean,
    distanceFraction: Float,
    refreshing: Boolean,
    isAnimating: Boolean = false,
    triggerFraction: Float = 1f,
    resetFraction: Float = 0.85f,
): PullThresholdStep {
    if (refreshing) return PullThresholdStep(armed = true, fireHaptic = false)
    if (distanceFraction.isNaN()) return PullThresholdStep(armed = armed, fireHaptic = false)
    if (isAnimating) {
        return PullThresholdStep(
            armed = armed && distanceFraction > resetFraction,
            fireHaptic = false,
        )
    }
    return when {
        !armed && distanceFraction >= triggerFraction ->
            PullThresholdStep(armed = true, fireHaptic = true)
        armed && distanceFraction <= resetFraction ->
            PullThresholdStep(armed = false, fireHaptic = false)
        else -> PullThresholdStep(armed = armed, fireHaptic = false)
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
