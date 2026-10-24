package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.EDIT_WINDOW_MS
import com.goldrift.instadm.ui.chat.MessageReaction
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.applyReactionDelta
import com.goldrift.instadm.ui.chat.applyReactionToMessages
import com.goldrift.instadm.ui.chat.canEdit
import com.goldrift.instadm.ui.chat.canReact
import com.goldrift.instadm.ui.chat.canReply
import com.goldrift.instadm.ui.chat.canUnsend
import com.goldrift.instadm.ui.chat.reactionGroups
import com.goldrift.instadm.ui.chat.restoreViewerReaction
import com.goldrift.instadm.ui.chat.toggleViewerReaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageActionsTest {
    private fun message(
        id: String = "mid.1",
        text: String = "hello",
        sender: MessageSender = MessageSender.Me,
        timestampMs: Long = 1_000_000,
        pending: Boolean = false,
        kind: String = "text",
        reactions: List<MessageReaction> = emptyList(),
    ) = ChatMessage(
        id = id,
        text = text,
        sender = sender,
        timestamp = "",
        timestampMs = timestampMs,
        pending = pending,
        kind = kind,
        reactions = reactions,
    )

    @Test
    fun groupsReactionsInFirstSeenOrderAndMarksTheViewer() {
        val message = message(
            reactions = listOf(
                MessageReaction("❤️", senderFbid = 111, isFromViewer = true),
                MessageReaction("😂", senderFbid = 222),
                MessageReaction("❤️", senderFbid = 333),
            ),
        )

        val groups = message.reactionGroups()

        assertEquals(2, groups.size)
        assertEquals("❤️", groups[0].emoji)
        assertEquals(2, groups[0].count)
        assertTrue(groups[0].fromViewer)
        assertEquals("😂", groups[1].emoji)
        assertEquals(1, groups[1].count)
        assertFalse(groups[1].fromViewer)
    }

    @Test
    fun addingTheSameEmojiAgainIsANoOp() {
        val once = applyReactionDelta(emptyList(), "❤️", senderFbid = 222, fromViewer = false, remove = false)
        val twice = applyReactionDelta(once, "❤️", senderFbid = 222, fromViewer = false, remove = false)

        assertEquals(listOf(MessageReaction("❤️", 222, false)), once)
        assertSame(once, twice)
    }

    @Test
    fun aDifferentEmojiReplacesTheSameSendersReaction() {
        val reactions = listOf(MessageReaction("❤️", 222, false))

        val updated = applyReactionDelta(reactions, "👍", senderFbid = 222, fromViewer = false, remove = false)

        assertEquals(listOf(MessageReaction("👍", 222, false)), updated)
    }

    @Test
    fun removeDropsTheSendersReactionOnly() {
        val reactions = listOf(
            MessageReaction("❤️", 111, true),
            MessageReaction("😂", 222, false),
        )

        val updated = applyReactionDelta(reactions, "❤️", senderFbid = 111, fromViewer = true, remove = true)

        assertEquals(listOf(MessageReaction("😂", 222, false)), updated)
    }

    @Test
    fun unattributableOtherReactionIsIgnored() {
        val reactions = listOf(MessageReaction("❤️", 222, false))

        val updated = applyReactionDelta(reactions, "👍", senderFbid = 0, fromViewer = false, remove = false)

        assertSame(reactions, updated)
    }

    @Test
    fun viewerToggleAddsThenRemovesTheSameEmoji() {
        val empty = message()
        val (added, removeWhenAdding) = toggleViewerReaction(empty, "❤️")

        assertEquals(listOf(MessageReaction("❤️", 0, true)), added)
        assertFalse(removeWhenAdding)

        val reacted = empty.copy(reactions = added)
        val (removed, removeWhenRemoving) = toggleViewerReaction(reacted, "❤️")

        assertTrue(removed.isEmpty())
        assertTrue(removeWhenRemoving)
    }

    @Test
    fun viewerToggleReplacesThePreviousEmoji() {
        val reacted = message(reactions = listOf(MessageReaction("❤️", 111, true)))

        val (updated, remove) = toggleViewerReaction(reacted, "😂")

        assertEquals(listOf(MessageReaction("😂", 111, true)), updated)
        assertFalse(remove)
    }

    @Test
    fun restoreViewerReactionKeepsOtherSenders() {
        val current = listOf(
            MessageReaction("😂", 111, true),
            MessageReaction("👍", 222, false),
        )

        val restored = restoreViewerReaction(current, MessageReaction("❤️", 111, true))

        assertEquals(
            listOf(MessageReaction("👍", 222, false), MessageReaction("❤️", 111, true)),
            restored,
        )
    }

    @Test
    fun applyReactionToMessagesTouchesOnlyTheTarget() {
        val target = message(id = "mid.1")
        val other = message(id = "mid.2", reactions = listOf(MessageReaction("🔥", 222, false)))

        val updated = applyReactionToMessages(
            messages = listOf(target, other),
            messageId = "mid.1",
            emoji = "❤️",
            senderFbid = 111,
            fromViewer = true,
            remove = false,
        )

        assertEquals(listOf(MessageReaction("❤️", 111, true)), updated[0].reactions)
        assertSame(other, updated[1])
    }

    @Test
    fun unknownMessageLeavesTheListUntouched() {
        val messages = listOf(message(id = "mid.1"))

        val updated = applyReactionToMessages(
            messages = messages,
            messageId = "mid.missing",
            emoji = "❤️",
            senderFbid = 111,
            fromViewer = true,
            remove = false,
        )

        assertSame(messages, updated)
    }

    @Test
    fun onlyRecentServerMessagesFromTheViewerCanBeEdited() {
        val now = 10 * EDIT_WINDOW_MS
        val recent = message(sender = MessageSender.Me, timestampMs = now - 1_000)
        val expired = message(sender = MessageSender.Me, timestampMs = now - EDIT_WINDOW_MS - 1)
        val theirs = message(sender = MessageSender.Them, timestampMs = now)
        val pending = message(sender = MessageSender.Me, timestampMs = now, pending = true)
        val mediaOnly = message(sender = MessageSender.Me, timestampMs = now, text = "")

        assertTrue(recent.canEdit(now))
        assertFalse(expired.canEdit(now))
        assertFalse(theirs.canEdit(now))
        assertFalse(pending.canEdit(now))
        assertFalse(mediaOnly.canEdit(now))
    }

    @Test
    fun actionsRequireAServerIdAndOwnershipForUnsend() {
        val serverMine = message()
        val serverTheirs = message(sender = MessageSender.Them)
        val pending = message(pending = true)
        val placeholder = message(id = "message-0")

        assertTrue(serverMine.canReact())
        assertTrue(serverTheirs.canReact())
        assertTrue(serverMine.canUnsend())
        assertFalse(serverTheirs.canUnsend())
        assertFalse(pending.canReact())
        assertFalse(pending.canUnsend())
        assertFalse(placeholder.canReact())
    }

    @Test
    fun adminActivityNoticesCannotBeQuoted() {
        val like = message(id = "mid.like", text = "Liked a message", kind = "admin")
        val reaction = message(
            id = "mid.reaction",
            text = "Reacted ❤ to your message",
            kind = "admin",
        )
        val text = message()
        val pending = message(pending = true)
        val placeholder = message(id = "message-0")

        assertFalse(like.canReply())
        assertFalse(reaction.canReply())
        assertTrue(text.canReply())
        assertFalse(pending.canReply())
        assertFalse(placeholder.canReply())
    }
}
