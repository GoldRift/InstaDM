package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.ActiveThreadState
import com.goldrift.instadm.ui.chat.Contact
import com.goldrift.instadm.ui.chat.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ActiveThreadStateTest {
    private fun conversation(id: String, unreadCount: Int) = Conversation(
        id = id,
        contact = Contact(id = id, name = "Nadia", initials = "N"),
        unreadCount = unreadCount,
    )

    @Test
    fun nullLiveCountKeepsTheRowCount() {
        val merged = ActiveThreadState(threadId = "t")
            .mergeInto(conversation("t", unreadCount = 5))

        assertEquals(5, merged.unreadCount)
    }

    @Test
    fun liveCountOverridesTheRowCount() {
        val merged = ActiveThreadState(threadId = "t", unreadCount = 2)
            .mergeInto(conversation("t", unreadCount = 5))

        assertEquals(2, merged.unreadCount)
    }

    @Test
    fun anotherThreadKeepsTheConversationUnchanged() {
        val summary = conversation("t", unreadCount = 5)

        val merged = ActiveThreadState(threadId = "other", unreadCount = 2).mergeInto(summary)

        assertEquals(5, merged.unreadCount)
        assertSame(summary, merged)
    }

    @Test
    fun explicitZeroLiveCountClearsTheRowCount() {
        // Zero means "the open thread has no unread rows", not "unset": it
        // must override a stale row count so the badge trigger re-fires.
        val merged = ActiveThreadState(threadId = "t", unreadCount = 0)
            .mergeInto(conversation("t", unreadCount = 5))

        assertEquals(0, merged.unreadCount)
    }
}
