package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.ADMIN_GROUP_KIND
import com.goldrift.instadm.ui.chat.ADMIN_KIND
import com.goldrift.instadm.ui.chat.ADMIN_RESTRICTED_KIND
import com.goldrift.instadm.ui.chat.CALL_KIND
import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.applyReadReceiptToMessages
import com.goldrift.instadm.ui.chat.canMarkRead
import com.goldrift.instadm.ui.chat.canRevealTimestamp
import com.goldrift.instadm.ui.chat.lastOutgoingMessageId
import com.goldrift.instadm.ui.chat.publishesReadReceipt
import com.goldrift.instadm.ui.chat.requestReadWatermark
import com.goldrift.instadm.ui.chat.shouldPublishReadReceipt
import com.goldrift.instadm.ui.chat.showsReadReceipt
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
        timestamp: String = "",
    ) = ChatMessage(
        id = id,
        text = id,
        sender = sender,
        timestamp = timestamp,
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
    fun inboxThreadsFollowTheReceiptSetting() {
        assertTrue(publishesReadReceipt("", receiptsEnabled = true, fromSend = true))
        assertTrue(publishesReadReceipt("", receiptsEnabled = true, fromSend = false))
        assertTrue(publishesReadReceipt("", receiptsEnabled = false, fromSend = true))
        assertFalse(publishesReadReceipt("", receiptsEnabled = false, fromSend = false))
    }

    @Test
    fun messageRequestsNeverPublishAReadReceipt() {
        assertFalse(publishesReadReceipt("PENDING", receiptsEnabled = true, fromSend = true))
        assertFalse(publishesReadReceipt("PENDING", receiptsEnabled = true, fromSend = false))
        assertFalse(publishesReadReceipt("PENDING", receiptsEnabled = false, fromSend = true))
        assertFalse(publishesReadReceipt("PENDING", receiptsEnabled = false, fromSend = false))
        assertFalse(publishesReadReceipt("SPAM", receiptsEnabled = true, fromSend = true))
        assertFalse(publishesReadReceipt("SPAM", receiptsEnabled = true, fromSend = false))
        assertFalse(publishesReadReceipt("SPAM", receiptsEnabled = false, fromSend = true))
        assertFalse(publishesReadReceipt("SPAM", receiptsEnabled = false, fromSend = false))
    }

    @Test
    fun onlyTheNewestOutgoingMessageShowsTheReadReceipt() {
        val older = message("mid.older", MessageSender.Me, 1_000)
        val incoming = message("mid.incoming", MessageSender.Them, 2_000)
        val newest = message("mid.newest", MessageSender.Me, 3_000)
        val messages = listOf(older, incoming, newest)

        val lastOutgoingId = lastOutgoingMessageId(messages)

        assertEquals("mid.newest", lastOutgoingId)
        assertTrue(newest.showsReadReceipt(lastOutgoingId))
        assertFalse(older.showsReadReceipt(lastOutgoingId))
        assertFalse(incoming.showsReadReceipt(lastOutgoingId))
    }

    @Test
    fun nonAdminMessagesWithATimeCanRevealTheirTimestamp() {
        assertTrue(message("mid.in", MessageSender.Them, 1_000).canRevealTimestamp())
        assertTrue(message("mid.out", MessageSender.Me, 2_000).canRevealTimestamp())
        assertTrue(message("mid.fallback", MessageSender.Them, 0, timestamp = "8:14 PM").canRevealTimestamp())
        assertFalse(message("mid.notime", MessageSender.Me, 0, timestamp = "").canRevealTimestamp())
    }

    @Test
    fun adminNoticesCannotRevealATimestamp() {
        val kinds = listOf(ADMIN_KIND, ADMIN_GROUP_KIND, ADMIN_RESTRICTED_KIND, CALL_KIND)
        for (kind in kinds) {
            val notice = message("mid.$kind", MessageSender.Them, 1_000, kind = kind, timestamp = "8:14 PM")
            assertFalse(kind, notice.canRevealTimestamp())
        }
    }

    @Test
    fun adminNoticesDoNotOwnTheReadReceipt() {
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
        assertTrue(outgoing.showsReadReceipt(lastOutgoingId))
        assertFalse(restricted.showsReadReceipt(lastOutgoingId))
        assertFalse(call.showsReadReceipt(lastOutgoingId))
    }

    @Test
    fun threadsWithoutOutgoingMessagesHaveNoReceiptOwner() {
        val messages = listOf(message("mid.1", MessageSender.Them, 1_000))

        assertEquals(null, lastOutgoingMessageId(messages))
        assertEquals(null, lastOutgoingMessageId(emptyList()))
        assertFalse(messages[0].showsReadReceipt(null))
    }
}
