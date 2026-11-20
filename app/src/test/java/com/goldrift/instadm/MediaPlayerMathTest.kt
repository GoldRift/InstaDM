package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.VideoPlaybackSpeeds
import com.goldrift.instadm.ui.chat.VoicePlaybackSpeeds
import com.goldrift.instadm.ui.chat.canSwipeToDismiss
import com.goldrift.instadm.ui.chat.clampImagePan
import com.goldrift.instadm.ui.chat.clampImageScale
import com.goldrift.instadm.ui.chat.doubleTapZoomTarget
import com.goldrift.instadm.ui.chat.formatPlaybackSpeed
import com.goldrift.instadm.ui.chat.mediaDismissTransform
import com.goldrift.instadm.ui.chat.nextPlaybackSpeed
import com.goldrift.instadm.ui.chat.shouldAutoHidePlayerChrome
import com.goldrift.instadm.ui.chat.shouldDismissMedia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPlayerMathTest {
    @Test
    fun verticalSwipeDismissesPastDistanceOrMatchingFling() {
        assertTrue(
            shouldDismissMedia(
                offsetPx = 220f,
                velocityPxPerSec = 0f,
                thresholdPx = 200f,
                flingVelocityPxPerSec = 1_200f,
            ),
        )
        assertTrue(
            shouldDismissMedia(
                offsetPx = -220f,
                velocityPxPerSec = 0f,
                thresholdPx = 200f,
                flingVelocityPxPerSec = 1_200f,
            ),
        )
        assertTrue(
            shouldDismissMedia(
                offsetPx = 60f,
                velocityPxPerSec = 1_500f,
                thresholdPx = 200f,
                flingVelocityPxPerSec = 1_200f,
            ),
        )
        // Reversing direction into a fling back toward center cancels the dismiss.
        assertFalse(
            shouldDismissMedia(
                offsetPx = 60f,
                velocityPxPerSec = -1_500f,
                thresholdPx = 200f,
                flingVelocityPxPerSec = 1_200f,
            ),
        )
        assertFalse(
            shouldDismissMedia(
                offsetPx = 80f,
                velocityPxPerSec = 400f,
                thresholdPx = 200f,
                flingVelocityPxPerSec = 1_200f,
            ),
        )
    }

    @Test
    fun dismissTransformInterpolatesScaleCornersAndScrim() {
        val rest = mediaDismissTransform(offsetPx = 0f, containerHeightPx = 1_000f)
        assertEquals(1f, rest.scale, 0.001f)
        assertEquals(0f, rest.cornerDp, 0.001f)
        assertEquals(1f, rest.backdropAlpha, 0.001f)
        assertEquals(1f, rest.chromeAlpha, 0.001f)

        val pulled = mediaDismissTransform(offsetPx = 350f, containerHeightPx = 1_000f)
        assertTrue(pulled.scale in 0.8f..0.95f)
        assertTrue(pulled.cornerDp > 10f)
        assertTrue(pulled.backdropAlpha < 0.7f)
        assertEquals(0f, pulled.chromeAlpha, 0.001f)

        val far = mediaDismissTransform(offsetPx = -2_000f, containerHeightPx = 1_000f)
        assertEquals(0.8f, far.scale, 0.001f)
        assertEquals(28f, far.cornerDp, 0.001f)
        assertEquals(0f, far.backdropAlpha, 0.001f)
    }

    @Test
    fun zoomedImageBlocksVerticalSwipeDismiss() {
        assertTrue(canSwipeToDismiss(zoomScale = 1f))
        assertTrue(canSwipeToDismiss(zoomScale = 1.005f))
        assertFalse(canSwipeToDismiss(zoomScale = 1.25f))
        assertFalse(canSwipeToDismiss(zoomScale = 2.5f))
    }

    @Test
    fun imageScaleAndPanStayWithinViewportBounds() {
        assertEquals(1f, clampImageScale(0.5f), 0.001f)
        assertEquals(5f, clampImageScale(9f), 0.001f)
        assertEquals(2.5f, clampImageScale(2.5f), 0.001f)

        val unzoomed = clampImagePan(
            offsetX = 120f,
            offsetY = -80f,
            scale = 1f,
            viewportWidth = 1_000f,
            viewportHeight = 2_000f,
        )
        assertEquals(0f, unzoomed.first, 0.001f)
        assertEquals(0f, unzoomed.second, 0.001f)

        val zoomed = clampImagePan(
            offsetX = 900f,
            offsetY = -1_500f,
            scale = 2f,
            viewportWidth = 1_000f,
            viewportHeight = 2_000f,
        )
        assertEquals(500f, zoomed.first, 0.001f)
        assertEquals(-1_000f, zoomed.second, 0.001f)
    }

    @Test
    fun doubleTapTogglesBetweenZoomedFocalPointAndReset() {
        val zoomIn = doubleTapZoomTarget(
            currentScale = 1f,
            tapX = 750f,
            tapY = 1_000f,
            viewportWidth = 1_000f,
            viewportHeight = 2_000f,
        )
        assertEquals(2.5f, zoomIn.scale, 0.001f)
        // Tapping right of center shifts content left so the tapped point stays centered.
        assertTrue(zoomIn.offsetX < 0f)
        assertEquals(0f, zoomIn.offsetY, 0.001f)

        val zoomOut = doubleTapZoomTarget(
            currentScale = 2.5f,
            tapX = 750f,
            tapY = 1_000f,
            viewportWidth = 1_000f,
            viewportHeight = 2_000f,
        )
        assertEquals(1f, zoomOut.scale, 0.001f)
        assertEquals(0f, zoomOut.offsetX, 0.001f)
        assertEquals(0f, zoomOut.offsetY, 0.001f)
    }

    @Test
    fun playbackSpeedCyclesAndFormatsCompactLabels() {
        assertEquals(1.25f, nextPlaybackSpeed(1f, VideoPlaybackSpeeds), 0.001f)
        assertEquals(1.5f, nextPlaybackSpeed(1.25f, VideoPlaybackSpeeds), 0.001f)
        assertEquals(2f, nextPlaybackSpeed(1.5f, VideoPlaybackSpeeds), 0.001f)
        assertEquals(1f, nextPlaybackSpeed(2f, VideoPlaybackSpeeds), 0.001f)

        assertEquals(1.5f, nextPlaybackSpeed(1f, VoicePlaybackSpeeds), 0.001f)
        assertEquals(2f, nextPlaybackSpeed(1.5f, VoicePlaybackSpeeds), 0.001f)
        assertEquals(1f, nextPlaybackSpeed(2f, VoicePlaybackSpeeds), 0.001f)

        assertEquals("1×", formatPlaybackSpeed(1f))
        assertEquals("1.25×", formatPlaybackSpeed(1.25f))
        assertEquals("1.5×", formatPlaybackSpeed(1.5f))
        assertEquals("2×", formatPlaybackSpeed(2f))
    }

    @Test
    fun playerChromeAutoHidesOnlyDuringActiveUnscrubbedPlayback() {
        assertTrue(
            shouldAutoHidePlayerChrome(
                chromeVisible = true,
                isVideo = true,
                isPlaying = true,
                isScrubbing = false,
                isBuffering = false,
            ),
        )
        assertFalse(
            shouldAutoHidePlayerChrome(
                chromeVisible = true,
                isVideo = false,
                isPlaying = false,
                isScrubbing = false,
                isBuffering = false,
            ),
        )
        assertFalse(
            shouldAutoHidePlayerChrome(
                chromeVisible = true,
                isVideo = true,
                isPlaying = false,
                isScrubbing = false,
                isBuffering = false,
            ),
        )
        assertFalse(
            shouldAutoHidePlayerChrome(
                chromeVisible = true,
                isVideo = true,
                isPlaying = true,
                isScrubbing = true,
                isBuffering = false,
            ),
        )
        assertFalse(
            shouldAutoHidePlayerChrome(
                chromeVisible = true,
                isVideo = true,
                isPlaying = true,
                isScrubbing = false,
                isBuffering = true,
            ),
        )
    }
}
