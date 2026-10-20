package com.goldrift.instadm

import com.goldrift.instadm.data.MediaPolicy
import com.goldrift.instadm.data.formatMediaBytes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPolicyTest {
    @Test
    fun normalizesProviderAliases() {
        assertEquals("image/jpeg", MediaPolicy.normalizeMimeType("image/jpg", "photo.jpg"))
        assertEquals("audio/mpeg", MediaPolicy.normalizeMimeType("audio/mp3", "voice.mp3"))
        assertEquals("audio/mp4", MediaPolicy.normalizeMimeType("audio/x-m4a", "voice.m4a"))
        assertEquals("image/png", MediaPolicy.normalizeMimeType("IMAGE/PNG; charset=binary", "photo.png"))
    }

    @Test
    fun fallsBackToTheFileExtension() {
        assertEquals("image/webp", MediaPolicy.normalizeMimeType(null, "photo.webp"))
        assertEquals("video/quicktime", MediaPolicy.normalizeMimeType("", "clip.MOV"))
        assertEquals("application/octet-stream", MediaPolicy.normalizeMimeType(null, "upload"))
    }

    @Test
    fun mirrorsTheGoAllowList() {
        assertTrue(MediaPolicy.isAllowed("image/jpeg"))
        assertTrue(MediaPolicy.isAllowed("video/mp4"))
        assertTrue(MediaPolicy.isAllowed("audio/wav"))
        assertTrue(MediaPolicy.isAllowed("application/pdf"))
        assertFalse(MediaPolicy.isAllowed("image/heic"))
        assertFalse(MediaPolicy.isAllowed("audio/aac"))
    }

    @Test
    fun imagesUseTheSmallerLimit() {
        assertEquals(MediaPolicy.MAX_IMAGE_BYTES, MediaPolicy.maxBytes("image/png"))
        assertEquals(MediaPolicy.MAX_MEDIA_BYTES, MediaPolicy.maxBytes("video/mp4"))
        assertEquals(MediaPolicy.MAX_MEDIA_BYTES, MediaPolicy.maxBytes("audio/mpeg"))
        assertEquals(MediaPolicy.MAX_MEDIA_BYTES, MediaPolicy.maxBytes("application/pdf"))
    }

    @Test
    fun mapsMimeTypesToMessageKinds() {
        assertEquals("image", MediaPolicy.kind("image/gif"))
        assertEquals("video", MediaPolicy.kind("video/mp4"))
        assertEquals("audio", MediaPolicy.kind("audio/mpeg"))
        assertEquals("media", MediaPolicy.kind("application/pdf"))
    }

    @Test
    fun formatsLimitsCompactly() {
        assertEquals("8.0 MB", formatMediaBytes(8L * 1024 * 1024))
        assertEquals("25 MB", formatMediaBytes(25L * 1024 * 1024))
        assertEquals("41 MB", formatMediaBytes((41.3 * 1024 * 1024).toLong()))
    }
}
