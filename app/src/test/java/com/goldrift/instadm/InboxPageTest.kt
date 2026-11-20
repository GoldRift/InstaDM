package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.ActiveThreadState
import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.Contact
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.hasOlderConversations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxPageTest {
    @Test
    fun followsAPageThatHasACursor() {
        assertTrue(hasOlderConversations("end-cursor", hasNextPage = true))
    }

    @Test
    fun stopsOnTheLastPage() {
        assertFalse(hasOlderConversations("end-cursor", hasNextPage = false))
    }

    @Test
    fun neverFollowsABlankCursor() {
        // Paging with a blank cursor would refetch the same page forever.
        assertFalse(hasOlderConversations("", hasNextPage = true))
        assertFalse(hasOlderConversations("   ", hasNextPage = true))
    }

    @Test
    fun activeThreadStateMergesOnlyMatchingConversation() {
        val summary = Conversation(
            id = "thread-1",
            contact = Contact(id = "thread-1", name = "Maya", initials = "M"),
        )
        val other = Conversation(
            id = "thread-2",
            contact = Contact(id = "thread-2", name = "Dev", initials = "D"),
        )
        val msg = ChatMessage(
            id = "mid.1",
            text = "hello",
            sender = MessageSender.Them,
            timestamp = "9:00 AM",
        )
        val active = ActiveThreadState(
            threadId = "thread-1",
            messages = listOf(msg),
            loaded = true,
            isLoadingOlder = true,
            olderError = false,
        )

        val merged = active.mergeInto(summary)
        assertEquals(listOf(msg), merged.messages)
        assertTrue(merged.messagesLoaded)
        assertTrue(merged.isLoadingOlder)
        assertFalse(merged.olderError)
        // Unrelated conversations stay untouched and keep reference identity.
        assertSame(other, active.mergeInto(other))
    }
}
