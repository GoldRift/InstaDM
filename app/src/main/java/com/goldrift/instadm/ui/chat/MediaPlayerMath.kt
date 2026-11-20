package com.goldrift.instadm.ui.chat

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal val VideoPlaybackSpeeds = listOf(1f, 1.25f, 1.5f, 2f)
internal val VoicePlaybackSpeeds = listOf(1f, 1.5f, 2f)

internal data class MediaDismissTransform(
    val scale: Float,
    val cornerDp: Float,
    val backdropAlpha: Float,
    val chromeAlpha: Float,
)

internal data class ImageZoomState(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
)

internal fun canSwipeToDismiss(zoomScale: Float): Boolean = zoomScale <= ZOOM_DISMISS_EPSILON

internal fun shouldDismissMedia(
    offsetPx: Float,
    velocityPxPerSec: Float,
    thresholdPx: Float,
    flingVelocityPxPerSec: Float,
): Boolean {
    if (thresholdPx <= 0f) return false
    if (abs(offsetPx) >= thresholdPx) return true
    if (abs(velocityPxPerSec) < flingVelocityPxPerSec) return false
    // Ignore flings opposing the current pull direction so a quick snap-back does not dismiss.
    return offsetPx == 0f || sign(offsetPx) == sign(velocityPxPerSec)
}

internal fun shouldDismissDownward(
    offsetPx: Float,
    velocityPxPerSec: Float,
    thresholdPx: Float,
    flingVelocityPxPerSec: Float,
): Boolean {
    if (thresholdPx <= 0f || offsetPx < 0f) return false
    if (offsetPx >= thresholdPx) return true
    return velocityPxPerSec >= flingVelocityPxPerSec
}

internal fun mediaDismissTransform(
    offsetPx: Float,
    containerHeightPx: Float,
): MediaDismissTransform {
    if (containerHeightPx <= 0f) {
        return MediaDismissTransform(
            scale = 1f,
            cornerDp = 0f,
            backdropAlpha = 1f,
            chromeAlpha = 1f,
        )
    }
    val progress = (abs(offsetPx) / (containerHeightPx * DISMISS_TRAVEL_FRACTION)).coerceIn(0f, 1f)
    val chromeProgress = (progress / CHROME_FADE_END_PROGRESS).coerceIn(0f, 1f)
    return MediaDismissTransform(
        scale = 1f - (1f - MIN_DISMISS_SCALE) * progress,
        cornerDp = MAX_DISMISS_CORNER_DP * progress,
        backdropAlpha = (1f - progress).coerceIn(0f, 1f),
        chromeAlpha = (1f - chromeProgress).coerceIn(0f, 1f),
    )
}

internal fun clampImageScale(scale: Float): Float =
    scale.coerceIn(MIN_IMAGE_SCALE, MAX_IMAGE_SCALE)

internal fun clampImagePan(
    offsetX: Float,
    offsetY: Float,
    scale: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): Pair<Float, Float> {
    val clampedScale = clampImageScale(scale)
    if (clampedScale <= 1f || viewportWidth <= 0f || viewportHeight <= 0f) {
        return 0f to 0f
    }
    val maxX = viewportWidth * (clampedScale - 1f) / 2f
    val maxY = viewportHeight * (clampedScale - 1f) / 2f
    return offsetX.coerceIn(-maxX, maxX) to offsetY.coerceIn(-maxY, maxY)
}

internal fun doubleTapZoomTarget(
    currentScale: Float,
    tapX: Float,
    tapY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
): ImageZoomState {
    if (!canSwipeToDismiss(currentScale)) {
        return ImageZoomState(scale = 1f, offsetX = 0f, offsetY = 0f)
    }
    val targetScale = DOUBLE_TAP_ZOOM_SCALE
    val rawOffsetX = (viewportWidth / 2f - tapX) * (targetScale - 1f)
    val rawOffsetY = (viewportHeight / 2f - tapY) * (targetScale - 1f)
    val (clampedX, clampedY) = clampImagePan(
        offsetX = rawOffsetX,
        offsetY = rawOffsetY,
        scale = targetScale,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
    )
    return ImageZoomState(scale = targetScale, offsetX = clampedX, offsetY = clampedY)
}

internal fun nextPlaybackSpeed(current: Float, steps: List<Float>): Float {
    if (steps.isEmpty()) return 1f
    val index = steps.indexOfFirst { abs(it - current) < 0.01f }
    return if (index == -1) steps.first() else steps[(index + 1) % steps.size]
}

internal fun formatPlaybackSpeed(speed: Float): String {
    val hundredths = (speed * 100f).roundToInt()
    val formatted = when {
        hundredths % 100 == 0 -> "${hundredths / 100}"
        hundredths % 10 == 0 -> "${hundredths / 100}.${(hundredths % 100) / 10}"
        else -> "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')}"
    }
    return "${formatted}×"
}

internal fun shouldAutoHidePlayerChrome(
    chromeVisible: Boolean,
    isVideo: Boolean,
    isPlaying: Boolean,
    isScrubbing: Boolean,
    isBuffering: Boolean,
): Boolean = chromeVisible && isVideo && isPlaying && !isScrubbing && !isBuffering

private const val ZOOM_DISMISS_EPSILON = 1.01f
private const val DISMISS_TRAVEL_FRACTION = 0.5f
private const val MIN_DISMISS_SCALE = 0.8f
private const val MAX_DISMISS_CORNER_DP = 28f
private const val CHROME_FADE_END_PROGRESS = 0.25f
private const val MIN_IMAGE_SCALE = 1f
private const val MAX_IMAGE_SCALE = 5f
private const val DOUBLE_TAP_ZOOM_SCALE = 2.5f
