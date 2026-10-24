package com.goldrift.instadm

import com.goldrift.instadm.ui.chat.isOpenableLink
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
}
