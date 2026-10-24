package com.goldrift.instadm.ui.chat

/**
 * Applies another participant's read watermark to an open thread. A message the
 * viewer sent is "seen" once a participant's watermark reaches its timestamp.
 * Receipts from the viewer only mean the viewer has read the thread, so they
 * never produce seen ticks.
 */
fun applyReadReceiptToMessages(
    messages: List<ChatMessage>,
    watermarkMs: Long,
    fromViewer: Boolean,
): List<ChatMessage> {
    if (watermarkMs <= 0 || fromViewer) return messages

    var changed = false
    val updated = messages.map { message ->
        if (message.sender == MessageSender.Me &&
            message.timestampMs in 1..watermarkMs &&
            !message.seen
        ) {
            changed = true
            message.copy(seen = true)
        } else {
            message
        }
    }
    return if (changed) updated else messages
}

/**
 * The newest message the viewer sent. Its sent/seen ticks stay under the bubble
 * as a persistent receipt, while older outgoing bubbles only reveal theirs when
 * the viewer taps them.
 */
fun lastOutgoingMessageId(messages: List<ChatMessage>): String? =
    messages.lastOrNull { it.sender == MessageSender.Me }?.id

/**
 * Whether this bubble shows its sent/seen ticks: always for the newest outgoing
 * message, and for any outgoing message the viewer tapped in this chat session.
 * Other outgoing bubbles keep their tap-to-reveal affordance hidden.
 */
fun ChatMessage.showsStatusTicks(
    lastOutgoingId: String?,
    revealedId: String?,
): Boolean = sender == MessageSender.Me && (id == lastOutgoingId || id == revealedId)

/**
 * True when mark-read can target this message. Optimistic sends and mapped
 * messages without a server ID must not be sent to the protocol mutation, and
 * the viewer's own messages are already read.
 */
fun ChatMessage.canMarkRead(): Boolean =
    sender == MessageSender.Them && isServerMessage()

/**
 * Local read watermark for a thread Instagram will not accept a viewer receipt
 * for, such as a message request. The newest incoming message is what the user
 * has seen, and it is only used to clear the inbox badge.
 */
fun requestReadWatermark(messages: List<ChatMessage>): Long =
    messages.lastOrNull { it.sender == MessageSender.Them && it.timestampMs > 0 }
        ?.timestampMs
        ?: 0L
