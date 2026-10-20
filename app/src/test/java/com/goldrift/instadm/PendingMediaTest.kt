package com.goldrift.instadm

import com.goldrift.instadm.data.OfflineThreadingIds
import com.goldrift.instadm.ui.chat.ChatMessage
import com.goldrift.instadm.ui.chat.MessageSender
import com.goldrift.instadm.ui.chat.PendingMedia
import com.goldrift.instadm.ui.chat.previewLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingMediaTest {
    @Test
    fun localMediaDrivesTheInboxPreview() {
        val message = ChatMessage(
            id = "local-1",
            text = "",
            sender = MessageSender.Me,
            timestamp = "12:00 PM",
            pending = true,
            localMedia = PendingMedia(
                uri = "content://capture/1",
                kind = "image",
                fileName = "capture.jpg",
            ),
        )

        assertEquals("[image]", message.previewLabel())
    }

    @Test
    fun localMediaClampsExtremeAspectRatios() {
        val tall = PendingMedia(uri = "u", kind = "image", fileName = "a", width = 100, height = 1000)
        val square = PendingMedia(uri = "u", kind = "image", fileName = "a")

        assertEquals(0.6f, tall.aspectRatio)
        assertEquals(1f, square.aspectRatio)
    }

    @Test
    fun offlineThreadingIdsAreUniqueAndNumeric() {
        val first = OfflineThreadingIds.next()
        val second = OfflineThreadingIds.next()

        assertNotEquals(first, second)
        assertTrue(first.all(Char::isDigit))
        assertTrue(second.toLong() > 0)
    }
}
