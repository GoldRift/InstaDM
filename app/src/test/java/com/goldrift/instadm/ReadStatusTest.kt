package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.ADMIN_RESTRICTED_KIND
import com.goldrift.instadm.ui.chat.CALL_KIND
import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.applyReadReceiptToMessages
import com.goldrift.instadm.ui.chat.canMarkRead
import com.goldrift.instadm.ui.chat.lastOutgoingMessageId
import com.goldrift.instadm.ui.chat.requestReadWatermark
import com.goldrift.instadm.ui.chat.shouldPublishReadReceipt
import com.goldrift.instadm.ui.chat.showsStatusTicks
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
        kind: String = "",
    ) = ChatMessage(
        id = id,
        text = id,
        sender = sender,
        timestamp = "",
        timestampMs = timestampMs,
        seen = seen,
        pending = pending,
        kind = kind,
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

    @Test
    fun requestThreadsClearUpToTheNewestIncomingMessage() {
        val messages = listOf(
            message("mid.1", MessageSender.Them, 1_000),
            message("mid.2", MessageSender.Me, 2_000),
            message("mid.3", MessageSender.Them, 3_000),
        )

        assertEquals(3_000, requestReadWatermark(messages))
        assertEquals(0, requestReadWatermark(emptyList()))
        assertEquals(0, requestReadWatermark(listOf(message("mid.4", MessageSender.Me, 4_000))))
    }

    @Test
    fun hiddenReceiptsStillPublishWhenTheViewerSends() {
        assertFalse(shouldPublishReadReceipt(receiptsEnabled = false, fromSend = false))
        assertTrue(shouldPublishReadReceipt(receiptsEnabled = false, fromSend = true))
        assertTrue(shouldPublishReadReceipt(receiptsEnabled = true, fromSend = false))
        assertTrue(shouldPublishReadReceipt(receiptsEnabled = true, fromSend = true))
    }

    @Test
    fun onlyTheNewestOutgoingMessageOwnsThePersistentTicks() {
        val older = message("mid.older", MessageSender.Me, 1_000)
        val incoming = message("mid.incoming", MessageSender.Them, 2_000)
        val newest = message("mid.newest", MessageSender.Me, 3_000)
        val messages = listOf(older, incoming, newest)

        val lastOutgoingId = lastOutgoingMessageId(messages)

        assertEquals("mid.newest", lastOutgoingId)
        assertTrue(newest.showsStatusTicks(lastOutgoingId, revealedId = null))
        assertFalse(older.showsStatusTicks(lastOutgoingId, revealedId = null))
        assertFalse(incoming.showsStatusTicks(lastOutgoingId, revealedId = null))
    }

    @Test
    fun aTappedOutgoingMessageRevealsItsTicks() {
        val older = message("mid.older", MessageSender.Me, 1_000)
        val newest = message("mid.newest", MessageSender.Me, 2_000)
        val lastOutgoingId = lastOutgoingMessageId(listOf(older, newest))

        assertTrue(older.showsStatusTicks(lastOutgoingId, revealedId = "mid.older"))
        // The persistent newest bubble keeps its ticks either way.
        assertTrue(newest.showsStatusTicks(lastOutgoingId, revealedId = "mid.older"))
        // Revealing an incoming message never invents ticks for it.
        assertFalse(
            message("mid.incoming", MessageSender.Them, 3_000)
                .showsStatusTicks(lastOutgoingId, revealedId = "mid.incoming"),
        )
    }

    @Test
    fun adminNoticesDoNotOwnTheViewerTicks() {
        val outgoing = message("mid.out", MessageSender.Me, 1_000)
        val restricted = message(
            "mid.restricted",
            MessageSender.Me,
            2_000,
            kind = ADMIN_RESTRICTED_KIND,
        )
        val call = message("mid.call", MessageSender.Me, 3_000, kind = CALL_KIND)

        val lastOutgoingId = lastOutgoingMessageId(listOf(outgoing, restricted, call))

        assertEquals("mid.out", lastOutgoingId)
        assertTrue(outgoing.showsStatusTicks(lastOutgoingId, revealedId = null))
        assertFalse(restricted.showsStatusTicks(lastOutgoingId, revealedId = restricted.id))
        assertFalse(call.showsStatusTicks(lastOutgoingId, revealedId = call.id))
    }

    @Test
    fun threadsWithoutOutgoingMessagesHaveNoTickOwner() {
        val messages = listOf(message("mid.1", MessageSender.Them, 1_000))

        assertEquals(null, lastOutgoingMessageId(messages))
        assertEquals(null, lastOutgoingMessageId(emptyList()))
        assertFalse(messages[0].showsStatusTicks(null, revealedId = null))
    }
}
