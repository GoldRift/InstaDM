package com.goldrift.instadm.ui.chat

/**
 * Whether the viewer's read should be sent to Instagram. Hiding receipts stops
 * the receipt for merely opening a chat, but a reply still publishes it so the
 * server never sees a sent message after an unseen one.
 */
fun shouldPublishReadReceipt(receiptsEnabled: Boolean, fromSend: Boolean): Boolean =
    receiptsEnabled || fromSend

/** Message requests must be accepted before they are marked read. */
internal val READ_REQUEST_FOLDERS = setOf("PENDING", "SPAM")

/** A read request folder takes no receipt at all, not even the one a reply would publish. */
fun publishesReadReceipt(folder: String, receiptsEnabled: Boolean, fromSend: Boolean): Boolean =
    folder !in READ_REQUEST_FOLDERS && shouldPublishReadReceipt(receiptsEnabled, fromSend)

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
 * The newest message the viewer sent. Only this bubble carries the persistent
 * sent/seen receipt; older messages only show their timestamp when tapped.
 * Activity notices are receipts for the thread, not messages the viewer sent,
 * so they never own the receipt.
 */
fun lastOutgoingMessageId(messages: List<ChatMessage>): String? =
    messages.lastOrNull { it.sender == MessageSender.Me && !it.isAdminNotice() }?.id

/**
 * Whether this bubble shows the sent/seen read receipt. Only the newest
 * outgoing non-admin message displays a receipt.
 */
fun ChatMessage.showsReadReceipt(lastOutgoingId: String?): Boolean =
    sender == MessageSender.Me && !isAdminNotice() && id == lastOutgoingId

/**
 * Whether tapping this bubble can reveal its timestamp. Any non-admin message
 * with a known timestamp or formatted time label supports tap-to-reveal.
 */
fun ChatMessage.canRevealTimestamp(): Boolean =
    !isAdminNotice() && (timestampMs > 0 || timestamp.isNotBlank())

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
