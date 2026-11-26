package com.goldrift.instadm

import com.goldrift.instadm.data.REACTION_LOG_KIND
import com.goldrift.instadm.data.RealtimeEvent
import com.goldrift.instadm.data.db.PinnedMessageEntity
import com.goldrift.instadm.data.parsePinnedMessages
import com.goldrift.instadm.ui.chat.ADMIN_KIND
import com.goldrift.instadm.ui.chat.ActiveThreadState
import com.goldrift.instadm.ui.chat.CALL_KIND
import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.Contact
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.PinnedMessage
import com.goldrift.instadm.ui.chat.canPin
import com.goldrift.instadm.ui.chat.toPinnedMessage
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedMessagesTest {
    @Test
    fun canPinAllowsServerMessagesFromEitherSideAndBlocksNonMessages() {
        assertTrue(
            ChatMessage(
                id = "mid.1",
                text = "meet at 8",
                sender = MessageSender.Them,
                timestamp = "",
                kind = "text",
            ).canPin(),
        )
        assertTrue(
            ChatMessage(
                id = "mid.2",
                text = "",
                sender = MessageSender.Me,
                timestamp = "",
                kind = "image",
            ).canPin(),
        )
        assertFalse(
            ChatMessage(
                id = "local:1",
                text = "sending",
                sender = MessageSender.Me,
                timestamp = "",
                pending = true,
                kind = "text",
            ).canPin(),
        )
        assertFalse(
            ChatMessage(
                id = "mid.3",
                text = "Joined the chat",
                sender = MessageSender.Them,
                timestamp = "",
                kind = ADMIN_KIND,
            ).canPin(),
        )
        assertFalse(
            ChatMessage(
                id = "mid.4",
                text = "Started a call",
                sender = MessageSender.Them,
                timestamp = "",
                kind = CALL_KIND,
            ).canPin(),
        )
        assertFalse(
            ChatMessage(
                id = "mid.5",
                text = "Liked a message",
                sender = MessageSender.Them,
                timestamp = "",
                kind = REACTION_LOG_KIND,
            ).canPin(),
        )
    }

    @Test
    fun realtimeEventParsesMessagePinIds() {
        val raw = """
            {
              "type": "message.pin",
              "thread_id": "t1",
              "seq_id": 55,
              "pinned_message_ids": ["mid.1", "mid.2"],
              "unpinned_message_ids": ["mid.0"]
            }
        """.trimIndent()

        val event = RealtimeEvent.fromJson(raw)
        assertNotNull(event)
        assertEquals("message.pin", event!!.type)
        assertEquals("t1", event.threadId)
        assertEquals(listOf("mid.1", "mid.2"), event.pinnedMessageIds)
        assertEquals(listOf("mid.0"), event.unpinnedMessageIds)
    }

    @Test
    fun parsePinnedMessagesPreservesOrderAndSkipsBlankIds() {
        val array = JSONArray(
            """
            [
              {"id": "mid.1", "sender_fbid": 222, "sender_name": "Maya", "is_from_viewer": false, "timestamp_ms": 1700000001000, "kind": "text", "text": "address is 42 Pine St"},
              {"id": "", "text": "ignored"},
              {"id": "mid.2", "sender_fbid": 111, "is_from_viewer": true, "timestamp_ms": 1700000002000, "kind": "image", "media_count": 1}
            ]
            """.trimIndent(),
        )

        val entities = parsePinnedMessages(array, threadId = "t1")
        assertEquals(2, entities.size)
        assertEquals("mid.1", entities[0].messageId)
        assertEquals("address is 42 Pine St", entities[0].text)
        assertEquals(0, entities[0].pinnedOrder)
        assertEquals("mid.2", entities[1].messageId)
        assertTrue(entities[1].isFromViewer)
        assertEquals("image", entities[1].kind)
        assertEquals(1, entities[1].pinnedOrder)

        val mapped = entities[0].toPinnedMessage()
        assertEquals("mid.1", mapped.messageId)
        assertEquals("Maya", mapped.senderName)
        assertEquals(MessageSender.Them, mapped.sender)
        assertEquals("address is 42 Pine St", mapped.text)
    }

    @Test
    fun activeThreadStateMergesPinnedMessagesAndMarksMatchingMessages() {
        val m1 = ChatMessage("mid.1", "first", MessageSender.Them, "")
        val m2 = ChatMessage("mid.2", "second", MessageSender.Me, "")
        val pin = PinnedMessage("mid.1", "first", MessageSender.Them, senderName = "Maya")
        val state = ActiveThreadState(
            threadId = "t1",
            messages = listOf(m1, m2),
            loaded = true,
            pinnedMessages = listOf(pin),
        )
        val base = Conversation(id = "t1", contact = Contact("t1", "Maya", "M"))

        val merged = state.mergeInto(base)

        assertEquals(listOf(pin), merged.pinnedMessages)
        assertTrue(merged.messages[0].isPinned)
        assertFalse(merged.messages[1].isPinned)
    }
}
