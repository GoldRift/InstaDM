package com.goldrift.instadm.ui.chat

import android.app.Activity
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.goldrift.instadm.R
import com.goldrift.instadm.data.CameraCapture
import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.data.RecordedVoice
import com.goldrift.instadm.data.VoiceRecorder
import com.goldrift.instadm.data.VoiceRecordingState
import com.goldrift.instadm.service.AppVisibility
import com.goldrift.instadm.ui.components.Avatar
import com.goldrift.instadm.ui.components.ConversationAvatar
import com.goldrift.instadm.ui.theme.InstaDMTheme
import com.goldrift.instadm.ui.theme.LocalMessageBubbleColors
import java.io.File
import java.text.DateFormat
import java.util.Date

private val BubbleShape = RoundedCornerShape(24.dp)

private val ChatContentShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

@Composable
fun ChatScreen(
    conversation: Conversation,
    onSend: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onSendMedia: (Uri) -> Unit = {},
    onRetryMedia: (String) -> Unit = {},
    isTyping: Boolean = false,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onLoadMedia: MediaLoader? = null,
    onMarkRead: (String) -> Unit = {},
    onLoadOlder: () -> Unit = {},
    onRetryOlder: () -> Unit = {},
    onReact: (messageId: String, emoji: String) -> Unit = { _, _ -> },
    onEdit: (messageId: String, text: String) -> Unit = { _, _ -> },
    onUnsend: (messageId: String) -> Unit = {},
    onSendVoice: (RecordedVoice) -> Unit = {},
) {
    val messages = conversation.messages
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var draft by remember { mutableStateOf("") }
    var viewerTarget by remember { mutableStateOf<MediaViewerTarget?>(null) }
    var actionMessageId by remember { mutableStateOf<String?>(null) }
    var editMessageId by remember { mutableStateOf<String?>(null) }
    var unsendMessageId by remember { mutableStateOf<String?>(null) }
    var emojiPickerMessageId by remember { mutableStateOf<String?>(null) }
    var captureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var capturePath by rememberSaveable { mutableStateOf<String?>(null) }
    var recordingLocked by remember { mutableStateOf(false) }
    var recordingCancel by remember { mutableStateOf(false) }
    val voiceRecorder = remember { VoiceRecorder(context) }
    val voicePlayer = remember { VoicePlayer(context) }
    val listState = rememberLazyListState()

    val cameraLauncher = rememberLauncherForActivityResult(
        TakePictureWithWritePermission(),
    ) { success ->
        val uri = captureUri
        val path = capturePath
        captureUri = null
        capturePath = null
        if (success && uri != null) {
            onSendMedia(uri.toUri())
        } else if (path != null) {
            runCatching { File(path).delete() }
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onSendMedia(uri)
    }

    fun takePhoto() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, R.string.chat_camera_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val target = CameraCapture.newTarget(context)
        captureUri = target.uri.toString()
        capturePath = target.file.absolutePath
        cameraLauncher.launch(target.uri)
    }

    fun pickMedia() {
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
        )
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, R.string.chat_mic_permission, Toast.LENGTH_LONG).show()
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current

    fun hasMicPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    fun startRecording() {
        if (!hasMicPermission()) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        keyboard?.hide()
        voicePlayer.pause()
        recordingLocked = false
        recordingCancel = false
        voiceRecorder.start()
    }

    fun finishRecording() {
        val recorded = voiceRecorder.stop()
        recordingLocked = false
        recordingCancel = false
        if (recorded != null) {
            onSendVoice(recorded)
        } else if (voiceRecorder.errorMessage == null) {
            Toast.makeText(context, R.string.chat_recording_too_short, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(voiceRecorder.isRecording) {
        if (!voiceRecorder.isRecording) {
            recordingLocked = false
            recordingCancel = false
        }
    }

    LaunchedEffect(voiceRecorder.errorMessage) {
        voiceRecorder.errorMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            voiceRecorder.clearError()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecorder.release()
            voicePlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                voicePlayer.pause()
                voiceRecorder.cancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val editMessage = editMessageId?.let { id -> messages.firstOrNull { it.id == id } }
    val unsendMessage = unsendMessageId?.let { id -> messages.firstOrNull { it.id == id } }
    val emojiPickerMessage = emojiPickerMessageId?.let { id -> messages.firstOrNull { it.id == id } }

    fun sendMessage() {
        val text = draft.trim()
        if (text.isEmpty()) return

        onSend(text)
        draft = ""
    }

    // Follow the newest bubble on first load, on a new message, and when typing
    // starts. Prepending an older page must not move the viewport, so this keys
    // on the newest message ID rather than the list size; a new message only
    // scrolls when the newest bubble is already on screen, unless the viewer
    // sent it.
    val newestMessage = messages.lastOrNull()
    LaunchedEffect(newestMessage?.id, isTyping) {
        if (newestMessage == null) return@LaunchedEffect
        if (listState.firstVisibleItemIndex <= 1 || newestMessage.sender == MessageSender.Me) {
            listState.animateScrollToItem(0)
        }
    }

    // Mark the thread read whenever the newest server message changes while the
    // chat is composed, and again when the app returns to the foreground (a
    // receipt may have been skipped while the chat was in the background).
    val readTarget = messages.lastOrNull { it.canMarkRead() }?.id.orEmpty()
    val isForeground = AppVisibility.isForeground
    LaunchedEffect(readTarget, isForeground) {
        if (isForeground && readTarget.isNotBlank()) {
            onMarkRead(readTarget)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ChatTopSection(
                contact = conversation.contact,
                isGroup = conversation.isGroup,
                participants = conversation.participants,
                onBack = onBack,
            )
        },
        bottomBar = {
            MessageComposer(
                draft = draft,
                onDraftChange = { draft = it },
                onSend = ::sendMessage,
                onCamera = ::takePhoto,
                onPickMedia = ::pickMedia,
                recorder = voiceRecorder,
                locked = recordingLocked,
                cancelCandidate = recordingCancel,
                onMicStart = ::startRecording,
                onMicMove = { lock, cancel ->
                    if (lock && !recordingLocked) {
                        recordingLocked = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    if (!recordingLocked) recordingCancel = cancel
                },
                onMicRelease = { lock, cancel ->
                    if (!lock && voiceRecorder.isRecording) {
                        if (cancel) {
                            voiceRecorder.cancel()
                            recordingLocked = false
                            recordingCancel = false
                        } else {
                            finishRecording()
                        }
                    }
                },
                onRecordingSend = ::finishRecording,
                onRecordingDelete = {
                    voiceRecorder.cancel()
                    recordingLocked = false
                    recordingCancel = false
                },
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            shape = ChatContentShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
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
                            contact = conversation.contact,
                            isGroup = conversation.isGroup,
                            participants = conversation.participants,
                            isTyping = isTyping,
                            listState = listState,
                            threadId = conversation.id,
                            hasMoreOlder = conversation.hasMoreOlder,
                            isLoadingOlder = conversation.isLoadingOlder,
                            olderError = conversation.olderError,
                            onLoadOlder = onLoadOlder,
                            onRetryOlder = onRetryOlder,
                            onOpenMedia = { messageId, media ->
                                viewerTarget = MediaViewerTarget(messageId, media)
                            },
                            onLongPress = { actionMessageId = it.id },
                            actionMessageId = actionMessageId,
                            onDismissActions = { actionMessageId = null },
                            onReactAt = { messageId, emoji ->
                                actionMessageId = null
                                onReact(messageId, emoji)
                            },
                            onCopyAt = { messageId ->
                                actionMessageId = null
                                messages.firstOrNull { it.id == messageId }?.let {
                                    copyToClipboard(context, it.text)
                                }
                            },
                            onMoreEmojiAt = { messageId ->
                                actionMessageId = null
                                emojiPickerMessageId = messageId
                            },
                            onEditAt = { messageId ->
                                actionMessageId = null
                                editMessageId = messageId
                            },
                            onUnsendAt = { messageId ->
                                actionMessageId = null
                                unsendMessageId = messageId
                            },
                            onRetryMedia = onRetryMedia,
                            onLoadMedia = onLoadMedia,
                            voicePlayer = voicePlayer,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
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

    emojiPickerMessage?.let { message ->
        EmojiPickerDialog(
            onPick = { emoji ->
                emojiPickerMessageId = null
                onReact(message.id, emoji)
            },
            onDismiss = { emojiPickerMessageId = null },
        )
    }

    editMessage?.let { message ->
        EditMessageDialog(
            message = message,
            onSave = { text ->
                editMessageId = null
                onEdit(message.id, text)
            },
            onDismiss = { editMessageId = null },
        )
    }

    unsendMessage?.let { message ->
        UnsendMessageDialog(
            onConfirm = {
                unsendMessageId = null
                onUnsend(message.id)
            },
            onDismiss = { unsendMessageId = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopSection(
    contact: Contact,
    isGroup: Boolean,
    participants: List<ConversationParticipant>,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ConversationAvatar(
                        contact = contact,
                        isGroup = isGroup,
                        participants = participants,
                        size = 40.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    contact: Contact,
    isGroup: Boolean,
    participants: List<ConversationParticipant>,
    isTyping: Boolean,
    listState: LazyListState,
    threadId: String,
    hasMoreOlder: Boolean,
    isLoadingOlder: Boolean,
    olderError: Boolean,
    onLoadOlder: () -> Unit,
    onRetryOlder: () -> Unit,
    onOpenMedia: (messageId: String, media: MessageMedia) -> Unit,
    onLongPress: (ChatMessage) -> Unit,
    actionMessageId: String?,
    onDismissActions: () -> Unit,
    onReactAt: (messageId: String, emoji: String) -> Unit,
    onCopyAt: (messageId: String) -> Unit,
    onMoreEmojiAt: (messageId: String) -> Unit,
    onEditAt: (messageId: String) -> Unit,
    onUnsendAt: (messageId: String) -> Unit,
    onRetryMedia: (messageId: String) -> Unit,
    onLoadMedia: MediaLoader?,
    voicePlayer: VoicePlayer,
    modifier: Modifier = Modifier,
) {
    val reversed = messages.asReversed()
    val visibleMessageIds by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.mapNotNullTo(HashSet()) { it.key as? String }
        }
    }

    // The list is reversed, so older messages are appended past the visible
    // range and stable keys keep the reader's position while a page is
    // prepended. Ask for another page once the oldest bubbles are on screen;
    // pages keep loading until the viewport is filled or the thread begins.
    val loadOlderNow by remember(listState, hasMoreOlder, isLoadingOlder, olderError) {
        derivedStateOf {
            if (!hasMoreOlder || isLoadingOlder || olderError) {
                false
            } else {
                val info = listState.layoutInfo
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index
                lastVisible != null && lastVisible >= info.totalItemsCount - 3
            }
        }
    }
    // Keyed on the page size as well so a page that does not fill the viewport
    // immediately pulls the next one.
    LaunchedEffect(loadOlderNow, messages.size) {
        if (loadOlderNow) onLoadOlder()
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isTyping) {
            item(key = "typing", contentType = "typing") {
                TypingBubble(
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
            val previous = messages.getOrNull(originalIndex - 1)
            val sender = if (isGroup) {
                participants.firstOrNull { it.fbid == message.senderFbid }
            } else {
                null
            }
            val senderInitials = sender?.initials
                ?: message.senderName.takeIf { it.isNotBlank() }?.let(::initialsOf)
                ?: contact.initials
            val senderAvatarUrl = if (isGroup) {
                sender?.avatarUrl.orEmpty()
            } else {
                contact.avatarUrl
            }
            // Direct chats show no avatars beside bubbles.
            // Groups keep one avatar per sender cluster.
            val showAvatar = isGroup &&
                message.sender == MessageSender.Them &&
                (
                    previous?.sender != MessageSender.Them ||
                        previous?.senderFbid != message.senderFbid
                    )
            // The list is reversed, so the oldest message of a calendar day sits
            // at the top of that day's block. A header at the top of this item
            // therefore separates it from the previous (older) day.
            val showDayHeader = message.timestampMs > 0 &&
                (
                    previous == null ||
                        previous.timestampMs <= 0 ||
                        calendarDaysBetween(previous.timestampMs, message.timestampMs) > 0
                    )

            Column(modifier = Modifier.animateItem()) {
                if (showDayHeader) {
                    DayHeader(timestampMs = message.timestampMs)
                }
                MessageRow(
                    message = message,
                    senderInitials = senderInitials,
                    senderAvatarUrl = senderAvatarUrl,
                    showAvatar = showAvatar,
                    threadId = threadId,
                    isVisible = message.id in visibleMessageIds,
                    onOpenMedia = { media -> onOpenMedia(message.id, media) },
                    onLongPress = { if (message.isServerMessage()) onLongPress(message) },
                    actionsExpanded = actionMessageId == message.id,
                    onDismissActions = onDismissActions,
                    onReact = { emoji -> onReactAt(message.id, emoji) },
                    onCopy = { onCopyAt(message.id) },
                    onMoreEmoji = { onMoreEmojiAt(message.id) },
                    onEdit = { onEditAt(message.id) },
                    onUnsend = { onUnsendAt(message.id) },
                    onRetry = { onRetryMedia(message.id) },
                    onLoadMedia = onLoadMedia,
                    voicePlayer = voicePlayer,
                )
            }
        }

        if (hasMoreOlder) {
            item(key = "older", contentType = "older") {
                OlderMessagesFooter(
                    isLoading = isLoadingOlder,
                    hasError = olderError,
                    onRetry = onRetryOlder,
                )
            }
        }
    }
}

@Composable
private fun MessageRow(
    message: ChatMessage,
    senderInitials: String,
    senderAvatarUrl: String,
    showAvatar: Boolean,
    threadId: String,
    isVisible: Boolean,
    onOpenMedia: (MessageMedia) -> Unit,
    onLongPress: () -> Unit,
    actionsExpanded: Boolean,
    onDismissActions: () -> Unit,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onMoreEmoji: () -> Unit,
    onEdit: () -> Unit,
    onUnsend: () -> Unit,
    onRetry: () -> Unit,
    onLoadMedia: MediaLoader?,
    voicePlayer: VoicePlayer,
    modifier: Modifier = Modifier,
) {
    val isMine = message.sender == MessageSender.Me
    val bubbles = LocalMessageBubbleColors.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isMine && showAvatar) {
            Avatar(
                initials = senderInitials,
                size = 28.dp,
                imageUrl = senderAvatarUrl,
                colorSeed = senderInitials,
            )
            Spacer(Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            val actionsLabel = stringResource(R.string.chat_message_actions)
            Box {
                Surface(
                    modifier = Modifier
                        .semantics {
                            onLongClick(label = actionsLabel) {
                                onLongPress()
                                true
                            }
                        }
                        .pointerInput(message.id) {
                            detectTapGestures(onLongPress = { onLongPress() })
                        },
                    shape = BubbleShape,
                    color = if (isMine) bubbles.outgoing else bubbles.incoming,
                    contentColor = if (isMine) bubbles.onOutgoing else bubbles.onIncoming,
                ) {
                    MessageBubbleContent(
                        message = message,
                        threadId = threadId,
                        isVisible = isVisible,
                        onOpenMedia = onOpenMedia,
                        onLongPress = onLongPress,
                        onLoadMedia = onLoadMedia,
                        voicePlayer = voicePlayer,
                    )
                }

                if (actionsExpanded) {
                    MessageActionsPopup(
                        message = message,
                        isMine = isMine,
                        onDismiss = onDismissActions,
                        onReact = onReact,
                        onCopy = onCopy,
                        onMoreEmoji = onMoreEmoji,
                        onEdit = onEdit,
                        onUnsend = onUnsend,
                    )
                }
            }

            ReactionPills(message)

            if (isMine) {
                Row(
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when {
                        message.failed -> Row(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .clickable(role = Role.Button, onClick = onRetry)
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = stringResource(R.string.chat_media_failed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        message.pending -> CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        else -> Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = stringResource(
                                if (message.seen) R.string.chat_seen else R.string.chat_sent,
                            ),
                            tint = if (message.seen) {
                                bubbles.accent
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
    onLongPress: () -> Unit,
    onLoadMedia: MediaLoader?,
    voicePlayer: VoicePlayer,
) {
    val media = message.media.filter { it.isVisual }
    val voiceNotes = message.media.filter { it.isVoiceNote }
    val localMedia = message.localMedia
    val hasAttachment = media.isNotEmpty() || voiceNotes.isNotEmpty() || localMedia != null
    Column(
        modifier = Modifier.padding(
            horizontal = if (hasAttachment) 4.dp else 14.dp,
            vertical = if (hasAttachment) 4.dp else 9.dp,
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
                onClick = { if (message.isServerMessage()) onOpenMedia(item) },
                onLongClick = onLongPress,
            )
        }

        if (voiceNotes.isNotEmpty()) {
            if (media.isNotEmpty()) Spacer(Modifier.height(4.dp))
            voiceNotes.forEach { item ->
                VoiceNoteBubble(
                    media = item,
                    threadId = threadId,
                    messageId = message.id,
                    player = voicePlayer,
                    loadMedia = onLoadMedia,
                    onLongClick = onLongPress,
                )
            }
        }

        if (media.isEmpty() && voiceNotes.isEmpty() && localMedia != null) {
            LocalMediaThumbnail(
                media = localMedia,
                onLongClick = onLongPress,
            )
        }

        if (message.text.isNotBlank()) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(
                    horizontal = if (hasAttachment) 10.dp else 0.dp,
                    vertical = if (hasAttachment) 6.dp else 0.dp,
                ),
            )
        }
    }
}

/** Grouped reaction pills under a bubble, in first-seen order. */
@Composable
private fun ReactionPills(message: ChatMessage) {
    val groups = message.reactionGroups()
    if (groups.isEmpty()) return

    Row(
        modifier = Modifier.padding(top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEach { group ->
            Surface(
                shape = CircleShape,
                color = if (group.fromViewer) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (group.fromViewer) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ) {
                Text(
                    text = if (group.count > 1) "${group.emoji} ${group.count}" else group.emoji,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private val QuickReactions = listOf("❤️", "😂", "😮", "😢", "😡", "👍")

/**
 * Instagram-style actions popup: a floating reaction pill above a menu card,
 * anchored to the long-pressed bubble. The menu starts at the bubble's top
 * edge, so the card overlays the message instead of the composer.
 */
@Composable
private fun MessageActionsPopup(
    message: ChatMessage,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onMoreEmoji: () -> Unit,
    onEdit: () -> Unit,
    onUnsend: () -> Unit,
) {
    val density = LocalDensity.current
    val reactionBarHeight = with(density) { 60.dp.roundToPx() }
    val margin = with(density) { 8.dp.roundToPx() }
    val gap = with(density) { 6.dp.roundToPx() }
    val positionProvider = remember(isMine, reactionBarHeight, margin, gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val maxX = (windowSize.width - popupContentSize.width - margin)
                    .coerceAtLeast(margin)
                val x = if (isMine) {
                    (anchorBounds.right - popupContentSize.width).coerceIn(margin, maxX)
                } else {
                    anchorBounds.left.coerceIn(margin, maxX)
                }
                val maxY = (windowSize.height - popupContentSize.height - margin)
                    .coerceAtLeast(margin)
                val y = (anchorBounds.top - reactionBarHeight - gap).coerceIn(margin, maxY)
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
            dismissOnBackPress = true,
        ),
    ) {
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        ) {
            ReactionBar(onReact = onReact, onMoreEmoji = onMoreEmoji)
            Spacer(Modifier.height(6.dp))
            MessageMenu(
                message = message,
                onCopy = onCopy,
                onEdit = onEdit,
                onUnsend = onUnsend,
            )
        }
    }
}

@Composable
private fun ReactionBar(
    onReact: (String) -> Unit,
    onMoreEmoji: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickReactions.forEach { emoji ->
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) { onReact(emoji) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 26.sp)
                }
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onMoreEmoji),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.chat_more_reactions),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MessageMenu(
    message: ChatMessage,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onUnsend: () -> Unit,
) {
    val showCopy = message.text.isNotBlank()
    val showEdit = message.canEdit()
    val showUnsend = message.canUnsend()
    if (!showCopy && !showEdit && !showUnsend) return

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = Modifier.widthIn(min = 210.dp, max = 280.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            if (showCopy) {
                MessageActionRow(
                    icon = Icons.Rounded.ContentCopy,
                    label = R.string.chat_action_copy,
                    onClick = onCopy,
                )
            }
            if (showEdit) {
                MessageActionRow(
                    icon = Icons.Rounded.Edit,
                    label = R.string.chat_action_edit,
                    onClick = onEdit,
                )
            }
            if (showUnsend) {
                MessageActionRow(
                    icon = Icons.Rounded.Delete,
                    label = R.string.chat_action_unsend,
                    onClick = onUnsend,
                    destructive = true,
                )
            }
        }
    }
}

@Composable
private fun EmojiPickerDialog(
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentOnPick by rememberUpdatedState(onPick)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(420.dp),
        ) {
            AndroidView(
                factory = { context ->
                    EmojiPickerView(context).apply {
                        setOnEmojiPickedListener { item -> currentOnPick(item.emoji) }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MessageActionRow(
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val tint = if (destructive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) tint else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EditMessageDialog(
    message: ChatMessage,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(message.id) { mutableStateOf(message.text) }
    val trimmed = text.trim()
    val canSave = trimmed.isNotEmpty() && trimmed != message.text

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_edit_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= MAX_MESSAGE_LENGTH) text = it },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 6,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(trimmed) },
                enabled = canSave,
            ) {
                Text(stringResource(R.string.chat_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.chat_cancel))
            }
        },
    )
}

@Composable
private fun UnsendMessageDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_unsend_title)) },
        text = { Text(stringResource(R.string.chat_unsend_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.chat_action_unsend),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.chat_cancel))
            }
        },
    )
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("message", text))
    Toast.makeText(context, R.string.chat_copied, Toast.LENGTH_SHORT).show()
}

@Composable
private fun TypingBubble(
    modifier: Modifier = Modifier,
) {
    val bubbles = LocalMessageBubbleColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            shape = BubbleShape,
            color = bubbles.incoming,
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
private fun OlderMessagesFooter(
    isLoading: Boolean,
    hasError: Boolean,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            hasError -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.chat_older_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onRetry) {
                    Text(
                        text = stringResource(R.string.chat_older_retry),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.chat_loading_older),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Sticky-looking date pill between calendar days, following the device locale. */
@Composable
private fun DayHeader(timestampMs: Long) {
    val today = stringResource(R.string.chat_today)
    val yesterday = stringResource(R.string.chat_yesterday)
    val label = remember(timestampMs, today, yesterday) {
        val days = calendarDaysBetween(timestampMs, System.currentTimeMillis())
        when (days) {
            0L -> today
            1L -> yesterday
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestampMs))
        }
    }
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
                text = label,
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
    onCamera: () -> Unit,
    onPickMedia: () -> Unit,
    recorder: VoiceRecorder,
    locked: Boolean,
    cancelCandidate: Boolean,
    onMicStart: () -> Unit,
    onMicMove: (Boolean, Boolean) -> Unit,
    onMicRelease: (Boolean, Boolean) -> Unit,
    onRecordingSend: () -> Unit,
    onRecordingDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val showActions = draft.isBlank()
    val recording = recorder.isRecording
    val bubbles = LocalMessageBubbleColors.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        if (recording && locked) {
            LockedRecordingBar(
                recorder = recorder,
                onDelete = onRecordingDelete,
                onSend = onRecordingSend,
            )
        } else {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.largeIncreased,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (showActions) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .alpha(if (recording) 0f else 1f)
                                    .clickable(
                                        enabled = !recording,
                                        role = Role.Button,
                                        onClick = onCamera,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PhotoCamera,
                                    contentDescription = stringResource(R.string.chat_attach_camera),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }

                        if (recording) {
                            RecordingStatus(
                                recorder = recorder,
                                cancelCandidate = cancelCandidate,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp, end = 8.dp),
                            )
                        } else {
                            BasicTextField(
                                value = draft,
                                onValueChange = onDraftChange,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(
                                        start = if (showActions) 0.dp else 12.dp,
                                        end = 8.dp,
                                    ),
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

                        if (showActions) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .alpha(if (recording) 0f else 1f)
                                    .clickable(
                                        enabled = !recording,
                                        role = Role.Button,
                                        onClick = onPickMedia,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Image,
                                    contentDescription = stringResource(R.string.chat_attach_gallery),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (showActions) {
                    MicButton(
                        recording = recording,
                        onStart = onMicStart,
                        onToggle = { if (recording) onRecordingSend() else onMicStart() },
                        onMove = onMicMove,
                        onRelease = onMicRelease,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(role = Role.Button, onClick = onSend),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(bubbles.composerAccent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.chat_send),
                                tint = bubbles.onComposerAccent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hold-to-record mic. Slide up to lock, slide left to cancel, release to send.
 * TalkBack and other accessibility services activate the same flow through the
 * semantics click action, which starts a recording and then sends it.
 */
@Composable
private fun MicButton(
    recording: Boolean,
    onStart: () -> Unit,
    onToggle: () -> Unit,
    onMove: (Boolean, Boolean) -> Unit,
    onRelease: (Boolean, Boolean) -> Unit,
) {
    val lockThreshold = with(LocalDensity.current) { LOCK_DISTANCE.toPx() }
    val cancelThreshold = with(LocalDensity.current) { CANCEL_DISTANCE.toPx() }
    val start = rememberUpdatedState(onStart)
    val toggle = rememberUpdatedState(onToggle)
    val move = rememberUpdatedState(onMove)
    val release = rememberUpdatedState(onRelease)
    val recordLabel = stringResource(R.string.chat_mic_record)
    val actionLabel = stringResource(
        if (recording) R.string.chat_mic_send else R.string.chat_mic_start,
    )
    val stateLabel = if (recording) stringResource(R.string.chat_mic_recording) else null
    val bubbles = LocalMessageBubbleColors.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .semantics {
                role = Role.Button
                contentDescription = recordLabel
                if (stateLabel != null) stateDescription = stateLabel
                onClick(label = actionLabel) {
                    toggle.value()
                    true
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    start.value()
                    var locked = false
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dy = change.position.y - down.position.y
                        val dx = change.position.x - down.position.x
                        locked = dy < -lockThreshold
                        cancelled = !locked && dx < -cancelThreshold
                        move.value(locked, cancelled)
                        if (!change.pressed) break
                    }
                    release.value(locked, cancelled)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(
                    if (recording) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        bubbles.composerAccent
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                tint = if (recording) {
                    MaterialTheme.colorScheme.error
                } else {
                    bubbles.onComposerAccent
                },
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun RecordingStatus(
    recorder: VoiceRecorder,
    cancelCandidate: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (recorder.state == VoiceRecordingState.Paused) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatMediaDuration(recorder.elapsedMs.toInt()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(10.dp))
        RecordingWaveform(
            amplitudes = recorder.waveform,
            modifier = Modifier
                .weight(1f)
                .height(24.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(
                if (cancelCandidate) R.string.chat_recording_cancel_hint
                else R.string.chat_recording_slide_up,
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (cancelCandidate) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LockedRecordingBar(
    recorder: VoiceRecorder,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paused = recorder.state == VoiceRecordingState.Paused
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = stringResource(R.string.chat_recording_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        Surface(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (paused) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatMediaDuration(recorder.elapsedMs.toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(10.dp))
                RecordingWaveform(
                    amplitudes = recorder.waveform,
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp),
                )
            }
        }
        IconButton(
            onClick = { if (paused) recorder.resume() else recorder.pause() },
        ) {
            Icon(
                imageVector = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                contentDescription = stringResource(
                    if (paused) R.string.chat_recording_resume else R.string.chat_recording_pause,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = stringResource(R.string.chat_recording_send),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val LOCK_DISTANCE = 72.dp
private val CANCEL_DISTANCE = 96.dp


@Preview(name = "Chat light", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ChatScreenLightPreview() {
    InstaDMTheme(darkTheme = false, dynamicColor = false) {
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
    backgroundColor = 0xFF10131C,
)
@Composable
private fun ChatScreenDarkPreview() {
    InstaDMTheme(darkTheme = true, dynamicColor = false) {
        ChatScreen(
            conversation = sampleConversations().first(),
            isTyping = true,
            onSend = {},
            onBack = {},
        )
    }
}

/**
 * `TakePicture` does not grant the camera app write access to the output URI, so
 * the FileProvider target fails on some devices. Add both flags explicitly.
 */
private class TakePictureWithWritePermission : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, input)
            .addFlags(
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK
}
