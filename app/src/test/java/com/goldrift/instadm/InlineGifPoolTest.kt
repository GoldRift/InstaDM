package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.InlineGifPool
import com.goldrift.instadm.ui.chat.resolveCachedMediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InlineGifPoolTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun capsActivePlayersToMostRecentlyVisibleItems() {
        val pool = InlineGifPool(maxActive = 2)

        pool.setVisible("m1:0", true)
        pool.setVisible("m2:0", true)
        assertEquals(setOf("m1:0", "m2:0"), pool.activeKeys)
        assertTrue(pool.isActive("m1:0"))
        assertTrue(pool.isActive("m2:0"))

        // A third visible GIF evicts the oldest one so active count stays capped at 2.
        pool.setVisible("m3:0", true)
        assertEquals(setOf("m2:0", "m3:0"), pool.activeKeys)
        assertFalse(pool.isActive("m1:0"))
        assertTrue(pool.isActive("m2:0"))
        assertTrue(pool.isActive("m3:0"))
    }

    @Test
    fun releasingActiveItemPromotesWaitingVisibleItem() {
        val pool = InlineGifPool(maxActive = 2)
        pool.setVisible("m1:0", true)
        pool.setVisible("m2:0", true)
        pool.setVisible("m3:0", true)
        assertEquals(setOf("m2:0", "m3:0"), pool.activeKeys)

        // When m3 scrolls away, m1 is still visible and reclaims the freed slot.
        pool.setVisible("m3:0", false)
        assertEquals(setOf("m1:0", "m2:0"), pool.activeKeys)
        assertTrue(pool.isActive("m1:0"))
        assertFalse(pool.isActive("m3:0"))
    }

    @Test
    fun duplicateVisibilityAndUnknownRemovalsAreIdempotent() {
        val pool = InlineGifPool(maxActive = 2)
        pool.setVisible("missing:0", false)
        assertEquals(emptySet<String>(), pool.activeKeys)

        pool.setVisible("m1:0", true)
        pool.setVisible("m2:0", true)
        pool.setVisible("m1:0", true)
        // Re-reporting m1 as visible promotes it to most recent, so m2 evicts next.
        pool.setVisible("m3:0", true)
        assertEquals(setOf("m1:0", "m3:0"), pool.activeKeys)

        pool.clear()
        assertEquals(emptySet<String>(), pool.activeKeys)
    }

    @Test
    fun resolveCachedMediaFileRequiresExistingNonEmptyFile() {
        assertNull(resolveCachedMediaFile(""))
        assertNull(resolveCachedMediaFile("   "))

        val missing = File(tempFolder.root, "missing.mp4")
        assertNull(resolveCachedMediaFile(missing.absolutePath))

        val empty = tempFolder.newFile("empty.mp4")
        assertNull(resolveCachedMediaFile(empty.absolutePath))

        val directory = tempFolder.newFolder("dir.mp4")
        assertNull(resolveCachedMediaFile(directory.absolutePath))

        val valid = tempFolder.newFile("gif.mp4").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        assertEquals(valid.absolutePath, resolveCachedMediaFile(valid.absolutePath)?.absolutePath)
    }
}
