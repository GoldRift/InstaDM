package com.goldrift.instadm

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.goldrift.instadm.data.RealtimeEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
