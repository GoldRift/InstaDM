package com.goldrift.instadm

import com.goldrift.instadm.data.MessageMedia
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
}
