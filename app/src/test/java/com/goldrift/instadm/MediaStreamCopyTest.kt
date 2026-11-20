package com.goldrift.instadm

import com.goldrift.instadm.data.StreamCopyResult
import com.goldrift.instadm.data.copyStreamWithLimit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File

class MediaStreamCopyTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun copiesBytesToFileWithinLimit() {
        val payload = ByteArray(150_000) { (it % 251).toByte() }
        val target = File(tempFolder.root, "video.mp4")

        val result = copyStreamWithLimit(ByteArrayInputStream(payload), target, maxBytes = 200_000)

        assertEquals(StreamCopyResult.Success(150_000L), result)
        assertArrayEquals(payload, target.readBytes())
    }

    @Test
    fun rejectsEmptyStreamAndLeavesNoTargetFile() {
        val target = File(tempFolder.root, "empty.jpg")

        val result = copyStreamWithLimit(ByteArrayInputStream(ByteArray(0)), target, maxBytes = 1024)

        assertEquals(StreamCopyResult.Empty, result)
        assertFalse(target.exists())
    }

    @Test
    fun stopsEarlyWhenLimitExceededAndRemovesPartialFile() {
        val payload = ByteArray(200_000) { 7 }
        val target = File(tempFolder.root, "huge.mp4")

        val result = copyStreamWithLimit(ByteArrayInputStream(payload), target, maxBytes = 100_000)

        assert(result is StreamCopyResult.TooLarge)
        assertFalse(target.exists())
    }
}
