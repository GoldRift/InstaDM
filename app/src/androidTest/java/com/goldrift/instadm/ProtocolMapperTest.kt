package com.goldrift.instadm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goldrift.instadm.data.db.ParticipantEntity
import com.goldrift.instadm.data.db.ThreadEntity
import com.goldrift.instadm.data.db.ThreadWithParticipants
import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.Contact
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.mapThread
import com.goldrift.instadm.ui.chat.toConversation
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtocolMapperTest {
    @Test
    fun mapsMessageReactions() {        val json = JSONObject(
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

    @Test
    fun mapsThreadAvatarsAndParticipants() {
        val json = JSONObject(
            """
            {
              "thread": {
                "thread_fbid": "thread-fbid",
                "title": "Weekend plans",
                "avatar_url": "https://cdn.example.invalid/group.jpg",
                "participants": [
                  { "fbid": 111, "name": "Me", "avatar_url": "https://cdn.example.invalid/me.jpg", "is_viewer": true },
                  { "fbid": 222, "name": "Alice", "avatar_url": "https://cdn.example.invalid/alice.jpg" },
                  { "fbid": 333, "username": "bob", "avatar_url": "https://cdn.example.invalid/bob.jpg" }
                ]
              },
              "messages": [
                {
                  "id": "mid.1",
                  "sender_fbid": 222,
                  "sender_name": "Alice",
                  "text": "hello",
                  "timestamp_ms": 1700000000000,
                  "kind": "text"
                }
              ]
            }
            """.trimIndent(),
        )
        val conversation = Conversation(
            id = "thread-1",
            contact = Contact(id = "thread-1", name = "Old title", initials = "OT"),
        )

        val mapped = mapThread(json, conversation)

        assertEquals("Weekend plans", mapped.contact.name)
        assertEquals("https://cdn.example.invalid/group.jpg", mapped.contact.avatarUrl)
        assertEquals(2, mapped.participants.size)
        assertEquals(222L, mapped.participants[0].fbid)
        assertEquals("https://cdn.example.invalid/alice.jpg", mapped.participants[0].avatarUrl)
        assertEquals("bob", mapped.participants[1].name)
        assertEquals(222L, mapped.messages.single().senderFbid)
        assertEquals("Alice", mapped.messages.single().senderName)
    }

    @Test
    fun mapsInboxRowPreviewAndContact() {
        val row = ThreadWithParticipants(
            thread = ThreadEntity(
                id = "thread-1",
                title = "Alice",
                avatarUrl = "https://cdn.example.invalid/alice.jpg",
                lastActivityMs = 1_700_000_000_000,
                lastMessageText = "see you soon",
                lastMessageKind = "text",
            ),
            participants = listOf(
                ParticipantEntity(threadId = "thread-1", igUserId = "ig-a", fbid = 222, name = "Alice"),
            ),
        )

        val conversation = row.toConversation(null)

        assertEquals("Alice", conversation.contact.name)
        assertEquals("https://cdn.example.invalid/alice.jpg", conversation.contact.avatarUrl)
        assertEquals("see you soon", conversation.preview)
        assertEquals(1_700_000_000_000, conversation.lastActivityMs)
        assertTrue(conversation.messages.isEmpty())
    }

    @Test
    fun prefixesTheViewerLastMessageAndUsesKindPlaceholder() {
        val row = ThreadWithParticipants(
            thread = ThreadEntity(
                id = "thread-1",
                lastMessageKind = "image",
                lastMessageFromViewer = true,
            ),
            participants = emptyList(),
        )

        assertEquals("You: [image]", row.toConversation(null).preview)
    }

    @Test
    fun keepsLoadedMessagesAndPagingWhenTheInboxRefreshes() {
        val previous = Conversation(
            id = "thread-1",
            contact = Contact(id = "thread-1", name = "Alice", initials = "A"),
            messages = listOf(
                ChatMessage(
                    id = "mid.1",
                    text = "hello",
                    sender = MessageSender.Them,
                    timestamp = "9:41",
                ),
            ),
            messagesLoaded = true,
            hasMoreOlder = true,
            oldestCursor = "cursor",
        )
        val row = ThreadWithParticipants(
            thread = ThreadEntity(id = "thread-1", title = "Alice"),
            participants = emptyList(),
        )

        val conversation = row.toConversation(previous)

        assertEquals(1, conversation.messages.size)
        assertTrue(conversation.messagesLoaded)
        assertTrue(conversation.hasMoreOlder)
        assertEquals("cursor", conversation.oldestCursor)
    }

    @Test
    fun fallsBackToParticipantNamesForUntitledThreads() {
        val row = ThreadWithParticipants(
            thread = ThreadEntity(id = "group-1", isGroup = true),
            participants = listOf(
                ParticipantEntity(threadId = "group-1", igUserId = "ig-a", fbid = 1, name = "Alice"),
                ParticipantEntity(threadId = "group-1", igUserId = "ig-b", fbid = 2, name = "Bob"),
                ParticipantEntity(threadId = "group-1", igUserId = "ig-me", fbid = 3, name = "Me", isViewer = true),
            ),
        )

        val conversation = row.toConversation(null)

        assertEquals("Alice, Bob", conversation.contact.name)
        assertEquals(2, conversation.participants.size)
    }
}
