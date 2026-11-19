package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.showScrollToBottom
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollToBottomTest {
    @Test
    fun hidesWhileTheNewestMessageIsOnScreen() {
        assertFalse(showScrollToBottom(0))
    }

    @Test
    fun hidesWithinTheBuffer() {
        assertFalse(showScrollToBottom(1))
        assertFalse(showScrollToBottom(2))
    }

    @Test
    fun showsJustPastTheBuffer() {
        assertTrue(showScrollToBottom(3))
    }

    @Test
    fun showsFarUpTheTranscript() {
        assertTrue(showScrollToBottom(240))
    }

    @Test
    fun coercesInputOutsideTheList() {
        assertFalse(showScrollToBottom(-4))
        assertTrue(showScrollToBottom(Int.MAX_VALUE))
        assertFalse(showScrollToBottom(0, buffer = -2))
    }

    @Test
    fun honoursACustomBuffer() {
        assertFalse(showScrollToBottom(4, buffer = 4))
        assertTrue(showScrollToBottom(5, buffer = 4))
    }
}
