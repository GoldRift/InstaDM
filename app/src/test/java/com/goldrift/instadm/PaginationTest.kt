package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.mergeOlderMessages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PaginationTest {
    private fun message(
        id: String,
        timestampMs: Long,
        seen: Boolean = false,
        pending: Boolean = false,
    ) = ChatMessage(
        id = id,
        text = id,
        sender = MessageSender.Them,
        timestamp = "",
        timestampMs = timestampMs,
        seen = seen,
        pending = pending,
    )

    @Test
    fun prependsTheOlderPageOldestFirst() {
        val existing = listOf(message("mid.3", 3_000), message("mid.4", 4_000))
        val older = listOf(message("mid.1", 1_000), message("mid.2", 2_000))

        val merged = mergeOlderMessages(existing, older)

        assertEquals(listOf("mid.1", "mid.2", "mid.3", "mid.4"), merged.map { it.id })
    }

    @Test
    fun dropsMessagesThePagesShareAtTheCursorBoundary() {
        val existing = listOf(message("mid.2", 2_000), message("mid.3", 3_000))
        val older = listOf(message("mid.1", 1_000), message("mid.2", 2_000))

        val merged = mergeOlderMessages(existing, older)

        assertEquals(listOf("mid.1", "mid.2", "mid.3"), merged.map { it.id })
    }

    @Test
    fun keepsExistingInstancesSoPendingAndSeenSurvive() {
        val pending = message("mid.2", 2_000, pending = true)
        val seen = message("mid.3", 3_000, seen = true)
        val merged = mergeOlderMessages(
            existing = listOf(pending, seen),
            older = listOf(
                message("mid.1", 1_000),
                message("mid.2", 2_000),
                message("mid.3", 3_000),
            ),
        )

        assertEquals(listOf("mid.1", "mid.2", "mid.3"), merged.map { it.id })
        assertSame(pending, merged[1])
        assertSame(seen, merged[2])
        assertTrue(merged[1].pending)
        assertTrue(merged[2].seen)
    }

    @Test
    fun deduplicatesWithinTheOlderPage() {
        val merged = mergeOlderMessages(
            existing = listOf(message("mid.2", 2_000)),
            older = listOf(message("mid.1", 1_000), message("mid.1", 1_000)),
        )

        assertEquals(listOf("mid.1", "mid.2"), merged.map { it.id })
    }

    @Test
    fun emptyPageLeavesTheListUntouched() {
        val existing = listOf(message("mid.1", 1_000))

        assertSame(existing, mergeOlderMessages(existing, emptyList()))
    }

    @Test
    fun pageWithOnlyKnownMessagesLeavesTheListUntouched() {
        val existing = listOf(message("mid.1", 1_000))

        assertSame(existing, mergeOlderMessages(existing, listOf(message("mid.1", 1_000))))
    }

    @Test
    fun prependedPageKeepsChronologicalOrder() {
        val existing = listOf(message("mid.4", 4_000), message("mid.5", 5_000))
        val older = listOf(message("mid.1", 1_000), message("mid.2", 2_000), message("mid.3", 3_000))

        val timestamps = mergeOlderMessages(existing, older).map { it.timestampMs }

        assertEquals(timestamps.sorted(), timestamps)
        assertFalse(timestamps.isEmpty())
    }
}
