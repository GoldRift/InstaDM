package com.goldrift.instadm

import com.goldrift.instadm.data.stageMediaCopies
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class ShareMediaStagingTest {
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("share-staging-test-").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun stagesDistinctCopiesWithSinglePrimaryRead() = runBlocking {
        val outboxDir = File(tempDir, "outbox")
        val payload = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        var writeCalls = 0

        val files = stageMediaCopies(
            outboxDir = outboxDir,
            otids = listOf("otid-1", "otid-2", "otid-3"),
            extension = "jpg",
        ) { primary ->
            writeCalls++
            primary.writeBytes(payload)
        }

        assertEquals(1, writeCalls)
        assertEquals(
            listOf(
                File(outboxDir, "otid-1.jpg"),
                File(outboxDir, "otid-2.jpg"),
                File(outboxDir, "otid-3.jpg"),
            ),
            files,
        )
        files.forEach { staged ->
            assertTrue(staged.isFile)
            assertArrayEquals(payload, staged.readBytes())
        }
    }

    @Test
    fun deletingFirstCopyLeavesRemainingCopiesIntact() = runBlocking {
        val outboxDir = File(tempDir, "outbox")
        val payload = "shared-photo-bytes".encodeToByteArray()

        val files = stageMediaCopies(
            outboxDir = outboxDir,
            otids = listOf("otid-a", "otid-b", "otid-c"),
            extension = "jpg",
        ) { primary ->
            primary.writeBytes(payload)
        }

        val moved = File(tempDir, "cache-a.jpg")
        assertTrue(files[0].renameTo(moved))
        assertFalse(files[0].exists())

        assertTrue(files[1].isFile)
        assertTrue(files[2].isFile)
        assertArrayEquals(payload, files[1].readBytes())
        assertArrayEquals(payload, files[2].readBytes())
    }

    @Test
    fun cleansUpPartialAndCreatedFilesWhenWritePrimaryFails() = runBlocking {
        val outboxDir = File(tempDir, "outbox")
        var caught: IOException? = null

        try {
            stageMediaCopies(
                outboxDir = outboxDir,
                otids = listOf("otid-1", "otid-2"),
                extension = "mp4",
            ) { primary ->
                primary.writeBytes(byteArrayOf(42))
                throw IOException("read failed")
            }
        } catch (error: IOException) {
            caught = error
        }

        assertEquals("read failed", caught?.message)
        assertEquals(emptyList<File>(), outboxDir.listFiles()?.toList().orEmpty())
    }

    @Test
    fun returnsEmptyWithoutCallingWriterWhenOtidsEmpty() = runBlocking {
        val outboxDir = File(tempDir, "outbox")
        var writeCalls = 0

        val files = stageMediaCopies(
            outboxDir = outboxDir,
            otids = emptyList(),
            extension = "jpg",
        ) {
            writeCalls++
        }

        assertEquals(0, writeCalls)
        assertEquals(emptyList<File>(), files)
    }
}
