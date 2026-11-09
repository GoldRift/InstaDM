package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.hasOlderConversations
import org.junit.Assert.assertFalse
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
}
