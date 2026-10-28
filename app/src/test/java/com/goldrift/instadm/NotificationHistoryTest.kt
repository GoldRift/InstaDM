package com.goldrift.instadm

import com.goldrift.instadm.service.MESSAGE_NOTIFICATION_BASE
import com.goldrift.instadm.service.NotifiedMessage
import com.goldrift.instadm.service.ThreadNotificationHistory
import com.goldrift.instadm.service.messageNotificationId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationHistoryTest {
    private fun message(id: String, timestampMs: Long = 1_700_000_000_000) = NotifiedMessage(
        id = id,
        text = "message $id",
        timestampMs = timestampMs,
        senderFbid = 222,
        senderName = "Alice",
        senderAvatarUrl = "https://cdn.example.invalid/alice.jpg",
    )

    @Test
    fun messagesFromTheSameThreadStack() {
        val history = ThreadNotificationHistory()

        history.record("thread-1", message("message-1"), append = false)
        val stacked = history.record("thread-1", message("message-2"), append = true)

        assertEquals(listOf("message-1", "message-2"), stacked.map { it.id })
    }

    @Test
    fun messagesFromDifferentGroupSendersShareTheConversationStack() {
        val history = ThreadNotificationHistory()

        history.record("group-1", message("alice-1"), append = false)
        val stacked = history.record(
            "group-1",
            message("bob-1").copy(senderFbid = 333, senderName = "Bob"),
            append = true,
        )

        assertEquals(listOf("alice-1", "bob-1"), stacked.map { it.id })
        assertEquals(listOf("Alice", "Bob"), stacked.map { it.senderName })
    }

    @Test
    fun duplicateDeliveryDoesNotDuplicateALine() {
        val history = ThreadNotificationHistory()

        history.record("thread-1", message("message-1"), append = false)
        val stacked = history.record("thread-1", message("message-1"), append = true)

        assertEquals(listOf("message-1"), stacked.map { it.id })
    }

    @Test
    fun historyKeepsOnlyTheNewestMessages() {
        val history = ThreadNotificationHistory(limit = 2)

        history.record("thread-1", message("message-1"), append = false)
        history.record("thread-1", message("message-2"), append = true)
        val stacked = history.record("thread-1", message("message-3"), append = true)

        assertEquals(listOf("message-2", "message-3"), stacked.map { it.id })
    }

    @Test
    fun aFreshNotificationStartsAFreshStack() {
        val history = ThreadNotificationHistory()

        history.record("thread-1", message("message-1"), append = false)
        history.record("thread-1", message("message-2"), append = true)
        val stacked = history.record("thread-1", message("message-3"), append = false)

        assertEquals(listOf("message-3"), stacked.map { it.id })
    }

    @Test
    fun threadsDoNotShareHistory() {
        val history = ThreadNotificationHistory()

        history.record("thread-1", message("message-1"), append = false)
        val other = history.record("thread-2", message("message-2"), append = false)

        assertEquals(listOf("message-2"), other.map { it.id })
    }

    @Test
    fun notificationIdsAreStableAndAboveTheServiceNotification() {
        assertEquals(messageNotificationId("thread-1"), messageNotificationId("thread-1"))
        assertTrue(messageNotificationId("thread-1") >= MESSAGE_NOTIFICATION_BASE)
        assertTrue(messageNotificationId("thread-1") != messageNotificationId("thread-2"))
    }
}
