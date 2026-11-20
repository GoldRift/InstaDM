package com.goldrift.instadm.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.viewfinder.core.FocusState
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashAuto
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.IconToggleButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil.compose.AsyncImage
import com.goldrift.instadm.R
import com.goldrift.instadm.data.CameraCapture
import com.goldrift.instadm.data.CaptureTarget
import com.goldrift.instadm.data.MediaPolicy
import com.goldrift.instadm.ui.components.confirm
import com.goldrift.instadm.ui.components.longPress
import com.goldrift.instadm.ui.components.segmentTick
import com.goldrift.instadm.ui.components.thresholdActivated
import com.goldrift.instadm.ui.components.toggle
import com.goldrift.instadm.ui.theme.InstaDMTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sign

/** Active request that opens the full-screen in-app CameraX overlay. */
data class CameraCaptureRequest(
    val onCaptured: (Uri) -> Unit,
    val onOpenPicker: () -> Unit,
)

private data class CapturedPreview(
    val target: CaptureTarget,
    val isVideo: Boolean,
)

private data class FocusIndicatorState(
    val offset: Offset,
    val locked: Boolean,
    val token: Int,
)

/**
 * Full-screen in-window CameraX capture overlay with Material 3 Expressive
 * controls, tap-to-focus, pinch-to-zoom, photo and video capture, review, and
 * predictive-back / swipe-to-dismiss transitions over the live chat.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CameraCaptureOverlay(
    request: CameraCaptureRequest?,
    voicePlayer: VoicePlayer,
    dynamicColor: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var shownRequest by remember { mutableStateOf<CameraCaptureRequest?>(null) }
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
        label = "cameraCaptureOverlay",
    ) {
        val active = shownRequest ?: return@AnimatedVisibility
        key(openGeneration) {
            InstaDMTheme(darkTheme = true, dynamicColor = dynamicColor) {
                CameraCaptureContent(
                    request = active,
                    voicePlayer = voicePlayer,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CameraCaptureContent(
    request: CameraCaptureRequest,
    voicePlayer: VoicePlayer,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val motion = MaterialTheme.motionScheme

    DisposableEffect(view) {
        val window = context.findCameraActivity()?.window
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

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permissionRequested by remember { mutableStateOf(false) }

    // Re-check permission if the user returns from system settings.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequested = true
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission && !permissionRequested) {
            permissionRequested = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var mode by remember { mutableStateOf(CameraCaptureMode.Photo) }
    var flashMode by remember { mutableStateOf(CameraFlashMode.Auto) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var lensSpinTarget by remember { mutableFloatStateOf(0f) }
    var hasBackCamera by remember { mutableStateOf(true) }
    var hasFrontCamera by remember { mutableStateOf(false) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf(false) }

    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var activeVideoTarget by remember { mutableStateOf<CaptureTarget?>(null) }
    var recordingDurationMs by remember { mutableLongStateOf(0L) }
    var takingPhoto by remember { mutableStateOf(false) }
    var shutterBlinkAlpha by remember { mutableFloatStateOf(0f) }
    var captured by remember { mutableStateOf<CapturedPreview?>(null) }

    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1f) }
    var focusIndicator by remember { mutableStateOf<FocusIndicatorState?>(null) }
    var focusToken by remember { mutableIntStateOf(0) }
    var pendingVideoAfterMicPrompt by remember { mutableStateOf(false) }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val dragOffsetY = remember { Animatable(0f) }
    var draggingDismiss by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }

    val isRecording = activeRecording != null
    val canDismissByGesture = !isRecording && captured == null && !dismissing

    fun discardCaptured() {
        captured?.target?.file?.let { file -> runCatching { file.delete() } }
        captured = null
    }

    fun closeOverlay(openPickerAfter: Boolean = false) {
        if (dismissing) return
        dismissing = true
        activeRecording?.stop()
        activeRecording = null
        discardCaptured()
        onDismiss()
        if (openPickerAfter) {
            request.onOpenPicker()
        }
    }

    fun startVideoRecording(withAudio: Boolean) {
        val capture = videoCapture ?: return
        if (activeRecording != null) return
        voicePlayer.pause()
        val target = CameraCapture.newVideoTarget(context)
        activeVideoTarget = target
        recordingDurationMs = 0L
        val outputOptions = FileOutputOptions.Builder(target.file)
            .setFileSizeLimit((MediaPolicy.MAX_MEDIA_BYTES - 512L * 1024L).coerceAtLeast(1024L * 1024L))
            .build()
        var pending = capture.output.prepareRecording(context, outputOptions)
        if (withAudio) {
            pending = pending.withAudioEnabled()
        }
        haptic.longPress()
        activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    recordingDurationMs = 0L
                }
                is VideoRecordEvent.Status -> {
                    recordingDurationMs = event.recordingStats.recordedDurationNanos / 1_000_000L
                }
                is VideoRecordEvent.Finalize -> {
                    activeRecording = null
                    val finishedTarget = activeVideoTarget
                    activeVideoTarget = null
                    if (event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED) {
                        finishedTarget?.file?.let { runCatching { it.delete() } }
                        if (!dismissing) {
                            Toast.makeText(
                                context,
                                R.string.camera_capture_failed,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    } else if (finishedTarget != null && !dismissing) {
                        haptic.confirm()
                        captured = CapturedPreview(target = finishedTarget, isVideo = true)
                    } else {
                        finishedTarget?.file?.let { runCatching { it.delete() } }
                    }
                }
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (pendingVideoAfterMicPrompt) {
            pendingVideoAfterMicPrompt = false
            startVideoRecording(withAudio = granted)
        }
    }

    fun toggleVideoCapture() {
        val recording = activeRecording
        if (recording != null) {
            recording.stop()
            return
        }
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            startVideoRecording(withAudio = true)
        } else {
            pendingVideoAfterMicPrompt = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun takePhoto() {
        val capture = imageCapture ?: return
        if (takingPhoto) return
        takingPhoto = true
        haptic.confirm()
        val target = CameraCapture.newTarget(context)
        val isFront = lensFacing == CameraSelector.LENS_FACING_FRONT
        capture.flashMode = if (isFront) {
            if (flashMode == CameraFlashMode.On && capture.screenFlash != null) {
                ImageCapture.FLASH_MODE_SCREEN
            } else {
                ImageCapture.FLASH_MODE_OFF
            }
        } else {
            when (flashMode) {
                CameraFlashMode.Auto -> ImageCapture.FLASH_MODE_AUTO
                CameraFlashMode.On -> ImageCapture.FLASH_MODE_ON
                CameraFlashMode.Off -> ImageCapture.FLASH_MODE_OFF
            }
        }
        val metadata = ImageCapture.Metadata().apply {
            isReversedHorizontal = isFront
        }
        val outputOptions = ImageCapture.OutputFileOptions.Builder(target.file)
            .setMetadata(metadata)
            .build()
        scope.launch {
            shutterBlinkAlpha = 0.85f
            delay(80L)
            shutterBlinkAlpha = 0f
        }
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    takingPhoto = false
                    if (!dismissing) {
                        captured = CapturedPreview(target = target, isVideo = false)
                    } else {
                        runCatching { target.file.delete() }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    takingPhoto = false
                    runCatching { target.file.delete() }
                    Toast.makeText(
                        context,
                        R.string.camera_capture_failed,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )
    }

    // Bind CameraX whenever camera permission, lens, or review state changes.
    var singleUseCaseFallback by remember { mutableStateOf(false) }
    val bindModeKey = if (singleUseCaseFallback) mode else CameraCaptureMode.Photo

    LaunchedEffect(hasCameraPermission, lensFacing, captured == null, bindModeKey) {
        if (!hasCameraPermission || captured != null) {
            surfaceRequest = null
            boundCamera = null
            return@LaunchedEffect
        }
        cameraError = false
        val provider = runCatching { ProcessCameraProvider.awaitInstance(context) }.getOrNull()
        if (provider == null) {
            cameraError = true
            return@LaunchedEffect
        }
        hasBackCamera = runCatching {
            provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
        }.getOrDefault(false)
        hasFrontCamera = runCatching {
            provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        }.getOrDefault(false)

        val resolvedFacing = when {
            lensFacing == CameraSelector.LENS_FACING_BACK && hasBackCamera ->
                CameraSelector.LENS_FACING_BACK
            lensFacing == CameraSelector.LENS_FACING_FRONT && hasFrontCamera ->
                CameraSelector.LENS_FACING_FRONT
            hasBackCamera -> CameraSelector.LENS_FACING_BACK
            hasFrontCamera -> CameraSelector.LENS_FACING_FRONT
            else -> {
                cameraError = true
                return@LaunchedEffect
            }
        }
        if (resolvedFacing != lensFacing) {
            lensFacing = resolvedFacing
        }

        val selector = CameraSelector.Builder().requireLensFacing(resolvedFacing).build()
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { req -> surfaceRequest = req }
        }
        val imgCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(90)
            .build()
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                ),
            )
            .build()
        val vidCapture = VideoCapture.withOutput(recorder)

        provider.unbindAll()
        val camera = try {
            if (!singleUseCaseFallback) {
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imgCapture, vidCapture)
            } else if (mode == CameraCaptureMode.Video) {
                provider.bindToLifecycle(lifecycleOwner, selector, preview, vidCapture)
            } else {
                provider.bindToLifecycle(lifecycleOwner, selector, preview, imgCapture)
            }
        } catch (_: IllegalArgumentException) {
            // Some legacy or emulated camera HALs only allow two concurrent surfaces.
            singleUseCaseFallback = true
            provider.unbindAll()
            runCatching {
                if (mode == CameraCaptureMode.Video) {
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, vidCapture)
                } else {
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, imgCapture)
                }
            }.getOrNull()
        } catch (_: Exception) {
            null
        }

        if (camera == null) {
            cameraError = true
            return@LaunchedEffect
        }
        boundCamera = camera
        imageCapture = imgCapture
        videoCapture = vidCapture
        hasFlashUnit = camera.cameraInfo.hasFlashUnit()
    }

    DisposableEffect(boundCamera) {
        val cam = boundCamera
        if (cam == null) {
            onDispose {}
        } else {
            val observer = Observer<androidx.camera.core.ZoomState> { state ->
                zoomRatio = state.zoomRatio
                minZoomRatio = state.minZoomRatio
                maxZoomRatio = state.maxZoomRatio
            }
            cam.cameraInfo.zoomState.observeForever(observer)
            onDispose { cam.cameraInfo.zoomState.removeObserver(observer) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activeRecording?.stop()
            activeRecording = null
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    LaunchedEffect(focusIndicator?.token) {
        if (focusIndicator != null) {
            delay(1_100)
            focusIndicator = null
        }
    }

    // Predictive back: returns from review to viewfinder, stops active recording,
    // or scales the dark camera smoothly back into the chat.
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = true,
        onBackCompleted = {
            when {
                captured != null -> discardCaptured()
                isRecording -> activeRecording?.stop()
                else -> closeOverlay()
            }
        },
    )

    val predictiveBack = navEventState.transitionState as? NavigationEventTransitionState.InProgress
    val predictiveProgress = if (canDismissByGesture) {
        predictiveBack?.latestEvent?.progress?.coerceIn(0f, 1f) ?: 0f
    } else {
        0f
    }
    val predictiveEdge = predictiveBack?.latestEvent?.swipeEdge ?: NavigationEvent.EDGE_NONE

    val viewportHeight = viewportSize.height.toFloat().coerceAtLeast(1f)
    val viewportWidth = viewportSize.width.toFloat().coerceAtLeast(1f)
    val dragTransform = mediaDismissTransform(
        offsetPx = dragOffsetY.value,
        containerHeightPx = viewportHeight,
    )
    val backScale = 1f - 0.14f * predictiveProgress
    val backCornerDp = 28f * predictiveProgress
    val backBackdropAlpha = (1f - 0.65f * predictiveProgress).coerceIn(0f, 1f)
    val backdropAlpha = (dragTransform.backdropAlpha * backBackdropAlpha).coerceIn(0f, 1f)
    val contentScale = (dragTransform.scale * backScale).coerceIn(0.72f, 1f)
    val predictiveTranslateX = when (predictiveEdge) {
        NavigationEvent.EDGE_LEFT -> predictiveProgress * viewportWidth * 0.10f
        NavigationEvent.EDGE_RIGHT -> -predictiveProgress * viewportWidth * 0.10f
        else -> 0f
    }
    val predictiveTranslateY = predictiveProgress * viewportHeight * 0.04f
    val containerCorner = maxOf(dragTransform.cornerDp, backCornerDp).dp

    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    val dismissVelocityPx = with(density) { 750.dp.toPx() }

    val dismissGesture = Modifier.pointerInput(canDismissByGesture) {
        if (!canDismissByGesture) return@pointerInput
        val velocityTracker = VelocityTracker()
        var armed = false
        detectVerticalDragGestures(
            onDragStart = {
                draggingDismiss = true
                armed = false
                velocityTracker.resetTracking()
            },
            onVerticalDrag = { change, dragAmount ->
                // Only drag downward to dismiss so upward gestures stay out of the way.
                val next = (dragOffsetY.value + dragAmount).coerceAtLeast(0f)
                if (next > 0f || dragOffsetY.value > 0f) {
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    if (!armed && next >= dismissThresholdPx) {
                        armed = true
                        haptic.thresholdActivated()
                    }
                    scope.launch { dragOffsetY.snapTo(next) }
                }
            },
            onDragEnd = {
                draggingDismiss = false
                val velocityY = velocityTracker.calculateVelocity().y
                if (
                    shouldDismissDownward(
                        offsetPx = dragOffsetY.value,
                        velocityPxPerSec = velocityY,
                        thresholdPx = dismissThresholdPx,
                        flingVelocityPxPerSec = dismissVelocityPx,
                    )
                ) {
                    dismissing = true
                    scope.launch {
                        val exitTarget = sign(dragOffsetY.value.takeIf { it != 0f } ?: 1f) *
                            viewportHeight
                        dragOffsetY.animateTo(exitTarget, motion.fastSpatialSpec())
                        closeOverlay()
                    }
                } else {
                    scope.launch { dragOffsetY.animateTo(0f, motion.defaultSpatialSpec()) }
                }
            },
            onDragCancel = {
                draggingDismiss = false
                scope.launch { dragOffsetY.animateTo(0f, motion.defaultSpatialSpec()) }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .background(Color.Black.copy(alpha = backdropAlpha)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = predictiveTranslateX
                    translationY = dragOffsetY.value + predictiveTranslateY
                    scaleX = contentScale
                    scaleY = contentScale
                    clip = containerCorner > 0.dp
                    shape = RoundedCornerShape(containerCorner)
                }
                .background(Color.Black)
                .then(dismissGesture),
        ) {
            AnimatedContent(
                targetState = captured,
                transitionSpec = {
                    (fadeIn(motion.fastEffectsSpec()) +
                        scaleIn(initialScale = 0.96f, animationSpec = motion.defaultSpatialSpec())) togetherWith
                        fadeOut(motion.fastEffectsSpec())
                },
                label = "cameraStage",
            ) { preview ->
                if (preview != null) {
                    CameraReviewStage(
                        preview = preview,
                        onRetake = {
                            haptic.segmentTick()
                            discardCaptured()
                        },
                        onConfirm = {
                            haptic.confirm()
                            val uri = preview.target.uri
                            captured = null
                            dismissing = true
                            onDismiss()
                            request.onCaptured(uri)
                        },
                    )
                } else if (!hasCameraPermission) {
                    CameraPermissionStage(
                        permanentlyDenied = permissionRequested &&
                            context.findCameraActivity()?.let { activity ->
                                !ActivityCompat.shouldShowRequestPermissionRationale(
                                    activity,
                                    Manifest.permission.CAMERA,
                                )
                            } == true,
                        onRequestPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onOpenSettings = {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                            runCatching { context.startActivity(intent) }
                        },
                        onClose = { closeOverlay() },
                    )
                } else {
                    CameraLiveStage(
                        surfaceRequest = surfaceRequest,
                        cameraError = cameraError,
                        mode = mode,
                        flashMode = flashMode,
                        isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT,
                        showFlashButton = hasFlashUnit || lensFacing == CameraSelector.LENS_FACING_FRONT,
                        showSwitchLens = hasBackCamera && hasFrontCamera && !isRecording,
                        lensSpinTarget = lensSpinTarget,
                        isRecording = isRecording,
                        recordingDurationMs = recordingDurationMs,
                        takingPhoto = takingPhoto,
                        shutterBlinkAlpha = shutterBlinkAlpha,
                        zoomRatio = zoomRatio,
                        minZoomRatio = minZoomRatio,
                        maxZoomRatio = maxZoomRatio,
                        focusIndicator = focusIndicator,
                        onClose = {
                            haptic.segmentTick()
                            closeOverlay()
                        },
                        onCycleFlash = {
                            flashMode = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                if (flashMode == CameraFlashMode.On) CameraFlashMode.Off else CameraFlashMode.On
                            } else {
                                flashMode.next()
                            }
                            haptic.toggle(flashMode != CameraFlashMode.Off)
                        },
                        onSelectMode = { nextMode ->
                            if (!isRecording && mode != nextMode) {
                                mode = nextMode
                                haptic.segmentTick()
                            }
                        },
                        onShutter = {
                            when (mode) {
                                CameraCaptureMode.Photo -> takePhoto()
                                CameraCaptureMode.Video -> toggleVideoCapture()
                            }
                        },
                        onSwitchLens = {
                            if (!isRecording && hasBackCamera && hasFrontCamera) {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                                lensSpinTarget += 180f
                                haptic.segmentTick()
                            }
                        },
                        onOpenGallery = {
                            haptic.segmentTick()
                            closeOverlay(openPickerAfter = true)
                        },
                        onSelectZoom = { ratio ->
                            boundCamera?.cameraControl?.setZoomRatio(
                                ratio.coerceIn(minZoomRatio, maxZoomRatio),
                            )
                            haptic.segmentTick()
                        },
                        onZoomRatioChanged = { zoomRatio = it },
                        onTapToFocus = { offset, focusState ->
                            when (focusState) {
                                FocusState.STARTED -> {
                                    focusToken++
                                    focusIndicator = FocusIndicatorState(
                                        offset = offset,
                                        locked = false,
                                        token = focusToken,
                                    )
                                    haptic.segmentTick()
                                }
                                FocusState.FOCUSED -> {
                                    focusIndicator = focusIndicator?.copy(locked = true)
                                }
                                FocusState.NOT_FOCUSED, FocusState.FAILED -> {
                                    focusIndicator = null
                                }
                            }
                        },
                        onScreenFlashReady = { screenFlash ->
                            imageCapture?.screenFlash = screenFlash
                        },
                        onViewfinderRelease = {
                            imageCapture?.screenFlash = null
                        },
                    )
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CameraLiveStage(
    surfaceRequest: SurfaceRequest?,
    cameraError: Boolean,
    mode: CameraCaptureMode,
    flashMode: CameraFlashMode,
    isFrontCamera: Boolean,
    showFlashButton: Boolean,
    showSwitchLens: Boolean,
    lensSpinTarget: Float,
    isRecording: Boolean,
    recordingDurationMs: Long,
    takingPhoto: Boolean,
    shutterBlinkAlpha: Float,
    zoomRatio: Float,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    focusIndicator: FocusIndicatorState?,
    onClose: () -> Unit,
    onCycleFlash: () -> Unit,
    onSelectMode: (CameraCaptureMode) -> Unit,
    onShutter: () -> Unit,
    onSwitchLens: () -> Unit,
    onOpenGallery: () -> Unit,
    onSelectZoom: (Float) -> Unit,
    onZoomRatioChanged: (Float) -> Unit,
    onTapToFocus: (Offset, Int) -> Unit,
    onScreenFlashReady: (ImageCapture.ScreenFlash) -> Unit,
    onViewfinderRelease: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val buttonShapes = IconButtonShapes(
        shape = CircleShape,
        pressedShape = RoundedCornerShape(14.dp),
    )
    val presets = remember(minZoomRatio, maxZoomRatio) {
        zoomPresets(minZoomRatio, maxZoomRatio)
    }
    val activePresetIndex = nearestZoomPresetIndex(presets, zoomRatio)
    val lensRotation by animateFloatAsState(
        targetValue = lensSpinTarget,
        animationSpec = motion.defaultSpatialSpec(),
        label = "cameraLensFlip",
    )
    val animatedBlink by animateFloatAsState(
        targetValue = shutterBlinkAlpha,
        animationSpec = motion.fastEffectsSpec(),
        label = "shutterBlink",
    )

    Box(Modifier.fillMaxSize()) {
        // Live viewfinder fills the screen using EMBEDDED mode so rounded
        // predictive-back and drag-to-dismiss transforms clip cleanly.
        if (surfaceRequest != null) {
            CameraXViewfinder(
                surfaceRequest = surfaceRequest,
                modifier = Modifier.fillMaxSize(),
                implementationMode = ImplementationMode.EMBEDDED,
                contentScale = ContentScale.Crop,
                isTapToFocusEnabled = true,
                onTapToFocus = onTapToFocus,
                isPinchToZoomEnabled = true,
                onZoomRatioChanged = onZoomRatioChanged,
                onScreenFlashReady = onScreenFlashReady,
                onRelease = onViewfinderRelease,
            )
        } else if (cameraError) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = stringResource(R.string.chat_camera_unavailable),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        FilledTonalButton(
                            onClick = onOpenGallery,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(stringResource(R.string.camera_open_gallery))
                        }
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ContainedLoadingIndicator()
            }
        }

        // Brief shutter blink feedback when snapping a photo.
        if (animatedBlink > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = animatedBlink)),
            )
        }

        // Animated tap-to-focus ring.
        if (focusIndicator != null) {
            key(focusIndicator.token) {
                FocusRing(indicator = focusIndicator)
            }
        }

        // Top and bottom scrims keep floating controls legible over bright scenes.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.56f), Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                ),
        )

        // Top floating bar: Close, live recording badge, and Flash toggle.
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            FilledTonalIconButton(
                onClick = onClose,
                shapes = buttonShapes,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.48f),
                    contentColor = Color.White,
                ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.camera_close),
                )
            }

            AnimatedVisibility(
                visible = isRecording,
                enter = fadeIn(motion.fastEffectsSpec()) + scaleIn(
                    initialScale = 0.85f,
                    animationSpec = motion.fastSpatialSpec(),
                ),
                exit = fadeOut(motion.fastEffectsSpec()) + scaleOut(
                    targetScale = 0.85f,
                    animationSpec = motion.fastSpatialSpec(),
                ),
            ) {
                RecordingTimerPill(elapsedMs = recordingDurationMs)
            }

            if (showFlashButton) {
                val flashActive = flashMode != CameraFlashMode.Off
                val flashDescription = when {
                    isFrontCamera && flashActive -> stringResource(R.string.camera_flash_on)
                    isFrontCamera -> stringResource(R.string.camera_flash_off)
                    flashMode == CameraFlashMode.Auto -> stringResource(R.string.camera_flash_auto)
                    flashMode == CameraFlashMode.On -> stringResource(R.string.camera_flash_on)
                    else -> stringResource(R.string.camera_flash_off)
                }
                FilledTonalIconToggleButton(
                    checked = flashActive,
                    onCheckedChange = { onCycleFlash() },
                    shapes = IconToggleButtonShapes(
                        shape = CircleShape,
                        pressedShape = RoundedCornerShape(14.dp),
                        checkedShape = RoundedCornerShape(16.dp),
                    ),
                    colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.48f),
                        contentColor = Color.White,
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    AnimatedContent(
                        targetState = if (isFrontCamera && flashMode == CameraFlashMode.Auto) {
                            CameraFlashMode.Off
                        } else {
                            flashMode
                        },
                        transitionSpec = {
                            (fadeIn(motion.fastEffectsSpec()) + scaleIn(
                                initialScale = 0.8f,
                                animationSpec = motion.fastSpatialSpec(),
                            )) togetherWith (fadeOut(motion.fastEffectsSpec()) + scaleOut(
                                targetScale = 0.8f,
                                animationSpec = motion.fastSpatialSpec(),
                            ))
                        },
                        label = "flashModeIcon",
                    ) { activeFlash ->
                        Icon(
                            imageVector = when (activeFlash) {
                                CameraFlashMode.Auto -> Icons.Rounded.FlashAuto
                                CameraFlashMode.On -> Icons.Rounded.FlashOn
                                CameraFlashMode.Off -> Icons.Rounded.FlashOff
                            },
                            contentDescription = flashDescription,
                        )
                    }
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
        }

        // Bottom expressive camera dock: zoom presets, mode switcher, and capture bar.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (presets.isNotEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.48f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                ) {
                    ButtonGroup(
                        overflowIndicator = {},
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(3.dp),
                    ) {
                        presets.forEachIndexed { index, preset ->
                            val selected = index == activePresetIndex
                            val label = if (selected) {
                                formatZoomRatio(zoomRatio)
                            } else {
                                formatZoomRatio(preset)
                            }
                            customItem(
                                buttonGroupContent = {
                                    val interaction = remember { MutableInteractionSource() }
                                    ToggleButton(
                                        checked = selected,
                                        onCheckedChange = { onSelectZoom(preset) },
                                        interactionSource = interaction,
                                        shapes = ToggleButtonShapes(
                                            shape = CircleShape,
                                            pressedShape = RoundedCornerShape(10.dp),
                                            checkedShape = CircleShape,
                                        ),
                                        colors = ToggleButtonDefaults.colors(
                                            containerColor = Color.Transparent,
                                            contentColor = Color.White.copy(alpha = 0.85f),
                                            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier
                                            .animateWidth(interaction)
                                            .height(32.dp)
                                            .widthIn(min = 44.dp),
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                fontFeatureSettings = "tnum",
                                            ),
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            maxLines = 1,
                                        )
                                    }
                                },
                                menuContent = {},
                            )
                        }
                    }
                }
            }

            // Photo / Video expressive mode switcher.
            val photoLabel = stringResource(R.string.camera_mode_photo)
            val videoLabel = stringResource(R.string.camera_mode_video)
            AnimatedVisibility(
                visible = !isRecording,
                enter = fadeIn(motion.fastEffectsSpec()),
                exit = fadeOut(motion.fastEffectsSpec()),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.48f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                ) {
                    ButtonGroup(
                        overflowIndicator = {},
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(4.dp),
                    ) {
                        listOf(
                            CameraCaptureMode.Photo to photoLabel,
                            CameraCaptureMode.Video to videoLabel,
                        ).forEach { (itemMode, label) ->
                            val selected = mode == itemMode
                            customItem(
                                buttonGroupContent = {
                                    val interaction = remember { MutableInteractionSource() }
                                    ToggleButton(
                                        checked = selected,
                                        onCheckedChange = { onSelectMode(itemMode) },
                                        interactionSource = interaction,
                                        shapes = ToggleButtonShapes(
                                            shape = CircleShape,
                                            pressedShape = RoundedCornerShape(12.dp),
                                            checkedShape = CircleShape,
                                        ),
                                        colors = ToggleButtonDefaults.colors(
                                            containerColor = Color.Transparent,
                                            contentColor = Color.White.copy(alpha = 0.82f),
                                            checkedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            checkedContentColor = MaterialTheme.colorScheme.onSurface,
                                        ),
                                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                                        modifier = Modifier
                                            .animateWidth(interaction)
                                            .height(36.dp),
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                        )
                                    }
                                },
                                menuContent = {},
                            )
                        }
                    }
                }
            }

            // Capture bar: Gallery shortcut, morphing shutter button, and Flip camera.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (!isRecording) {
                    FilledTonalIconButton(
                        onClick = onOpenGallery,
                        shapes = buttonShapes,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.size(54.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = stringResource(R.string.camera_open_gallery),
                        )
                    }
                } else {
                    Spacer(Modifier.size(54.dp))
                }

                ShutterButton(
                    mode = mode,
                    isRecording = isRecording,
                    enabled = surfaceRequest != null && !takingPhoto,
                    onClick = onShutter,
                )

                if (showSwitchLens) {
                    FilledTonalIconButton(
                        onClick = onSwitchLens,
                        shapes = buttonShapes,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.size(54.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cameraswitch,
                            contentDescription = stringResource(R.string.camera_switch_lens),
                            modifier = Modifier.graphicsLayer { rotationZ = lensRotation },
                        )
                    }
                } else {
                    Spacer(Modifier.size(54.dp))
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShutterButton(
    mode: CameraCaptureMode,
    isRecording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val outerScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.92f
            isRecording -> 1.08f
            else -> 1f
        },
        animationSpec = motion.fastSpatialSpec(),
        label = "shutterOuterScale",
    )
    val innerSize by animateDpAsState(
        targetValue = when {
            isRecording -> 30.dp
            pressed -> 52.dp
            else -> 60.dp
        },
        animationSpec = motion.fastSpatialSpec(),
        label = "shutterInnerSize",
    )
    val innerCorner by animateDpAsState(
        targetValue = if (isRecording) 10.dp else 30.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "shutterInnerCorner",
    )
    val innerColor by animateColorAsState(
        targetValue = when (mode) {
            CameraCaptureMode.Photo -> Color.White
            CameraCaptureMode.Video -> Color(0xFFFF3B30)
        },
        animationSpec = motion.fastEffectsSpec(),
        label = "shutterInnerColor",
    )
    val description = when {
        mode == CameraCaptureMode.Photo -> stringResource(R.string.camera_shutter_photo)
        isRecording -> stringResource(R.string.camera_shutter_video_stop)
        else -> stringResource(R.string.camera_shutter_video_start)
    }

    Box(
        modifier = Modifier
            .size(78.dp)
            .graphicsLayer {
                scaleX = outerScale
                scaleY = outerScale
            }
            .clip(CircleShape)
            .border(
                width = 4.dp,
                color = if (isRecording) Color(0xFFFF3B30).copy(alpha = 0.72f) else Color.White,
                shape = CircleShape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(RoundedCornerShape(innerCorner))
                .background(innerColor),
        )
    }
}

@Composable
private fun RecordingTimerPill(elapsedMs: Long) {
    val pulse = rememberInfiniteTransition(label = "recPulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "recDotAlpha",
    )
    Surface(
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.64f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30)),
            )
            Text(
                text = formatRecordingDuration(elapsedMs),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFeatureSettings = "tnum",
                ),
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusRing(indicator: FocusIndicatorState) {
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val ringSizePx = with(density) { 68.dp.toPx() }
    val scale = remember { Animatable(1.35f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, motion.fastSpatialSpec())
    }
    val ringColor by animateColorAsState(
        targetValue = if (indicator.locked) MaterialTheme.colorScheme.primary else Color.White,
        animationSpec = motion.fastEffectsSpec(),
        label = "focusRingColor",
    )
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = (indicator.offset.x - ringSizePx / 2f).roundToInt(),
                    y = (indicator.offset.y - ringSizePx / 2f).roundToInt(),
                )
            }
            .size(68.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .border(
                width = 2.dp,
                color = ringColor,
                shape = CircleShape,
            ),
    )
}

@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CameraPermissionStage(
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onClose: () -> Unit,
) {
    val buttonShapes = IconButtonShapes(
        shape = CircleShape,
        pressedShape = RoundedCornerShape(14.dp),
    )
    Box(Modifier.fillMaxSize()) {
        FilledTonalIconButton(
            onClick = onClose,
            shapes = buttonShapes,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(16.dp)
                .size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.camera_close),
            )
        }

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(56.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.camera_permission_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.camera_permission_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = onClose,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.chat_cancel))
                    }
                    Button(
                        onClick = if (permanentlyDenied) onOpenSettings else onRequestPermission,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(
                            stringResource(
                                if (permanentlyDenied) {
                                    R.string.camera_permission_settings
                                } else {
                                    R.string.camera_permission_allow
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CameraReviewStage(
    preview: CapturedPreview,
    onRetake: () -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var muted by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (preview.isVideo) {
            var preferSoftware by remember(preview.target.uri) { mutableStateOf(false) }
            val player = remember(preview.target.uri, preferSoftware) {
                createExoPlayer(
                    context = context,
                    preferSoftwareDecoder = preferSoftware,
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    handleAudioFocus = true,
                ).apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItem(MediaItem.fromUri(preview.target.uri))
                    prepare()
                    playWhenReady = true
                }
            }
            LaunchedEffect(player, muted) {
                player.volume = if (muted) 0f else 1f
            }
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        if (!preferSoftware && error.isDecoderFailure()) {
                            preferSoftware = true
                        }
                    }
                }
                player.addListener(listener)
                onDispose {
                    player.removeListener(listener)
                    player.release()
                }
            }
            ContentFrame(
                player = player,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp)
                    .clip(RoundedCornerShape(24.dp)),
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = ContentScale.Fit,
            )

            FilledTonalIconToggleButton(
                checked = muted,
                onCheckedChange = { next ->
                    muted = next
                    haptic.toggle(!next)
                },
                shapes = IconToggleButtonShapes(
                    shape = CircleShape,
                    pressedShape = RoundedCornerShape(14.dp),
                    checkedShape = RoundedCornerShape(16.dp),
                ),
                colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.52f),
                    contentColor = Color.White,
                    checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(48.dp),
            ) {
                Icon(
                    imageVector = if (muted) {
                        Icons.AutoMirrored.Rounded.VolumeOff
                    } else {
                        Icons.AutoMirrored.Rounded.VolumeUp
                    },
                    contentDescription = stringResource(
                        if (muted) R.string.media_viewer_unmute else R.string.media_viewer_mute,
                    ),
                )
            }
        } else {
            AsyncImage(
                model = preview.target.uri,
                contentDescription = stringResource(R.string.chat_media_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 96.dp)
                    .clip(RoundedCornerShape(24.dp)),
            )
        }

        // Bottom review action bar: Retake and Use photo/video.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = onRetake,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.camera_retake))
            }
            Button(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (preview.isVideo) R.string.camera_use_video else R.string.camera_use_photo,
                    ),
                )
            }
        }
    }
}

private fun Context.findCameraActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
