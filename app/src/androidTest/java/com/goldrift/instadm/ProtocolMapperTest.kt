package com.goldrift.instadm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goldrift.instadm.ui.chat.Contact
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.mapThread
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtocolMapperTest {
    @Test
    fun mapsMessageReactions() {
        val json = JSONObject(
            """
            {
              "thread": { "thread_fbid": "thread-fbid" },
              "messages": [
                {
                  "id": "mid.1",
                  "text": "hello",
                  "is_from_viewer": true,
                  "timestamp_ms": 1700000000000,
                  "kind": "text",
                  "reactions": [
                    { "emoji": "❤️", "sender_fbid": 111, "is_from_viewer": true },
                    { "emoji": "😂", "sender_fbid": 222 }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        val conversation = Conversation(
            id = "thread-1",
            contact = Contact(id = "thread-1", name = "Test User", initials = "TU"),
        )

        val mapped = mapThread(json, conversation)
        val reactions = mapped.messages.single().reactions

        assertEquals(2, reactions.size)
        assertEquals("❤️", reactions[0].emoji)
        assertEquals(111L, reactions[0].senderFbid)
        assertTrue(reactions[0].isFromViewer)
        assertEquals("😂", reactions[1].emoji)
        assertEquals(222L, reactions[1].senderFbid)
        assertEquals(false, reactions[1].isFromViewer)
    }
}
