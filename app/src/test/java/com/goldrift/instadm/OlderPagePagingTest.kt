package com.goldrift.instadm

import com.goldrift.instadm.data.olderPageHasMore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OlderPagePagingTest {

    @Test
    fun aPageThatStoredMessagesKeepsPagingWhenTheServerEndsEarly() {
        // Instagram sometimes ends a page with has_next_page = false while the
        // cursor still points into the thread; the stored messages are proof
        // that more history exists.
        assertTrue(
            olderPageHasMore(
                cursorAdvanced = true,
                pageMessageCount = 10,
                added = 10,
                retained = 10,
                serverHasMore = false,
            ),
        )
    }

    @Test
    fun aPageThatStoredMessagesKeepsPagingWhenTheServerReportsMore() {
        assertTrue(
            olderPageHasMore(
                cursorAdvanced = true,
                pageMessageCount = 20,
                added = 20,
                retained = 20,
                serverHasMore = true,
            ),
        )
    }

    @Test
    fun aCursorThatDidNotAdvanceStopsPaging() {
        assertFalse(
            olderPageHasMore(
                cursorAdvanced = false,
                pageMessageCount = 20,
                added = 20,
                retained = 20,
                serverHasMore = true,
            ),
        )
    }

    @Test
    fun anOverlapPageKeepsPagingWhileTheServerReportsMore() {
        assertFalse(
            olderPageHasMore(
                cursorAdvanced = true,
                pageMessageCount = 20,
                added = 0,
                retained = 0,
                serverHasMore = false,
            ),
        )
        assertTrue(
            olderPageHasMore(
                cursorAdvanced = true,
                pageMessageCount = 20,
                added = 0,
                retained = 0,
                serverHasMore = true,
            ),
        )
    }

    @Test
    fun anEmptyPageEndsPagingEvenWhenTheServerReportsMore() {
        assertFalse(
            olderPageHasMore(
                cursorAdvanced = true,
                pageMessageCount = 0,
                added = 0,
                retained = 0,
                serverHasMore = true,
            ),
        )
    }

    @Test
    fun aPagePrunedAtTheRetentionCapEndsPaging() {
        assertFalse(
            olderPageHasMore(
                cursorAdvanced = true,
                pageMessageCount = 20,
                added = 20,
                retained = 0,
                serverHasMore = true,
            ),
        )
    }
}
