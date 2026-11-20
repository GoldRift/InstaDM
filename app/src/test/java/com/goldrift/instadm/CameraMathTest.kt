package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.CameraCaptureMode
import com.goldrift.instadm.ui.chat.CameraFlashMode
import com.goldrift.instadm.ui.chat.ResolvedPhotoFlash
import com.goldrift.instadm.ui.chat.desiredCameraTorchUpdate
import com.goldrift.instadm.ui.chat.effectiveCameraFlashMode
import com.goldrift.instadm.ui.chat.formatRecordingDuration
import com.goldrift.instadm.ui.chat.formatZoomRatio
import com.goldrift.instadm.ui.chat.nearestZoomPresetIndex
import com.goldrift.instadm.ui.chat.nextCameraFlashMode
import com.goldrift.instadm.ui.chat.normalizePhotoPickerAccentColor
import com.goldrift.instadm.ui.chat.resolvePhotoFlash
import com.goldrift.instadm.ui.chat.shouldEnableCameraTorch
import com.goldrift.instadm.ui.chat.shouldShowFlashControl
import com.goldrift.instadm.ui.chat.zoomPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraMathTest {
    @Test
    fun flashModeCyclesAutoOnOffAuto() {
        assertEquals(CameraFlashMode.On, CameraFlashMode.Auto.next())
        assertEquals(CameraFlashMode.Off, CameraFlashMode.On.next())
        assertEquals(CameraFlashMode.Auto, CameraFlashMode.Off.next())
    }

    @Test
    fun showFlashControlMatchesLensAndModeCapabilities() {
        assertTrue(shouldShowFlashControl(CameraCaptureMode.Photo, isFrontCamera = false, hasFlashUnit = true))
        assertFalse(shouldShowFlashControl(CameraCaptureMode.Photo, isFrontCamera = false, hasFlashUnit = false))
        assertTrue(shouldShowFlashControl(CameraCaptureMode.Photo, isFrontCamera = true, hasFlashUnit = false))
        assertTrue(shouldShowFlashControl(CameraCaptureMode.Video, isFrontCamera = false, hasFlashUnit = true))
        assertFalse(shouldShowFlashControl(CameraCaptureMode.Video, isFrontCamera = false, hasFlashUnit = false))
        assertFalse(shouldShowFlashControl(CameraCaptureMode.Video, isFrontCamera = true, hasFlashUnit = false))
        assertTrue(shouldShowFlashControl(CameraCaptureMode.Video, isFrontCamera = true, hasFlashUnit = true))
    }

    @Test
    fun effectiveFlashModeCollapsesAutoToOffForFrontAndVideo() {
        assertEquals(
            CameraFlashMode.Auto,
            effectiveCameraFlashMode(CameraCaptureMode.Photo, CameraFlashMode.Auto, isFrontCamera = false, hasFlashUnit = true),
        )
        assertEquals(
            CameraFlashMode.Off,
            effectiveCameraFlashMode(CameraCaptureMode.Photo, CameraFlashMode.Auto, isFrontCamera = true, hasFlashUnit = false),
        )
        assertEquals(
            CameraFlashMode.On,
            effectiveCameraFlashMode(CameraCaptureMode.Photo, CameraFlashMode.On, isFrontCamera = true, hasFlashUnit = false),
        )
        assertEquals(
            CameraFlashMode.Off,
            effectiveCameraFlashMode(CameraCaptureMode.Video, CameraFlashMode.Auto, isFrontCamera = false, hasFlashUnit = true),
        )
        assertEquals(
            CameraFlashMode.On,
            effectiveCameraFlashMode(CameraCaptureMode.Video, CameraFlashMode.On, isFrontCamera = false, hasFlashUnit = true),
        )
        assertEquals(
            CameraFlashMode.Off,
            effectiveCameraFlashMode(CameraCaptureMode.Video, CameraFlashMode.On, isFrontCamera = true, hasFlashUnit = false),
        )
    }

    @Test
    fun nextFlashModeTogglesBinaryForFrontPhotoAndVideoTorch() {
        assertEquals(
            CameraFlashMode.On,
            nextCameraFlashMode(CameraCaptureMode.Photo, CameraFlashMode.Auto, isFrontCamera = false, hasFlashUnit = true),
        )
        assertEquals(
            CameraFlashMode.Off,
            nextCameraFlashMode(CameraCaptureMode.Photo, CameraFlashMode.On, isFrontCamera = false, hasFlashUnit = true),
        )
        assertEquals(
            CameraFlashMode.Auto,
            nextCameraFlashMode(CameraCaptureMode.Photo, CameraFlashMode.Off, isFrontCamera = false, hasFlashUnit = true),
        )
        assertEquals(
            CameraFlashMode.On,
            nextCameraFlashMode(CameraCaptureMode.Photo, CameraFlashMode.Auto, isFrontCamera = true, hasFlashUnit = false),
        )
        assertEquals(
            CameraFlashMode.Off,
            nextCameraFlashMode(CameraCaptureMode.Photo, CameraFlashMode.On, isFrontCamera = true, hasFlashUnit = false),
        )
        assertEquals(
            CameraFlashMode.On,
            nextCameraFlashMode(CameraCaptureMode.Video, CameraFlashMode.Auto, isFrontCamera = false, hasFlashUnit = true),
        )
        assertEquals(
            CameraFlashMode.Off,
            nextCameraFlashMode(CameraCaptureMode.Video, CameraFlashMode.On, isFrontCamera = false, hasFlashUnit = true),
        )
    }

    @Test
    fun torchOnlyEnablesInLiveVideoModeWhenFlashIsOn() {
        assertTrue(
            shouldEnableCameraTorch(
                mode = CameraCaptureMode.Video,
                flashMode = CameraFlashMode.On,
                isFrontCamera = false,
                hasFlashUnit = true,
                isLivePreview = true,
            ),
        )
        assertFalse(
            shouldEnableCameraTorch(
                mode = CameraCaptureMode.Video,
                flashMode = CameraFlashMode.Auto,
                isFrontCamera = false,
                hasFlashUnit = true,
                isLivePreview = true,
            ),
        )
        assertFalse(
            shouldEnableCameraTorch(
                mode = CameraCaptureMode.Video,
                flashMode = CameraFlashMode.On,
                isFrontCamera = false,
                hasFlashUnit = true,
                isLivePreview = false,
            ),
        )
        assertFalse(
            shouldEnableCameraTorch(
                mode = CameraCaptureMode.Photo,
                flashMode = CameraFlashMode.On,
                isFrontCamera = false,
                hasFlashUnit = true,
                isLivePreview = true,
            ),
        )
        assertFalse(
            shouldEnableCameraTorch(
                mode = CameraCaptureMode.Video,
                flashMode = CameraFlashMode.On,
                isFrontCamera = true,
                hasFlashUnit = false,
                isLivePreview = true,
            ),
        )
    }

    @Test
    fun desiredTorchUpdateSkipsOffCallsWhenTorchIsAlreadyOff() {
        assertNull(
            desiredCameraTorchUpdate(
                mode = CameraCaptureMode.Photo,
                flashMode = CameraFlashMode.On,
                isFrontCamera = false,
                hasFlashUnit = true,
                isLivePreview = true,
                isTorchCurrentlyOn = false,
            ),
        )
        assertNull(
            desiredCameraTorchUpdate(
                mode = CameraCaptureMode.Photo,
                flashMode = CameraFlashMode.Auto,
                isFrontCamera = false,
                hasFlashUnit = true,
                isLivePreview = true,
                isTorchCurrentlyOn = false,
            ),
        )
        assertEquals(
            true,
            desiredCameraTorchUpdate(
                mode = CameraCaptureMode.Video,
                flashMode = CameraFlashMode.On,
                isFrontCamera = false,
                hasFlashUnit = true,
                isLivePreview = true,
                isTorchCurrentlyOn = false,
            ),
        )
        assertEquals(
            false,
            desiredCameraTorchUpdate(
                mode = CameraCaptureMode.Video,
                flashMode = CameraFlashMode.Off,
                isFrontCamera = false,
                hasFlashUnit = true,
                isLivePreview = true,
                isTorchCurrentlyOn = true,
            ),
        )
        assertEquals(
            false,
            desiredCameraTorchUpdate(
                mode = CameraCaptureMode.Photo,
                flashMode = CameraFlashMode.On,
                isFrontCamera = false,
                hasFlashUnit = true,
                isLivePreview = true,
                isTorchCurrentlyOn = true,
            ),
        )
        assertNull(
            desiredCameraTorchUpdate(
                mode = CameraCaptureMode.Video,
                flashMode = CameraFlashMode.On,
                isFrontCamera = true,
                hasFlashUnit = false,
                isLivePreview = true,
                isTorchCurrentlyOn = false,
            ),
        )
    }

    @Test
    fun resolvePhotoFlashMapsHardwareAndScreenFlashCleanly() {
        assertEquals(
            ResolvedPhotoFlash.Auto,
            resolvePhotoFlash(
                mode = CameraCaptureMode.Photo,
                flashMode = CameraFlashMode.Auto,
                isFrontCamera = false,
                hasFlashUnit = true,
                hasScreenFlash = true,
            ),
        )
        assertEquals(
            ResolvedPhotoFlash.HardwareOn,
            resolvePhotoFlash(
                mode = CameraCaptureMode.Photo,
                flashMode = CameraFlashMode.On,
                isFrontCamera = false,
                hasFlashUnit = true,
                hasScreenFlash = true,
            ),
        )
        assertEquals(
            ResolvedPhotoFlash.ScreenOn,
            resolvePhotoFlash(
                mode = CameraCaptureMode.Photo,
                flashMode = CameraFlashMode.On,
                isFrontCamera = true,
                hasFlashUnit = false,
                hasScreenFlash = true,
            ),
        )
        assertEquals(
            ResolvedPhotoFlash.Off,
            resolvePhotoFlash(
                mode = CameraCaptureMode.Photo,
                flashMode = CameraFlashMode.On,
                isFrontCamera = true,
                hasFlashUnit = false,
                hasScreenFlash = false,
            ),
        )
        assertEquals(
            ResolvedPhotoFlash.Off,
            resolvePhotoFlash(
                mode = CameraCaptureMode.Video,
                flashMode = CameraFlashMode.On,
                isFrontCamera = false,
                hasFlashUnit = true,
                hasScreenFlash = true,
            ),
        )
    }

    @Test
    fun zoomPresetsIncludeUltrawideAndTelephotoWhenSupported() {
        assertEquals(listOf(0.5f, 1f, 2f), zoomPresets(minZoomRatio = 0.5f, maxZoomRatio = 8f))
        assertEquals(listOf(1f, 2f, 3f), zoomPresets(minZoomRatio = 1f, maxZoomRatio = 5f))
        assertEquals(listOf(1f, 2f), zoomPresets(minZoomRatio = 1f, maxZoomRatio = 2.4f))
        assertEquals(emptyList<Float>(), zoomPresets(minZoomRatio = 1f, maxZoomRatio = 1f))
    }

    @Test
    fun nearestZoomPresetMatchesClosestStop() {
        val presets = listOf(0.5f, 1f, 2f)
        assertEquals(0, nearestZoomPresetIndex(presets, 0.55f))
        assertEquals(1, nearestZoomPresetIndex(presets, 1.15f))
        assertEquals(2, nearestZoomPresetIndex(presets, 2.8f))
        assertEquals(-1, nearestZoomPresetIndex(emptyList(), 1f))
    }

    @Test
    fun formatZoomRatioDropsTrailingZeroForWholeNumbers() {
        assertEquals("0.5×", formatZoomRatio(0.5f))
        assertEquals("1×", formatZoomRatio(1.0f))
        assertEquals("1.4×", formatZoomRatio(1.42f))
        assertEquals("2×", formatZoomRatio(2.0f))
    }

    @Test
    fun formatRecordingDurationShowsMinutesAndPaddedSeconds() {
        assertEquals("0:00", formatRecordingDuration(0L))
        assertEquals("0:09", formatRecordingDuration(9_400L))
        assertEquals("1:05", formatRecordingDuration(65_000L))
        assertEquals("0:00", formatRecordingDuration(-500L))
    }

    @Test
    fun photoPickerAccentColorForcesOpaqueAndSafeLuminance() {
        // Pure black (luminance 0.0) must be lifted into Android's [0.05, 0.90] range.
        val fromBlack = normalizePhotoPickerAccentColor(0x80000000.toInt())
        assertEquals(0xFF000000L, fromBlack and 0xFF000000L)
        assertTrue(relativeLuminance(fromBlack) in 0.05..0.90)

        // Pure white (luminance 1.0) must be darkened into Android's [0.05, 0.90] range.
        val fromWhite = normalizePhotoPickerAccentColor(0xFFFFFFFF.toInt())
        assertEquals(0xFF000000L, fromWhite and 0xFF000000L)
        assertTrue(relativeLuminance(fromWhite) in 0.05..0.90)

        // Mid-tone Material 3 primary passes through opaque unchanged.
        val midPurple = 0xFF6750A4.toInt()
        assertEquals(0xFF6750A4L, normalizePhotoPickerAccentColor(midPurple))
    }

    private fun relativeLuminance(argb: Long): Double {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        fun linear(c: Double): Double =
            if (c <= 0.04045) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        return 0.2126 * linear(r) + 0.7152 * linear(g) + 0.0722 * linear(b)
    }
}
