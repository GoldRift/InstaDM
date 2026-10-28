package com.goldrift.instadm

import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.data.waveformJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaModelsTest {
    @Test
    fun treatsGifTypeAsVideoEvenWithoutAnMp4Suffix() {
        val media = MessageMedia(
            type = "gif",
            url = "https://cdn.example/animated",
            previewUrl = "https://cdn.example/animated-preview.jpg",
        )

        assertTrue(media.isVideo)
        assertTrue(media.isVisual)
        assertEquals("https://cdn.example/animated-preview.jpg", media.thumbnailUrl)
    }

    @Test
    fun treatsMp4FallbackUrlAsVideo() {
        val media = MessageMedia(
            type = "media",
            url = "",
            fallbackUrl = "https://cdn.example/video.mp4?token=abc",
        )

        assertTrue(media.isVideo)
        assertTrue(media.isVisual)
    }

    @Test
    fun doesNotUseVideoUrlAsAnImageThumbnail() {
        val media = MessageMedia(
            type = "video",
            url = "https://cdn.example/clip.mp4",
        )

        assertTrue(media.isVideo)
        assertEquals("", media.thumbnailUrl)
    }

    @Test
    fun prefersPreviewForThumbnailAndOriginalForViewing() {
        val media = MessageMedia(
            type = "image",
            url = "https://cdn.example/full.jpg",
            previewUrl = "https://cdn.example/preview.jpg",
        )

        assertEquals("https://cdn.example/preview.jpg", media.thumbnailUrl)
        assertEquals("https://cdn.example/full.jpg", media.fullUrl)
    }

    @Test
    fun clampsExtremeAspectRatios() {
        val tall = MessageMedia(type = "image", url = "u", width = 100, height = 1000)
        val wide = MessageMedia(type = "image", url = "u", width = 1000, height = 100)
        val square = MessageMedia(type = "image", url = "u")

        assertEquals(0.6f, tall.aspectRatio)
        assertEquals(1.8f, wide.aspectRatio)
        assertEquals(1f, square.aspectRatio)
    }

    @Test
    fun audioIsNotVisual() {
        val media = MessageMedia(type = "audio", url = "https://cdn.example/audio.mp4")

        assertFalse(media.isVisual)
        assertFalse(media.isVideo)
    }

    @Test
    fun recognizesVoiceNotes() {
        val downloaded = MessageMedia(
            type = "audio",
            url = "https://cdn.example/voice.m4a",
            localPath = "/tmp/voice.m4a",
            waveform = listOf(0.2f, 0.8f),
        )
        val remote = MessageMedia(type = "audio", url = "https://cdn.example/voice.m4a")

        assertTrue(downloaded.isVoiceNote)
        assertTrue(remote.isVoiceNote)
        assertFalse(MessageMedia(type = "image", url = "u").isVoiceNote)
        assertEquals(listOf(0.2f, 0.8f), downloaded.waveform)
    }

    @Test
    fun viewOnceMediaIsNotRenderedInline() {
        val media = MessageMedia(
            type = "image",
            url = "",
            viewOnce = true,
            viewMode = "viewed",
        )

        assertTrue(media.viewOnce)
        assertFalse(media.isVisual)
        assertEquals("", media.thumbnailUrl)
        assertEquals("", media.fullUrl)
    }

    @Test
    fun serializesWaveformAmplitudes() {
        assertEquals("", waveformJson(emptyList()))
        assertEquals("[0.25,1.0]", waveformJson(listOf(0.25f, 2f)))
        assertEquals("[0.0,0.5]", waveformJson(listOf(-1f, 0.5f)))
    }

    @Test
    fun reelsArePlayableAndUseAPortraitCard() {
        val reel = MessageMedia(
            type = "reel",
            url = "",
            previewUrl = "https://cdn.example/reel.jpg",
            targetId = "123456789",
            shortcode = "ABC_123",
        )

        assertTrue(reel.isReel)
        assertTrue(reel.isVideo)
        assertTrue(reel.isVisual)
        assertEquals("https://cdn.example/reel.jpg", reel.thumbnailUrl)
        assertEquals(9f / 16f, reel.aspectRatio)
        assertEquals("123456789", reel.targetId)
        assertEquals("ABC_123", reel.shortcode)
    }

    @Test
    fun linksAreNotVideoAndUseOnlyThePreviewForTheThumbnail() {
        val link = MessageMedia(
            type = "link",
            url = "https://www.instagram.com/p/POST123/",
            previewUrl = "https://cdn.example/post.jpg",
        )

        assertTrue(link.isLink)
        assertFalse(link.isVideo)
        assertFalse(link.isVisual)
        assertEquals("https://cdn.example/post.jpg", link.thumbnailUrl)
        assertEquals("https://www.instagram.com/p/POST123/", link.fullUrl)
        assertEquals("https://www.instagram.com/p/POST123/", link.shareUrl)
    }

    @Test
    fun shareUrlPrefersTheTargetUrlCarriedByAReelShare() {
        val reel = MessageMedia(
            type = "reel",
            url = "",
            previewUrl = "https://cdn.example/reel.jpg",
            targetId = "123456789",
            shortcode = "ABC_123",
            targetUrl = "https://www.instagram.com/reel/ABC_123/",
        )

        assertEquals("https://www.instagram.com/reel/ABC_123/", reel.shareUrl)
    }

    @Test
    fun shareUrlRebuildsTheReelLinkFromItsShortcode() {
        val reel = MessageMedia(type = "reel", url = "", targetId = "123456789", shortcode = "ABC_123")
        val withoutIdentifiers = MessageMedia(type = "reel", url = "", targetId = "123456789")

        assertEquals("https://www.instagram.com/reel/ABC_123/", reel.shareUrl)
        assertEquals("", withoutIdentifiers.shareUrl)
    }

    @Test
    fun plainAttachmentsHaveNoShareUrl() {
        val image = MessageMedia(
            type = "image",
            url = "https://cdn.example/image.jpg",
            shortcode = "ABC_123",
        )

        assertEquals("", image.shareUrl)
    }

    @Test
    fun linkWithoutPreviewDoesNotUseThePageAsAnImage() {
        val link = MessageMedia(type = "link", url = "https://www.instagram.com/p/POST123/")

        assertEquals("", link.thumbnailUrl)
    }

}
