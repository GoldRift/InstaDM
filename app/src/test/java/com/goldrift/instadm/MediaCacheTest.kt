package com.goldrift.instadm

import com.goldrift.instadm.data.pruneMediaCache
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class MediaCacheTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun deletesTheLeastRecentlyUsedFilesFirst() {
        val dir = temp.newFolder("media")
        val old = mediaFile(dir, "old.jpg", 100, 1_000)
        val middle = mediaFile(dir, "middle.jpg", 100, 2_000)
        val keep = mediaFile(dir, "keep.jpg", 100, 3_000)

        pruneMediaCache(dir, maxBytes = 250, keep = keep)

        assertFalse(old.exists())
        assertTrue(middle.exists())
        assertTrue(keep.exists())
    }

    @Test
    fun keepsTheFileThatWasJustDownloaded() {
        val dir = temp.newFolder("media")
        val old = mediaFile(dir, "old.jpg", 300, 1_000)
        val keep = mediaFile(dir, "keep.jpg", 300, 2_000)

        pruneMediaCache(dir, maxBytes = 100, keep = keep)

        assertFalse(old.exists())
        assertTrue(keep.exists())
    }

    @Test
    fun leavesTheCacheAloneWhenItFits() {
        val dir = temp.newFolder("media")
        val first = mediaFile(dir, "first.jpg", 100, 1_000)
        val second = mediaFile(dir, "second.jpg", 100, 2_000)

        pruneMediaCache(dir, maxBytes = 10_000, keep = second)

        assertTrue(first.exists())
        assertTrue(second.exists())
    }

    private fun mediaFile(directory: File, name: String, size: Int, modifiedAt: Long): File =
        File(directory, name).apply {
            writeBytes(ByteArray(size))
            setLastModified(modifiedAt)
        }
}
