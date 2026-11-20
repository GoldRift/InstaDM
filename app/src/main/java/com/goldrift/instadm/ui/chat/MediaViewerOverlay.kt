package com.goldrift.instadm.ui.chat

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberMuteButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPlaybackSpeedState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.goldrift.instadm.R
import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.ui.components.segmentFrequentTick
import com.goldrift.instadm.ui.components.segmentTick
import com.goldrift.instadm.ui.components.thresholdActivated
import com.goldrift.instadm.ui.components.toggle
import com.goldrift.instadm.ui.theme.InstaDMTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.time.Duration.Companion.milliseconds

/** Attachment group and message context shown in the full-screen viewer. */
data class MediaViewerRequest(
    val threadId: String,
    val messageId: String,
    val items: List<MessageMedia>,
    val initialIndex: Int = 0,
    val senderName: String = "",
    val timestamp: String = "",
)

/**
 * Full-screen in-window media viewer for photos, stickers, GIFs, videos, and
 * reels. Runs inside the host window rather than a separate Dialog so vertical
 * swipe-to-dismiss and system predictive back reveal the live chat underneath.
 */
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaViewerOverlay(
    request: MediaViewerRequest?,
    loadMedia: MediaLoader?,
    voicePlayer: VoicePlayer,
    dynamicColor: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shownRequest by remember { mutableStateOf<MediaViewerRequest?>(null) }
    var openGeneration by remember { mutableIntStateOf(0) }
    var wasOpen by remember { mutableStateOf(false) }
    if (request != null) {
        if (!wasOpen) openGeneration++
        wasOpen = true
        shownRequest = request
    } else {
        wasOpen = false
    }

    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = request != null,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn(motion.fastEffectsSpec()) +
            scaleIn(initialScale = 0.94f, animationSpec = motion.defaultSpatialSpec()),
        exit = fadeOut(motion.fastEffectsSpec()) +
            scaleOut(targetScale = 0.94f, animationSpec = motion.fastSpatialSpec()),
        label = "mediaViewerOverlay",
    ) {
        val active = shownRequest ?: return@AnimatedVisibility
        key(openGeneration) {
            InstaDMTheme(darkTheme = true, dynamicColor = dynamicColor) {
                MediaViewerContent(
                    request = active,
                    loadMedia = loadMedia,
                    voicePlayer = voicePlayer,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaViewerContent(
    request: MediaViewerRequest,
    loadMedia: MediaLoader?,
    voicePlayer: VoicePlayer,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme

    // Force light-on-dark system bar icons over the dark viewer regardless of app theme.
    DisposableEffect(view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousLightStatus = controller?.isAppearanceLightStatusBars
        val previousLightNav = controller?.isAppearanceLightNavigationBars
        if (controller != null) {
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
        onDispose {
            if (controller != null) {
                if (previousLightStatus != null) {
                    controller.isAppearanceLightStatusBars = previousLightStatus
                }
                if (previousLightNav != null) {
                    controller.isAppearanceLightNavigationBars = previousLightNav
                }
            }
        }
    }

    val safeInitial = request.initialIndex.coerceIn(0, (request.items.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeInitial,
        pageCount = { request.items.size },
    )
    val currentMedia = request.items.getOrElse(pagerState.currentPage) { request.items.first() }

    var chromeVisible by remember(request) { mutableStateOf(true) }
    var activeZoomScale by remember(request) { mutableFloatStateOf(1f) }
    var currentReadyFile by remember(request, pagerState.currentPage) { mutableStateOf<File?>(null) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(pagerState.settledPage) {
        activeZoomScale = 1f
    }

    // Predictive back gesture state.
    val navigationState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = navigationState,
        isBackEnabled = true,
        onBackCompleted = onDismiss,
    )
    val backEvent = (navigationState.transitionState as? NavigationEventTransitionState.InProgress)
        ?.latestEvent
    val rawBackProgress = backEvent?.progress ?: 0f
    val backEdge = backEvent?.swipeEdge ?: NavigationEvent.EDGE_LEFT
    val backProgress by animateFloatAsState(
        targetValue = rawBackProgress,
        animationSpec = motion.fastSpatialSpec(),
        label = "viewerBackProgress",
    )

    // Vertical swipe-to-dismiss state.
    val dragOffsetY = remember(request) { Animatable(0f) }
    var dismissing by remember(request) { mutableStateOf(false) }

    val latestZoom by rememberUpdatedState(activeZoomScale)
    val latestOnDismiss by rememberUpdatedState(onDismiss)

    val containerHeightPx = containerSize.height.toFloat().coerceAtLeast(1f)
    val containerWidthPx = containerSize.width.toFloat().coerceAtLeast(1f)
    val dragTransform = mediaDismissTransform(
        offsetPx = dragOffsetY.value,
        containerHeightPx = containerHeightPx,
    )

    val backScale = 1f - 0.14f * backProgress
    val backShiftX = (if (backEdge == NavigationEvent.EDGE_LEFT) 1f else -1f) *
        backProgress * (containerWidthPx * 0.08f)
    val backCornerDp = 28f * backProgress
    val backBackdropAlpha = (1f - 0.65f * backProgress).coerceIn(0f, 1f)
    val backChromeAlpha = (1f - backProgress * 4f).coerceIn(0f, 1f)

    val combinedScale = (dragTransform.scale * backScale).coerceIn(0.72f, 1f)
    val combinedCornerDp = maxOf(dragTransform.cornerDp, backCornerDp).dp
    val combinedBackdropAlpha = (dragTransform.backdropAlpha * backBackdropAlpha).coerceIn(0f, 1f)
    val combinedChromeAlpha = (dragTransform.chromeAlpha * backChromeAlpha).coerceIn(0f, 1f)

    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    val flingVelocityPx = with(density) { 900.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            // Absorb stray touches so nothing reaches the underlying chat screen.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { chromeVisible = !chromeVisible })
            },
    ) {
        // Dark tonal backdrop that fades smoothly to reveal the live chat behind.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = combinedBackdropAlpha }
                .background(ViewerBackdropColor),
        )

        // Media canvas with vertical swipe-to-dismiss and predictive back transform.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = backShiftX
                    translationY = dragOffsetY.value
                    scaleX = combinedScale
                    scaleY = combinedScale
                    clip = combinedCornerDp > 0.dp
                    shape = RoundedCornerShape(combinedCornerDp)
                }
                .pointerInput(request, dismissThresholdPx, flingVelocityPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (dismissing || !canSwipeToDismiss(latestZoom)) return@awaitEachGesture
                        val touchSlop = viewConfiguration.touchSlop
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        var totalDx = 0f
                        var totalDy = 0f
                        var lockedVertical = false
                        var armedHaptic = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val activeChanges = event.changes.filter { it.pressed }
                            if (activeChanges.isEmpty()) break
                            if (activeChanges.size > 1 || !canSwipeToDismiss(latestZoom)) {
                                // Yield immediately to multi-touch pinch or zoomed pan.
                                if (lockedVertical) {
                                    scope.launch {
                                        dragOffsetY.animateTo(0f, motion.defaultSpatialSpec())
                                    }
                                }
                                return@awaitEachGesture
                            }
                            val change = activeChanges.firstOrNull { it.id == down.id }
                                ?: activeChanges.first()
                            if (change.isConsumed) {
                                if (lockedVertical) {
                                    scope.launch {
                                        dragOffsetY.animateTo(0f, motion.defaultSpatialSpec())
                                    }
                                }
                                return@awaitEachGesture
                            }
                            val delta = change.positionChange()
                            if (!lockedVertical) {
                                totalDx += delta.x
                                totalDy += delta.y
                                val absDx = abs(totalDx)
                                val absDy = abs(totalDy)
                                if (absDx > touchSlop || absDy > touchSlop) {
                                    if (absDy > absDx * 1.15f) {
                                        lockedVertical = true
                                    } else {
                                        // Let HorizontalPager or system edge gestures handle horizontal swipes.
                                        return@awaitEachGesture
                                    }
                                }
                            }
                            if (lockedVertical) {
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val nextOffset = dragOffsetY.value + delta.y
                                if (!armedHaptic && abs(nextOffset) >= dismissThresholdPx) {
                                    armedHaptic = true
                                    haptic.thresholdActivated()
                                }
                                scope.launch { dragOffsetY.snapTo(nextOffset) }
                            }
                        }

                        if (lockedVertical) {
                            val velocityY = velocityTracker.calculateVelocity().y
                            val currentOffset = dragOffsetY.value
                            if (
                                shouldDismissMedia(
                                    offsetPx = currentOffset,
                                    velocityPxPerSec = velocityY,
                                    thresholdPx = dismissThresholdPx,
                                    flingVelocityPxPerSec = flingVelocityPx,
                                )
                            ) {
                                dismissing = true
                                val direction = if (currentOffset != 0f) {
                                    sign(currentOffset)
                                } else if (velocityY != 0f) {
                                    sign(velocityY)
                                } else {
                                    1f
                                }
                                scope.launch {
                                    dragOffsetY.animateTo(
                                        targetValue = direction * containerHeightPx * 0.65f,
                                        animationSpec = motion.fastSpatialSpec(),
                                    )
                                    latestOnDismiss()
                                }
                            } else {
                                scope.launch {
                                    dragOffsetY.animateTo(0f, motion.defaultSpatialSpec())
                                }
                            }
                        }
                    }
                },
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = request.items.size > 1 && canSwipeToDismiss(activeZoomScale),
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { pageIndex ->
                val item = request.items[pageIndex]
                val isCurrentPage = pagerState.currentPage == pageIndex
                MediaViewerPage(
                    threadId = request.threadId,
                    messageId = request.messageId,
                    media = item,
                    isActivePage = isCurrentPage,
                    chromeVisible = chromeVisible,
                    chromeAlpha = combinedChromeAlpha,
                    loadMedia = loadMedia,
                    voicePlayer = voicePlayer,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                    onSetChromeVisible = { chromeVisible = it },
                    onZoomChange = { zoom ->
                        if (isCurrentPage) activeZoomScale = zoom
                    },
                    onReadyFile = { file ->
                        if (isCurrentPage) currentReadyFile = file
                    },
                    onShare = { file ->
                        shareMediaFile(context, file, item)
                    },
                    pageCount = request.items.size,
                    pageIndex = pageIndex,
                )
            }
        }

        // Top floating expressive header (Close, sender/time/type metadata, Share / Open in IG).
        AnimatedVisibility(
            visible = chromeVisible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .alpha(combinedChromeAlpha),
            enter = fadeIn(motion.fastEffectsSpec()) +
                slideInVertically(motion.fastSpatialSpec()) { -it / 2 },
            exit = fadeOut(motion.fastEffectsSpec()) +
                slideOutVertically(motion.fastSpatialSpec()) { -it / 2 },
            label = "viewerTopBar",
        ) {
            ViewerTopBar(
                senderName = request.senderName,
                timestamp = request.timestamp,
                media = currentMedia,
                pageIndex = pagerState.currentPage,
                pageCount = request.items.size,
                readyFile = currentReadyFile,
                onClose = onDismiss,
                onShare = { file -> shareMediaFile(context, file, currentMedia) },
            )
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ViewerTopBar(
    senderName: String,
    timestamp: String,
    media: MessageMedia,
    pageIndex: Int,
    pageCount: Int,
    readyFile: File?,
    onClose: () -> Unit,
    onShare: (File) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val shareUrl = media.shareUrl
    val canOpenExternal = media.isReel && isOpenableLink(shareUrl)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.72f),
                        Color.Black.copy(alpha = 0.28f),
                        Color.Transparent,
                    ),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilledTonalIconButton(
                onClick = onClose,
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.chat_media_close),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                if (senderName.isNotBlank()) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (timestamp.isNotBlank()) {
                        Text(
                            text = timestamp,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                        )
                    }
                    val badge = when {
                        media.isReel -> stringResource(R.string.chat_reel_badge)
                        media.type == "gif" -> stringResource(R.string.media_viewer_gif_badge)
                        else -> null
                    }
                    if (badge != null) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    if (pageCount > 1) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.media_viewer_page,
                                    pageIndex + 1,
                                    pageCount,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }

            if (canOpenExternal) {
                FilledTonalIconButton(
                    onClick = {
                        runCatching { uriHandler.openUri(shareUrl) }
                            .onFailure {
                                Toast.makeText(
                                    context,
                                    R.string.chat_link_error,
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                    },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.media_viewer_open_instagram),
                    )
                }
            }

            if (readyFile != null) {
                FilledTonalIconButton(
                    onClick = { onShare(readyFile) },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.media_viewer_share),
                    )
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaViewerPage(
    threadId: String,
    messageId: String,
    media: MessageMedia,
    isActivePage: Boolean,
    chromeVisible: Boolean,
    chromeAlpha: Float,
    loadMedia: MediaLoader?,
    voicePlayer: VoicePlayer,
    onToggleChrome: () -> Unit,
    onSetChromeVisible: (Boolean) -> Unit,
    onZoomChange: (Float) -> Unit,
    onReadyFile: (File?) -> Unit,
    onShare: (File) -> Unit,
    pageCount: Int,
    pageIndex: Int,
) {
    var retryToken by remember(messageId, media.partIndex) { mutableIntStateOf(0) }
    var state by remember(messageId, media.partIndex) {
        mutableStateOf<ViewerLoadState>(ViewerLoadState.Loading)
    }

    LaunchedEffect(messageId, media.partIndex, retryToken) {
        state = ViewerLoadState.Loading
        onReadyFile(null)
        val loader = loadMedia
        if (loader == null) {
            state = ViewerLoadState.Failed
            return@LaunchedEffect
        }
        state = runCatching {
            loader(threadId, messageId, media)
        }.fold(
            onSuccess = { ViewerLoadState.Ready(it) },
            onFailure = { ViewerLoadState.Failed },
        )
    }

    LaunchedEffect(state, isActivePage) {
        if (isActivePage) {
            onReadyFile((state as? ViewerLoadState.Ready)?.file)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            ViewerLoadState.Loading -> {
                MediaLoadingPlaceholder(media = media)
            }

            ViewerLoadState.Failed -> {
                MediaErrorCard(
                    message = stringResource(
                        if (media.isReel) R.string.chat_reel_error else R.string.chat_media_error,
                    ),
                    onRetry = { retryToken++ },
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is ViewerLoadState.Ready -> {
                if (media.isVideo) {
                    ExpressiveVideoPlayer(
                        file = current.file,
                        media = media,
                        isActivePage = isActivePage,
                        chromeVisible = chromeVisible,
                        chromeAlpha = chromeAlpha,
                        voicePlayer = voicePlayer,
                        onToggleChrome = onToggleChrome,
                        onSetChromeVisible = onSetChromeVisible,
                        onShare = { onShare(current.file) },
                    )
                } else {
                    ZoomableImagePage(
                        file = current.file,
                        media = media,
                        isActivePage = isActivePage,
                        chromeVisible = chromeVisible,
                        chromeAlpha = chromeAlpha,
                        pageIndex = pageIndex,
                        pageCount = pageCount,
                        onToggleChrome = onToggleChrome,
                        onZoomChange = onZoomChange,
                    )
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MediaLoadingPlaceholder(media: MessageMedia) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (media.thumbnailUrl.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(media.thumbnailUrl)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.45f),
            )
        }
        ContainedLoadingIndicator(modifier = Modifier.size(56.dp))
    }
}

@Composable
private fun MediaErrorCard(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 320.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.BrokenImage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(34.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRetry,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.media_viewer_retry))
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ZoomableImagePage(
    file: File,
    media: MessageMedia,
    isActivePage: Boolean,
    chromeVisible: Boolean,
    chromeAlpha: Float,
    pageIndex: Int,
    pageCount: Int,
    onToggleChrome: () -> Unit,
    onZoomChange: (Float) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    val scale = remember(file) { Animatable(1f) }
    val offsetX = remember(file) { Animatable(0f) }
    val offsetY = remember(file) { Animatable(0f) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(isActivePage) {
        if (!isActivePage && scale.value != 1f) {
            scale.snapTo(1f)
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
        }
    }

    LaunchedEffect(scale.value) {
        onZoomChange(scale.value)
    }

    val contentDescription = media.altText.ifBlank {
        stringResource(R.string.chat_media_photo)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .pointerInput(file) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = { tap ->
                        val target = doubleTapZoomTarget(
                            currentScale = scale.value,
                            tapX = tap.x,
                            tapY = tap.y,
                            viewportWidth = viewportSize.width.toFloat(),
                            viewportHeight = viewportSize.height.toFloat(),
                        )
                        scope.launch {
                            launch { scale.animateTo(target.scale, motion.defaultSpatialSpec()) }
                            launch { offsetX.animateTo(target.offsetX, motion.defaultSpatialSpec()) }
                            launch { offsetY.animateTo(target.offsetY, motion.defaultSpatialSpec()) }
                        }
                    },
                )
            }
            .pointerInput(file) {
                // Only consume multi-touch pinch or zoomed panning so unzoomed
                // single-finger drags stay free for vertical dismiss and paging.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var hadPinch = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break

                        val zoomChange = if (pressed.size >= 2) event.calculateZoom() else 1f
                        val panChange = event.calculatePan()
                        val zoomedIn = !canSwipeToDismiss(scale.value)

                        if (pressed.size >= 2 || zoomedIn) {
                            if (pressed.size >= 2) hadPinch = true
                            val nextScale = clampImageScale(scale.value * zoomChange)
                            val centroid = event.calculateCentroid(useCurrent = false)
                            val width = viewportSize.width.toFloat()
                            val height = viewportSize.height.toFloat()
                            val scaleFactor = if (scale.value > 0f) nextScale / scale.value else 1f
                            val rawX = if (pressed.size >= 2 && centroid != Offset.Unspecified) {
                                (offsetX.value + panChange.x) * scaleFactor +
                                    (width / 2f - centroid.x) * (scaleFactor - 1f)
                            } else {
                                offsetX.value + panChange.x
                            }
                            val rawY = if (pressed.size >= 2 && centroid != Offset.Unspecified) {
                                (offsetY.value + panChange.y) * scaleFactor +
                                    (height / 2f - centroid.y) * (scaleFactor - 1f)
                            } else {
                                offsetY.value + panChange.y
                            }
                            val (clampedX, clampedY) = clampImagePan(
                                offsetX = rawX,
                                offsetY = rawY,
                                scale = nextScale,
                                viewportWidth = width,
                                viewportHeight = height,
                            )
                            scope.launch {
                                scale.snapTo(nextScale)
                                offsetX.snapTo(clampedX)
                                offsetY.snapTo(clampedY)
                            }
                            event.changes.forEach { change ->
                                if (change.positionChange() != Offset.Zero) change.consume()
                            }
                        }
                    }
                    if (hadPinch && canSwipeToDismiss(scale.value)) {
                        scope.launch {
                            launch { scale.animateTo(1f, motion.fastSpatialSpec()) }
                            launch { offsetX.animateTo(0f, motion.fastSpatialSpec()) }
                            launch { offsetY.animateTo(0f, motion.fastSpatialSpec()) }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file)
                .crossfade(false)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offsetX.value
                    translationY = offsetY.value
                },
            loading = { MediaLoadingPlaceholder(media = media) },
            error = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.chat_media_error),
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            },
        )

        if (pageCount > 1) {
            AnimatedVisibility(
                visible = chromeVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
                    .alpha(chromeAlpha),
                enter = fadeIn(motion.fastEffectsSpec()) +
                    slideInVertically(motion.fastSpatialSpec()) { it / 2 },
                exit = fadeOut(motion.fastEffectsSpec()) +
                    slideOutVertically(motion.fastSpatialSpec()) { it / 2 },
                label = "viewerPageDots",
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(pageCount) { index ->
                            val selected = index == pageIndex
                            Box(
                                modifier = Modifier
                                    .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ExpressiveVideoPlayer(
    file: File,
    media: MessageMedia,
    isActivePage: Boolean,
    chromeVisible: Boolean,
    chromeAlpha: Float,
    voicePlayer: VoicePlayer,
    onToggleChrome: () -> Unit,
    onSetChromeVisible: (Boolean) -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val isGif = media.type == "gif"
    val loop = isGif || media.isReel

    val exoPlayer = remember(file) {
        ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ !isGif,
            )
            .build()
            .apply {
                repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                if (isGif) volume = 0f
                setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                prepare()
            }
    }

    var isBuffering by remember(exoPlayer) { mutableStateOf(true) }
    var isEnded by remember(exoPlayer) { mutableStateOf(false) }
    var playbackError by remember(exoPlayer) { mutableStateOf(false) }
    var isScrubbing by remember(exoPlayer) { mutableStateOf(false) }
    var scrubPositionMs by remember(exoPlayer) { mutableStateOf<Long?>(null) }
    var interactionTick by remember(exoPlayer) { mutableIntStateOf(0) }
    var seekFeedback by remember(exoPlayer) { mutableStateOf<SeekFeedback?>(null) }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                isEnded = playbackState == Player.STATE_ENDED
                if (playbackState == Player.STATE_READY) playbackError = false
                if (playbackState == Player.STATE_ENDED) onSetChromeVisible(true)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && !isGif && exoPlayer.volume > 0f) {
                    voicePlayer.pause()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = true
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, isActivePage) {
        exoPlayer.playWhenReady = isActivePage
        if (!isActivePage) exoPlayer.pause()
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val playPauseState = rememberPlayPauseButtonState(exoPlayer)
    val muteState = rememberMuteButtonState(exoPlayer)
    val speedState = rememberPlaybackSpeedState(exoPlayer)
    val progressState = rememberProgressStateWithTickInterval(exoPlayer, tickIntervalMs = 100L)

    val isPlaying = !playPauseState.showPlay
    val durationMs = when {
        progressState.durationMs != C.TIME_UNSET && progressState.durationMs > 0L ->
            progressState.durationMs
        media.durationMs > 0 -> media.durationMs.toLong()
        else -> 0L
    }
    val displayPositionMs = (scrubPositionMs ?: progressState.currentPositionMs)
        .coerceIn(0L, durationMs.coerceAtLeast(1L))

    // Auto-hide controls during steady playback; stay pinned when paused, buffering, or scrubbing.
    LaunchedEffect(chromeVisible, isPlaying, isScrubbing, isBuffering, interactionTick) {
        if (
            shouldAutoHidePlayerChrome(
                chromeVisible = chromeVisible,
                isVideo = true,
                isPlaying = isPlaying,
                isScrubbing = isScrubbing,
                isBuffering = isBuffering,
            )
        ) {
            delay(CONTROLS_AUTO_HIDE_MS.milliseconds)
            onSetChromeVisible(false)
        }
    }

    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(650.milliseconds)
            seekFeedback = null
        }
    }

    fun seekBy(deltaMs: Long) {
        if (durationMs <= 0L) return
        val target = (exoPlayer.currentPosition + deltaMs).coerceIn(0L, durationMs)
        exoPlayer.seekTo(target)
        haptic.segmentTick()
        interactionTick++
        seekFeedback = SeekFeedback(forward = deltaMs > 0L, token = interactionTick)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(exoPlayer, isGif) {
                detectTapGestures(
                    onTap = {
                        interactionTick++
                        onToggleChrome()
                    },
                    onDoubleTap = { tap ->
                        if (isGif || durationMs <= 0L) {
                            playPauseState.onClick()
                            haptic.toggle(playPauseState.showPlay)
                            return@detectTapGestures
                        }
                        val third = size.width / 3f
                        when {
                            tap.x < third -> seekBy(-SEEK_STEP_MS)
                            tap.x > third * 2f -> seekBy(SEEK_STEP_MS)
                            else -> {
                                playPauseState.onClick()
                                haptic.toggle(playPauseState.showPlay)
                                interactionTick++
                            }
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        ContentFrame(
            player = exoPlayer,
            modifier = Modifier.fillMaxSize(),
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = ContentScale.Fit,
            shutter = { MediaLoadingPlaceholder(media = media) },
        )

        if (isBuffering && !playbackError) {
            ContainedLoadingIndicator(modifier = Modifier.size(56.dp))
        }

        if (playbackError) {
            MediaErrorCard(
                message = stringResource(
                    if (media.isReel) R.string.chat_reel_error else R.string.chat_media_error,
                ),
                onRetry = {
                    playbackError = false
                    isBuffering = true
                    exoPlayer.prepare()
                    exoPlayer.play()
                },
            )
        }

        // Double-tap seek feedback pill.
        AnimatedVisibility(
            visible = seekFeedback != null,
            modifier = Modifier
                .align(
                    if (seekFeedback?.forward == true) {
                        Alignment.CenterEnd
                    } else {
                        Alignment.CenterStart
                    },
                )
                .padding(horizontal = 28.dp),
            enter = fadeIn(motion.fastEffectsSpec()) +
                scaleIn(initialScale = 0.8f, animationSpec = motion.fastSpatialSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) +
                scaleOut(targetScale = 0.85f, animationSpec = motion.fastSpatialSpec()),
            label = "videoSeekFeedback",
        ) {
            val forward = seekFeedback?.forward ?: true
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = if (forward) Icons.Rounded.Forward5 else Icons.Rounded.Replay5,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        text = if (forward) "+5s" else "-5s",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }

        // Bottom expressive transport dock.
        AnimatedVisibility(
            visible = chromeVisible && !playbackError,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .alpha(chromeAlpha),
            enter = fadeIn(motion.fastEffectsSpec()) +
                slideInVertically(motion.defaultSpatialSpec()) { it / 2 },
            exit = fadeOut(motion.fastEffectsSpec()) +
                slideOutVertically(motion.fastSpatialSpec()) { it / 2 },
            label = "viewerVideoControls",
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.45f),
                                Color.Black.copy(alpha = 0.78f),
                            ),
                        ),
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isGif) {
                    GifTransportPill(
                        isPlaying = isPlaying,
                        onTogglePlay = {
                            haptic.toggle(!isPlaying)
                            playPauseState.onClick()
                            interactionTick++
                        },
                        onShare = onShare,
                    )
                } else {
                    VideoTransportDock(
                        isPlaying = isPlaying,
                        isEnded = isEnded,
                        isMuted = muteState.showMuted,
                        playbackSpeed = speedState.playbackSpeed,
                        positionMs = displayPositionMs,
                        durationMs = durationMs,
                        onTogglePlay = {
                            if (isEnded) {
                                exoPlayer.seekTo(0L)
                                exoPlayer.play()
                                haptic.toggle(true)
                            } else {
                                haptic.toggle(!isPlaying)
                                playPauseState.onClick()
                            }
                            interactionTick++
                        },
                        onSeekBack = { seekBy(-SEEK_STEP_MS) },
                        onSeekForward = { seekBy(SEEK_STEP_MS) },
                        onToggleMute = {
                            val nextMuted = !muteState.showMuted
                            haptic.toggle(!nextMuted)
                            muteState.onClick()
                            if (!nextMuted && isPlaying) voicePlayer.pause()
                            interactionTick++
                        },
                        onCycleSpeed = {
                            val next = nextPlaybackSpeed(
                                speedState.playbackSpeed,
                                VideoPlaybackSpeeds,
                            )
                            speedState.updatePlaybackSpeed(next)
                            haptic.segmentTick()
                            interactionTick++
                        },
                        onScrubChange = { fraction ->
                            isScrubbing = true
                            scrubPositionMs = (durationMs * fraction.coerceIn(0f, 1f)).toLong()
                            interactionTick++
                        },
                        onScrubFinish = {
                            val target = scrubPositionMs
                            if (target != null) {
                                exoPlayer.seekTo(target)
                            }
                            scrubPositionMs = null
                            isScrubbing = false
                            interactionTick++
                        },
                    )
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GifTransportPill(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(
        modifier = Modifier.pointerInput(Unit) {
            // Keep vertical drags on transport controls from pulling the media into dismiss.
            detectVerticalDragGestures { change, _ -> change.consume() }
        },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MorphingPlayPauseButton(
                isPlaying = isPlaying,
                isEnded = false,
                onClick = onTogglePlay,
                size = 44.dp,
            )
            Text(
                text = stringResource(R.string.media_viewer_gif_badge),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            FilledTonalIconButton(
                onClick = onShare,
                shape = CircleShape,
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = stringResource(R.string.media_viewer_share),
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun VideoTransportDock(
    isPlaying: Boolean,
    isEnded: Boolean,
    isMuted: Boolean,
    playbackSpeed: Float,
    positionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onToggleMute: () -> Unit,
    onCycleSpeed: () -> Unit,
    onScrubChange: (Float) -> Unit,
    onScrubFinish: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme
    val scrubberLabel = stringResource(R.string.media_viewer_scrubber)
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val sliderInteraction = remember { MutableInteractionSource() }
    val sliderState = remember { SliderState(value = progress) }
    var lastTickStep by remember { mutableIntStateOf(-1) }

    LaunchedEffect(progress) {
        if (lastTickStep == -1) {
            sliderState.value = progress
        }
    }

    val muteCorner by animateFloatAsState(
        targetValue = if (isMuted) 30f else 50f,
        animationSpec = motion.fastSpatialSpec(),
        label = "muteCorner",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp)
            .pointerInput(Unit) {
                // Keep vertical drags on the scrubber and controls from triggering dismiss.
                detectVerticalDragGestures { change, _ -> change.consume() }
            },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (durationMs > 0L) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = formatMediaDuration(positionMs.toInt()),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        state = sliderState,
                        onValueChange = { fraction ->
                            sliderState.value = fraction
                            val step = scrubTickStep(fraction, steps = 24)
                            if (step != lastTickStep) {
                                lastTickStep = step
                                haptic.segmentFrequentTick()
                            }
                            onScrubChange(fraction)
                        },
                        onValueChangeFinished = {
                            lastTickStep = -1
                            onScrubFinish()
                        },
                        interactionSource = sliderInteraction,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = scrubberLabel },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        ),
                    )
                    Text(
                        text = formatMediaDuration(durationMs.toInt()),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFeatureSettings = "tnum",
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                FilledTonalIconToggleButton(
                    checked = isMuted,
                    onCheckedChange = { onToggleMute() },
                    shape = RoundedCornerShape(percent = muteCorner.roundToInt()),
                    colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = if (isMuted) {
                            Icons.AutoMirrored.Rounded.VolumeOff
                        } else {
                            Icons.AutoMirrored.Rounded.VolumeUp
                        },
                        contentDescription = stringResource(
                            if (isMuted) R.string.media_viewer_unmute else R.string.media_viewer_mute,
                        ),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalIconButton(
                        onClick = onSeekBack,
                        enabled = durationMs > 0L,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay5,
                            contentDescription = stringResource(R.string.media_viewer_seek_back),
                        )
                    }

                    MorphingPlayPauseButton(
                        isPlaying = isPlaying,
                        isEnded = isEnded,
                        onClick = onTogglePlay,
                        size = 56.dp,
                    )

                    FilledTonalIconButton(
                        onClick = onSeekForward,
                        enabled = durationMs > 0L,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forward5,
                            contentDescription = stringResource(R.string.media_viewer_seek_forward),
                        )
                    }
                }

                val speedLabel = formatPlaybackSpeed(playbackSpeed)
                val speedDescription = stringResource(R.string.media_viewer_speed, speedLabel)
                val speedActive = abs(playbackSpeed - 1f) > 0.01f
                Surface(
                    onClick = onCycleSpeed,
                    shape = RoundedCornerShape(percent = if (speedActive) 34 else 50),
                    color = if (speedActive) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (speedActive) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .height(44.dp)
                        .widthIn(min = 52.dp)
                        .semantics { contentDescription = speedDescription },
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = speedLabel,
                            transitionSpec = {
                                (scaleIn(initialScale = 0.8f, animationSpec = motion.fastSpatialSpec()) +
                                    fadeIn(motion.fastEffectsSpec()))
                                    .togetherWith(
                                        scaleOut(targetScale = 0.8f, animationSpec = motion.fastSpatialSpec()) +
                                            fadeOut(motion.fastEffectsSpec()),
                                    )
                            },
                            label = "videoSpeedLabel",
                        ) { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontFeatureSettings = "tnum",
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingPlayPauseButton(
    isPlaying: Boolean,
    isEnded: Boolean,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
) {
    val motion = MaterialTheme.motionScheme
    val cornerPercent by animateFloatAsState(
        targetValue = if (isPlaying) 28f else 50f,
        animationSpec = motion.fastSpatialSpec(),
        label = "videoPlayCorner",
    )
    val actionLabel = stringResource(
        when {
            isEnded -> R.string.media_viewer_replay
            isPlaying -> R.string.media_viewer_pause
            else -> R.string.media_viewer_play
        },
    )
    val iconState = when {
        isEnded -> PlayGlyphState.Replay
        isPlaying -> PlayGlyphState.Pause
        else -> PlayGlyphState.Play
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = cornerPercent.roundToInt()),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .size(size)
            .semantics { contentDescription = actionLabel },
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = iconState,
                transitionSpec = {
                    (scaleIn(initialScale = 0.65f, animationSpec = motion.fastSpatialSpec()) +
                        fadeIn(motion.fastEffectsSpec()))
                        .togetherWith(
                            scaleOut(targetScale = 0.65f, animationSpec = motion.fastSpatialSpec()) +
                                fadeOut(motion.fastEffectsSpec()),
                        )
                },
                label = "videoPlayPauseGlyph",
            ) { glyph ->
                Icon(
                    imageVector = when (glyph) {
                        PlayGlyphState.Play -> Icons.Rounded.PlayArrow
                        PlayGlyphState.Pause -> Icons.Rounded.Pause
                        PlayGlyphState.Replay -> Icons.Rounded.Replay
                    },
                    contentDescription = null,
                    modifier = Modifier.size(if (size >= 52.dp) 30.dp else 24.dp),
                )
            }
        }
    }
}

private enum class PlayGlyphState { Play, Pause, Replay }

private data class SeekFeedback(
    val forward: Boolean,
    val token: Int,
)

private sealed interface ViewerLoadState {
    data object Loading : ViewerLoadState
    data class Ready(val file: File) : ViewerLoadState
    data object Failed : ViewerLoadState
}

private fun shareMediaFile(
    context: Context,
    file: File,
    media: MessageMedia,
) {
    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val mime = when {
            media.isVideo -> "video/mp4"
            media.isSticker -> "image/webp"
            else -> "image/*"
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.media_viewer_share)),
        )
    }.onFailure {
        Toast.makeText(context, R.string.media_viewer_share_error, Toast.LENGTH_SHORT).show()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val ViewerBackdropColor = Color(0xFF0A0C10)
private const val CONTROLS_AUTO_HIDE_MS = 3_000L
private const val SEEK_STEP_MS = 5_000L
