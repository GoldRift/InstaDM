package com.goldrift.instadm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goldrift.instadm.data.RealtimeEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealtimeEventTest {
    @Test
    fun parsesNewMessage() {
        val event = RealtimeEvent.fromJson(
            """
            {
              "type": "message.new",
              "thread_id": "thread-1",
              "seq_id": 42,
              "timestamp_ms": 1700000000000,
              "message": {
                "id": "mid.test",
                "text": "hello",
                "sender_name": "Test User",
                "is_from_viewer": false,
                "timestamp_ms": 1700000000000,
                "kind": "text"
              }
            }
            """.trimIndent(),
        )

        assertNotNull(event)
        assertEquals("message.new", event!!.type)
        assertEquals("thread-1", event.threadId)
        assertEquals(42L, event.seqId)
        assertFalse(event.message!!.isFromViewer)
        assertEquals("hello", event.message.text)
    }

    @Test
    fun parsesMediaAttachments() {
        val event = RealtimeEvent.fromJson(
            """
            {
              "type": "message.new",
              "thread_id": "thread-1",
              "message": {
                "id": "mid.media",
                "offline_threading_id": "7300000000000000042",
                "text": "",
                "kind": "image",
                "timestamp_ms": 1700000000000,
                "media": [
                  {
                    "type": "image",
                    "url": "https://cdn.example/full.jpg",
                    "preview_url": "https://cdn.example/preview.jpg",
                    "width": 800,
                    "height": 600
                  },
                  {
                    "type": "video",
                    "url": "https://cdn.example/clip.mp4",
                    "duration_ms": 4200
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertNotNull(event)
        val media = event!!.message!!.media
        assertEquals("7300000000000000042", event.message!!.offlineThreadingId)
        assertEquals(2, media.size)
        assertEquals("image", media[0].type)
        assertEquals("https://cdn.example/full.jpg", media[0].fullUrl)
        assertEquals("https://cdn.example/preview.jpg", media[0].thumbnailUrl)
        assertEquals(800, media[0].width)
        assertEquals(600, media[0].height)
        assertEquals(0, media[0].partIndex)
        assertTrue(media[1].isVideo)
        assertEquals(1, media[1].partIndex)
        assertEquals(4200, media[1].durationMs)
    }

    @Test
    fun keepsViewOnceMediaWithoutAUrl() {
        val event = RealtimeEvent.fromJson(
            """
            {
              "type": "message.new",
              "thread_id": "thread-1",
              "message": {
                "id": "mid.view-once",
                "text": "",
                "kind": "image",
                "timestamp_ms": 1700000000000,
                "media": [
                  {
                    "type": "image",
                    "view_once": true,
                    "view_mode": "viewed"
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertNotNull(event)
        val media = event!!.message!!.media.single()
        assertEquals("image", media.type)
        assertTrue(media.viewOnce)
        assertEquals("viewed", media.viewMode)
        assertFalse(media.isVisual)
    }

    @Test
    fun parsesSharedReelWithoutAPlayableUrl() {
        val event = RealtimeEvent.fromJson(
            """
            {
              "type": "message.new",
              "thread_id": "thread-1",
              "message": {
                "id": "mid.reel",
                "text": "",
                "kind": "reel",
                "timestamp_ms": 1700000000000,
                "media": [
                  {
                    "type": "reel",
                    "preview_url": "https://cdn.example/reel.jpg",
                    "width": 1080,
                    "height": 1920,
                    "target_id": "123456789",
                    "shortcode": "ABC_123",
                    "target_url": "https://www.instagram.com/reel/ABC_123/"
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertNotNull(event)
        val media = event!!.message!!.media.single()
        assertEquals("reel", media.type)
        assertTrue(media.isReel)
        assertTrue(media.isVideo)
        assertTrue(media.isVisual)
        assertEquals("123456789", media.targetId)
        assertEquals("ABC_123", media.shortcode)
        assertEquals("https://www.instagram.com/reel/ABC_123/", media.shareUrl)
        assertEquals("https://cdn.example/reel.jpg", media.thumbnailUrl)
    }

    @Test
    fun parsesSharedLinkWithItsTargetUrl() {
        val event = RealtimeEvent.fromJson(
            """
            {
              "type": "message.new",
              "thread_id": "thread-1",
              "message": {
                "id": "mid.post",
                "text": "",
                "kind": "link",
                "timestamp_ms": 1700000000000,
                "media": [
                  {
                    "type": "link",
                    "url": "https://www.instagram.com/p/POST123/",
                    "preview_url": "https://cdn.example/post.jpg"
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        assertNotNull(event)
        val media = event!!.message!!.media.single()
        assertEquals("link", media.type)
        assertTrue(media.isLink)
        assertFalse(media.isVideo)
        assertFalse(media.isVisual)
        assertEquals("https://www.instagram.com/p/POST123/", media.fullUrl)
        assertEquals("https://cdn.example/post.jpg", media.thumbnailUrl)
    }

    @Test
    fun parsesReadReceipt() {
        val event = RealtimeEvent.fromJson(
            """
            {
              "type": "read.receipt",
              "thread_id": "thread-1",
              "timestamp_ms": 1700000002000,
              "participant_fbid": 222,
              "from_viewer": false
            }
            """.trimIndent(),
        )

        assertNotNull(event)
        assertEquals("read.receipt", event!!.type)
        assertEquals(1_700_000_002_000L, event.timestampMs)
        assertEquals(222L, event.participantFbid)
        assertFalse(event.fromViewer)
    }

    @Test
    fun parsesViewerReadReceipt() {
        val event = RealtimeEvent.fromJson(
            """
            {
              "type": "read.receipt",
              "thread_id": "thread-1",
              "timestamp_ms": 1700000002000,
              "participant_fbid": 111,
              "from_viewer": true
            }
            """.trimIndent(),
        )

        assertNotNull(event)
        assertTrue(event!!.fromViewer)
    }

    @Test
    fun parsesReactionEvents() {
        val added = RealtimeEvent.fromJson(
            """
            {
              "type": "reaction.add",
              "thread_id": "thread-1",
              "reaction": "❤️",
              "participant_fbid": 222,
              "from_viewer": false,
              "message": { "id": "mid.1" }
            }
            """.trimIndent(),
        )

        assertNotNull(added)
        assertEquals("reaction.add", added!!.type)
        assertEquals("❤️", added.reaction)
        assertEquals("mid.1", added.message!!.id)
        assertEquals(222L, added.participantFbid)
        assertFalse(added.fromViewer)

        val removed = RealtimeEvent.fromJson(
            """
            {
              "type": "reaction.remove",
              "thread_id": "thread-1",
              "reaction": "👍",
              "participant_fbid": 111,
              "from_viewer": true,
              "message": { "id": "mid.1" }
            }
            """.trimIndent(),
        )

        assertNotNull(removed)
        assertEquals("reaction.remove", removed!!.type)
        assertEquals("👍", removed.reaction)
        assertTrue(removed.fromViewer)
    }

    @Test
    fun parsesDisconnectedEventWithoutMessage() {
        val event = RealtimeEvent.fromJson("""{"type":"disconnected","error":"socket closed"}""")

        assertNotNull(event)
        assertEquals("disconnected", event!!.type)
        assertEquals("socket closed", event.error)
        assertNull(event.message)
    }

    @Test
    fun rejectsMissingType() {
        assertNull(RealtimeEvent.fromJson("""{"thread_id":"thread-1"}"""))
        assertNull(RealtimeEvent.fromJson("not json"))
    }
}
