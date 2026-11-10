package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.ADMIN_GROUP_KIND
import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.startsGroupSenderRun
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListTest {
    private fun message(
        id: String,
        sender: MessageSender = MessageSender.Them,
        senderFbid: Long = 1,
        kind: String = "text",
    ) = ChatMessage(
        id = id,
        text = id,
        sender = sender,
        timestamp = "",
        senderFbid = senderFbid,
        kind = kind,
    )

    @Test
    fun aNoticeBreaksAGroupSenderRun() {
        val previous = message("mid.1")
        val notice = message("mid.notice", kind = ADMIN_GROUP_KIND)
        val next = message("mid.2")

        assertFalse(
            startsGroupSenderRun(
                previous = previous,
                message = next,
                isGroup = true,
                showDayHeader = false,
            ),
        )
        assertTrue(
            startsGroupSenderRun(
                previous = notice,
                message = next,
                isGroup = true,
                showDayHeader = false,
            ),
        )
    }

    @Test
    fun aNewDayOrSenderStillStartsARun() {
        val previous = message("mid.1")
        val next = message("mid.2")

        assertTrue(
            startsGroupSenderRun(
                previous = previous,
                message = next,
                isGroup = true,
                showDayHeader = true,
            ),
        )
        assertTrue(
            startsGroupSenderRun(
                previous = previous,
                message = message("mid.3", senderFbid = 2),
                isGroup = true,
                showDayHeader = false,
            ),
        )
        assertFalse(
            startsGroupSenderRun(
                previous = previous,
                message = next,
                isGroup = false,
                showDayHeader = false,
            ),
        )
    }
}
