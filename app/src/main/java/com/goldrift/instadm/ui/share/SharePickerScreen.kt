package com.goldrift.instadm.ui.share

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.goldrift.instadm.R
import com.goldrift.instadm.data.PendingShare
import com.goldrift.instadm.data.PickedMedia
import com.goldrift.instadm.data.ShareEnqueueResult
import com.goldrift.instadm.data.SharedContent
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.InboxStatus
import com.goldrift.instadm.ui.chat.MAX_MESSAGE_LENGTH
import com.goldrift.instadm.ui.chat.formatMediaDuration
import com.goldrift.instadm.ui.chat.label
import com.goldrift.instadm.ui.chat.render
import com.goldrift.instadm.ui.chat.sampleConversations
import com.goldrift.instadm.ui.chat.toPreview
import com.goldrift.instadm.ui.components.ConversationAvatar
import com.goldrift.instadm.ui.components.ExpressiveStateBadge
import com.goldrift.instadm.ui.components.InlineBanner
import com.goldrift.instadm.ui.components.confirm
import com.goldrift.instadm.ui.components.segmentTick
import com.goldrift.instadm.ui.components.toggle
import com.goldrift.instadm.ui.theme.InstaDMTheme
import com.goldrift.instadm.ui.theme.LocalMessageBubbleColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val StringListSaver = listSaver<List<String>, String>(
    save = { it.toList() },
    restore = { it },
)

private val ShareThumbSize = 64.dp

/**
 * Multi-recipient share sheet shown when another app sends text or media into
 * InstaDM. Each selected conversation receives its own queued outbox copy.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SharePickerScreen(
    pendingShare: PendingShare,
    conversations: List<Conversation>,
    status: InboxStatus,
    errorMessage: String?,
    hasMoreConversations: Boolean,
    isLoadingMore: Boolean,
    moreError: Boolean,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    onRefresh: () -> Unit,
    onPrepareMedia: suspend (Uri) -> Result<PickedMedia>,
    onSendShare: suspend (threadIds: List<String>, text: String, media: List<PickedMedia>) -> ShareEnqueueResult,
    onShareSent: (ShareEnqueueResult) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme

    var selectedIds by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(emptyList())
    }
    var filterQuery by rememberSaveable { mutableStateOf("") }
    var removedUris by rememberSaveable(stateSaver = StringListSaver) {
        mutableStateOf(emptyList())
    }
    val noteState = rememberTextFieldState(initialText = pendingShare.content.text)

    var stagedMedia by remember { mutableStateOf<List<PickedMedia>>(emptyList()) }
    var preparingMedia by remember {
        mutableStateOf(pendingShare.content.uris.any { it.toString() !in removedUris })
    }
    var shareError by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }

    LaunchedEffect(pendingShare.id) {
        val uris = pendingShare.content.uris.filterNot { it.toString() in removedUris }
        if (uris.isEmpty()) {
            stagedMedia = emptyList()
            preparingMedia = false
            return@LaunchedEffect
        }
        preparingMedia = true
        val prepared = mutableListOf<PickedMedia>()
        var firstError: String? = null
        for (uri in uris) {
            onPrepareMedia(uri)
                .onSuccess { prepared += it }
                .onFailure { error ->
                    if (firstError == null) {
                        firstError = error.message?.takeIf { it.isNotBlank() }
                            ?: context.getString(R.string.chat_media_unreadable)
                    }
                }
        }
        stagedMedia = prepared
        if (firstError != null) shareError = firstError
        preparingMedia = false
    }

    val validSelectedIds = remember(selectedIds, conversations) {
        pruneSelectedRecipients(selectedIds, conversations)
    }
    val selectedSet = remember(validSelectedIds) { validSelectedIds.toSet() }
    val selectedConversations = remember(validSelectedIds, conversations) {
        val byId = conversations.associateBy { it.id }
        validSelectedIds.mapNotNull(byId::get)
    }
    val filteredConversations = remember(conversations, filterQuery) {
        filterShareConversations(conversations, filterQuery)
    }

    val noteLength = shareTextLength(noteState.text.toString())
    val canSend = canSendShare(
        selectedCount = validSelectedIds.size,
        noteText = noteState.text.toString(),
        stagedMediaCount = stagedMedia.size,
        preparingMedia = preparingMedia,
        isSending = isSending,
    )

    BackHandler(enabled = !isSending) { onDismiss() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            var lastSelectedCount by remember { mutableIntStateOf(0) }
            if (validSelectedIds.isNotEmpty() && validSelectedIds.size != lastSelectedCount) {
                SideEffect { lastSelectedCount = validSelectedIds.size }
            }
            TopAppBar(
                navigationIcon = {
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isSending,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.share_cancel),
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.share_title),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                        AnimatedVisibility(
                            visible = validSelectedIds.isNotEmpty(),
                            enter = expandVertically(motion.fastSpatialSpec()) +
                                fadeIn(motion.fastEffectsSpec()),
                            exit = shrinkVertically(motion.fastSpatialSpec()) +
                                fadeOut(motion.fastEffectsSpec()),
                        ) {
                            val count = validSelectedIds.size.takeIf { it > 0 }
                                ?: lastSelectedCount.coerceAtLeast(1)
                            Text(
                                text = pluralStringResource(
                                    R.plurals.share_selected_count,
                                    count,
                                    count,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    AnimatedVisibility(
                        visible = validSelectedIds.isNotEmpty() && !isSending,
                        enter = fadeIn(motion.fastEffectsSpec()),
                        exit = fadeOut(motion.fastEffectsSpec()),
                    ) {
                        TextButton(
                            onClick = { selectedIds = emptyList() },
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.share_clear_selection))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
    ) { innerPadding ->
        val sheetShape = RoundedCornerShape(
            topStart = MaterialTheme.shapes.extraLarge.topStart,
            topEnd = MaterialTheme.shapes.extraLarge.topEnd,
            bottomStart = CornerSize(0.dp),
            bottomEnd = CornerSize(0.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxSize(),
                shape = sheetShape,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .imePadding(),
                ) {
                    when {
                        status == InboxStatus.Loading && conversations.isEmpty() -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                val loading = stringResource(R.string.app_loading)
                                LoadingIndicator(
                                    modifier = Modifier.semantics { contentDescription = loading },
                                )
                            }
                        }

                        status == InboxStatus.Error && conversations.isEmpty() -> {
                            ShareStateHint(
                                badgeShape = MaterialShapes.SoftBurst.toShape(),
                                badgeIcon = Icons.Rounded.CloudOff,
                                badgeContainerColor = MaterialTheme.colorScheme.errorContainer,
                                badgeIconTint = MaterialTheme.colorScheme.onErrorContainer,
                                title = stringResource(R.string.inbox_error_title),
                                body = errorMessage ?: stringResource(R.string.inbox_error_body),
                                actionLabel = stringResource(R.string.inbox_retry),
                                onAction = onRefresh,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        conversations.isEmpty() -> {
                            ShareStateHint(
                                badgeShape = MaterialShapes.Clover4Leaf.toShape(),
                                badgeIcon = Icons.Rounded.Forum,
                                badgeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                badgeIconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                title = stringResource(R.string.inbox_empty_title),
                                body = stringResource(R.string.inbox_empty_body),
                                modifier = Modifier.weight(1f),
                            )
                        }

                        else -> {
                            TextField(
                                value = filterQuery,
                                onValueChange = { filterQuery = it },
                                enabled = !isSending,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 8.dp),
                                placeholder = { Text(stringResource(R.string.share_filter_hint)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Search,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                },
                                trailingIcon = {
                                    if (filterQuery.isNotEmpty() && !isSending) {
                                        IconButton(
                                            onClick = { filterQuery = "" },
                                            shapes = IconButtonDefaults.shapes(),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = stringResource(R.string.share_filter_clear),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = CircleShape,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                ),
                            )

                            var shownSelected by remember { mutableStateOf(selectedConversations) }
                            if (selectedConversations.isNotEmpty()) {
                                SideEffect { shownSelected = selectedConversations }
                            }
                            AnimatedVisibility(
                                visible = selectedConversations.isNotEmpty(),
                                enter = expandVertically(motion.defaultSpatialSpec()) +
                                    fadeIn(motion.defaultEffectsSpec()),
                                exit = shrinkVertically(motion.defaultSpatialSpec()) +
                                    fadeOut(motion.defaultEffectsSpec()),
                            ) {
                                SelectedRecipientsStrip(
                                    recipients = selectedConversations.ifEmpty { shownSelected },
                                    enabled = !isSending,
                                    onRemove = { conversation ->
                                        haptic.toggle(false)
                                        selectedIds = toggleShareRecipient(selectedIds, conversation.id)
                                    },
                                )
                            }

                            var lastShareError by remember { mutableStateOf(shareError) }
                            if (shareError != null && shareError != lastShareError) {
                                SideEffect { lastShareError = shareError }
                            }
                            AnimatedVisibility(
                                visible = shareError != null,
                                enter = expandVertically(motion.defaultSpatialSpec()) +
                                    fadeIn(motion.defaultEffectsSpec()),
                                exit = shrinkVertically(motion.defaultSpatialSpec()) +
                                    fadeOut(motion.defaultEffectsSpec()),
                            ) {
                                val message = shareError ?: lastShareError ?: return@AnimatedVisibility
                                InlineBanner(
                                    text = message,
                                    icon = Icons.Rounded.ErrorOutline,
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    onDismiss = { shareError = null },
                                )
                            }

                            if (filteredConversations.isEmpty()) {
                                ShareNoMatchesState(
                                    query = filterQuery.trim(),
                                    hasMoreConversations = hasMoreConversations,
                                    isLoadingMore = isLoadingMore,
                                    moreError = moreError,
                                    onLoadMore = onLoadMore,
                                    onRetryMore = onRetryMore,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                val listState = rememberLazyListState()
                                val loadMoreNow by remember(
                                    listState,
                                    hasMoreConversations,
                                    isLoadingMore,
                                    moreError,
                                    filterQuery,
                                ) {
                                    derivedStateOf {
                                        if (filterQuery.isNotBlank() ||
                                            !hasMoreConversations ||
                                            isLoadingMore ||
                                            moreError
                                        ) {
                                            false
                                        } else {
                                            val info = listState.layoutInfo
                                            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index
                                            lastVisible != null &&
                                                lastVisible >= info.totalItemsCount - 3
                                        }
                                    }
                                }
                                LaunchedEffect(loadMoreNow, filteredConversations.size) {
                                    if (loadMoreNow) onLoadMore()
                                }
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .testTag("share_conversation_list"),
                                    contentPadding = PaddingValues(vertical = 4.dp),
                                ) {
                                    items(
                                        items = filteredConversations,
                                        key = { it.id },
                                        contentType = { "share_conversation" },
                                    ) { conversation ->
                                        val isSelected = conversation.id in selectedSet
                                        ShareConversationRow(
                                            conversation = conversation,
                                            selected = isSelected,
                                            enabled = !isSending,
                                            onToggle = {
                                                haptic.toggle(!isSelected)
                                                selectedIds = toggleShareRecipient(
                                                    selectedIds,
                                                    conversation.id,
                                                )
                                            },
                                        )
                                    }
                                    if (hasMoreConversations && filterQuery.isBlank()) {
                                        item(key = "older", contentType = "footer") {
                                            ShareOlderFooter(
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

                    ShareComposerBar(
                        noteState = noteState,
                        noteLength = noteLength,
                        stagedMedia = stagedMedia,
                        preparingMedia = preparingMedia,
                        isSending = isSending,
                        canSend = canSend,
                        selectedCount = validSelectedIds.size,
                        onRemoveMedia = { picked ->
                            haptic.segmentTick()
                            val uriString = picked.uri.toString()
                            if (uriString !in removedUris) {
                                removedUris = removedUris + uriString
                            }
                            stagedMedia = stagedMedia - picked
                        },
                        onSend = {
                            isSending = true
                            shareError = null
                            scope.launch {
                                val result = try {
                                    onSendShare(
                                        validSelectedIds,
                                        noteState.text.toString(),
                                        stagedMedia,
                                    )
                                } catch (cancellation: CancellationException) {
                                    throw cancellation
                                } catch (error: Throwable) {
                                    ShareEnqueueResult(
                                        queuedThreadIds = emptyList(),
                                        lastMediaError = error.message?.takeIf { it.isNotBlank() }
                                            ?: context.getString(R.string.share_failed),
                                    )
                                }
                                if (result.queuedThreadIds.isEmpty()) {
                                    isSending = false
                                    shareError = result.lastMediaError
                                        ?: context.getString(R.string.share_failed)
                                } else {
                                    haptic.confirm()
                                    onShareSent(result)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedRecipientsStrip(
    recipients: List<Conversation>,
    enabled: Boolean,
    onRemove: (Conversation) -> Unit,
) {
    val rowState = rememberLazyListState()
    var previousCount by remember { mutableIntStateOf(recipients.size) }
    LaunchedEffect(recipients.size) {
        if (recipients.size > previousCount && recipients.isNotEmpty()) {
            rowState.animateScrollToItem(recipients.lastIndex)
        }
        previousCount = recipients.size
    }
    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = recipients, key = { it.id }) { conversation ->
            InputChip(
                selected = true,
                enabled = enabled,
                onClick = { onRemove(conversation) },
                label = {
                    Text(
                        text = conversation.contact.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 140.dp),
                    )
                },
                avatar = {
                    ConversationAvatar(
                        contact = conversation.contact,
                        isGroup = conversation.isGroup,
                        participants = conversation.participants,
                        size = 20.dp,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(
                            R.string.share_remove_recipient,
                            conversation.contact.name,
                        ),
                        modifier = Modifier.size(InputChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShareConversationRow(
    conversation: Conversation,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    val resources = LocalResources.current
    val motion = MaterialTheme.motionScheme
    val rowShape = RoundedCornerShape(20.dp)
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = motion.fastEffectsSpec(),
        label = "shareRowContainer",
    )
    val subtitle = if (conversation.isGroup) {
        conversation.participants
            .mapNotNull { it.name.trim().takeIf(String::isNotEmpty) }
            .takeIf { it.isNotEmpty() }
            ?.joinToString(", ")
            ?: pluralStringResource(
                R.plurals.chat_member_count,
                conversation.participants.size,
                conversation.participants.size,
            )
    } else {
        (conversation.messages.lastOrNull()?.toPreview() ?: conversation.preview)
            .label(isGroup = false)
            ?.render { id, args -> resources.getString(id, *args.toTypedArray()) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("share_conversation_row")
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(rowShape)
            .background(containerColor)
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { onToggle() },
            )
            .heightIn(min = 72.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
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
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                color = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        ShareSelectionIndicator(selected = selected)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShareSelectionIndicator(selected: Boolean) {
    val motion = MaterialTheme.motionScheme
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(primaryColor)
                } else {
                    Modifier.border(2.dp, borderColor, CircleShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.fastSpatialSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec()),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShareComposerBar(
    noteState: androidx.compose.foundation.text.input.TextFieldState,
    noteLength: Int,
    stagedMedia: List<PickedMedia>,
    preparingMedia: Boolean,
    isSending: Boolean,
    canSend: Boolean,
    selectedCount: Int,
    onRemoveMedia: (PickedMedia) -> Unit,
    onSend: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val bubbles = LocalMessageBubbleColors.current
    var shownStagedMedia by remember { mutableStateOf(stagedMedia) }
    if (stagedMedia.isNotEmpty()) {
        SideEffect { shownStagedMedia = stagedMedia }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            AnimatedVisibility(
                visible = preparingMedia || stagedMedia.isNotEmpty(),
                enter = expandVertically(motion.defaultSpatialSpec()) +
                    fadeIn(motion.defaultEffectsSpec()),
                exit = shrinkVertically(motion.defaultSpatialSpec()) +
                    fadeOut(motion.defaultEffectsSpec()),
            ) {
                when {
                    preparingMedia && stagedMedia.isEmpty() -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            LoadingIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.share_preparing_media),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> {
                        val previewMedia = stagedMedia.ifEmpty { shownStagedMedia }
                        if (previewMedia.isNotEmpty()) {
                            ShareStagedMediaStrip(
                                media = previewMedia,
                                canRemove = !isSending,
                                onRemove = onRemoveMedia,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = noteLength > MAX_MESSAGE_LENGTH,
                enter = expandVertically(motion.fastSpatialSpec()) +
                    fadeIn(motion.fastEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) +
                    fadeOut(motion.fastEffectsSpec()),
            ) {
                Text(
                    text = stringResource(
                        R.string.share_text_too_long,
                        noteLength,
                        MAX_MESSAGE_LENGTH,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, top = 6.dp, end = 16.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.largeIncreased,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    val hint = stringResource(
                        if (preparingMedia || stagedMedia.isNotEmpty()) {
                            R.string.share_note_hint
                        } else {
                            R.string.chat_message_hint
                        },
                    )
                    BasicTextField(
                        state = noteState,
                        enabled = !isSending,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        lineLimits = TextFieldLineLimits.MultiLine(
                            minHeightInLines = 1,
                            maxHeightInLines = 4,
                        ),
                        decorator = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (noteState.text.isEmpty()) {
                                    Text(
                                        text = hint,
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

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = onSend,
                    enabled = canSend,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = bubbles.composerAccent,
                        contentColor = bubbles.onComposerAccent,
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("share_send_button"),
                ) {
                    if (isSending) {
                        LoadingIndicator(
                            modifier = Modifier.size(18.dp),
                            color = LocalContentColor.current,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (selectedCount >= 2) {
                            stringResource(R.string.share_send_count, selectedCount)
                        } else {
                            stringResource(R.string.share_send)
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareStagedMediaStrip(
    media: List<PickedMedia>,
    canRemove: Boolean,
    onRemove: (PickedMedia) -> Unit,
) {
    val context = LocalContext.current
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = media, key = { it.uri.toString() }) { picked ->
            Box(modifier = Modifier.size(ShareThumbSize)) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    when (picked.kind) {
                        "image" -> {
                            AsyncImage(
                                model = picked.uri,
                                contentDescription = picked.fileName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        "video" -> {
                            val videoFrame by produceState<Bitmap?>(null, picked.uri) {
                                value = withContext(Dispatchers.IO) {
                                    val retriever = MediaMetadataRetriever()
                                    try {
                                        retriever.setDataSource(context, picked.uri)
                                        retriever.getFrameAtTime(
                                            0L,
                                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                                        )
                                    } catch (_: Exception) {
                                        null
                                    } finally {
                                        runCatching { retriever.release() }
                                    }
                                }
                            }
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                val frame = videoFrame
                                if (frame != null) {
                                    Image(
                                        bitmap = frame.asImageBitmap(),
                                        contentDescription = picked.fileName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Videocam,
                                        contentDescription = picked.fileName,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                if (picked.durationMs > 0) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.Black.copy(alpha = 0.68f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(4.dp),
                                    ) {
                                        Text(
                                            text = formatMediaDuration(picked.durationMs),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontFeatureSettings = "tnum",
                                            ),
                                            color = Color.White,
                                            modifier = Modifier.padding(
                                                horizontal = 5.dp,
                                                vertical = 1.dp,
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                        "audio" -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Audiotrack,
                                    contentDescription = picked.fileName,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        else -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Description,
                                    contentDescription = picked.fileName,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
                if (canRemove) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { onRemove(picked) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.chat_staged_remove),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShareNoMatchesState(
    query: String,
    hasMoreConversations: Boolean,
    isLoadingMore: Boolean,
    moreError: Boolean,
    onLoadMore: () -> Unit,
    onRetryMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ExpressiveStateBadge(
            shape = MaterialShapes.Clover4Leaf.toShape(),
            icon = Icons.Rounded.SearchOff,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.share_no_matches, query),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (hasMoreConversations) {
            Spacer(Modifier.height(12.dp))
            when {
                isLoadingMore -> {
                    val loading = stringResource(R.string.inbox_loading_older)
                    LoadingIndicator(
                        modifier = Modifier
                            .size(28.dp)
                            .semantics { contentDescription = loading },
                    )
                }

                moreError -> TextButton(onClick = onRetryMore, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.inbox_retry))
                }

                else -> TextButton(onClick = onLoadMore, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.share_load_older))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShareOlderFooter(
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
            hasError -> TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
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
private fun ShareStateHint(
    badgeShape: Shape,
    badgeIcon: ImageVector,
    badgeContainerColor: Color,
    badgeIconTint: Color,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
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
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction, shapes = ButtonDefaults.shapes()) {
                Text(actionLabel)
            }
        }
    }
}

@Preview(name = "Share picker light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SharePickerLightPreview() {
    InstaDMTheme(darkTheme = false, dynamicColor = false) {
        SharePickerScreen(
            pendingShare = PendingShare(
                id = 1L,
                content = SharedContent(text = "Check this out", uris = emptyList()),
            ),
            conversations = sampleConversations(),
            status = InboxStatus.Ready,
            errorMessage = null,
            hasMoreConversations = false,
            isLoadingMore = false,
            moreError = false,
            onLoadMore = {},
            onRetryMore = {},
            onRefresh = {},
            onPrepareMedia = { Result.failure(IllegalStateException("unused")) },
            onSendShare = { ids, _, _ -> ShareEnqueueResult(ids) },
            onShareSent = {},
            onDismiss = {},
        )
    }
}

@Preview(
    name = "Share picker dark",
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
    backgroundColor = 0xFF10131C,
)
@Composable
private fun SharePickerDarkPreview() {
    InstaDMTheme(darkTheme = true, dynamicColor = false) {
        SharePickerScreen(
            pendingShare = PendingShare(
                id = 2L,
                content = SharedContent(text = "Look at these photos", uris = emptyList()),
            ),
            conversations = sampleConversations(),
            status = InboxStatus.Ready,
            errorMessage = null,
            hasMoreConversations = false,
            isLoadingMore = false,
            moreError = false,
            onLoadMore = {},
            onRetryMore = {},
            onRefresh = {},
            onPrepareMedia = { Result.failure(IllegalStateException("unused")) },
            onSendShare = { ids, _, _ -> ShareEnqueueResult(ids) },
            onShareSent = {},
            onDismiss = {},
        )
    }
}
