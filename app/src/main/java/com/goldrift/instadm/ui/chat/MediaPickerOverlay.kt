package com.goldrift.instadm.ui.chat

import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import android.widget.photopicker.EmbeddedPhotoPickerFeatureInfo
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresExtension
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.photopicker.compose.EmbeddedPhotoPicker
import androidx.photopicker.compose.ExperimentalPhotoPickerComposeApi
import androidx.photopicker.compose.rememberEmbeddedPhotoPickerState
import com.goldrift.instadm.R
import com.goldrift.instadm.ui.components.confirm
import com.goldrift.instadm.ui.components.segmentTick
import com.goldrift.instadm.ui.components.thresholdActivated
import kotlinx.coroutines.launch

/** Active request that opens the in-app embedded Photo Picker sheet. */
data class MediaPickerRequest(
    val maxSelectionLimit: Int,
    val onGrantUris: (List<Uri>) -> Unit,
    val onRevokeUris: (List<Uri>) -> Unit,
    val onOpenCamera: () -> Unit,
    val onFallbackToSystemPicker: () -> Unit,
)

internal fun isEmbeddedPhotoPickerSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 15

internal fun buildVisualMediaRequest(
    accentColorArgb: Int,
    maxItems: Int,
): PickVisualMediaRequest {
    val builder = PickVisualMediaRequest.Builder()
        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
        .setOrderedSelection(true)
        .setAccentColor(normalizePhotoPickerAccentColor(accentColorArgb))
    if (maxItems >= 2) {
        builder.setMaxItems(maxItems)
    }
    return builder.build()
}

/**
 * In-window Material 3 Expressive bottom sheet hosting Android's embedded
 * Photo Picker on Android 14+ (SDK ext 15+), with live continuous selection,
 * app-accent theming, drag-to-expand/dismiss, predictive back, and a direct
 * shortcut into the in-app camera.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaPickerOverlay(
    request: MediaPickerRequest?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isEmbeddedPhotoPickerSupported()) return

    var shownRequest by remember { mutableStateOf<MediaPickerRequest?>(null) }
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
        enter = fadeIn(motion.fastEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec()),
        label = "mediaPickerScrim",
    ) {
        val active = shownRequest ?: return@AnimatedVisibility
        key(openGeneration) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= 15
            ) {
                EmbeddedMediaPickerSheet(
                    request = active,
                    onDismiss = onDismiss,
                    sheetModifier = Modifier.animateEnterExit(
                        enter = slideInVertically(motion.defaultSpatialSpec()) { it },
                        exit = slideOutVertically(motion.fastSpatialSpec()) { it },
                    ),
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@RequiresExtension(extension = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, version = 15)
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalPhotoPickerComposeApi::class)
@Composable
private fun EmbeddedMediaPickerSheet(
    request: MediaPickerRequest,
    onDismiss: () -> Unit,
    sheetModifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf(false) }
    var selectedCount by remember { mutableIntStateOf(0) }
    var fallingBack by remember { mutableStateOf(false) }
    val dragOffsetY = remember { Animatable(0f) }

    val primaryArgb = MaterialTheme.colorScheme.primary.toArgb()
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val featureInfo = remember(primaryArgb, isDarkTheme, request.maxSelectionLimit) {
        EmbeddedPhotoPickerFeatureInfo.Builder()
            .setMimeTypes(listOf("image/*", "video/*"))
            .setAccentColor(normalizePhotoPickerAccentColor(primaryArgb))
            .setOrderedSelection(true)
            .setMaxSelectionLimit(request.maxSelectionLimit.coerceAtLeast(1))
            .setThemeNightMode(
                if (isDarkTheme) {
                    Configuration.UI_MODE_NIGHT_YES
                } else {
                    Configuration.UI_MODE_NIGHT_NO
                },
            )
            .build()
    }

    // Keep the embedded picker in expanded mode at all sheet heights: Android's
    // embedded Photo Picker disables touch scrolling in collapsed mode on U Ext <= 22.
    val pickerState = rememberEmbeddedPhotoPickerState(
        initialExpandedValue = true,
        onSessionError = {
            if (!fallingBack) {
                fallingBack = true
                onDismiss()
                request.onFallbackToSystemPicker()
            }
        },
        onUriPermissionGranted = { uris ->
            selectedCount += uris.size
            haptic.segmentTick()
            request.onGrantUris(uris)
        },
        onUriPermissionRevoked = { uris ->
            selectedCount = (selectedCount - uris.size).coerceAtLeast(0)
            haptic.segmentTick()
            request.onRevokeUris(uris)
        },
        onSelectionComplete = {
            haptic.confirm()
            onDismiss()
        },
    )

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = true,
        onBackCompleted = {
            if (expanded) {
                expanded = false
                haptic.segmentTick()
            } else {
                onDismiss()
            }
        },
    )

    val predictiveProgress =
        (navEventState.transitionState as? NavigationEventTransitionState.InProgress)
            ?.latestEvent?.progress?.coerceIn(0f, 1f) ?: 0f

    val dismissThresholdPx = with(density) { 110.dp.toPx() }
    val expandThresholdPx = with(density) { 64.dp.toPx() }
    val dismissVelocityPx = with(density) { 700.dp.toPx() }

    val buttonShapes = IconButtonShapes(
        shape = CircleShape,
        pressedShape = RoundedCornerShape(14.dp),
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val containerHeight = maxHeight
        val targetSheetHeight by animateDpAsState(
            targetValue = if (expanded) containerHeight * 0.94f else containerHeight * 0.62f,
            animationSpec = motion.defaultSpatialSpec(),
            label = "pickerSheetHeight",
        )
        val sheetHeightPx = with(density) { targetSheetHeight.toPx().coerceAtLeast(1f) }
        val dragDismissProgress = (dragOffsetY.value / (sheetHeightPx * 0.45f)).coerceIn(0f, 1f)
        val combinedDismiss = maxOf(dragDismissProgress, predictiveProgress)

        // Scrim above the sheet dismisses when tapped.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f * (1f - combinedDismiss)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        haptic.segmentTick()
                        onDismiss()
                    },
                ),
        )

        Surface(
            modifier = sheetModifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(targetSheetHeight)
                .graphicsLayer {
                    translationY = dragOffsetY.value + predictiveProgress * sheetHeightPx * 0.16f
                },
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            ) {
                    val currentExpanded by rememberUpdatedState(expanded)

                    // Header sits strictly above EmbeddedPhotoPicker's SurfaceView
                    // so setZOrderOnTop(true) never obscures any Compose control.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                val velocityTracker = VelocityTracker()
                                var dragState = MediaPickerDragState(sheetExpanded = currentExpanded)
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        dragState = MediaPickerDragState(sheetExpanded = currentExpanded)
                                        velocityTracker.resetTracking()
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        val previousOffsetY = dragState.dragOffsetY
                                        val result = applyMediaPickerHeaderDrag(
                                            current = dragState,
                                            dragDeltaY = dragAmount,
                                            expandThresholdPx = expandThresholdPx,
                                            dismissThresholdPx = dismissThresholdPx,
                                        )
                                        dragState = result.state
                                        if (result.expandedNow) {
                                            expanded = true
                                            haptic.segmentTick()
                                            scope.launch {
                                                dragOffsetY.animateTo(0f, motion.fastSpatialSpec())
                                            }
                                        } else {
                                            if (result.armedDismissNow) {
                                                haptic.thresholdActivated()
                                            }
                                            if (dragState.dragOffsetY != previousOffsetY) {
                                                val offsetY = dragState.dragOffsetY
                                                scope.launch { dragOffsetY.snapTo(offsetY) }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        val velocityY = velocityTracker.calculateVelocity().y
                                        if (
                                            shouldDismissDownward(
                                                offsetPx = dragState.dragOffsetY,
                                                velocityPxPerSec = velocityY,
                                                thresholdPx = dismissThresholdPx,
                                                flingVelocityPxPerSec = dismissVelocityPx,
                                            )
                                        ) {
                                            if (dragState.sheetExpanded) {
                                                expanded = false
                                                haptic.segmentTick()
                                                scope.launch {
                                                    dragOffsetY.animateTo(0f, motion.defaultSpatialSpec())
                                                }
                                            } else {
                                                onDismiss()
                                            }
                                        } else {
                                            scope.launch {
                                                dragOffsetY.animateTo(0f, motion.defaultSpatialSpec())
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch {
                                            dragOffsetY.animateTo(0f, motion.defaultSpatialSpec())
                                        }
                                    },
                                )
                            }
                            .padding(top = 10.dp, bottom = 8.dp, start = 16.dp, end = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                ),
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.picker_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            FilledTonalIconButton(
                                onClick = {
                                    haptic.segmentTick()
                                    onDismiss()
                                    request.onOpenCamera()
                                },
                                shapes = buttonShapes,
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoCamera,
                                    contentDescription = stringResource(R.string.picker_open_camera),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            AnimatedContent(
                                targetState = selectedCount > 0,
                                transitionSpec = {
                                    (fadeIn(motion.fastEffectsSpec()) + scaleIn(
                                        initialScale = 0.85f,
                                        animationSpec = motion.fastSpatialSpec(),
                                    )) togetherWith (fadeOut(motion.fastEffectsSpec()) + scaleOut(
                                        targetScale = 0.85f,
                                        animationSpec = motion.fastSpatialSpec(),
                                    ))
                                },
                                label = "pickerDoneAction",
                            ) { hasSelection ->
                                if (hasSelection) {
                                    Button(
                                        onClick = {
                                            haptic.confirm()
                                            onDismiss()
                                        },
                                        shapes = ButtonDefaults.shapes(),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        modifier = Modifier.height(40.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = stringResource(
                                                R.string.picker_done_count,
                                                selectedCount,
                                            ),
                                            style = MaterialTheme.typography.labelLarge,
                                        )
                                    }
                                } else {
                                    FilledTonalIconButton(
                                        onClick = {
                                            haptic.segmentTick()
                                            onDismiss()
                                        },
                                        shapes = buttonShapes,
                                        modifier = Modifier.size(40.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.picker_close),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    EmbeddedPhotoPicker(
                        state = pickerState,
                        embeddedPhotoPickerFeatureInfo = featureInfo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
            }
        }
    }
}
