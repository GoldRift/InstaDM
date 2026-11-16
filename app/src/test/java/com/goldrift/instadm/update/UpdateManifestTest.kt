package com.goldrift.instadm.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {
    private val shaA = "a".repeat(64)
    private val shaB = "b".repeat(64)

    private fun document(
        version: String = "1.2.3",
        versionCode: String = "10203",
        notes: String = "\"What changed\"",
        apks: String = """
            "arm64-v8a": {
              "url": "https://example.com/instadm-arm64-v8a.apk",
              "size": 23140233,
              "sha256": "$shaA"
            },
            "x86": {
              "url": "https://example.com/instadm-x86.apk",
              "size": 20000000,
              "sha256": "$shaB"
            }
        """,
    ) = """
        {
          "version": "$version",
          "versionCode": $versionCode,
          "notes": $notes,
          "apks": { $apks }
        }
    """.trimIndent()

    @Test
    fun parsesFullDocument() {
        val manifest = UpdateManifest.parse(document())

        assertEquals("1.2.3", manifest?.version)
        assertEquals(10203L, manifest?.versionCode)
        assertEquals("What changed", manifest?.notes)
        assertEquals(2, manifest?.apks?.size)
        assertEquals("https://example.com/instadm-arm64-v8a.apk", manifest?.apks?.get("arm64-v8a")?.url)
        assertEquals(23140233L, manifest?.apks?.get("arm64-v8a")?.size)
        assertEquals(shaA, manifest?.apks?.get("arm64-v8a")?.sha256)
    }

    @Test
    fun missingNotesIsEmpty() {
        val json = """
            {
              "version": "1.2.3",
              "versionCode": 1,
              "apks": {
                "arm64-v8a": {
                  "url": "https://example.com/a.apk",
                  "size": 1,
                  "sha256": "$shaA"
                }
              }
            }
        """.trimIndent()

        assertEquals("", UpdateManifest.parse(json)?.notes)
    }

    @Test
    fun picksFirstSupportedAbi() {
        val manifest = UpdateManifest.parse(document())!!

        assertEquals(
            "https://example.com/instadm-arm64-v8a.apk",
            manifest.apkFor(listOf("arm64-v8a", "x86"))?.url,
        )
        assertEquals(
            "https://example.com/instadm-x86.apk",
            manifest.apkFor(listOf("x86", "arm64-v8a"))?.url,
        )
    }

    @Test
    fun missingAbiIsNull() {
        assertNull(UpdateManifest.parse(document())?.apkFor(listOf("mips")))
    }

    @Test
    fun malformedJsonIsNull() {
        assertNull(UpdateManifest.parse("{"))
        assertNull(UpdateManifest.parse(""))
        assertNull(UpdateManifest.parse("[]"))
    }

    @Test
    fun missingFieldsAreRejected() {
        assertNull(UpdateManifest.parse("""{"version": "1.2.3", "versionCode": 1}"""))
        assertNull(UpdateManifest.parse("""{"versionCode": 1, "apks": {}}"""))
        assertNull(UpdateManifest.parse(document(versionCode = "0")))
        assertNull(UpdateManifest.parse(document(versionCode = "-5")))
        assertNull(UpdateManifest.parse(document(versionCode = "\"10203\"")))
    }

    @Test
    fun badSha256DropsApk() {
        val json = document(
            apks = """
                "arm64-v8a": {
                  "url": "https://example.com/a.apk",
                  "size": 1,
                  "sha256": "not-a-digest"
                },
                "x86": {
                  "url": "https://example.com/b.apk",
                  "size": 1,
                  "sha256": "$shaB"
                }
            """,
        )

        val manifest = UpdateManifest.parse(json)!!

        assertNull(manifest.apks["arm64-v8a"])
        assertEquals(shaB, manifest.apks["x86"]?.sha256)
    }

    @Test
    fun insecureUrlDropsApk() {
        val json = document(
            apks = """
                "arm64-v8a": {
                  "url": "http://example.com/a.apk",
                  "size": 1,
                  "sha256": "$shaA"
                }
            """,
        )

        assertNull(UpdateManifest.parse(json))
    }

    @Test
    fun newerVersionCodeWins() {
        val manifest = UpdateManifest.parse(document())!!

        assertTrue(manifest.isNewerThan(10202))
        assertFalse(manifest.isNewerThan(10203))
        assertFalse(manifest.isNewerThan(10204))
    }
}
