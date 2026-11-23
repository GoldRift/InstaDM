package com.goldrift.instadm

import com.goldrift.instadm.data.attachments
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentMappingTest {

    @Test
    fun mapsAMusicShareIntoOneAttachmentWithItsTrackFields() {
        val json = JSONObject(
            """
            {
              "media": [
                {
                  "type": "music",
                  "url": "https://cdn.example.invalid/preview.mp4",
                  "preview_url": "https://cdn.example.invalid/artwork.jpg",
                  "target_url": "https://www.instagram.com/_u/demoartist",
                  "title": "Night Drive",
                  "subtitle": "Demo Artist"
                }
              ]
            }
            """.trimIndent(),
        )

        val mapped = attachments(json, "mid.music")

        assertEquals(1, mapped.size)
        val attachment = mapped.single()
        assertEquals("music", attachment.kind)
        assertEquals("https://cdn.example.invalid/preview.mp4", attachment.url)
        assertEquals("https://cdn.example.invalid/artwork.jpg", attachment.previewUrl)
        assertEquals("https://www.instagram.com/_u/demoartist", attachment.targetUrl)
        assertEquals("Night Drive", attachment.title)
        assertEquals("Demo Artist", attachment.subtitle)
        assertEquals(0, attachment.partIndex)
    }

    @Test
    fun mapsALiveLocationLinkWithBlankTrackFields() {
        val json = JSONObject(
            """
            {
              "media": [
                {
                  "type": "link",
                  "url": "instagram://live_location/?latitude=12.34&longitude=-56.78",
                  "preview_url": "https://cdn.example.invalid/map.png"
                }
              ]
            }
            """.trimIndent(),
        )

        val mapped = attachments(json, "mid.location")

        assertEquals(1, mapped.size)
        val attachment = mapped.single()
        assertEquals("link", attachment.kind)
        assertEquals(
            "instagram://live_location/?latitude=12.34&longitude=-56.78",
            attachment.url,
        )
        assertEquals("https://cdn.example.invalid/map.png", attachment.previewUrl)
        assertEquals("", attachment.title)
        assertEquals("", attachment.subtitle)
        assertEquals(0, attachment.partIndex)
    }
}
