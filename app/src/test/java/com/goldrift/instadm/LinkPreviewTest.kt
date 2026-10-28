package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.isOpenableLink
import com.goldrift.instadm.ui.chat.shareLinkLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPreviewTest {
    @Test
    fun acceptsHttpAndHttpsLinks() {
        assertTrue(isOpenableLink("https://www.instagram.com/p/POST123/"))
        assertTrue(isOpenableLink("http://example.com/path?q=1"))
        assertTrue(isOpenableLink("HTTPS://EXAMPLE.COM"))
    }

    @Test
    fun rejectsNonWebAndMalformedTargets() {
        assertFalse(isOpenableLink(""))
        assertFalse(isOpenableLink("   "))
        assertFalse(isOpenableLink("javascript:alert(1)"))
        assertFalse(isOpenableLink("file:///etc/passwd"))
        assertFalse(isOpenableLink("intent://example.com/#Intent;end"))
        assertFalse(isOpenableLink("not a url"))
        assertFalse(isOpenableLink("https://"))
    }

    @Test
    fun showsInstagramSharesAsTheirPathWithoutTracking() {
        assertEquals(
            "/reel/ABC_123/",
            shareLinkLabel("https://www.instagram.com/reel/ABC_123/?igsh=tracking123"),
        )
        assertEquals(
            "/p/POST123/",
            shareLinkLabel("https://www.instagram.com/p/POST123/?utm_source=ig_web_copy_link"),
        )
        assertEquals(
            "/stories/maya/987654321/",
            shareLinkLabel("https://instagram.com/stories/maya/987654321/?hl=en"),
        )
    }

    @Test
    fun keepsTheHostForOtherPages() {
        assertEquals(
            "example.com/a/long/path",
            shareLinkLabel("https://www.example.com/a/long/path?with=query#section"),
        )
    }

    @Test
    fun fallsBackToTheHostWhenThereIsNoPath() {
        assertEquals("instagram.com", shareLinkLabel("https://www.instagram.com/?hl=en"))
        assertEquals("example.com", shareLinkLabel("https://example.com"))
    }

    @Test
    fun leavesUnparseableLinksAlone() {
        assertEquals("not a url", shareLinkLabel("not a url"))
    }
}
