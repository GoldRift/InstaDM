package com.goldrift.instadm.ui.chat

import android.app.Activity
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.foundation.content.consume
import androidx.compose.foundation.content.contentReceiver
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.goldrift.instadm.R
import com.goldrift.instadm.data.DEFAULT_DOUBLE_TAP_EMOJI
import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.data.PickedMedia
import com.goldrift.instadm.data.RecordedVoice
import com.goldrift.instadm.data.ReplySnapshot
import com.goldrift.instadm.data.SharedContent
import com.goldrift.instadm.data.VoiceRecorder
import com.goldrift.instadm.data.VoiceRecordingState
import com.goldrift.instadm.service.AppVisibility
import com.goldrift.instadm.ui.components.Avatar
import com.goldrift.instadm.ui.components.ConversationAvatar
import com.goldrift.instadm.ui.components.EmojiPickerDialog
import com.goldrift.instadm.ui.components.ExpressiveStateBadge
import com.goldrift.instadm.ui.components.InlineBanner
import com.goldrift.instadm.ui.components.MiniVoicePlayerInset
import com.goldrift.instadm.ui.components.confirm
import com.goldrift.instadm.ui.components.contextClick
import com.goldrift.instadm.ui.components.longPress
import com.goldrift.instadm.ui.components.reject
import com.goldrift.instadm.ui.components.segmentTick
import com.goldrift.instadm.ui.components.showVoiceMiniPlayer
import com.goldrift.instadm.ui.components.thresholdActivated
import com.goldrift.instadm.ui.components.toggle
import com.goldrift.instadm.ui.theme.InstaDMTheme
import com.goldrift.instadm.ui.theme.LocalMessageBubbleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/** Bubble corner radius, shared with nested share cards so their curves line up. */
internal val BubbleCornerRadius = 24.dp

/** Tight inner corner radius between consecutive bubbles from the same sender. */
private val ClusterInnerCornerRadius = 6.dp

/** Inset between a bubble's edge and an inline attachment. */
internal val AttachmentInset = 4.dp

private val BubbleShape = RoundedCornerShape(BubbleCornerRadius)

private fun bubbleShape(isMine: Boolean, position: ClusterPosition): RoundedCornerShape {
    val inner = ClusterInnerCornerRadius
    val outer = BubbleCornerRadius
    return when (position) {
        ClusterPosition.Solo -> BubbleShape
        ClusterPosition.First -> if (isMine) {
            RoundedCornerShape(topStart = outer, topEnd = outer, bottomEnd = inner, bottomStart = outer)
        } else {
            RoundedCornerShape(topStart = outer, topEnd = outer, bottomEnd = outer, bottomStart = inner)
        }
        ClusterPosition.Middle -> if (isMine) {
            RoundedCornerShape(topStart = outer, topEnd = inner, bottomEnd = inner, bottomStart = outer)
        } else {
            RoundedCornerShape(topStart = inner, topEnd = outer, bottomEnd = outer, bottomStart = inner)
        }
        ClusterPosition.Last -> if (isMine) {
            RoundedCornerShape(topStart = outer, topEnd = inner, bottomEnd = outer, bottomStart = outer)
        } else {
            RoundedCornerShape(topStart = inner, topEnd = outer, bottomEnd = outer, bottomStart = outer)
        }
    }
}


/** Horizontal drag needed to start a reply, and the distance the bubble follows. */
private val SWIPE_REPLY_THRESHOLD = 56.dp
private val SWIPE_REPLY_MAX = 88.dp

/** How long the bubble jumped to from a quote keeps its outline. */
private const val QUOTE_JUMP_HIGHLIGHT_MS = 1_500L

/** Picture column reserved beside incoming group bubbles. */
private val SenderAvatarSize = 28.dp
private val SenderAvatarGap = 8.dp

/** Picture inside a group reaction bubble, scaled to the pill's text line. */
private val ReactionAvatarSize = 18.dp

/** How many typing pictures the group bubble stacks before it stops growing. */
private const val MaxTypingAvatars = 3

/** Attachments a composer stages at once. */
private const val MAX_STAGED_MEDIA = 10

/** Staged attachment thumb and the gap between thumbs in the strip. */
private val STAGED_MEDIA_SIZE = 56.dp
private val STAGED_MEDIA_GAP = 8.dp

/** Distance from the strip's edge where a drag scrolls the row. */
private val STAGED_MEDIA_EDGE = 48.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatScreen(
    conversation: Conversation,
    onSend: (String, ChatMessage?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPrepareMedia: suspend (Uri) -> PickedMedia? = { null },
    onSendMedia: (List<PickedMedia>) -> Unit = {},
    onRetryMedia: (String) -> Unit = {},
    isTyping: Boolean = false,
    typingSenders: List<ConversationParticipant> = emptyList(),
    onTyping: () -> Unit = {},
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onDismissError: () -> Unit = {},
    onLoadMedia: MediaLoader? = null,
    onEnsureVoiceWaveform: ((messageId: String, media: MessageMedia) -> Unit)? = null,
    onMarkRead: (String) -> Unit = {},
    onLoadOlder: () -> Unit = {},
    onRetryOlder: () -> Unit = {},
    onReact: (messageId: String, emoji: String) -> Unit = { _, _ -> },
    onEdit: (messageId: String, text: String) -> Unit = { _, _ -> },
    onUnsend: (messageId: String) -> Unit = {},
    onSendVoice: (RecordedVoice) -> Unit = {},
    // Off until the conversation exists on Instagram: a first message creates
    // the thread, so it can only be text.
    attachmentsEnabled: Boolean = true,
    viewerName: String = "",
    viewerAvatarUrl: String = "",
    likeEmoji: String = DEFAULT_DOUBLE_TAP_EMOJI,
    readReceiptsEnabled: Boolean = true,
    sharedContent: SharedContent? = null,
    onSharedContentConsumed: () -> Unit = {},
    // The inbox owns the player so a note can outlive this chat.
    voicePlayer: VoicePlayer,
    voiceBackgroundPlayback: Boolean = true,
    /** A note the player bar opened; scrolled to and outlined like a quote jump. */
    focusMessageId: String? = null,
    onFocusHandled: () -> Unit = {},
    /** Height of the message box, so the player bar can float just above it. */
    onComposerHeight: (Dp) -> Unit = {},
    onOpenMediaViewer: ((MediaViewerRequest) -> Unit)? = null,
    onOpenCamera: ((CameraCaptureRequest) -> Unit)? = null,
    onOpenMediaPicker: ((MediaPickerRequest) -> Unit)? = null,
) {
    val messages = conversation.messages
    // Like/reaction activity logs stay in the inbox preview and notifications;
    // in the transcript they would repeat the reaction shown on the bubble.
    val transcript = remember(messages) { messages.filterNot { it.isReactionLog() } }
    val context = LocalContext.current
    val resources = LocalResources.current
    // TalkBack gets one polite status line for arrivals and typing; the visible
    // transcript is not a live region because a LazyColumn would re-announce
    // recycled items.
    var announcedIncomingId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var arrivalAnnouncement by remember(conversation.id) { mutableStateOf("") }
    val newestIncoming = transcript.lastOrNull { it.sender != MessageSender.Me }
    LaunchedEffect(newestIncoming?.id) {
        val message = newestIncoming ?: return@LaunchedEffect
        val previous = announcedIncomingId
        announcedIncomingId = message.id
        if (previous != null && previous != message.id) {
            arrivalAnnouncement = if (message.isAdminNotice()) {
                val call = message.kind == CALL_KIND
                val fallback = resources.getString(
                    if (call) R.string.chat_call_activity else R.string.chat_activity,
                )
                val body = message.text.trim().take(120)
                if (body.isEmpty()) {
                    fallback
                } else {
                    resources.getString(
                        if (call) {
                            R.string.chat_call_announcement
                        } else {
                            R.string.chat_activity_announcement
                        },
                        body,
                    )
                }
            } else {
                val sender = message.senderName.ifBlank { conversation.contact.name }
                // The text distinguishes back-to-back messages from one sender,
                // which would otherwise produce the same live-region string.
                message.text.trim().take(80).let { preview ->
                    if (preview.isEmpty()) {
                        resources.getString(R.string.chat_new_message_from, sender)
                    } else {
                        resources.getString(R.string.chat_new_message_from_text, sender, preview)
                    }
                }
            }
        }
    }
    val typingAnnouncement = when {
        !isTyping -> ""
        typingSenders.isEmpty() -> stringResource(R.string.chat_typing)
        typingSenders.size == 1 -> stringResource(
            R.string.chat_typing_named,
            typingSenders.first().name,
        )
        else -> stringResource(
            R.string.chat_typing_and_others,
            typingSenders.first().name,
        )
    }
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keepPlayingOnLeave by rememberUpdatedState(voiceBackgroundPlayback)
    val draft = rememberTextFieldState()
    // Attachments picked or shared into this chat wait here until the viewer
    // sends them, in the order the strip shows.
    var stagedMedia by remember(conversation.id) { mutableStateOf<List<PickedMedia>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun stageMedia(uris: List<Uri>) {
        val fresh = uris.distinct().filterNot { uri -> stagedMedia.any { it.uri == uri } }
        val room = MAX_STAGED_MEDIA - stagedMedia.size
        if (fresh.size > room) {
            Toast.makeText(
                context,
                context.getString(R.string.chat_staged_limit, MAX_STAGED_MEDIA),
                Toast.LENGTH_SHORT,
            ).show()
        }
        if (room <= 0) return
        scope.launch {
            for (uri in fresh.take(room)) {
                val picked = onPrepareMedia(uri) ?: continue
                stagedMedia = stagedMedia + picked
            }
        }
    }

    // Content shared from another app lands in this chat's composer once the
    // viewer picks the conversation.
    LaunchedEffect(sharedContent) {
        val share = sharedContent ?: return@LaunchedEffect
        if (share.text.isNotBlank()) {
            draft.setTextAndPlaceCursorAtEnd(
                if (draft.text.isBlank()) {
                    share.text
                } else {
                    draft.text.trimEnd().toString() + "\n" + share.text
                },
            )
        }
        stageMedia(share.uris)
        onSharedContentConsumed()
    }
    var fallbackViewerRequest by remember { mutableStateOf<MediaViewerRequest?>(null) }
    var fallbackCameraRequest by remember { mutableStateOf<CameraCaptureRequest?>(null) }
    var fallbackPickerRequest by remember { mutableStateOf<MediaPickerRequest?>(null) }
    var actionMessageId by remember { mutableStateOf<String?>(null) }
    var replyTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var editMessageId by remember { mutableStateOf<String?>(null) }
    var draftBeforeEdit by remember { mutableStateOf<String?>(null) }
    var unsendMessageId by remember { mutableStateOf<String?>(null) }
    var emojiPickerMessageId by remember { mutableStateOf<String?>(null) }
    // Only the newest outgoing bubble shows a read receipt. Tapping any
    // non-admin bubble toggles its inline timestamp for this visit; the reveal
    // resets when leaving the chat or switching threads.
    var revealedTimestampMessageId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var recordingLocked by remember { mutableStateOf(false) }
    var recordingCancel by remember { mutableStateOf(false) }
    val voiceRecorder = remember { VoiceRecorder(context) }
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val composerFocus = remember { FocusRequester() }
    var quotedMessageId by remember(conversation.id) { mutableStateOf<String?>(null) }
    var pendingQuoteJumpId by remember(conversation.id) { mutableStateOf<String?>(null) }
    val pickerAccentColor = MaterialTheme.colorScheme.primary.toArgb()
    val keyboard = LocalSoftwareKeyboardController.current

    fun cancelEditing() {
        val restore = draftBeforeEdit
        editMessageId = null
        draftBeforeEdit = null
        if (restore != null) draft.setTextAndPlaceCursorAtEnd(restore)
    }

    fun startEditing(message: ChatMessage) {
        replyTarget = null
        if (editMessageId == null) draftBeforeEdit = draft.text.toString()
        editMessageId = message.id
        draft.setTextAndPlaceCursorAtEnd(message.text)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_STAGED_MEDIA),
    ) { uris ->
        if (uris.isNotEmpty()) {
            cancelEditing()
            replyTarget = null
            stageMedia(uris)
        }
    }
    val singleGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            cancelEditing()
            replyTarget = null
            stageMedia(listOf(uri))
        }
    }

    fun launchSystemPicker() {
        val room = MAX_STAGED_MEDIA - stagedMedia.size
        if (room <= 0) {
            Toast.makeText(
                context,
                context.getString(R.string.chat_staged_limit, MAX_STAGED_MEDIA),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        val request = buildVisualMediaRequest(
            accentColorArgb = pickerAccentColor,
            maxItems = room,
        )
        if (room == 1) {
            singleGalleryLauncher.launch(request)
        } else {
            galleryLauncher.launch(request)
        }
    }

    lateinit var pickMedia: () -> Unit

    fun takePhoto() {
        val room = MAX_STAGED_MEDIA - stagedMedia.size
        if (room <= 0) {
            Toast.makeText(
                context,
                context.getString(R.string.chat_staged_limit, MAX_STAGED_MEDIA),
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        keyboard?.hide()
        val request = CameraCaptureRequest(
            onCaptured = { uri ->
                cancelEditing()
                replyTarget = null
                stageMedia(listOf(uri))
            },
            onOpenPicker = { pickMedia() },
        )
        if (onOpenCamera != null) {
            onOpenCamera(request)
        } else {
            fallbackCameraRequest = request
        }
    }

    pickMedia = {
        val room = MAX_STAGED_MEDIA - stagedMedia.size
        if (room <= 0) {
            Toast.makeText(
                context,
                context.getString(R.string.chat_staged_limit, MAX_STAGED_MEDIA),
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            keyboard?.hide()
            if (isEmbeddedPhotoPickerSupported()) {
                val request = MediaPickerRequest(
                    maxSelectionLimit = room,
                    onGrantUris = { uris ->
                        cancelEditing()
                        replyTarget = null
                        stageMedia(uris)
                    },
                    onRevokeUris = { uris ->
                        val revoked = uris.toSet()
                        stagedMedia = stagedMedia.filterNot { it.uri in revoked }
                    },
                    onOpenCamera = { takePhoto() },
                    onFallbackToSystemPicker = { launchSystemPicker() },
                )
                if (onOpenMediaPicker != null) {
                    onOpenMediaPicker(request)
                } else {
                    fallbackPickerRequest = request
                }
            } else {
                launchSystemPicker()
            }
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, R.string.chat_mic_permission, Toast.LENGTH_LONG).show()
        }
    }

    // Starting a reply jumps into the composer, matching the affordance of the
    // quoted bar once the gesture settles.
    LaunchedEffect(replyTarget?.id) {
        if (replyTarget != null) {
            composerFocus.requestFocus()
            keyboard?.show()
        }
    }

    LaunchedEffect(editMessageId) {
        if (editMessageId != null) {
            composerFocus.requestFocus()
            keyboard?.show()
        }
    }

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
        // Voice notes send without reply metadata, so drop a pending quote
        // rather than implying the recording will carry one.
        cancelEditing()
        replyTarget = null
        recordingLocked = false
        recordingCancel = false
        if (!voiceRecorder.isRecording && voiceRecorder.start()) {
            haptic.longPress()
        }
    }

    fun finishRecording() {
        val recorded = voiceRecorder.stop()
        recordingLocked = false
        recordingCancel = false
        if (recorded != null) {
            replyTarget = null
            haptic.confirm()
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
            // With the bar on, the note keeps playing after the chat closes.
            if (!keepPlayingOnLeave) voicePlayer.pause()
        }
    }

    DisposableEffect(lifecycleOwner, keepPlayingOnLeave) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (!keepPlayingOnLeave) voicePlayer.pause()
                voiceRecorder.cancel()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val editMessage = editMessageId?.let { id -> messages.firstOrNull { it.id == id } }
    LaunchedEffect(editMessage) {
        if (editMessageId != null && (editMessage == null || !editMessage.canEdit())) {
            cancelEditing()
        }
    }
    val unsendMessage = unsendMessageId?.let { id -> messages.firstOrNull { it.id == id } }
    val emojiPickerMessage = emojiPickerMessageId?.let { id -> messages.firstOrNull { it.id == id } }
    val lastOutgoingId = lastOutgoingMessageId(transcript)
    val replyLabel = replyTarget?.let { message ->
        when {
            message.sender == MessageSender.Me -> stringResource(R.string.chat_you)
            message.senderName.isNotBlank() -> message.senderName
            else -> conversation.contact.name
        }
    }.orEmpty()

    // The list is reversed, so an item index counts back from the newest bubble
    // and the typing indicator owns the first slot while it is showing. A jump
    // also pulls the item up from the newest end so it lands around the middle
    // of the viewport instead of hugging the composer; a message near the newest
    // end simply stops at the end of the list.
    fun scrollToQuotedMessage(messageId: String) {
        val originalIndex = transcript.indexOfFirst { it.id == messageId }
        if (originalIndex < 0) return
        quotedMessageId = messageId
        scope.launch {
            val itemIndex = (if (isTyping) 1 else 0) + transcript.lastIndex - originalIndex
            val centerShift = (listState.layoutInfo.viewportSize.height * 0.4f).roundToInt()
            listState.animateScrollToItem(itemIndex, scrollOffset = -centerShift)
        }
    }

    fun jumpToQuotedMessage(messageId: String) {
        if (transcript.any { it.id == messageId }) {
            scrollToQuotedMessage(messageId)
        } else if (conversation.hasMoreOlder &&
            !conversation.isLoadingOlder &&
            !conversation.olderError
        ) {
            // The quoted message can sit outside the loaded page, so page back
            // until it lands rather than leaving the tap without a destination.
            pendingQuoteJumpId = messageId
            onLoadOlder()
        }
    }

    LaunchedEffect(
        pendingQuoteJumpId,
        transcript,
        conversation.isLoadingOlder,
        conversation.hasMoreOlder,
        conversation.olderError,
    ) {
        val target = pendingQuoteJumpId ?: return@LaunchedEffect
        when {
            transcript.any { it.id == target } -> {
                pendingQuoteJumpId = null
                scrollToQuotedMessage(target)
            }
            conversation.olderError -> pendingQuoteJumpId = null
            conversation.isLoadingOlder -> Unit
            conversation.hasMoreOlder -> onLoadOlder()
            else -> pendingQuoteJumpId = null
        }
    }

    LaunchedEffect(quotedMessageId) {
        if (quotedMessageId != null) {
            delay(QUOTE_JUMP_HIGHLIGHT_MS)
            quotedMessageId = null
        }
    }

    // Opening the chat from the player bar lands on the note it is playing,
    // with the same scroll and outline a tapped reply quote gets.
    LaunchedEffect(focusMessageId, conversation.messagesLoaded) {
        val target = focusMessageId ?: return@LaunchedEffect
        if (!conversation.messagesLoaded) return@LaunchedEffect
        jumpToQuotedMessage(target)
        onFocusHandled()
    }

    fun sendMessage() {
        val editing = editMessage
        if (editing != null) {
            val text = editedText(editing, draft.text.toString())
            if (text != null) {
                haptic.confirm()
                onEdit(editing.id, text)
            }
            cancelEditing()
            return
        }
        val text = draft.text.trim().toString()
        if (text.isEmpty() && stagedMedia.isEmpty()) return

        haptic.confirm()
        if (text.isNotEmpty()) onSend(text, replyTarget)
        if (stagedMedia.isNotEmpty()) onSendMedia(stagedMedia)
        stagedMedia = emptyList()
        draft.clearText()
        replyTarget = null
    }

    val typing = rememberUpdatedState(onTyping)
    LaunchedEffect(draft) {
        snapshotFlow { draft.text }.collect { text ->
            if (text.isNotBlank() && editMessageId == null) typing.value()
        }
    }

    // The transcript is reversed, so the pill only shows once the reader has
    // scrolled past a couple of bubbles. Deferring it through derivedStateOf
    // keeps a scroll from recomposing the whole screen per frame.
    val motion = MaterialTheme.motionScheme
    val scrolledAwayFromNewest by remember(listState) {
        derivedStateOf { showScrollToBottom(listState.firstVisibleItemIndex) }
    }
    var unseenWhileScrolledCount by remember(conversation.id) { mutableIntStateOf(0) }
    var lastSeenNewestId by remember(conversation.id) { mutableStateOf<String?>(null) }

    // Follow the newest bubble on first load, on a new message, and when typing
    // starts. Prepending an older page must not move the viewport, so this keys
    // on the newest message ID rather than the list size; a new message only
    // scrolls when the newest bubble is already on screen, unless the viewer
    // sent it.
    val newestMessage = transcript.lastOrNull()
    LaunchedEffect(scrolledAwayFromNewest) {
        if (!scrolledAwayFromNewest) unseenWhileScrolledCount = 0
    }
    LaunchedEffect(newestMessage?.id) {
        val currentId = newestMessage?.id
        val arrivals = countNewArrivals(transcript, lastSeenNewestId)
        lastSeenNewestId = currentId
        if (newestMessage?.sender == MessageSender.Me) {
            unseenWhileScrolledCount = 0
        } else if (scrolledAwayFromNewest && arrivals > 0) {
            unseenWhileScrolledCount += arrivals
        }
    }
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
    // The setting is a key as well as a gate in InboxState: turning receipts
    // back on while a chat is open must re-run the mark for the visible message.
    LaunchedEffect(readTarget, isForeground, readReceiptsEnabled) {
        if (isForeground && readTarget.isNotBlank()) {
            onMarkRead(readTarget)
        }
    }

    // Notification suppression is scoped to this chat: any other conversation
    // still notifies while the app is open.
    DisposableEffect(conversation.id) {
        AppVisibility.openChat(conversation.id)
        onDispose { AppVisibility.closeChat(conversation.id) }
    }

    // Another chat's note keeps playing on the app's bar, which floats just
    // above this composer; the transcript keeps its last bubble clear of it.
    val playerBarShown = showVoiceMiniPlayer(
        nowPlaying = voicePlayer.nowPlaying,
        visibleThreadId = conversation.id,
        enabled = voiceBackgroundPlayback,
        finished = voicePlayer.finished,
    )
    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            topBar = {
                ChatTopSection(
                    contact = conversation.contact,
                    isGroup = conversation.isGroup,
                    participants = conversation.participants,
                    isTyping = isTyping,
                    typingSenders = typingSenders,
                    onBack = onBack,
                )
            },
            bottomBar = {
                MessageComposer(
                    draft = draft,
                    onSend = ::sendMessage,
                    // Keyboard GIFs and stickers join the strip like a picked image.
                    onCommitMedia = { uri ->
                        cancelEditing()
                        replyTarget = null
                        stageMedia(listOf(uri))
                    },
                    replyingTo = replyTarget?.toReplySnapshot(),
                    replyingToLabel = replyLabel,
                    replyingToThumbnail = replyTarget?.replyThumbnail(),
                    onCancelReply = { replyTarget = null },
                    editingMessage = editMessage,
                    onCancelEdit = ::cancelEditing,
                focusRequester = composerFocus,
                onCamera = ::takePhoto,
                attachmentsEnabled = attachmentsEnabled,
                onPickMedia = { pickMedia() },
                stagedMedia = stagedMedia,
                onRemoveStagedMedia = { picked -> stagedMedia = stagedMedia - picked },
                onMoveStagedMedia = { from, to -> stagedMedia = stagedMedia.moved(from, to) },
                recorder = voiceRecorder,
                locked = recordingLocked,
                cancelCandidate = recordingCancel,
                onMicStart = ::startRecording,
                onMicMove = { lock, cancel ->
                    if (lock && !recordingLocked) {
                        recordingLocked = true
                        haptic.thresholdActivated()
                    }
                    if (!recordingLocked) {
                        // Reject once per crossing of the cancel threshold.
                        if (cancel && !recordingCancel) haptic.reject()
                        recordingCancel = cancel
                    }
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
                    haptic.reject()
                    voiceRecorder.cancel()
                    recordingLocked = false
                    recordingCancel = false
                },
                // The player bar floats just above this box; measure it so it
                // can sit there whatever the composer grows into.
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    onComposerHeight(with(density) { coordinates.size.height.toDp() })
                },
            )
        },
    ) { innerPadding ->
        val chatContentShape = RoundedCornerShape(
            topStart = MaterialTheme.shapes.extraLarge.topStart,
            topEnd = MaterialTheme.shapes.extraLarge.topEnd,
            bottomEnd = CornerSize(0.dp),
            bottomStart = CornerSize(0.dp),
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            shape = chatContentShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.fillMaxSize()) {
                LiveAnnouncement(arrivalAnnouncement)
                LiveAnnouncement(typingAnnouncement)
                var lastErrorMessage by remember { mutableStateOf(errorMessage) }
                if (errorMessage != null && errorMessage != lastErrorMessage) {
                    SideEffect { lastErrorMessage = errorMessage }
                }
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = expandVertically(motion.defaultSpatialSpec()) +
                        fadeIn(motion.defaultEffectsSpec()),
                    exit = shrinkVertically(motion.defaultSpatialSpec()) +
                        fadeOut(motion.defaultEffectsSpec()),
                ) {
                    val message = errorMessage ?: lastErrorMessage ?: return@AnimatedVisibility
                    InlineBanner(
                        text = message,
                        icon = Icons.Rounded.ErrorOutline,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onDismiss = onDismissError,
                    )
                }

                when {
                    isLoading && transcript.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val loading = stringResource(R.string.app_loading)
                            LoadingIndicator(
                                modifier = Modifier.semantics { contentDescription = loading },
                            )
                        }
                    }

                    transcript.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            ExpressiveStateBadge(
                                shape = MaterialShapes.Clover4Leaf.toShape(),
                                icon = Icons.Rounded.ChatBubbleOutline,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                iconTint = MaterialTheme.colorScheme.onSecondaryContainer,
                                size = 76.dp,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.chat_no_messages),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    else -> {
                        Box(Modifier.fillMaxSize()) {
                            MessageList(
                                messages = transcript,
                                contact = conversation.contact,
                                isGroup = conversation.isGroup,
                                participants = conversation.participants,
                                isTyping = isTyping,
                                typingSenders = typingSenders,
                                listState = listState,
                                threadId = conversation.id,
                                hasMoreOlder = conversation.hasMoreOlder,
                                isLoadingOlder = conversation.isLoadingOlder,
                                olderError = conversation.olderError,
                                onLoadOlder = onLoadOlder,
                                onRetryOlder = onRetryOlder,
                                onOpenMedia = { messageId, media ->
                                    val request = buildChatMediaViewerRequest(
                                        threadId = conversation.id,
                                        messages = transcript,
                                        tappedMessageId = messageId,
                                        tappedMedia = media,
                                        selfSenderName = resources.getString(R.string.chat_you),
                                        fallbackSenderName = conversation.contact.name,
                                    )
                                    if (onOpenMediaViewer != null) {
                                        onOpenMediaViewer(request)
                                    } else {
                                        fallbackViewerRequest = request
                                    }
                                },
                                onLongPress = { actionMessageId = it.id },
                                onReply = {
                                    cancelEditing()
                                    replyTarget = it
                                },
                                onJumpToQuoted = ::jumpToQuotedMessage,
                                highlightedMessageId = quotedMessageId,
                                lastOutgoingId = lastOutgoingId,
                                revealedTimestampMessageId = revealedTimestampMessageId,
                                onToggleTimestamp = { messageId ->
                                    revealedTimestampMessageId =
                                        if (revealedTimestampMessageId == messageId) {
                                            null
                                        } else {
                                            messageId
                                        }
                                },
                                actionMessageId = actionMessageId,
                                onDismissActions = { actionMessageId = null },
                                onReactAt = { messageId, emoji ->
                                    actionMessageId = null
                                    onReact(messageId, emoji)
                                },
                                likeEmoji = likeEmoji,
                                onCopyAt = { messageId ->
                                    actionMessageId = null
                                    messages.firstOrNull { it.id == messageId }?.let {
                                        copyToClipboard(context, it.text)
                                    }
                                },
                                onReplyAt = { messageId ->
                                    actionMessageId = null
                                    messages.firstOrNull { it.id == messageId }?.let {
                                        cancelEditing()
                                        replyTarget = it
                                    }
                                },
                                onMoreEmojiAt = { messageId ->
                                    actionMessageId = null
                                    emojiPickerMessageId = messageId
                                },
                                onEditAt = { messageId ->
                                    actionMessageId = null
                                    messages.firstOrNull { it.id == messageId }?.let(::startEditing)
                                },
                                onUnsendAt = { messageId ->
                                    actionMessageId = null
                                    unsendMessageId = messageId
                                },
                                onRetryMedia = onRetryMedia,
                                onLoadMedia = onLoadMedia,
                                onEnsureVoiceWaveform = onEnsureVoiceWaveform,
                                voicePlayer = voicePlayer,
                                viewerName = viewerName,
                                viewerAvatarUrl = viewerAvatarUrl,
                                modifier = Modifier.fillMaxSize().padding(
                                    bottom = if (playerBarShown) MiniVoicePlayerInset else 0.dp,
                                ),
                            )

                            // Fully qualified: the enclosing Column's own
                            // AnimatedVisibility would take the implicit receiver.
                            androidx.compose.animation.AnimatedVisibility(
                                visible = scrolledAwayFromNewest,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    // Clear the player bar as well: it floats in the
                                    // transcript's own bottom inset.
                                    .padding(
                                        end = 16.dp,
                                        bottom = 16.dp +
                                            if (playerBarShown) MiniVoicePlayerInset else 0.dp,
                                    ),
                                enter = scaleIn(
                                    animationSpec = motion.fastSpatialSpec(),
                                    initialScale = 0.8f,
                                ) + fadeIn(motion.fastEffectsSpec()),
                                exit = scaleOut(
                                    animationSpec = motion.fastSpatialSpec(),
                                    targetScale = 0.8f,
                                ) + fadeOut(motion.fastEffectsSpec()),
                                label = "scrollToBottom",
                            ) {
                                val hasUnseenPill = unseenWhileScrolledCount > 0
                                val pillContainer by animateColorAsState(
                                    targetValue = if (hasUnseenPill) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                    animationSpec = motion.fastEffectsSpec(),
                                    label = "scrollPillContainer",
                                )
                                val pillContent by animateColorAsState(
                                    targetValue = if (hasUnseenPill) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    animationSpec = motion.fastEffectsSpec(),
                                    label = "scrollPillContent",
                                )
                                val scrollDescription = if (hasUnseenPill) {
                                    pluralStringResource(
                                        R.plurals.chat_scroll_to_bottom_new,
                                        unseenWhileScrolledCount,
                                        unseenWhileScrolledCount,
                                    )
                                } else {
                                    stringResource(R.string.chat_scroll_to_bottom)
                                }
                                Surface(
                                    onClick = {
                                        unseenWhileScrolledCount = 0
                                        haptic.segmentTick()
                                        scope.launch { listState.animateScrollToItem(0) }
                                    },
                                    shape = CircleShape,
                                    color = pillContainer,
                                    contentColor = pillContent,
                                    shadowElevation = 3.dp,
                                    modifier = Modifier
                                        .heightIn(min = 44.dp)
                                        .widthIn(min = 44.dp)
                                        .animateContentSize(motion.fastSpatialSpec())
                                        .semantics { contentDescription = scrollDescription },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = if (hasUnseenPill) 14.dp else 10.dp,
                                            vertical = 10.dp,
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = hasUnseenPill,
                                            enter = expandHorizontally(motion.fastSpatialSpec()) +
                                                fadeIn(motion.fastEffectsSpec()),
                                            exit = shrinkHorizontally(motion.fastSpatialSpec()) +
                                                fadeOut(motion.fastEffectsSpec()),
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = pluralStringResource(
                                                        R.plurals.chat_new_messages_pill,
                                                        unseenWhileScrolledCount,
                                                        unreadBadgeLabel(unseenWhileScrolledCount),
                                                    ),
                                                    style = MaterialTheme.typography.labelLarge.copy(
                                                        fontFeatureSettings = "tnum",
                                                    ),
                                                    maxLines = 1,
                                                )
                                                Spacer(Modifier.width(4.dp))
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.ArrowDownward,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp),
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

        androidx.compose.animation.AnimatedVisibility(
            visible = actionMessageId != null,
            enter = fadeIn(motion.fastEffectsSpec()),
            exit = fadeOut(motion.fastEffectsSpec()),
        ) {
            val dismissInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.24f))
                    .clickable(
                        interactionSource = dismissInteraction,
                        indication = null,
                    ) { actionMessageId = null },
            )
        }
    }

    if (onOpenMediaViewer == null) {
        MediaViewerOverlay(
            request = fallbackViewerRequest,
            loadMedia = onLoadMedia,
            voicePlayer = voicePlayer,
            dynamicColor = true,
            onDismiss = { fallbackViewerRequest = null },
        )
    }

    if (onOpenCamera == null) {
        CameraCaptureOverlay(
            request = fallbackCameraRequest,
            voicePlayer = voicePlayer,
            dynamicColor = true,
            onDismiss = { fallbackCameraRequest = null },
        )
    }

    if (onOpenMediaPicker == null) {
        MediaPickerOverlay(
            request = fallbackPickerRequest,
            onDismiss = { fallbackPickerRequest = null },
        )
    }

    emojiPickerMessage?.let { message ->
        EmojiPickerDialog(
            onPick = { emoji ->
                haptic.confirm()
                emojiPickerMessageId = null
                onReact(message.id, emoji)
            },
            onDismiss = { emojiPickerMessageId = null },
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatTopSection(
    contact: Contact,
    isGroup: Boolean,
    participants: List<ConversationParticipant>,
    isTyping: Boolean,
    typingSenders: List<ConversationParticipant>,
    onBack: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val memberCount = groupMemberCount(participants)
    val typingLabel = when {
        !isTyping -> null
        typingSenders.isEmpty() -> stringResource(R.string.chat_typing)
        typingSenders.size == 1 -> stringResource(
            R.string.chat_typing_named,
            typingSenders.first().name,
        )
        else -> stringResource(
            R.string.chat_typing_and_others,
            typingSenders.first().name,
        )
    }
    val subtitleText = typingLabel ?: if (isGroup && memberCount > 0) {
        pluralStringResource(R.plurals.chat_member_count, memberCount, memberCount)
    } else {
        null
    }
    val subtitleState = subtitleText?.let { it to (typingLabel != null) }
    var lastSubtitleState by remember { mutableStateOf(subtitleState) }
    if (subtitleState != null && subtitleState != lastSubtitleState) {
        SideEffect { lastSubtitleState = subtitleState }
    }

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
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(
                            text = contact.name,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                        AnimatedVisibility(
                            visible = subtitleState != null,
                            enter = expandVertically(motion.fastSpatialSpec()) +
                                fadeIn(motion.fastEffectsSpec()),
                            exit = shrinkVertically(motion.fastSpatialSpec()) +
                                fadeOut(motion.fastEffectsSpec()),
                        ) {
                            val active = subtitleState ?: lastSubtitleState ?: return@AnimatedVisibility
                            AnimatedContent(
                                targetState = active,
                                transitionSpec = {
                                    fadeIn(motion.fastEffectsSpec()) togetherWith
                                        fadeOut(motion.fastEffectsSpec())
                                },
                                label = "chatHeaderSubtitle",
                            ) { (label, typingActive) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (typingActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    shapes = IconButtonDefaults.shapes(),
                ) {
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

/**
 * Whether the transcript has scrolled away from its newest message. The list is
 * reversed, so index 0 is the newest bubble; a couple of bubbles stay in the
 * buffer because the typing indicator takes the first slot while it shows.
 */
internal fun showScrollToBottom(firstVisibleItemIndex: Int, buffer: Int = 2): Boolean =
    firstVisibleItemIndex.coerceAtLeast(0) > buffer.coerceAtLeast(0)

@Composable
private fun MessageList(
    messages: List<ChatMessage>,
    contact: Contact,
    isGroup: Boolean,
    participants: List<ConversationParticipant>,
    isTyping: Boolean,
    typingSenders: List<ConversationParticipant>,
    listState: LazyListState,
    threadId: String,
    hasMoreOlder: Boolean,
    isLoadingOlder: Boolean,
    olderError: Boolean,
    onLoadOlder: () -> Unit,
    onRetryOlder: () -> Unit,
    onOpenMedia: (messageId: String, media: MessageMedia) -> Unit,
    onLongPress: (ChatMessage) -> Unit,
    onReply: (ChatMessage) -> Unit,
    onJumpToQuoted: (messageId: String) -> Unit,
    highlightedMessageId: String?,
    lastOutgoingId: String?,
    revealedTimestampMessageId: String?,
    onToggleTimestamp: (messageId: String) -> Unit,
    actionMessageId: String?,
    onDismissActions: () -> Unit,
    onReactAt: (messageId: String, emoji: String) -> Unit,
    likeEmoji: String,
    onCopyAt: (messageId: String) -> Unit,
    onReplyAt: (messageId: String) -> Unit,
    onMoreEmojiAt: (messageId: String) -> Unit,
    onEditAt: (messageId: String) -> Unit,
    onUnsendAt: (messageId: String) -> Unit,
    onRetryMedia: (messageId: String) -> Unit,
    onLoadMedia: MediaLoader?,
    onEnsureVoiceWaveform: ((messageId: String, media: MessageMedia) -> Unit)?,
    voicePlayer: VoicePlayer,
    viewerName: String,
    viewerAvatarUrl: String,
    modifier: Modifier = Modifier,
) {
    val reversed = messages.asReversed()
    val messagesById = remember(messages) { messages.associateBy { it.id } }
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
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (isTyping) {
            item(key = "typing", contentType = "typing") {
                TypingBubble(
                    senders = typingSenders,
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .animateItem(
                            fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        ),
                )
            }
        }

        itemsIndexed(
            items = reversed,
            key = { _, message -> message.id },
            contentType = { _, message -> if (message.isAdminNotice()) "admin" else message.sender },
        ) { reverseIndex, message ->
            val adminNotice = message.isAdminNotice()
            val originalIndex = messages.lastIndex - reverseIndex
            val previous = messages.getOrNull(originalIndex - 1)
            val next = messages.getOrNull(originalIndex + 1)
            val member = if (isGroup) {
                participants.firstOrNull { it.fbid == message.senderFbid }
            } else {
                null
            }
            // Instagram only persists the sender on the message row, so fall
            // back to it when the participant list does not know the FBID.
            val senderName = if (isGroup) {
                member?.name?.takeIf { it.isNotBlank() }
                    ?: message.senderName.takeIf { it.isNotBlank() }.orEmpty()
            } else {
                ""
            }
            val senderInitials = member?.initials?.takeIf { it.isNotBlank() }
                ?: senderName.takeIf { it.isNotBlank() }?.let(::initialsOf)
                .orEmpty()
            val senderAvatarUrl = member?.avatarUrl.orEmpty()
            // The list is reversed, so the oldest message of a day or time block
            // sits at the top of that block. A header at the top of this item
            // separates it from the older message above it.
            val divider = transcriptDividerBefore(previous, message)
            val showDayHeader = divider == TranscriptDivider.Day
            val showTimeGap = divider == TranscriptDivider.TimeGap
            val clusterPos = clusterPosition(previous, message, next)
            val joinedWithPrevious = previous != null && sameSenderCluster(previous, message)
            val topSpacing = when {
                previous == null || divider != TranscriptDivider.None -> 0.dp
                joinedWithPrevious -> 2.dp
                else -> 10.dp
            }
            // Direct chats show no avatars beside bubbles.
            // Groups attribute the first bubble of each sender run with the
            // member's picture and name, like Instagram's own group threads.
            val startsSenderRun = startsGroupSenderRun(
                previous = previous,
                message = message,
                isGroup = isGroup,
                showDayHeader = showDayHeader,
                showTimeGap = showTimeGap,
            )
            // A run without any resolved identity still keeps the gutter so
            // every incoming bubble lines up with the ones around it.
            val senderKnown = senderName.isNotBlank() || senderAvatarUrl.isNotBlank()
            val showAvatar = !adminNotice && startsSenderRun && senderKnown
            val showSenderName = !adminNotice && startsSenderRun && senderName.isNotBlank()
            val reserveAvatarGutter =
                isGroup && message.sender == MessageSender.Them && !adminNotice
            // Prefer the loaded original (it has the full text) and fall back to
            // the snapshot Instagram embeds when it is outside the page.
            val quotedOriginal = message.reply?.let { stored -> messagesById[stored.id] }
            val reply = message.reply?.let { stored ->
                quotedOriginal?.toReplySnapshot() ?: stored
            }
            val replyThumbnail = quotedOriginal?.replyThumbnail()
            val replyLabel = reply?.let { replySenderLabel(it, contact) }.orEmpty()
            val showReadReceipt = !adminNotice && message.showsReadReceipt(lastOutgoingId)
            val timestampRevealed = !adminNotice && revealedTimestampMessageId == message.id

            Column(
                modifier = Modifier.animateItem(
                    fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                ),
            ) {
                if (showDayHeader) {
                    DayHeader(timestampMs = message.timestampMs)
                } else if (showTimeGap) {
                    TimeGapHeader(timestampMs = message.timestampMs)
                }
                if (topSpacing > 0.dp) {
                    Spacer(Modifier.height(topSpacing))
                }
                if (adminNotice) {
                    AdminNoticeRow(
                        message = message,
                        isGroup = isGroup,
                    )
                } else {
                    MessageRow(
                        message = message,
                        contact = contact,
                        senderName = senderName,
                        showSenderName = showSenderName,
                        senderInitials = senderInitials,
                        senderAvatarUrl = senderAvatarUrl,
                        senderColorSeed = member?.fbid?.toString() ?: senderInitials,
                        showAvatar = showAvatar,
                        reserveAvatarGutter = reserveAvatarGutter,
                        clusterPosition = clusterPos,
                        threadId = threadId,
                        isVisible = message.id in visibleMessageIds,
                        onOpenMedia = { media -> onOpenMedia(message.id, media) },
                        onLongPress = { if (message.isServerMessage()) onLongPress(message) },
                        reply = reply,
                        replyLabel = replyLabel,
                        replyThumbnail = replyThumbnail,
                        // A quote jumps to its message, paging back first when
                        // the original is older than the loaded window.
                        onQuoteClick = if (reply != null &&
                            (messagesById.containsKey(reply.id) || hasMoreOlder)
                        ) {
                            { onJumpToQuoted(reply.id) }
                        } else {
                            null
                        },
                        highlighted = message.id == highlightedMessageId,
                        onReply = { onReply(message) },
                        showReadReceipt = showReadReceipt,
                        timestampRevealed = timestampRevealed,
                        onToggleTimestamp = if (message.canRevealTimestamp()) {
                            { onToggleTimestamp(message.id) }
                        } else {
                            null
                        },
                        actionsExpanded = actionMessageId == message.id,
                        onDismissActions = onDismissActions,
                        onReact = { emoji -> onReactAt(message.id, emoji) },
                        likeEmoji = likeEmoji,
                        onLike = { onReactAt(message.id, likeEmoji) },
                        onCopy = { onCopyAt(message.id) },
                        onReplyAt = { onReplyAt(message.id) },
                        onMoreEmoji = { onMoreEmojiAt(message.id) },
                        onEdit = { onEditAt(message.id) },
                        onUnsend = { onUnsendAt(message.id) },
                        onRetry = { onRetryMedia(message.id) },
                        onLoadMedia = onLoadMedia,
                        onEnsureVoiceWaveform = onEnsureVoiceWaveform,
                        voicePlayer = voicePlayer,
                        isGroup = isGroup,
                        participants = participants,
                        viewerName = viewerName,
                        viewerAvatarUrl = viewerAvatarUrl,
                    )
                }
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

/**
 * Centered activity notice for Instagram admin text. It is deliberately not a
 * bubble: no sender avatar, swipe, long-press, reactions, ticks, or copy.
 */
@Composable
private fun AdminNoticeRow(
    message: ChatMessage,
    isGroup: Boolean,
    modifier: Modifier = Modifier,
) {
    val restricted = message.kind == ADMIN_RESTRICTED_KIND
    val call = message.kind == CALL_KIND
    val fallback = stringResource(
        if (call) R.string.chat_call_activity else R.string.chat_activity,
    )
    val text = message.text.trim().ifBlank { fallback }
    val icon = when {
        call -> Icons.Rounded.Call
        restricted -> Icons.Rounded.ReportProblem
        message.kind == ADMIN_GROUP_KIND || isGroup -> Icons.Rounded.Groups
        else -> Icons.Rounded.Info
    }
    val containerColor = if (restricted) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (restricted) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val iconTint = if (restricted) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = containerColor,
            contentColor = contentColor,
            border = if (restricted) {
                null
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

private enum class MessageStatus { Failed, Pending, Sent, Seen }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageRow(
    message: ChatMessage,
    contact: Contact,
    senderName: String,
    showSenderName: Boolean,
    senderInitials: String,
    senderAvatarUrl: String,
    senderColorSeed: String,
    showAvatar: Boolean,
    reserveAvatarGutter: Boolean,
    clusterPosition: ClusterPosition,
    threadId: String,
    isVisible: Boolean,
    onOpenMedia: (MessageMedia) -> Unit,
    onLongPress: () -> Unit,
    reply: ReplySnapshot?,
    replyLabel: String,
    replyThumbnail: ReplyThumbnail?,
    onQuoteClick: (() -> Unit)?,
    highlighted: Boolean,
    onReply: () -> Unit,
    showReadReceipt: Boolean,
    timestampRevealed: Boolean,
    onToggleTimestamp: (() -> Unit)?,
    actionsExpanded: Boolean,
    onDismissActions: () -> Unit,
    onReact: (String) -> Unit,
    likeEmoji: String,
    onLike: () -> Unit,
    onCopy: () -> Unit,
    onReplyAt: () -> Unit,
    onMoreEmoji: () -> Unit,
    onEdit: () -> Unit,
    onUnsend: () -> Unit,
    onRetry: () -> Unit,
    onLoadMedia: MediaLoader?,
    onEnsureVoiceWaveform: ((messageId: String, media: MessageMedia) -> Unit)?,
    voicePlayer: VoicePlayer,
    isGroup: Boolean,
    participants: List<ConversationParticipant>,
    viewerName: String,
    viewerAvatarUrl: String,
    modifier: Modifier = Modifier,
) {
    val isMine = message.sender == MessageSender.Me
    // The tap, double-tap, and long-press detectors live in a pointerInput keyed
    // only on the message, so read the current callbacks through state rather
    // than capturing the ones present when the bubble first composed.
    val currentOnToggleTimestamp by rememberUpdatedState(onToggleTimestamp)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val currentOnLike by rememberUpdatedState(onLike)
    val canShowActions by rememberUpdatedState(message.isServerMessage())
    val bubbles = LocalMessageBubbleColors.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val motion = MaterialTheme.motionScheme
    // Outlining the bubble a quote jumped to lets the eye find it after the
    // scroll; the tint comes from the bubble's own content role so an outgoing
    // accent bubble and a neutral incoming bubble both keep it visible.
    val jumpOutline by animateColorAsState(
        targetValue = if (highlighted) {
            if (isMine) bubbles.onOutgoing else bubbles.accent
        } else {
            Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "quoteJumpOutline",
    )
    // A sticker or 1 to 3 standalone emoji renders directly without a bubble container.
    val stickerOnly = message.isStickerOnly()
    val emojiOnly = message.isJumboEmoji()
    val transparentBubble = stickerOnly || emojiOnly
    var reactionBurstToken by remember(message.id) { mutableIntStateOf(0) }
    val canReply = message.canReply()
    val messageStatus = when {
        message.failed -> MessageStatus.Failed
        message.pending -> MessageStatus.Pending
        message.seen -> MessageStatus.Seen
        else -> MessageStatus.Sent
    }
    val replyThreshold = with(density) { SWIPE_REPLY_THRESHOLD.toPx() }
    val maxDrag = with(density) { SWIPE_REPLY_MAX.toPx() }
    var dragOffset by remember(message.id) { mutableFloatStateOf(0f) }
    var dragging by remember(message.id) { mutableStateOf(false) }
    var replyArmed by remember(message.id) { mutableStateOf(false) }
    // Snap while the finger is down, then spring back the moment the gesture
    // ends or is cancelled. The offset is plain state so no queued animation
    // can race the release and leave a bubble parked mid-swipe.
    val settledOffset by animateFloatAsState(
        targetValue = if (dragging) dragOffset else 0f,
        animationSpec = if (dragging) {
            snap()
        } else {
            MaterialTheme.motionScheme.defaultSpatialSpec()
        },
        label = "replySwipeOffset",
    )
    // Center the picture on the sender name rather than its top edge: the name
    // line is shorter than the picture, so the bubble column starts half the
    // difference lower and the picture's midline meets the name's midline.
    val senderNameLine = MaterialTheme.typography.labelMedium.lineHeight
        .takeOrElse { MaterialTheme.typography.labelMedium.fontSize }
    val senderNameInset = if (showSenderName) {
        with(density) {
            ((SenderAvatarSize - senderNameLine.toDp()) * 0.5f).coerceAtLeast(0.dp)
        }
    } else {
        0.dp
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = if (isMine) Alignment.Bottom else Alignment.Top,
    ) {
        if (reserveAvatarGutter) {
            // The gutter stays for every bubble of a sender run, so a run's
            // bubbles line up under the picture even though only the first
            // one shows it.
            Box(modifier = Modifier.size(SenderAvatarSize)) {
                if (showAvatar) {
                    Avatar(
                        initials = senderInitials,
                        size = SenderAvatarSize,
                        imageUrl = senderAvatarUrl,
                        colorSeed = senderColorSeed,
                    )
                }
            }
            Spacer(Modifier.width(SenderAvatarGap))
        }

        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(top = senderNameInset),
        ) {
            if (showSenderName) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                )
            }
            val actionsLabel = stringResource(R.string.chat_message_actions)
            val showTimeLabel = stringResource(R.string.chat_show_timestamp)
            val hideTimeLabel = stringResource(R.string.chat_hide_timestamp)
            Box {
                val dragProgress =
                    (settledOffset / replyThreshold).coerceIn(0f, 1f)
                SwipeReplyIcon(
                    progress = dragProgress,
                    armed = replyArmed,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .then(if (isMine) Modifier.offset(x = (-36).dp) else Modifier),
                )
                Surface(
                    modifier = Modifier
                        .offset { IntOffset(settledOffset.roundToInt(), 0) }
                        .pointerInput(message.id, canReply) {
                            if (!canReply) return@pointerInput
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    dragging = true
                                    replyArmed = false
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val next = (dragOffset + dragAmount)
                                        .coerceIn(0f, maxDrag)
                                    dragOffset = next
                                    val armed = next >= replyThreshold
                                    if (armed != replyArmed) {
                                        replyArmed = armed
                                        if (armed) haptic.thresholdActivated()
                                    }
                                },
                                onDragEnd = {
                                    val shouldReply = dragOffset >= replyThreshold
                                    dragging = false
                                    dragOffset = 0f
                                    replyArmed = false
                                    if (shouldReply) onReply()
                                },
                                onDragCancel = {
                                    dragging = false
                                    dragOffset = 0f
                                    replyArmed = false
                                },
                            )
                        }
                        .semantics {
                            onToggleTimestamp?.let { toggle ->
                                onClick(
                                    label = if (timestampRevealed) {
                                        hideTimeLabel
                                    } else {
                                        showTimeLabel
                                    },
                                ) {
                                    toggle()
                                    true
                                }
                            }
                            onLongClick(label = actionsLabel) {
                                onLongPress()
                                true
                            }
                        }
                        .pointerInput(message.id, message.isServerMessage()) {
                            detectTapGestures(
                                onTap = {
                                    currentOnToggleTimestamp?.let { toggle ->
                                        haptic.segmentTick()
                                        toggle()
                                    }
                                },
                                // A double tap likes the message, so a single
                                // tap waits out the double-tap timeout; the
                                // timestamp reveal can absorb that delay.
                                onDoubleTap = if (message.canReact()) {
                                    {
                                        reactionBurstToken++
                                        haptic.confirm()
                                        currentOnLike()
                                    }
                                } else {
                                    null
                                },
                                onLongPress = {
                                    if (canShowActions) {
                                        haptic.longPress()
                                        currentOnLongPress()
                                    }
                                },
                            )
                        },
                    shape = bubbleShape(isMine, clusterPosition),
                    color = if (transparentBubble) {
                        Color.Transparent
                    } else if (isMine) {
                        bubbles.outgoing
                    } else {
                        bubbles.incoming
                    },
                    contentColor = if (isMine) bubbles.onOutgoing else bubbles.onIncoming,
                    border = BorderStroke(2.dp, jumpOutline),
                ) {
                    MessageBubbleContent(
                        message = message,
                        threadId = threadId,
                        isVisible = isVisible,
                        reply = reply,
                        replyLabel = replyLabel,
                        replyThumbnail = replyThumbnail,
                        emojiOnly = emojiOnly,
                        onQuoteClick = onQuoteClick,
                        onOpenMedia = onOpenMedia,
                        onLongPress = onLongPress,
                        onLoadMedia = onLoadMedia,
                        onEnsureVoiceWaveform = onEnsureVoiceWaveform,
                        voicePlayer = voicePlayer,
                        // The bar names the chat; a group keeps the sender.
                        noteTitle = senderName.ifBlank { contact.name },
                        noteAvatarUrl = senderAvatarUrl.ifBlank { contact.avatarUrl },
                        noteInitials = senderInitials.ifBlank { contact.initials },
                    )
                }

                if (reactionBurstToken > 0) {
                    DoubleTapEmojiBurst(
                        emoji = likeEmoji,
                        triggerToken = reactionBurstToken,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                if (actionsExpanded) {
                    MessageActionsPopup(
                        message = message,
                        isMine = isMine,
                        onDismiss = onDismissActions,
                        onReact = onReact,
                        onCopy = onCopy,
                        onReply = onReplyAt,
                        onMoreEmoji = onMoreEmoji,
                        onEdit = onEdit,
                        onUnsend = onUnsend,
                    )
                }
            }

            ReactionPills(
                message = message,
                isGroup = isGroup,
                participants = participants,
                viewerName = viewerName,
                viewerAvatarUrl = viewerAvatarUrl,
            )

            val revealedTime = remember(message.timestampMs, message.timestamp, timestampRevealed) {
                if (timestampRevealed) {
                    formatRevealedTimestamp(message.timestampMs, message.timestamp)
                } else {
                    ""
                }
            }
            val showStatus = isMine && (message.failed || message.pending || showReadReceipt)
            val showTime = timestampRevealed && revealedTime.isNotBlank()
            AnimatedVisibility(
                visible = showStatus || showTime,
                enter = expandVertically(motion.fastSpatialSpec()) +
                    fadeIn(motion.fastEffectsSpec()),
                exit = shrinkVertically(motion.fastSpatialSpec()) +
                    fadeOut(motion.fastEffectsSpec()),
            ) {
                Row(
                    modifier = Modifier.padding(
                        top = if (isMine && message.failed) 4.dp else 2.dp,
                        start = if (isMine) 0.dp else 4.dp,
                        end = if (isMine) 4.dp else 0.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showStatus) {
                        AnimatedContent(
                            targetState = messageStatus,
                            transitionSpec = {
                                val enter = fadeIn(motion.fastEffectsSpec()) +
                                    scaleIn(motion.fastSpatialSpec(), initialScale = 0.85f)
                                val exit = fadeOut(motion.fastEffectsSpec()) +
                                    scaleOut(motion.fastSpatialSpec(), targetScale = 0.85f)
                                (enter togetherWith exit).using(SizeTransform(clip = false))
                            },
                            label = "messageStatus",
                        ) { status ->
                            val isTarget = transition.targetState == EnterExitState.Visible
                            when (status) {
                                MessageStatus.Failed -> Surface(
                                    onClick = onRetry,
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .inputOnlyWhen(isTarget),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 10.dp,
                                            vertical = 4.dp,
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.chat_media_failed),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }

                                MessageStatus.Pending -> LoadingIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                MessageStatus.Sent -> Text(
                                    text = stringResource(R.string.chat_sent),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                MessageStatus.Seen -> Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (!isGroup) {
                                        Avatar(
                                            initials = contact.initials,
                                            size = 14.dp,
                                            imageUrl = contact.avatarUrl,
                                            colorSeed = contact.id.ifBlank { contact.name },
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        text = stringResource(R.string.chat_seen),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFeatureSettings = "tnum",
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showTime,
                        enter = if (showStatus) {
                            expandHorizontally(motion.fastSpatialSpec()) +
                                fadeIn(motion.fastEffectsSpec())
                        } else {
                            EnterTransition.None
                        },
                        exit = if (showStatus) {
                            shrinkHorizontally(motion.fastSpatialSpec()) +
                                fadeOut(motion.fastEffectsSpec())
                        } else {
                            ExitTransition.None
                        },
                    ) {
                        val timeText = if (showStatus && !message.failed) {
                            stringResource(R.string.chat_inline_time_suffix, revealedTime)
                        } else {
                            revealedTime
                        }
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFeatureSettings = "tnum",
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = if (showStatus && message.failed) {
                                Modifier.padding(start = 6.dp)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Circular reply affordance revealed while dragging a bubble to the right. */
@Composable
private fun SwipeReplyIcon(
    progress: Float,
    armed: Boolean,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0f) return
    val bubbles = LocalMessageBubbleColors.current
    val motion = MaterialTheme.motionScheme
    val armedScale by animateFloatAsState(
        targetValue = if (armed) 1.18f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "swipeReplyArmedScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (armed) {
            bubbles.accent
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        animationSpec = motion.fastEffectsSpec(),
        label = "swipeReplyContainer",
    )
    val iconTint by animateColorAsState(
        targetValue = if (armed) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = motion.fastEffectsSpec(),
        label = "swipeReplyTint",
    )
    Box(
        modifier = modifier
            .size(32.dp)
            .graphicsLayer {
                val scale = (0.8f + 0.2f * progress) * armedScale
                scaleX = scale
                scaleY = scale
                alpha = progress
            }
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Reply,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Floating reaction burst played over a bubble on double-tap. */
@Composable
private fun DoubleTapEmojiBurst(
    emoji: String,
    triggerToken: Int,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val scale = remember { Animatable(0.4f) }
    val alpha = remember { Animatable(0f) }
    val rise = remember { Animatable(0f) }
    LaunchedEffect(triggerToken) {
        scale.snapTo(0.4f)
        alpha.snapTo(1f)
        rise.snapTo(0f)
        launch {
            scale.animateTo(1.35f, motion.fastSpatialSpec())
            scale.animateTo(1f, motion.fastSpatialSpec())
        }
        launch {
            rise.animateTo(-32f, motion.defaultSpatialSpec())
        }
        delay(160)
        alpha.animateTo(0f, motion.fastEffectsSpec())
    }
    Box(
        modifier = modifier.graphicsLayer {
            val currentAlpha = alpha.value
            if (currentAlpha <= 0f) {
                this.alpha = 0f
                return@graphicsLayer
            }
            translationY = rise.value.dp.toPx()
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = currentAlpha
        },
    ) {
        EmojiGlyph(emoji)
    }
}

/**
 * The quoted message block rendered at the top of a reply bubble. Its container
 * is keyed by the quoted author, not by the bubble holding it, so a quote of
 * your own message reads as the accent tint and a quote of theirs stays
 * neutral. When the quoted message is on screen, the block is a button that
 * scrolls the transcript back to it.
 */
@Composable
private fun ReplyQuote(
    reply: ReplySnapshot,
    label: String,
    thumbnail: ReplyThumbnail?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val bubbles = LocalMessageBubbleColors.current
    val fromViewer = reply.isFromViewer
    val color = if (fromViewer) bubbles.quoteFromViewer else bubbles.quoteFromThem
    val contentColor =
        if (fromViewer) bubbles.onQuoteFromViewer else bubbles.onQuoteFromThem
    val shape = MaterialTheme.shapes.medium
    val quoteModifier = modifier.widthIn(min = 180.dp)

    if (onClick == null) {
        Surface(
            modifier = quoteModifier,
            shape = shape,
            color = color,
            contentColor = contentColor,
        ) {
            ReplyQuoteBody(reply = reply, label = label, thumbnail = thumbnail)
        }
    } else {
        Surface(
            onClick = onClick,
            modifier = quoteModifier,
            shape = shape,
            color = color,
            contentColor = contentColor,
        ) {
            ReplyQuoteBody(reply = reply, label = label, thumbnail = thumbnail)
        }
    }
}

@Composable
private fun ReplyQuoteBody(
    reply: ReplySnapshot,
    label: String,
    thumbnail: ReplyThumbnail?,
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnail != null) {
            ReplyThumbnailBox(thumbnail = thumbnail, size = 40.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = replyContent(reply),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ReplyThumbnailBox(
    thumbnail: ReplyThumbnail,
    size: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = thumbnail.model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (thumbnail.isVideo) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.56f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** Display name for the quoted message's sender. */
@Composable
private fun replySenderLabel(reply: ReplySnapshot, contact: Contact): String = when {
    reply.isFromViewer -> stringResource(R.string.chat_you)
    reply.senderName.isNotBlank() -> reply.senderName
    contact.name.isNotBlank() -> contact.name
    else -> stringResource(R.string.chat_someone)
}

/** A human-readable line for a quoted message that has no text. */
@Composable
private fun replyContent(reply: ReplySnapshot): String {
    if (reply.text.isNotBlank()) return reply.text
    return when (reply.kind) {
        "image" -> stringResource(R.string.chat_reply_photo)
        "video" -> stringResource(R.string.chat_reply_video)
        "audio" -> stringResource(R.string.chat_reply_voice)
        "sticker" -> stringResource(R.string.chat_reply_sticker)
        "gif" -> stringResource(R.string.chat_reply_gif)
        "link" -> stringResource(R.string.chat_reply_link)
        "link-unavailable" -> stringResource(R.string.chat_reply_link)
        "post" -> stringResource(R.string.chat_reply_post)
        "post-unavailable" -> stringResource(R.string.chat_reply_post)
        "story" -> stringResource(R.string.chat_reply_story)
        "story-unavailable" -> stringResource(R.string.chat_reply_story)
        "reel" -> stringResource(R.string.chat_reply_reel)
        "reel-unavailable" -> stringResource(R.string.chat_reply_reel)
        else -> stringResource(R.string.chat_reply_attachment)
    }
}

@Composable
private fun MessageBubbleContent(
    message: ChatMessage,
    threadId: String,
    isVisible: Boolean,
    reply: ReplySnapshot?,
    replyLabel: String,
    replyThumbnail: ReplyThumbnail?,
    emojiOnly: Boolean,
    onQuoteClick: (() -> Unit)?,
    onOpenMedia: (MessageMedia) -> Unit,
    onLongPress: () -> Unit,
    onLoadMedia: MediaLoader?,
    onEnsureVoiceWaveform: ((messageId: String, media: MessageMedia) -> Unit)?,
    voicePlayer: VoicePlayer,
    noteTitle: String,
    noteAvatarUrl: String,
    noteInitials: String,
) {
    val media = message.media.filter { it.isVisual && !it.isReel }
    val shares = message.media.filter { (it.isLink || it.isReel) && !it.viewOnce }
    val viewOnce = message.media.filter { it.viewOnce }
    val voiceNotes = message.media.filter { it.isVoiceNote }
    val localMedia = message.localMedia
    val unavailable = message.kind == "unavailable" ||
        message.kind.endsWith("-unavailable")
    val hasAttachment = media.isNotEmpty() || shares.isNotEmpty() ||
        viewOnce.isNotEmpty() || voiceNotes.isNotEmpty() || localMedia != null ||
        unavailable
    Column(
        modifier = Modifier.padding(
            horizontal = if (hasAttachment || emojiOnly) AttachmentInset else 14.dp,
            vertical = if (hasAttachment || emojiOnly) AttachmentInset else 9.dp,
        ),
    ) {
        if (reply != null) {
            ReplyQuote(
                reply = reply,
                label = replyLabel,
                thumbnail = replyThumbnail,
                onClick = onQuoteClick,
                modifier = Modifier.padding(
                    start = if (hasAttachment) 6.dp else 0.dp,
                    end = if (hasAttachment) 6.dp else 0.dp,
                    bottom = 6.dp,
                ),
            )
        }

        viewOnce.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(4.dp))
            ViewOnceNotice(
                media = item,
                onLongClick = onLongPress,
            )
        }

        if (viewOnce.isNotEmpty() &&
            (media.isNotEmpty() || shares.isNotEmpty() || voiceNotes.isNotEmpty())
        ) {
            Spacer(Modifier.height(4.dp))
        }

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

        shares.forEachIndexed { index, item ->
            if (index > 0 || media.isNotEmpty()) Spacer(Modifier.height(4.dp))
            ShareCard(
                media = item,
                onOpenReel = { if (message.isServerMessage()) onOpenMedia(item) },
                onLongClick = onLongPress,
            )
        }

        if (voiceNotes.isNotEmpty()) {
            if (media.isNotEmpty() || shares.isNotEmpty()) Spacer(Modifier.height(4.dp))
            voiceNotes.forEach { item ->
                VoiceNoteBubble(
                    media = item,
                    threadId = threadId,
                    messageId = message.id,
                    player = voicePlayer,
                    loadMedia = onLoadMedia,
                    onLongClick = onLongPress,
                    noteTitle = noteTitle,
                    noteAvatarUrl = noteAvatarUrl,
                    noteInitials = noteInitials,
                    isVisible = isVisible,
                    onEnsureWaveform = onEnsureVoiceWaveform?.let { ensure ->
                        { ensure(message.id, item) }
                    },
                )
            }
        }

        if (media.isEmpty() && shares.isEmpty() && voiceNotes.isEmpty() && localMedia != null) {
            LocalMediaThumbnail(
                media = localMedia,
                onLongClick = onLongPress,
            )
        }

        if (unavailable && message.text.isNotBlank()) {
            UnavailableShareNotice(
                text = message.text,
                onLongClick = onLongPress,
            )
        } else if (emojiOnly) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 38.sp,
                    lineHeight = 44.sp,
                ),
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp),
            )
        } else if (message.text.isNotBlank()) {
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

/**
 * Reaction bubbles under a message, in first-seen order. Direct chats group
 * identical emoji; groups keep one bubble per reactor so each carries a face.
 */
@Composable
private fun ReactionPills(
    message: ChatMessage,
    isGroup: Boolean,
    participants: List<ConversationParticipant>,
    viewerName: String,
    viewerAvatarUrl: String,
) {
    // Groups attribute each reaction to its sender, so every reactor gets a
    // picture bubble. Direct chats have one other person, and the pill only
    // needs the emoji.
    if (isGroup) {
        val reactions = message.reactions.filter { it.emoji.isNotBlank() }
        if (reactions.isEmpty()) return
        FlowRow(
            modifier = Modifier.padding(top = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            reactions.forEach { reaction ->
                val participant = participants.firstOrNull {
                    reaction.senderFbid != 0L && it.fbid == reaction.senderFbid
                }
                ReactionPill(
                    emoji = reaction.emoji,
                    count = 1,
                    fromViewer = reaction.isFromViewer,
                    author = ReactionAuthor(
                        initials = if (reaction.isFromViewer) {
                            initialsOf(viewerName)
                        } else {
                            participant?.initials.orEmpty()
                        },
                        avatarUrl = if (reaction.isFromViewer) {
                            viewerAvatarUrl
                        } else {
                            participant?.avatarUrl.orEmpty()
                        },
                        colorSeed = if (reaction.isFromViewer) {
                            viewerName
                        } else {
                            reaction.senderFbid.toString()
                        },
                    ),
                )
            }
        }
        return
    }

    val groups = message.reactionGroups()
    if (groups.isEmpty()) return

    Row(
        modifier = Modifier.padding(top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        groups.forEach { group ->
            ReactionPill(
                emoji = group.emoji,
                count = group.count,
                fromViewer = group.fromViewer,
                author = null,
            )
        }
    }
}

/** Reactor shown inside a group reaction bubble. */
private data class ReactionAuthor(
    val initials: String,
    val avatarUrl: String,
    val colorSeed: String,
)

/** One reaction bubble: emoji alone in direct chats, with the reactor's picture in groups. */
@Composable
private fun ReactionPill(
    emoji: String,
    count: Int,
    fromViewer: Boolean,
    author: ReactionAuthor?,
) {
    val description = pluralStringResource(R.plurals.chat_reaction_count, count, emoji, count)
    val motion = MaterialTheme.motionScheme
    // Both paths (group and direct) get the entrance because it lives in the
    // pill, and a remembered Animatable keeps it to the pill's first appearance.
    val appearScale = remember { Animatable(0f) }
    val appearAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { appearScale.animateTo(1f, motion.defaultSpatialSpec()) }
        appearAlpha.animateTo(1f, motion.defaultEffectsSpec())
    }
    Surface(
        modifier = Modifier
            .graphicsLayer {
                scaleX = 0.6f + 0.4f * appearScale.value
                scaleY = 0.6f + 0.4f * appearScale.value
                alpha = appearAlpha.value
            }
            .clearAndSetSemantics { contentDescription = description },
        shape = CircleShape,
        color = if (fromViewer) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        contentColor = if (fromViewer) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (author == null) 8.dp else 3.dp,
                end = if (author == null) 8.dp else 7.dp,
                top = 2.dp,
                bottom = 2.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (author != null) {
                Avatar(
                    initials = author.initials,
                    size = ReactionAvatarSize,
                    imageUrl = author.avatarUrl,
                    colorSeed = author.colorSeed,
                )
            }
            Text(
                text = if (count > 1) "$emoji $count" else emoji,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private val QuickReactions = listOf("❤️", "😂", "😮", "😢", "😡", "👍")

private class PopupPlacementHolder(var belowAnchor: Boolean = true)

/**
 * Floating reaction pill and grouped action menu anchored cleanly below or
 * above the long-pressed bubble so the message text stays uncovered.
 */
@Composable
private fun MessageActionsPopup(
    message: ChatMessage,
    isMine: Boolean,
    onDismiss: () -> Unit,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onMoreEmoji: () -> Unit,
    onEdit: () -> Unit,
    onUnsend: () -> Unit,
) {
    val density = LocalDensity.current
    val motion = MaterialTheme.motionScheme
    // Re-created whenever the popup opens, so the entrance plays per open.
    val appearScale = remember { Animatable(0f) }
    val appearAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { appearScale.animateTo(1f, motion.defaultSpatialSpec()) }
        appearAlpha.animateTo(1f, motion.defaultEffectsSpec())
    }
    val placementHolder = remember { PopupPlacementHolder() }
    val margin = with(density) { 8.dp.roundToPx() }
    val topMargin = margin + WindowInsets.statusBars.getTop(density)
    val bottomMargin = margin + WindowInsets.navigationBars.getBottom(density)
    val gap = with(density) { 8.dp.roundToPx() }
    val positionProvider = remember(isMine, margin, topMargin, bottomMargin, gap, placementHolder) {
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
                val placement = messagePopupPlacement(
                    anchorTop = anchorBounds.top,
                    anchorBottom = anchorBounds.bottom,
                    popupHeight = popupContentSize.height,
                    windowHeight = windowSize.height,
                    topMargin = topMargin,
                    bottomMargin = bottomMargin,
                    gap = gap,
                )
                placementHolder.belowAnchor = placement.belowAnchor
                return IntOffset(x, placement.y)
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
            modifier = Modifier.graphicsLayer {
                transformOrigin = TransformOrigin(
                    if (isMine) 1f else 0f,
                    if (placementHolder.belowAnchor) 0f else 1f,
                )
                scaleX = 0.94f + 0.06f * appearScale.value
                scaleY = 0.94f + 0.06f * appearScale.value
                alpha = appearAlpha.value
            },
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        ) {
            ReactionBar(onReact = onReact, onMoreEmoji = onMoreEmoji)
            Spacer(Modifier.height(6.dp))
            MessageMenu(
                message = message,
                onCopy = onCopy,
                onReply = onReply,
                onEdit = onEdit,
                onUnsend = onUnsend,
            )
        }
    }
}

/**
 * Emoji keep their 26dp size at any font scale: they are decorative glyphs, and
 * scaling them with text clips them out of their 48dp target.
 */
@Composable
private fun EmojiGlyph(emoji: String) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density)) {
        Text(text = emoji, fontSize = 26.sp, maxLines = 1)
    }
}

@Composable
private fun ReactionBar(
    onReact: (String) -> Unit,
    onMoreEmoji: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    // One slot per quick emoji plus the "more" button; each reveals a beat
    // after the one before it so the bar unfolds rather than appearing whole.
    val entrance = remember { List(QuickReactions.size + 1) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        entrance.forEachIndexed { index, reveal ->
            launch {
                delay(index * 28L)
                reveal.animateTo(1f, motion.defaultSpatialSpec())
            }
        }
    }
    fun Modifier.reveal(index: Int, pressScale: State<Float>): Modifier = graphicsLayer {
        val progress = entrance[index].value
        val scale = (0.8f + 0.2f * progress) * pressScale.value
        scaleX = scale
        scaleY = scale
        alpha = progress
    }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuickReactions.forEachIndexed { index, emoji ->
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                val pressScale = animateFloatAsState(
                    targetValue = if (pressed) 1.25f else 1f,
                    animationSpec = motion.fastSpatialSpec(),
                    label = "reactionPress",
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .reveal(index, pressScale)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = interaction,
                            indication = LocalIndication.current,
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.chat_react_with, emoji),
                        ) {
                            haptic.confirm()
                            onReact(emoji)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    EmojiGlyph(emoji)
                }
            }
            val moreInteraction = remember { MutableInteractionSource() }
            val morePressed by moreInteraction.collectIsPressedAsState()
            val morePressScale = animateFloatAsState(
                targetValue = if (morePressed) 1.25f else 1f,
                animationSpec = motion.fastSpatialSpec(),
                label = "reactionMorePress",
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .reveal(QuickReactions.size, morePressScale)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = moreInteraction,
                        indication = LocalIndication.current,
                        role = Role.Button,
                        onClick = onMoreEmoji,
                    ),
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

private data class MenuActionItem(
    val icon: ImageVector,
    val label: Int,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageMenu(
    message: ChatMessage,
    onCopy: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onUnsend: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val primaryItems = buildList {
        if (message.canReply()) {
            add(
                MenuActionItem(
                    icon = Icons.AutoMirrored.Rounded.Reply,
                    label = R.string.chat_action_reply,
                    onClick = onReply,
                ),
            )
        }
        if (message.text.isNotBlank()) {
            add(
                MenuActionItem(
                    icon = Icons.Rounded.ContentCopy,
                    label = R.string.chat_action_copy,
                    onClick = {
                        haptic.contextClick()
                        onCopy()
                    },
                ),
            )
        }
        if (message.canEdit()) {
            add(
                MenuActionItem(
                    icon = Icons.Rounded.Edit,
                    label = R.string.chat_action_edit,
                    onClick = onEdit,
                ),
            )
        }
    }
    val destructiveItems = buildList {
        if (message.canUnsend()) {
            add(
                MenuActionItem(
                    icon = Icons.Rounded.Delete,
                    label = R.string.chat_action_unsend,
                    destructive = true,
                    onClick = onUnsend,
                ),
            )
        }
    }
    val groups = listOf(primaryItems, destructiveItems).filter { it.isNotEmpty() }
    if (groups.isEmpty()) return

    Column(
        modifier = Modifier.widthIn(min = 210.dp, max = 280.dp),
        verticalArrangement = Arrangement.spacedBy(MenuDefaults.GroupSpacing),
    ) {
        groups.forEachIndexed { groupIndex, items ->
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(groupIndex, groups.size),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp,
            ) {
                items.forEachIndexed { itemIndex, item ->
                    val colors = if (item.destructive) {
                        MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        MenuDefaults.itemColors()
                    }
                    DropdownMenuItem(
                        onClick = item.onClick,
                        text = { Text(stringResource(item.label)) },
                        shape = MenuDefaults.itemShape(itemIndex, items.size).shape,
                        leadingIcon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            )
                        },
                        colors = colors,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UnsendMessageDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.chat_unsend_title)) },
        text = { Text(stringResource(R.string.chat_unsend_body)) },
        confirmButton = {
            Button(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    haptic.reject()
                    onConfirm()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.chat_action_unsend))
            }
        },
        dismissButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = onDismiss,
            ) {
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

/**
 * Zero-height status text: it stays out of the layout but keeps its live
 * region in the semantics tree, so screen readers announce the change.
 */
@Composable
private fun LiveAnnouncement(text: String) {
    if (text.isEmpty()) return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .height(0.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun TypingBubble(
    senders: List<ConversationParticipant>,
    modifier: Modifier = Modifier,
) {
    val bubbles = LocalMessageBubbleColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (senders.isNotEmpty()) {
            TypingAvatars(senders.take(MaxTypingAvatars))
            Spacer(Modifier.width(SenderAvatarGap))
        }
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

/** Pictures of everyone typing, overlapping so the group still gets one bubble. */
@Composable
private fun TypingAvatars(senders: List<ConversationParticipant>) {
    val overlap = SenderAvatarSize / 4
    val ring = MaterialTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .width(SenderAvatarSize + overlap * (senders.size - 1))
            .height(SenderAvatarSize),
    ) {
        senders.forEachIndexed { index, sender ->
            Avatar(
                initials = sender.initials,
                size = SenderAvatarSize,
                modifier = Modifier
                    .offset(x = overlap * index)
                    .border(1.5.dp, ring, CircleShape),
                imageUrl = sender.avatarUrl,
                colorSeed = sender.fbid.toString(),
            )
        }
    }
}

@Composable
private fun TypingDots(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val bounce = with(LocalDensity.current) { 3.dp.toPx() }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            val wave by transition.animateFloat(
                initialValue = 0f,
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
                    .graphicsLayer {
                        // Read in the layer so the wave only redraws, never recomposes.
                        alpha = 0.25f + 0.75f * wave
                        translationY = -bounce * wave
                    }
                    .background(color, CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                LoadingIndicator(
                    modifier = Modifier.size(20.dp),
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

/** Centered plain time divider shown after a 20-minute same-day gap. */
@Composable
private fun TimeGapHeader(timestampMs: Long) {
    val label = remember(timestampMs) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestampMs))
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** An IME commits one image per keyboard GIF or sticker. */
private fun ClipData.firstUri(): Uri? =
    (0 until itemCount).firstNotNullOfOrNull { getItemAt(it).uri }

/**
 * AnimatedContent keeps the leaving child composed and hit-testable until its
 * exit ends. Swallow that child's pointers in the initial pass, before
 * clickable() looks for an unconsumed down, and drop its semantics so the
 * vanishing control is neither tapped nor read.
 */
private fun Modifier.inputOnlyWhen(active: Boolean): Modifier =
    if (active) {
        this
    } else {
        this
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
            .clearAndSetSemantics { }
    }

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MessageComposer(
    draft: TextFieldState,
    onSend: () -> Unit,
    onCommitMedia: (Uri) -> Unit,
    replyingTo: ReplySnapshot?,
    replyingToLabel: String,
    replyingToThumbnail: ReplyThumbnail?,
    onCancelReply: () -> Unit,
    editingMessage: ChatMessage?,
    onCancelEdit: () -> Unit,
    focusRequester: FocusRequester,
    onCamera: () -> Unit,
    onPickMedia: () -> Unit,
    stagedMedia: List<PickedMedia>,
    onRemoveStagedMedia: (PickedMedia) -> Unit,
    onMoveStagedMedia: (from: Int, to: Int) -> Unit,
    recorder: VoiceRecorder,
    locked: Boolean,
    cancelCandidate: Boolean,
    onMicStart: () -> Unit,
    onMicMove: (Boolean, Boolean) -> Unit,
    onMicRelease: (Boolean, Boolean) -> Unit,
    onRecordingSend: () -> Unit,
    onRecordingDelete: () -> Unit,
    attachmentsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val showSend = draft.text.isNotBlank() || stagedMedia.isNotEmpty()
    val editing = editingMessage != null
    val showActions = attachmentsEnabled && !editing
    val recording = recorder.isRecording
    val bubbles = LocalMessageBubbleColors.current
    val motion = MaterialTheme.motionScheme
    val replyArgs = replyingTo?.let { Triple(it, replyingToLabel, replyingToThumbnail) }
    // The bars take non-null arguments, so the last values are kept for the
    // frame the exit animation still draws.
    var shownReply by remember { mutableStateOf(replyArgs) }
    LaunchedEffect(replyArgs) { if (replyArgs != null) shownReply = replyArgs }
    var shownEdit by remember { mutableStateOf(editingMessage) }
    LaunchedEffect(editingMessage) { if (editingMessage != null) shownEdit = editingMessage }
    var shownStagedMedia by remember { mutableStateOf(stagedMedia) }
    LaunchedEffect(stagedMedia) {
        if (stagedMedia.isNotEmpty()) shownStagedMedia = stagedMedia
    }
    // Keyboard GIFs and stickers arrive through commitContent, which
    // BasicTextField hands over as an image URI.
    val haptic = LocalHapticFeedback.current
    val commitMedia = rememberUpdatedState(onCommitMedia)
    val contentReceiver = remember {
        ReceiveContentListener { content ->
            val uri = content.clipEntry.clipData.firstUri()
            if (uri == null) {
                content
            } else {
                commitMedia.value(uri)
                content.consume { true }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        AnimatedContent(
            targetState = recording && locked,
            transitionSpec = {
                val enter = fadeIn(motion.defaultEffectsSpec()) +
                    scaleIn(motion.defaultSpatialSpec(), initialScale = 0.94f)
                val exit = fadeOut(motion.defaultEffectsSpec()) +
                    scaleOut(motion.defaultSpatialSpec(), targetScale = 0.94f)
                (enter togetherWith exit).using(SizeTransform(clip = false))
            },
            label = "composerRecording",
        ) { lockedRecording ->
            val isTarget = transition.targetState == EnterExitState.Visible
            if (lockedRecording) {
                LockedRecordingBar(
                    recorder = recorder,
                    onDelete = onRecordingDelete,
                    onSend = onRecordingSend,
                    modifier = Modifier.inputOnlyWhen(isTarget),
                )
            } else {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .inputOnlyWhen(isTarget),
                ) {
                    AnimatedVisibility(
                        visible = editingMessage != null,
                        enter = expandVertically(motion.defaultSpatialSpec()) +
                            fadeIn(motion.defaultEffectsSpec()),
                        exit = shrinkVertically(motion.defaultSpatialSpec()) +
                            fadeOut(motion.defaultEffectsSpec()),
                    ) {
                        val message = editingMessage ?: shownEdit ?: return@AnimatedVisibility
                        EditComposerBar(message = message, onCancel = onCancelEdit)
                    }
                    AnimatedVisibility(
                        visible = replyingTo != null,
                        enter = expandVertically(motion.defaultSpatialSpec()) +
                            fadeIn(motion.defaultEffectsSpec()),
                        exit = shrinkVertically(motion.defaultSpatialSpec()) +
                            fadeOut(motion.defaultEffectsSpec()),
                    ) {
                        val shown = replyArgs ?: shownReply
                        val (reply, label, thumbnail) = shown ?: return@AnimatedVisibility
                        ReplyComposerBar(
                            reply = reply,
                            label = label,
                            thumbnail = thumbnail,
                            onCancel = onCancelReply,
                        )
                    }
                    AnimatedVisibility(
                        visible = stagedMedia.isNotEmpty() && !editing,
                        enter = expandVertically(motion.defaultSpatialSpec()) +
                            fadeIn(motion.defaultEffectsSpec()),
                        exit = shrinkVertically(motion.defaultSpatialSpec()) +
                            fadeOut(motion.defaultEffectsSpec()),
                    ) {
                        if (shownStagedMedia.isEmpty()) return@AnimatedVisibility
                        StagedMediaStrip(
                            media = shownStagedMedia,
                            onRemove = onRemoveStagedMedia,
                            onMove = onMoveStagedMedia,
                        )
                    }
                    Row(
                        modifier = Modifier
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
                                    .heightIn(min = 48.dp)
                                    .animateContentSize(animationSpec = motion.defaultSpatialSpec())
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AnimatedVisibility(
                                    visible = showActions && !recording,
                                    enter = fadeIn(motion.fastEffectsSpec()) +
                                        expandHorizontally(motion.fastSpatialSpec()) +
                                        scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f),
                                    exit = fadeOut(motion.fastEffectsSpec()) +
                                        shrinkHorizontally(motion.fastSpatialSpec()) +
                                        scaleOut(motion.fastSpatialSpec(), targetScale = 0.8f),
                                ) {
                                    val actionActive =
                                        transition.targetState == EnterExitState.Visible
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = {
                                                haptic.segmentTick()
                                                onCamera()
                                            },
                                            shapes = IconButtonDefaults.shapes(),
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            ),
                                            modifier = Modifier
                                                .size(44.dp)
                                                .inputOnlyWhen(actionActive),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PhotoCamera,
                                                contentDescription = stringResource(
                                                    R.string.chat_attach_camera,
                                                ),
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                        Spacer(Modifier.width(4.dp))
                                    }
                                }

                                AnimatedContent(
                                    targetState = recording,
                                    modifier = Modifier.weight(1f),
                                    transitionSpec = {
                                        (fadeIn(motion.fastEffectsSpec()) togetherWith
                                            fadeOut(motion.fastEffectsSpec()))
                                            .using(SizeTransform(clip = false))
                                    },
                                    contentAlignment = Alignment.CenterStart,
                                    label = "composerInput",
                                ) { activeRecording ->
                                    val isInputTarget =
                                        transition.targetState == EnterExitState.Visible
                                    if (activeRecording) {
                                        RecordingStatus(
                                            recorder = recorder,
                                            cancelCandidate = cancelCandidate,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .inputOnlyWhen(isInputTarget)
                                                .padding(start = 12.dp, end = 8.dp),
                                        )
                                    } else {
                                        BasicTextField(
                                            state = draft,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .inputOnlyWhen(isInputTarget)
                                                .focusRequester(focusRequester)
                                                .then(
                                                    if (attachmentsEnabled && !editing) {
                                                        Modifier.contentReceiver(contentReceiver)
                                                    } else {
                                                        Modifier
                                                    },
                                                )
                                                .padding(
                                                    start = if (showActions) 0.dp else 12.dp,
                                                    end = 8.dp,
                                                ),
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
                                                    if (draft.text.isEmpty()) {
                                                        Text(
                                                            text = stringResource(
                                                                R.string.chat_message_hint,
                                                            ),
                                                            style = MaterialTheme.typography.bodyLarge,
                                                            color = MaterialTheme.colorScheme
                                                                .onSurfaceVariant,
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

                                AnimatedVisibility(
                                    visible = showActions && !recording,
                                    enter = fadeIn(motion.fastEffectsSpec()) +
                                        expandHorizontally(motion.fastSpatialSpec()) +
                                        scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f),
                                    exit = fadeOut(motion.fastEffectsSpec()) +
                                        shrinkHorizontally(motion.fastSpatialSpec()) +
                                        scaleOut(motion.fastSpatialSpec(), targetScale = 0.8f),
                                ) {
                                    val actionActive =
                                        transition.targetState == EnterExitState.Visible
                                    IconButton(
                                        onClick = {
                                            haptic.segmentTick()
                                            onPickMedia()
                                        },
                                        shapes = IconButtonDefaults.shapes(),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                        modifier = Modifier
                                            .size(44.dp)
                                            .inputOnlyWhen(actionActive),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Image,
                                            contentDescription = stringResource(
                                                R.string.chat_attach_gallery,
                                            ),
                                            modifier = Modifier.size(22.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        AnimatedContent(
                            targetState = showSend || !attachmentsEnabled || editing,
                            transitionSpec = {
                                val enter = fadeIn(motion.fastEffectsSpec()) +
                                    scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f)
                                val exit = fadeOut(motion.fastEffectsSpec()) +
                                    scaleOut(motion.fastSpatialSpec(), targetScale = 0.8f)
                                (enter togetherWith exit).using(SizeTransform(clip = false))
                            },
                            contentKey = { it },
                            label = "composerAction",
                        ) { send ->
                            val isTarget = transition.targetState == EnterExitState.Visible
                            if (send) {
                                if (editingMessage != null) {
                                    FilledIconButton(
                                        onClick = onSend,
                                        enabled = draft.text.isNotBlank(),
                                        shapes = IconButtonDefaults.shapes(),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .inputOnlyWhen(isTarget),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = bubbles.composerAccent,
                                            contentColor = bubbles.onComposerAccent,
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = stringResource(
                                                R.string.chat_edit_save,
                                            ),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                } else {
                                    FilledIconButton(
                                        onClick = onSend,
                                        shapes = IconButtonDefaults.shapes(),
                                        modifier = Modifier
                                            .size(48.dp)
                                            .inputOnlyWhen(isTarget),
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = bubbles.composerAccent,
                                            contentColor = bubbles.onComposerAccent,
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.Send,
                                            contentDescription = stringResource(R.string.chat_send),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            } else {
                                MicButton(
                                    recording = recording,
                                    enabled = isTarget,
                                    onStart = onMicStart,
                                    onToggle = { if (recording) onRecordingSend() else onMicStart() },
                                    onMove = onMicMove,
                                    onRelease = onMicRelease,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Picked attachments waiting for Send; long-press a thumb to drag it into place. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StagedMediaStrip(
    media: List<PickedMedia>,
    onRemove: (PickedMedia) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val motion = MaterialTheme.motionScheme
    val stepPx = with(density) { (STAGED_MEDIA_SIZE + STAGED_MEDIA_GAP).toPx() }
    val edgePx = with(density) { STAGED_MEDIA_EDGE.toPx() }
    val moveLeft = stringResource(R.string.chat_staged_move_left)
    val moveRight = stringResource(R.string.chat_staged_move_right)
    var draggedIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    var scrollDelta by remember { mutableFloatStateOf(0f) }
    val currentLastIndex by rememberUpdatedState(media.lastIndex)

    // Holding a thumb against the strip's edge keeps scrolling the row. The
    // offset follows the scroll so the thumb stays under the finger and the
    // drop target keeps advancing with it.
    fun stopAutoScroll() {
        scrollJob?.cancel()
        scrollJob = null
        scrollDelta = 0f
    }

    fun autoScroll(delta: Float) {
        if (scrollJob?.isActive == true && scrollDelta == delta) return
        stopAutoScroll()
        scrollDelta = delta
        scrollJob = scope.launch {
            while (true) {
                val consumed = listState.dispatchRawDelta(delta)
                if (consumed == 0f) break
                dragOffset += consumed
                delay(16)
            }
        }
    }

    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(STAGED_MEDIA_GAP),
    ) {
        itemsIndexed(media, key = { _, picked -> picked.uri.toString() }) { index, picked ->
            val dragging = index == draggedIndex
            val dragScale by animateFloatAsState(
                targetValue = if (dragging) 1.08f else 1f,
                animationSpec = motion.fastSpatialSpec(),
                label = "stagedDragScale",
            )
            Box(
                modifier = Modifier
                    .animateItem(
                        fadeInSpec = motion.fastEffectsSpec(),
                        placementSpec = motion.defaultSpatialSpec(),
                        fadeOutSpec = motion.fastEffectsSpec(),
                    )
                    .size(STAGED_MEDIA_SIZE)
                    .zIndex(if (dragging) 1f else 0f)
                    .offset {
                        IntOffset(if (dragging) dragOffset.roundToInt() else 0, 0)
                    }
                    .graphicsLayer {
                        scaleX = dragScale
                        scaleY = dragScale
                    }
                    .pointerInput(picked.uri, index) {
                        var lastDragTarget = index
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggedIndex = index
                                dragOffset = 0f
                                lastDragTarget = index
                                stopAutoScroll()
                                haptic.longPress()
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.x
                                val nextTarget = stagedDragTarget(
                                    index = index,
                                    offsetPx = dragOffset,
                                    stepPx = stepPx,
                                    lastIndex = currentLastIndex,
                                )
                                if (nextTarget != lastDragTarget) {
                                    lastDragTarget = nextTarget
                                    haptic.segmentTick()
                                }
                                val info = listState.layoutInfo
                                val item = info.visibleItemsInfo.firstOrNull { it.index == index }
                                if (item != null && info.viewportSize.width > 0) {
                                    val center = item.offset + item.size / 2f + dragOffset
                                    when {
                                        center < edgePx -> autoScroll(-stepPx / 4f)
                                        center > info.viewportSize.width - edgePx ->
                                            autoScroll(stepPx / 4f)
                                        else -> stopAutoScroll()
                                    }
                                } else {
                                    stopAutoScroll()
                                }
                            },
                            onDragEnd = {
                                stopAutoScroll()
                                val target = stagedDragTarget(
                                    index = index,
                                    offsetPx = dragOffset,
                                    stepPx = stepPx,
                                    lastIndex = currentLastIndex,
                                )
                                draggedIndex = -1
                                dragOffset = 0f
                                onMove(index, target)
                            },
                            onDragCancel = {
                                stopAutoScroll()
                                draggedIndex = -1
                                dragOffset = 0f
                            },
                        )
                    }
                    .semantics {
                        contentDescription = picked.fileName
                        customActions = listOf(
                            CustomAccessibilityAction(moveLeft) {
                                if (index > 0) onMove(index, index - 1)
                                true
                            },
                            CustomAccessibilityAction(moveRight) {
                                if (index < media.lastIndex) onMove(index, index + 1)
                                true
                            },
                        )
                    },
            ) {
                val thumbShape = RoundedCornerShape(14.dp)
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = thumbShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    when (picked.kind) {
                        "image" -> {
                            AsyncImage(
                                model = picked.uri,
                                contentDescription = null,
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
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Videocam,
                                        contentDescription = null,
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
                        else -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Audiotrack,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .clickable(role = Role.Button) {
                            haptic.segmentTick()
                            onRemove(picked)
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.chat_staged_remove),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact quote above the composer. It repeats the quoted author's cue from the
 * transcript: the accent leading bar and label when the viewer wrote the quoted
 * message, a neutral bar when the other person did.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReplyComposerBar(
    reply: ReplySnapshot,
    label: String,
    thumbnail: ReplyThumbnail?,
    onCancel: () -> Unit,
) {
    val cue = if (reply.isFromViewer) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(cue),
            )
            Spacer(Modifier.width(10.dp))
            if (thumbnail != null) {
                ReplyThumbnailBox(thumbnail = thumbnail, size = 36.dp)
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = cue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = replyContent(reply),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onCancel,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.chat_reply_cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Edit bar above the composer. The edited text lives in the composer field, so
 * this only names the action and repeats the original for context.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditComposerBar(
    message: ChatMessage,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_edit_title),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onCancel,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.chat_edit_cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
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
    enabled: Boolean = true,
) {
    val lockThreshold = with(LocalDensity.current) { LOCK_DISTANCE.toPx() }
    val cancelThreshold = with(LocalDensity.current) { CANCEL_DISTANCE.toPx() }
    var lockProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(recording) {
        if (!recording) lockProgress = 0f
    }
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
    val motion = MaterialTheme.motionScheme
    val circleScale = animateFloatAsState(
        targetValue = if (recording) 1.15f else 1f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "micScale",
    )
    val cornerPercent by animateIntAsState(
        targetValue = if (recording) 32 else 50,
        animationSpec = motion.fastSpatialSpec(),
        label = "micCorner",
    )
    val containerColor by animateColorAsState(
        targetValue = if (recording) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            bubbles.composerAccent
        },
        animationSpec = motion.fastEffectsSpec(),
        label = "micContainer",
    )
    val iconTint by animateColorAsState(
        targetValue = if (recording) {
            MaterialTheme.colorScheme.error
        } else {
            bubbles.onComposerAccent
        },
        animationSpec = motion.fastEffectsSpec(),
        label = "micTint",
    )
    val interaction = if (enabled) {
        Modifier
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
                        lockProgress = if (locked) 1f else (-dy / lockThreshold).coerceIn(0f, 1f)
                        move.value(locked, cancelled)
                        if (!change.pressed) break
                    }
                    lockProgress = 0f
                    release.value(locked, cancelled)
                }
            }
    } else {
        Modifier.clearAndSetSemantics { }
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .then(interaction),
        contentAlignment = Alignment.Center,
    ) {
        if (recording) {
            val popupOffsetY = with(LocalDensity.current) { (-72).dp.roundToPx() }
            val lockReached = lockProgress >= 1f
            val pillScale by animateFloatAsState(
                targetValue = if (lockReached) 1.12f else 1f,
                animationSpec = motion.fastSpatialSpec(),
                label = "lockPillScale",
            )
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, popupOffsetY),
                properties = PopupProperties(focusable = false, clippingEnabled = false),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (lockReached) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (lockReached) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shadowElevation = 4.dp,
                    modifier = Modifier.graphicsLayer {
                        translationY = -12.dp.toPx() * lockProgress
                        scaleX = pillScale
                        scaleY = pillScale
                    },
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = if (lockReached) {
                                Icons.Rounded.Lock
                            } else {
                                Icons.Rounded.LockOpen
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .graphicsLayer {
                    scaleX = circleScale.value
                    scaleY = circleScale.value
                }
                .clip(RoundedCornerShape(percent = cornerPercent))
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun RecordingDot(paused: Boolean) {
    // A paused recording keeps the dot still, so no clock runs.
    val pulse = if (paused) {
        null
    } else {
        rememberInfiniteTransition(label = "recordingDot").animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "recordingDotAlpha",
        )
    }
    Box(
        modifier = Modifier
            .size(8.dp)
            .graphicsLayer { alpha = pulse?.value ?: 1f }
            .clip(CircleShape)
            .background(
                if (paused) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            ),
    )
}

@Composable
private fun RecordingStatus(
    recorder: VoiceRecorder,
    cancelCandidate: Boolean,
    modifier: Modifier = Modifier,
) {
    val paused = recorder.state == VoiceRecordingState.Paused
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordingDot(paused = paused)
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatMediaDuration(recorder.elapsedMs.toInt()),
            style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LockedRecordingBar(
    recorder: VoiceRecorder,
    onDelete: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paused = recorder.state == VoiceRecordingState.Paused
    val haptic = LocalHapticFeedback.current
    val bubbles = LocalMessageBubbleColors.current
    val controlDescription = stringResource(
        if (paused) R.string.chat_recording_resume else R.string.chat_recording_pause,
    )
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onDelete,
            shapes = IconButtonDefaults.shapes(),
        ) {
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
                RecordingDot(paused = paused)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatMediaDuration(recorder.elapsedMs.toInt()),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
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
        Spacer(Modifier.width(6.dp))
        FilledTonalIconToggleButton(
            checked = !paused,
            onCheckedChange = {
                haptic.toggle(paused)
                if (paused) recorder.resume() else recorder.pause()
            },
            shapes = IconToggleButtonShapes(
                shape = CircleShape,
                pressedShape = RoundedCornerShape(10.dp),
                checkedShape = RoundedCornerShape(12.dp),
            ),
            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = controlDescription },
        ) {
            VoicePlayPauseIcon(
                playing = !paused,
                loading = false,
                failed = false,
                tint = LocalContentColor.current,
                iconSize = 22.dp,
            )
        }
        Spacer(Modifier.width(6.dp))
        FilledIconButton(
            onClick = onSend,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = bubbles.composerAccent,
                contentColor = bubbles.onComposerAccent,
            ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = stringResource(R.string.chat_recording_send),
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
        val previewContext = LocalContext.current
        val player = remember { VoicePlayer(previewContext) }
        ChatScreen(
            conversation = sampleConversations().first(),
            isTyping = false,
            onSend = { _, _ -> },
            onBack = {},
            voicePlayer = player,
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
        val previewContext = LocalContext.current
        val player = remember { VoicePlayer(previewContext) }
        ChatScreen(
            conversation = sampleConversations().first(),
            isTyping = true,
            onSend = { _, _ -> },
            onBack = {},
            voicePlayer = player,
        )
    }
}
