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
    fun keepsTheViewersSendTimeWhenTheServerAcksLater() {
        assertEquals(1_000, sentTimestamp(pending, fromViewer = true, serverMs = 5_000))
    }

    @Test
    fun takesTheServerTimeWithoutAStoredRow() {
        assertEquals(5_000, sentTimestamp(null, fromViewer = true, serverMs = 5_000))
    }

    @Test
    fun incomingMessagesKeepTheServerTime() {
        assertEquals(5_000, sentTimestamp(pending, fromViewer = false, serverMs = 5_000))
    }

    @Test
    fun ignoresAStoredZero() {
        assertEquals(
            5_000,
            sentTimestamp(pending.copy(timestampMs = 0), fromViewer = true, serverMs = 5_000),
        )
    }
}
