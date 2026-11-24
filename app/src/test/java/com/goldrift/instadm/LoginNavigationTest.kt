package com.goldrift.instadm

import com.goldrift.instadm.ui.login.isAllowedLoginNavigation
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginNavigationTest {
    @Test
    fun allowsInstagramAndMetaConsentHostsOverHttps() {
        val allowed = listOf(
            "https" to "instagram.com",
            "https" to "www.instagram.com",
            "HTTPS" to "www.instagram.com",
            "https" to "facebook.com",
            "https" to "www.facebook.com",
            "https" to "meta.com",
            "https" to "consent.meta.com",
            "https" to "accountscenter.meta.com",
            "https" to "fbsbx.com",
            "https" to "lookaside.fbsbx.com",
        )
        for ((scheme, host) in allowed) {
            assertTrue("Expected $scheme://$host to be allowed", isAllowedLoginNavigation(scheme, host))
        }
    }

    @Test
    fun rejectsNonHttpsSchemesLookalikesAndExternalHosts() {
        val blocked = listOf(
            "http" to "www.instagram.com",
            "instagram" to "accounts",
            "intent" to "instagram.com",
            "javascript" to null,
            null to "www.instagram.com",
            "https" to "evilinstagram.com",
            "https" to "notfacebook.com",
            "https" to "fakemeta.com",
            "https" to "badfbsbx.com",
            "https" to "play.google.com",
            "https" to "example.com",
            "https" to null,
            "https" to "",
            "https" to "   ",
        )
        for ((scheme, host) in blocked) {
            assertFalse("Expected $scheme://$host to be rejected", isAllowedLoginNavigation(scheme, host))
        }
    }
}
