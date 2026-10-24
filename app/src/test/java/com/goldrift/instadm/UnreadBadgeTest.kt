package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.Contact
import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.unreadBadgeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnreadBadgeTest {
    private fun conversation(unreadCount: Int = 0, markedUnread: Boolean = false) = Conversation(
        id = "thread-1",
        contact = Contact(id = "thread-1", name = "Nadia", initials = "N"),
        unreadCount = unreadCount,
        markedUnread = markedUnread,
    )

    @Test
    fun capsTheBadgeAtNinetyNine() {
        assertEquals("1", unreadBadgeLabel(1))
        assertEquals("42", unreadBadgeLabel(42))
        assertEquals("99", unreadBadgeLabel(99))
        assertEquals("99+", unreadBadgeLabel(100))
        assertEquals("99+", unreadBadgeLabel(1_000))
    }

    @Test
    fun anyUnreadMessageHighlightsTheRow() {
        assertTrue(conversation(unreadCount = 1).hasUnread)
        assertTrue(conversation(markedUnread = true).hasUnread)
    }

    @Test
    fun aReadThreadStaysPlain() {
        assertFalse(conversation().hasUnread)
    }
}
