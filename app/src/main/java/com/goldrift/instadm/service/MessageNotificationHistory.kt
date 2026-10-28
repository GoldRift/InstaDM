package com.goldrift.instadm.service

internal const val MESSAGE_NOTIFICATION_BASE = 2000

/**
 * A notification ID for a conversation. Message notifications start above the
 * service's foreground notification ID so the two can never replace each other.
 */
internal fun messageNotificationId(threadId: String): Int =
    MESSAGE_NOTIFICATION_BASE + (threadId.hashCode() and 0x7FFFFFFF) % 1_000_000

/** One line in a conversation notification, resolved when the message arrives. */
internal data class NotifiedMessage(
    val id: String,
    val text: String,
    val timestampMs: Long,
    val senderFbid: Long,
    val senderName: String,
    val senderAvatarUrl: String,
)

/**
 * Keeps the messages shown in each conversation's notification. A notification
 * is updated in place, so this is what turns successive messages into a stack
 * instead of letting the newest one replace the previous body.
 */
internal class ThreadNotificationHistory(private val limit: Int = 8) {
    private val threads = mutableMapOf<String, MutableList<NotifiedMessage>>()

    /**
     * Adds [message] to [threadId] and returns the lines to display. [append]
     * is false once the previous notification is gone, so a swipe or tap starts
     * a fresh stack instead of resurrecting dismissed messages.
     */
    fun record(
        threadId: String,
        message: NotifiedMessage,
        append: Boolean,
    ): List<NotifiedMessage> {
        val entries = if (append) {
            threads.getOrPut(threadId) { mutableListOf() }
        } else {
            mutableListOf<NotifiedMessage>().also { threads[threadId] = it }
        }
        if (entries.none { it.id == message.id }) {
            entries += message
            while (entries.size > limit) entries.removeAt(0)
        }
        return entries.toList()
    }
}
