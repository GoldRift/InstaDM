package com.goldrift.instadm.ui.chat

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

enum class CameraCaptureMode {
    Photo,
    Video,
}

enum class CameraFlashMode {
    Auto,
    On,
    Off,
    ;

    fun next(): CameraFlashMode = when (this) {
        Auto -> On
        On -> Off
        Off -> Auto
    }
}

internal enum class ResolvedPhotoFlash {
    Off,
    Auto,
    HardwareOn,
    ScreenOn,
}

internal fun shouldShowFlashControl(
    mode: CameraCaptureMode,
    isFrontCamera: Boolean,
    hasFlashUnit: Boolean,
): Boolean = hasFlashUnit || (mode == CameraCaptureMode.Photo && isFrontCamera)

internal fun effectiveCameraFlashMode(
    mode: CameraCaptureMode,
    flashMode: CameraFlashMode,
    isFrontCamera: Boolean,
    hasFlashUnit: Boolean,
): CameraFlashMode {
    if (!shouldShowFlashControl(mode, isFrontCamera, hasFlashUnit)) {
        return CameraFlashMode.Off
    }
    return when {
        mode == CameraCaptureMode.Video || isFrontCamera ->
            if (flashMode == CameraFlashMode.On) CameraFlashMode.On else CameraFlashMode.Off
        else -> flashMode
    }
}

internal fun nextCameraFlashMode(
    mode: CameraCaptureMode,
    flashMode: CameraFlashMode,
    isFrontCamera: Boolean,
    hasFlashUnit: Boolean,
): CameraFlashMode {
    if (!shouldShowFlashControl(mode, isFrontCamera, hasFlashUnit)) {
        return CameraFlashMode.Off
    }
    val current = effectiveCameraFlashMode(mode, flashMode, isFrontCamera, hasFlashUnit)
    return if (mode == CameraCaptureMode.Photo && !isFrontCamera) {
        current.next()
    } else if (current == CameraFlashMode.On) {
        CameraFlashMode.Off
    } else {
        CameraFlashMode.On
    }
}

internal fun shouldEnableCameraTorch(
    mode: CameraCaptureMode,
    flashMode: CameraFlashMode,
    isFrontCamera: Boolean,
    hasFlashUnit: Boolean,
    isLivePreview: Boolean,
): Boolean = isLivePreview &&
    mode == CameraCaptureMode.Video &&
    effectiveCameraFlashMode(mode, flashMode, isFrontCamera, hasFlashUnit) == CameraFlashMode.On &&
    hasFlashUnit

/**
 * Returns the torch state to send to `CameraControl.enableTorch`, or `null` when
 * no call should be issued. Avoiding redundant `enableTorch(false)` calls in photo
 * mode keeps CameraX's CameraPipe 3A state from overwriting still-capture flash.
 */
internal fun desiredCameraTorchUpdate(
    mode: CameraCaptureMode,
    flashMode: CameraFlashMode,
    isFrontCamera: Boolean,
    hasFlashUnit: Boolean,
    isLivePreview: Boolean,
    isTorchCurrentlyOn: Boolean,
): Boolean? {
    if (!hasFlashUnit) return null
    val wantTorch = shouldEnableCameraTorch(
        mode = mode,
        flashMode = flashMode,
        isFrontCamera = isFrontCamera,
        hasFlashUnit = true,
        isLivePreview = isLivePreview,
    )
    return when {
        wantTorch -> true
        isTorchCurrentlyOn -> false
        else -> null
    }
}

internal fun resolvePhotoFlash(
    mode: CameraCaptureMode,
    flashMode: CameraFlashMode,
    isFrontCamera: Boolean,
    hasFlashUnit: Boolean,
    hasScreenFlash: Boolean,
): ResolvedPhotoFlash {
    if (mode != CameraCaptureMode.Photo) return ResolvedPhotoFlash.Off
    val effective = effectiveCameraFlashMode(mode, flashMode, isFrontCamera, hasFlashUnit)
    if (isFrontCamera) {
        return when {
            effective != CameraFlashMode.On -> ResolvedPhotoFlash.Off
            hasScreenFlash -> ResolvedPhotoFlash.ScreenOn
            hasFlashUnit -> ResolvedPhotoFlash.HardwareOn
            else -> ResolvedPhotoFlash.Off
        }
    }
    if (!hasFlashUnit) return ResolvedPhotoFlash.Off
    return when (effective) {
        CameraFlashMode.Auto -> ResolvedPhotoFlash.Auto
        CameraFlashMode.On -> ResolvedPhotoFlash.HardwareOn
        CameraFlashMode.Off -> ResolvedPhotoFlash.Off
    }
}

/**
 * Returns up to three discrete zoom buttons supported by the active lens, or an
 * empty list when the lens does not support zooming.
 */
internal fun zoomPresets(minZoomRatio: Float, maxZoomRatio: Float): List<Float> {
    if (maxZoomRatio <= minZoomRatio + 0.05f) return emptyList()
    val presets = mutableListOf<Float>()
    if (minZoomRatio <= 0.65f) {
        presets += 0.5f
    }
    if (1f in minZoomRatio..maxZoomRatio) {
        presets += 1f
    }
    if (maxZoomRatio >= 2f) {
        presets += 2f
    }
    if (presets.size < 3 && maxZoomRatio >= 3.5f) {
        presets += 3f
    }
    return if (presets.size >= 2) presets else emptyList()
}

internal fun nearestZoomPresetIndex(presets: List<Float>, zoomRatio: Float): Int {
    if (presets.isEmpty()) return -1
    var bestIndex = 0
    var bestDistance = abs(presets[0] - zoomRatio)
    for (i in 1 until presets.size) {
        val distance = abs(presets[i] - zoomRatio)
        if (distance < bestDistance) {
            bestDistance = distance
            bestIndex = i
        }
    }
    return bestIndex
}

internal fun formatZoomRatio(zoomRatio: Float): String {
    val tenths = (zoomRatio.coerceAtLeast(0.1f) * 10f).roundToInt()
    return if (tenths % 10 == 0) {
        "${tenths / 10}×"
    } else {
        String.format(Locale.US, "%.1f×", tenths / 10f)
    }
}

internal fun formatRecordingDuration(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

/**
 * Normalizes an ARGB color into the opaque ARGB long expected by Android's
 * Photo Picker accent color APIs, nudging luminance into Android's accepted
 * `(0.05, 0.90)` range so extreme dark or light theme primaries aren't ignored.
 */
internal fun normalizePhotoPickerAccentColor(argb: Int): Long {
    var r = ((argb shr 16) and 0xFF) / 255.0
    var g = ((argb shr 8) and 0xFF) / 255.0
    var b = (argb and 0xFF) / 255.0

    val luminance = relativeLuminance(r, g, b)
    when {
        luminance < 0.06 -> {
            val blend = 0.30
            r += (1.0 - r) * blend
            g += (1.0 - g) * blend
            b += (1.0 - b) * blend
        }
        luminance > 0.88 -> {
            val scale = 0.82
            r *= scale
            g *= scale
            b *= scale
        }
    }

    val rInt = (r * 255.0).roundToInt().coerceIn(0, 255)
    val gInt = (g * 255.0).roundToInt().coerceIn(0, 255)
    val bInt = (b * 255.0).roundToInt().coerceIn(0, 255)
    return 0xFF000000L or
        (rInt.toLong() shl 16) or
        (gInt.toLong() shl 8) or
        bInt.toLong()
}

private fun relativeLuminance(r: Double, g: Double, b: Double): Double {
    fun linear(channel: Double): Double =
        if (channel <= 0.04045) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
    return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
}
