package com.goldrift.instadm

import androidx.media3.common.PlaybackException
import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.VideoPlaybackSpeeds
import com.goldrift.instadm.ui.chat.VoicePlaybackSpeeds
import com.goldrift.instadm.ui.chat.buildChatMediaViewerRequest
import com.goldrift.instadm.ui.chat.canSwipeToDismiss
import com.goldrift.instadm.ui.chat.clampImagePan
import com.goldrift.instadm.ui.chat.clampImageScale
import com.goldrift.instadm.ui.chat.doubleTapZoomTarget
import com.goldrift.instadm.ui.chat.formatPlaybackSpeed
import com.goldrift.instadm.ui.chat.isDecoderFailureCode
import com.goldrift.instadm.ui.chat.mediaDismissTransform
import com.goldrift.instadm.ui.chat.nextPlaybackSpeed
import com.goldrift.instadm.ui.chat.pageIndicatorWindow
import com.goldrift.instadm.ui.chat.shouldAutoHidePlayerChrome
import com.goldrift.instadm.ui.chat.shouldDismissDownward
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
    fun downwardDismissIgnoresUpwardFlingAndAcceptsQuickDownwardFlick() {
        assertTrue(
            shouldDismissDownward(
                offsetPx = 80f,
                velocityPxPerSec = 0f,
                thresholdPx = 70f,
                flingVelocityPxPerSec = 900f,
            ),
        )
        assertTrue(
            shouldDismissDownward(
                offsetPx = 18f,
                velocityPxPerSec = 1_100f,
                thresholdPx = 70f,
                flingVelocityPxPerSec = 900f,
            ),
        )
        // Upward flicks at rest or mid-pull must never dismiss a downward-only bar.
        assertFalse(
            shouldDismissDownward(
                offsetPx = 0f,
                velocityPxPerSec = -1_400f,
                thresholdPx = 70f,
                flingVelocityPxPerSec = 900f,
            ),
        )
        assertFalse(
            shouldDismissDownward(
                offsetPx = 24f,
                velocityPxPerSec = -1_400f,
                thresholdPx = 70f,
                flingVelocityPxPerSec = 900f,
            ),
        )
        assertFalse(
            shouldDismissDownward(
                offsetPx = 24f,
                velocityPxPerSec = 300f,
                thresholdPx = 70f,
                flingVelocityPxPerSec = 900f,
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

    @Test
    fun decoderFailureCodesTriggerSoftwareFallbackOnlyForCodecErrors() {
        assertTrue(isDecoderFailureCode(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED))
        assertTrue(isDecoderFailureCode(PlaybackException.ERROR_CODE_DECODING_FAILED))
        assertTrue(isDecoderFailureCode(PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED))
        assertFalse(isDecoderFailureCode(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND))
        assertFalse(isDecoderFailureCode(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
    }

    @Test
    fun chatMediaViewerRequestCollectsVisualMediaAcrossMessagesInChronologicalOrder() {
        val photo1 = MessageMedia(type = "image", url = "https://cdn.test/1.jpg", partIndex = 0)
        val photo2 = MessageMedia(type = "image", url = "https://cdn.test/2.jpg", partIndex = 1)
        val clip = MessageMedia(type = "video", url = "https://cdn.test/3.mp4", partIndex = 0)
        val reel = MessageMedia(
            type = "reel",
            url = "",
            previewUrl = "https://cdn.test/reel.jpg",
            targetId = "reel-1",
            partIndex = 0,
        )
        val sticker = MessageMedia(type = "sticker", url = "https://cdn.test/s.webp", partIndex = 0)
        val gif = MessageMedia(type = "gif", url = "https://cdn.test/g.mp4", partIndex = 0)
        val voice = MessageMedia(type = "audio", url = "https://cdn.test/v.m4a", partIndex = 0)
        val link = MessageMedia(type = "link", url = "https://example.com/post", partIndex = 0)
        val viewOnce = MessageMedia(type = "image", url = "", viewOnce = true, partIndex = 0)
        val pendingPhoto = MessageMedia(type = "image", url = "https://cdn.test/p.jpg", partIndex = 0)

        val messages = listOf(
            ChatMessage(id = "mid.text", text = "hi", sender = MessageSender.Them, timestamp = "1:00 PM"),
            ChatMessage(
                id = "mid.album",
                text = "",
                sender = MessageSender.Me,
                timestamp = "1:01 PM",
                media = listOf(photo1, photo2),
            ),
            ChatMessage(
                id = "mid.voice",
                text = "",
                sender = MessageSender.Them,
                timestamp = "1:02 PM",
                media = listOf(voice),
            ),
            ChatMessage(
                id = "mid.video",
                text = "",
                sender = MessageSender.Them,
                senderName = "Dev Patel",
                timestamp = "1:03 PM",
                media = listOf(clip),
            ),
            ChatMessage(
                id = "mid.link",
                text = "",
                sender = MessageSender.Them,
                timestamp = "1:04 PM",
                media = listOf(link),
            ),
            ChatMessage(
                id = "mid.once",
                text = "",
                sender = MessageSender.Them,
                timestamp = "1:05 PM",
                media = listOf(viewOnce),
            ),
            ChatMessage(
                id = "mid.reel",
                text = "",
                sender = MessageSender.Them,
                senderName = "",
                timestamp = "1:06 PM",
                media = listOf(reel),
            ),
            ChatMessage(
                id = "mid.sticker",
                text = "",
                sender = MessageSender.Them,
                senderName = "Sofia Rossi",
                timestamp = "1:07 PM",
                media = listOf(sticker),
            ),
            ChatMessage(
                id = "mid.gif",
                text = "",
                sender = MessageSender.Me,
                timestamp = "1:08 PM",
                media = listOf(gif),
            ),
            ChatMessage(
                id = "local:1",
                text = "",
                sender = MessageSender.Me,
                timestamp = "1:09 PM",
                pending = true,
                media = listOf(pendingPhoto),
            ),
            ChatMessage(
                id = "mid.reaction",
                text = "Reacted ❤️",
                sender = MessageSender.Them,
                timestamp = "1:10 PM",
                kind = "reaction-log",
                media = listOf(pendingPhoto),
            ),
        )

        val request = buildChatMediaViewerRequest(
            threadId = "thread-42",
            messages = messages,
            tappedMessageId = "mid.video",
            tappedMedia = clip,
            selfSenderName = "You",
            fallbackSenderName = "Maya Chen",
        )

        assertEquals("thread-42", request.threadId)
        assertEquals(2, request.initialIndex)
        assertEquals(6, request.items.size)
        assertEquals(
            listOf("mid.album:0", "mid.album:1", "mid.video:0", "mid.reel:0", "mid.sticker:0", "mid.gif:0"),
            request.items.map { it.key },
        )
        assertEquals(
            listOf("You", "You", "Dev Patel", "Maya Chen", "Sofia Rossi", "You"),
            request.items.map { it.senderName },
        )
        assertEquals(
            listOf("1:01 PM", "1:01 PM", "1:03 PM", "1:06 PM", "1:07 PM", "1:08 PM"),
            request.items.map { it.timestamp },
        )
    }

    @Test
    fun chatMediaViewerRequestTargetsExactPartInsideMultiAttachmentMessage() {
        val photo1 = MessageMedia(type = "image", url = "https://cdn.test/a.jpg", partIndex = 0)
        val photo2 = MessageMedia(type = "image", url = "https://cdn.test/b.jpg", partIndex = 1)
        val photo3 = MessageMedia(type = "image", url = "https://cdn.test/c.jpg", partIndex = 0)
        val messages = listOf(
            ChatMessage(
                id = "mid.1",
                text = "",
                sender = MessageSender.Me,
                timestamp = "2:00 PM",
                media = listOf(photo1, photo2),
            ),
            ChatMessage(
                id = "mid.2",
                text = "",
                sender = MessageSender.Them,
                senderName = "Dev Patel",
                timestamp = "2:05 PM",
                media = listOf(photo3),
            ),
        )

        val request = buildChatMediaViewerRequest(
            threadId = "thread-1",
            messages = messages,
            tappedMessageId = "mid.1",
            tappedMedia = photo2,
            selfSenderName = "You",
            fallbackSenderName = "Dev Patel",
        )

        assertEquals(1, request.initialIndex)
        assertEquals(listOf("mid.1:0", "mid.1:1", "mid.2:0"), request.items.map { it.key })
    }

    @Test
    fun chatMediaViewerRequestFallsBackToTappedItemWhenMissingFromMessages() {
        val photo = MessageMedia(type = "image", url = "https://cdn.test/solo.jpg", partIndex = 0)

        val request = buildChatMediaViewerRequest(
            threadId = "thread-9",
            messages = emptyList(),
            tappedMessageId = "mid.solo",
            tappedMedia = photo,
            selfSenderName = "You",
            fallbackSenderName = "Maya Chen",
        )

        assertEquals("thread-9", request.threadId)
        assertEquals(0, request.initialIndex)
        assertEquals(1, request.items.size)
        assertEquals("mid.solo", request.items.first().messageId)
        assertEquals("Maya Chen", request.items.first().senderName)
        assertEquals("", request.items.first().timestamp)
    }

    @Test
    fun pageIndicatorWindowBoundsLargeGalleriesAndSignalsOverflow() {
        val small = pageIndicatorWindow(currentPage = 2, pageCount = 4, maxVisible = 7)
        assertEquals(0, small.startIndex)
        assertEquals(3, small.endIndex)
        assertFalse(small.hasLeadingOverflow)
        assertFalse(small.hasTrailingOverflow)

        val start = pageIndicatorWindow(currentPage = 0, pageCount = 12, maxVisible = 7)
        assertEquals(0, start.startIndex)
        assertEquals(6, start.endIndex)
        assertFalse(start.hasLeadingOverflow)
        assertTrue(start.hasTrailingOverflow)

        val middle = pageIndicatorWindow(currentPage = 6, pageCount = 12, maxVisible = 7)
        assertEquals(3, middle.startIndex)
        assertEquals(9, middle.endIndex)
        assertTrue(middle.hasLeadingOverflow)
        assertTrue(middle.hasTrailingOverflow)

        val end = pageIndicatorWindow(currentPage = 11, pageCount = 12, maxVisible = 7)
        assertEquals(5, end.startIndex)
        assertEquals(11, end.endIndex)
        assertTrue(end.hasLeadingOverflow)
        assertFalse(end.hasTrailingOverflow)
    }
}
