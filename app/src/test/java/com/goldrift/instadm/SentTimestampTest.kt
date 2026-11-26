package com.goldrift.instadm

import com.goldrift.instadm.data.db.MessageEntity
import com.goldrift.instadm.data.sentTimestamp
import org.junit.Assert.assertEquals
import org.junit.Test

class SentTimestampTest {
    private val pending = MessageEntity(
        id = "local:1",
        threadId = "thread",
        timestampMs = 1_000,
        isFromViewer = true,
    )

    @Test
    fun keepsTheViewersSendTimeWhilePendingSiblingsWaitBehindIt() {
        assertEquals(
            1_000,
            sentTimestamp(pending, fromViewer = true, serverMs = 5_000, hasPendingSiblings = true),
        )
    }

    @Test
    fun adoptsServerTimeOnceNoPendingSiblingsRemain() {
        assertEquals(
            5_000,
            sentTimestamp(pending, fromViewer = true, serverMs = 5_000, hasPendingSiblings = false),
        )
    }

    @Test
    fun adoptsEarlierServerTimeWhenLocalClockIsAhead() {
        val ahead = pending.copy(timestampMs = 5_200)
        assertEquals(
            5_000,
            sentTimestamp(ahead, fromViewer = true, serverMs = 5_000, hasPendingSiblings = true),
        )
        assertEquals(
            5_000,
            sentTimestamp(ahead, fromViewer = true, serverMs = 5_000, hasPendingSiblings = false),
        )
    }

    @Test
    fun takesTheServerTimeWithoutAStoredRow() {
        assertEquals(5_000, sentTimestamp(null, fromViewer = true, serverMs = 5_000))
    }

    @Test
    fun incomingMessagesKeepTheServerTime() {
        assertEquals(
            5_000,
            sentTimestamp(pending, fromViewer = false, serverMs = 5_000, hasPendingSiblings = true),
        )
    }

    @Test
    fun ignoresAStoredZero() {
        assertEquals(
            5_000,
            sentTimestamp(
                pending.copy(timestampMs = 0),
                fromViewer = true,
                serverMs = 5_000,
                hasPendingSiblings = true,
            ),
        )
    }
}
