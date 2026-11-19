package com.goldrift.instadm.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeSeenTest {
    private val manifest = UpdateManifest.parse(
        """
        {
          "version": "1.2.3",
          "versionCode": 10203,
          "apks": {
            "arm64-v8a": {
              "url": "https://example.com/instadm-arm64-v8a.apk",
              "size": 23140233,
              "sha256": "${"a".repeat(64)}"
            }
          }
        }
        """.trimIndent(),
    )!!
    private val apk = manifest.apkFor(listOf("arm64-v8a"))!!
    private val pending = UpdateState.Available(manifest, apk)

    @Test
    fun badgeShowsForAnUnseenPendingUpdate() {
        assertTrue(showUpdateBadge(pending, seenVersionCode = 0L))
        assertTrue(showUpdateBadge(pending, seenVersionCode = manifest.versionCode - 1))
        assertFalse(showUpdateBadge(pending, seenVersionCode = manifest.versionCode))
    }

    @Test
    fun badgeHidesWithoutAPendingUpdate() {
        assertFalse(showUpdateBadge(null, seenVersionCode = 0L))
        assertFalse(showUpdateBadge(null, seenVersionCode = manifest.versionCode))
    }

    @Test
    fun thePendingUpdateIsMarkedSeen() {
        assertEquals(manifest.versionCode, versionToMarkSeen(pending))
        assertNull(versionToMarkSeen(null))
    }
}
