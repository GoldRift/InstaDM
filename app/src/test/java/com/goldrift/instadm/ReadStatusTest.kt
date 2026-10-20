package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.applyReadReceiptToMessages
import com.goldrift.instadm.ui.chat.canMarkRead
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadStatusTest {
    private fun message(
        id: String,
        sender: MessageSender,
        timestampMs: Long,
        seen: Boolean = false,
        pending: Boolean = false,
    ) = ChatMessage(
        id = id,
        text = id,
        sender = sender,
        timestamp = "",
        timestampMs = timestampMs,
        seen = seen,
        pending = pending,
    )

    @Test
    fun marksViewerMessagesUpToTheOtherParticipantsWatermark() {
        val older = message("mid.older", MessageSender.Me, 1_000)
        val boundary = message("mid.boundary", MessageSender.Me, 2_000)
        val newer = message("mid.newer", MessageSender.Me, 3_000)
        val incoming = message("mid.incoming", MessageSender.Them, 1_500)

        val updated = applyReadReceiptToMessages(
            messages = listOf(older, boundary, newer, incoming),
            watermarkMs = 2_000,
            fromViewer = false,
        )

        assertTrue(updated[0].seen)
        assertTrue(updated[1].seen)
        assertFalse(updated[2].seen)
        assertFalse(updated[3].seen)
    }

    @Test
    fun ignoresTheViewersOwnReceipt() {
        val messages = listOf(message("mid.1", MessageSender.Me, 1_000))

        val updated = applyReadReceiptToMessages(
            messages = messages,
            watermarkMs = 2_000,
            fromViewer = true,
        )

        assertSame(messages, updated)
        assertFalse(updated[0].seen)
    }

    @Test
    fun ignoresAlreadySeenMessagesAndInvalidWatermarks() {
        val messages = listOf(message("mid.1", MessageSender.Me, 1_000, seen = true))

        assertSame(
            messages,
            applyReadReceiptToMessages(messages, 0, fromViewer = false),
        )
        assertSame(
            messages,
            applyReadReceiptToMessages(messages, 2_000, fromViewer = false),
        )
    }

    @Test
    fun onlyIncomingServerMessagesCanBeMarkedRead() {
        assertTrue(message("mid.\$abc", MessageSender.Them, 1).canMarkRead())
        assertTrue(message("1234567890", MessageSender.Them, 1).canMarkRead())
        // The viewer's own messages are already read.
        assertFalse(message("mid.\$abc", MessageSender.Me, 1).canMarkRead())
        assertFalse(message("local-123", MessageSender.Them, 1, pending = true).canMarkRead())
        assertFalse(message("local-123", MessageSender.Them, 1).canMarkRead())
        assertFalse(message("message-0", MessageSender.Them, 1).canMarkRead())
        assertFalse(message("mid.\$abc", MessageSender.Them, 1, pending = true).canMarkRead())
        assertFalse(message("", MessageSender.Them, 1).canMarkRead())
    }
}
