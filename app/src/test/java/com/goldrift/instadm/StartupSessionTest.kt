package com.goldrift.instadm

import com.goldrift.instadm.data.SessionCookieCache
import com.goldrift.instadm.ui.resolveStartRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupSessionTest {
    @Test
    fun startRouteResolvesSynchronouslyFromStoredSessionFlag() {
        assertEquals("inbox", resolveStartRoute(hasInitialSession = true))
        assertEquals("login", resolveStartRoute(hasInitialSession = false))
    }

    @Test
    fun encryptedSessionDecryptsOnceAcrossSharedReaders() {
        var decryptCalls = 0
        val cache = SessionCookieCache(
            isDebuggable = false,
            hasEncryptedFile = { true },
            decryptEncryptedFile = {
                decryptCalls++
                """{"sessionid":"abc"}"""
            },
            deleteEncryptedFile = {},
            legacyFileLastModified = { null },
            readLegacyFile = { null },
        )

        assertEquals("""{"sessionid":"abc"}""", cache.read())
        assertEquals("""{"sessionid":"abc"}""", cache.read())
        assertEquals(1, decryptCalls)

        cache.recordEncrypted("""{"sessionid":"rotated"}""")
        assertEquals("""{"sessionid":"rotated"}""", cache.read())
        assertEquals(1, decryptCalls)

        cache.clear()
        assertEquals("""{"sessionid":"abc"}""", cache.read())
        assertEquals(2, decryptCalls)
    }

    @Test
    fun debuggableLegacyCookieReloadsOnlyWhenFileTimestampChanges() {
        var legacyMtime: Long? = 1_000L
        var legacyText = """{"sessionid":"v1"}"""
        var legacyReads = 0

        val cache = SessionCookieCache(
            isDebuggable = true,
            hasEncryptedFile = { false },
            decryptEncryptedFile = { null },
            deleteEncryptedFile = {},
            legacyFileLastModified = { legacyMtime },
            readLegacyFile = {
                legacyReads++
                legacyText
            },
        )

        assertEquals("""{"sessionid":"v1"}""", cache.read())
        assertEquals("""{"sessionid":"v1"}""", cache.read())
        assertEquals(1, legacyReads)

        // Updating the file timestamp simulates `adb push cookies.json`.
        legacyMtime = 2_000L
        legacyText = """{"sessionid":"v2"}"""
        assertEquals("""{"sessionid":"v2"}""", cache.read())
        assertEquals(2, legacyReads)

        legacyMtime = null
        assertNull(cache.read())
    }

    @Test
    fun corruptEncryptedSessionDeletesFileAndFallsBackToLegacyInDebug() {
        var deleted = false
        val cache = SessionCookieCache(
            isDebuggable = true,
            hasEncryptedFile = { !deleted },
            decryptEncryptedFile = { error("keystore invalidated") },
            deleteEncryptedFile = { deleted = true },
            legacyFileLastModified = { 500L },
            readLegacyFile = { """{"sessionid":"legacy"}""" },
        )

        assertEquals("""{"sessionid":"legacy"}""", cache.read())
        assertTrue(deleted)
    }
}
