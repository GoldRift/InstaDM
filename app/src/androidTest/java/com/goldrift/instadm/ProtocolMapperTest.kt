package com.goldrift.instadm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goldrift.instadm.data.db.AttachmentEntity
import com.goldrift.instadm.data.db.MessageEntity
import com.goldrift.instadm.data.db.MessageWithMedia
import com.goldrift.instadm.data.db.ParticipantEntity
import com.goldrift.instadm.data.db.ReactionEntity
import com.goldrift.instadm.data.db.ThreadEntity
import com.goldrift.instadm.data.db.ThreadWithParticipants
import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.Contact
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.toChatMessage
import com.goldrift.instadm.ui.chat.toConversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtocolMapperTest {
    @Test
    fun mapsPersistedMessageWithReactionsAndSeenState() {
        val message = MessageWithMedia(
            message = MessageEntity(
                id = "mid.1",
                threadId = "thread-1",
                timestampMs = 1_700_000_000_000,
                kind = "text",
                text = "hello",
                isFromViewer = true,
                seen = true,
                otid = "otid-1",
            ),
            attachments = emptyList(),
            reactions = listOf(
                ReactionEntity(messageId = "mid.1", senderFbid = 111, emoji = "❤️", isFromViewer = true),
                ReactionEntity(messageId = "mid.1", senderFbid = 222, emoji = "😂"),
            ),
        )

        val chat = message.toChatMessage()

        assertEquals("mid.1", chat.id)
        assertEquals("hello", chat.text)
        assertEquals(MessageSender.Me, chat.sender)
        assertEquals(1_700_000_000_000, chat.timestampMs)
        assertTrue(chat.seen)
        assertFalse(chat.pending)
        assertEquals("otid-1", chat.offlineThreadingId)
        assertEquals(2, chat.reactions.size)
        assertEquals("❤️", chat.reactions[0].emoji)
        assertEquals(111L, chat.reactions[0].senderFbid)
        assertTrue(chat.reactions[0].isFromViewer)
        assertEquals("😂", chat.reactions[1].emoji)
        assertEquals(222L, chat.reactions[1].senderFbid)
        assertFalse(chat.reactions[1].isFromViewer)
    }

    @Test
    fun mapsPersistedMediaIntoTheBubble() {
        val message = MessageWithMedia(
            message = MessageEntity(
                id = "mid.2",
                threadId = "thread-1",
                timestampMs = 1_700_000_001_000,
                kind = "image",
                mediaCount = 1,
            ),
            attachments = listOf(
                AttachmentEntity(
                    messageId = "mid.2",
                    partIndex = 0,
                    kind = "image",
                    url = "https://cdn.example.invalid/image.jpg",
                    previewUrl = "https://cdn.example.invalid/image-preview.jpg",
                    width = 800,
                    height = 600,
                ),
            ),
            reactions = emptyList(),
        )

        val chat = message.toChatMessage()

        assertEquals(MessageSender.Them, chat.sender)
        assertEquals(1, chat.media.size)
        assertEquals("image", chat.media.single().type)
        assertEquals("https://cdn.example.invalid/image.jpg", chat.media.single().url)
        assertEquals("https://cdn.example.invalid/image-preview.jpg", chat.media.single().thumbnailUrl)
        assertEquals(800, chat.media.single().width)
        assertEquals(600, chat.media.single().height)
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
    fun keepsLoadedMessagesWhenTheInboxRefreshes() {
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
            thread = ThreadEntity(
                id = "thread-1",
                title = "Alice",
                hasMoreOlder = true,
                oldestCursor = "cursor",
            ),
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
