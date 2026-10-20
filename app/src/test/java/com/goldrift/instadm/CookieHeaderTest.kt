package com.goldrift.instadm

import com.goldrift.instadm.data.canonicalCookieJson
import com.goldrift.instadm.data.missingCookies
import com.goldrift.instadm.data.parseCookieHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieHeaderTest {
    @Test
    fun parsesCookieHeaderAndKeepsEqualsInValues() {
        val cookies = parseCookieHeader(
            " Cookie: sessionid=abc%3D123; csrftoken=token=with=equals; ds_user_id=42 ",
        )

        assertEquals("abc%3D123", cookies["sessionid"])
        assertEquals("token=with=equals", cookies["csrftoken"])
        assertEquals("42", cookies["ds_user_id"])
    }

    @Test
    fun ignoresMalformedPartsAndLastDuplicateWins() {
        val cookies = parseCookieHeader("sessionid=old; broken; sessionid=new; =empty")

        assertEquals(mapOf("sessionid" to "new"), cookies)
    }

    @Test
    fun reportsMissingRequiredCookies() {
        val cookies = parseCookieHeader("sessionid=abc; csrftoken=; ds_user_id=42")

        assertEquals(listOf("csrftoken"), missingCookies(cookies))
        assertTrue(missingCookies(emptyMap()).containsAll(listOf("sessionid", "csrftoken", "ds_user_id")))
    }

    @Test
    fun canonicalJsonSortsKeysAndEscapesValues() {
        val cookies = linkedMapOf(
            "sessionid" to "a\"b",
            "csrftoken" to "line\nbreak",
            "ds_user_id" to "42\\1",
        )

        assertEquals(
            "{\"csrftoken\":\"line\\nbreak\",\"ds_user_id\":\"42\\\\1\",\"sessionid\":\"a\\\"b\"}",
            canonicalCookieJson(cookies),
        )
    }
}
