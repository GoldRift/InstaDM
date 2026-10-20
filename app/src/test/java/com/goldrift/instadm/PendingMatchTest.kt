package com.goldrift.instadm

import com.goldrift.instadm.data.RealtimeMessage
import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.matchesPendingSend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingMatchTest {
    private fun pending(
        text: String = "",
        otid: String = "",
    ) = ChatMessage(
        id = "local-1",
        text = text,
        sender = MessageSender.Me,
        timestamp = "12:00 PM",
        pending = true,
        offlineThreadingId = otid,
    )

    private fun incoming(text: String = "", otid: String = "") = RealtimeMessage(
        id = "mid.1",
        offlineThreadingId = otid,
        text = text,
        senderName = "Me",
        isFromViewer = true,
        timestampMs = 0,
        kind = "text",
    )

    @Test
    fun matchesMediaEchoesByOfflineThreadingId() {
        assertTrue(matchesPendingSend(pending(otid = "123"), incoming(otid = "123")))
        assertFalse(matchesPendingSend(pending(otid = "123"), incoming(otid = "456")))
    }

    @Test
    fun fallsBackToNonBlankText() {
        assertTrue(matchesPendingSend(pending(text = "hello"), incoming(text = "hello")))
        assertFalse(matchesPendingSend(pending(text = "hello"), incoming(text = "other")))
    }

    @Test
    fun emptyTextDoesNotMatchAnUnrelatedMediaBubble() {
        assertFalse(matchesPendingSend(pending(), incoming()))
    }

    @Test
    fun ignoresDeliveredAndIncomingBubbles() {
        assertFalse(
            matchesPendingSend(
                pending(text = "hi").copy(pending = false),
                incoming(text = "hi"),
            ),
        )
        assertFalse(
            matchesPendingSend(
                pending(text = "hi").copy(sender = MessageSender.Them),
                incoming(text = "hi"),
            ),
        )
    }
}
