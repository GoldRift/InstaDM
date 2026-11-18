package com.goldrift.instadm.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.goldrift.instadm.R
import com.goldrift.instadm.data.AppSettings
import com.goldrift.instadm.data.ChatStore
import com.goldrift.instadm.data.Outbox
import com.goldrift.instadm.data.ProtocolApi
import com.goldrift.instadm.data.REACTION_LOG_KIND
import com.goldrift.instadm.data.db.MessageStatus
import com.goldrift.instadm.ui.chat.MAX_MESSAGE_LENGTH
import com.goldrift.instadm.ui.chat.isAdminNoticeKind
import com.goldrift.instadm.ui.chat.publishesReadReceipt

internal const val ACTION_REPLY = "com.goldrift.instadm.action.REPLY"
internal const val ACTION_LIKE = "com.goldrift.instadm.action.LIKE"
internal const val EXTRA_LONG_THREAD_ID = "com.goldrift.instadm.extra.LONG_THREAD_ID"
internal const val EXTRA_MESSAGE_ID = "com.goldrift.instadm.extra.MESSAGE_ID"
internal const val EXTRA_TIMESTAMP_MS = "com.goldrift.instadm.extra.TIMESTAMP_MS"
internal const val EXTRA_REPLY_TEXT = "com.goldrift.instadm.extra.REPLY_TEXT"

/**
 * The notification line the actions apply to. A notification outlives this
 * process, so its receiver can only rebuild state from the intent extras.
 */
internal data class NotificationTarget(
    val threadId: String,
    val longThreadId: String,
    val messageId: String,
    val timestampMs: Long,
) {
    fun putInto(intent: Intent): Intent = intent.apply {
        putExtra(MessageNotifier.EXTRA_THREAD_ID, threadId)
        putExtra(EXTRA_LONG_THREAD_ID, longThreadId)
        putExtra(EXTRA_MESSAGE_ID, messageId)
        putExtra(EXTRA_TIMESTAMP_MS, timestampMs)
    }

    companion object {
        fun from(intent: Intent): NotificationTarget? {
            val threadId = intent.getStringExtra(MessageNotifier.EXTRA_THREAD_ID)
                ?.trim()
                .orEmpty()
            if (threadId.isEmpty()) return null
            return NotificationTarget(
                threadId = threadId,
                longThreadId = intent.getStringExtra(EXTRA_LONG_THREAD_ID).orEmpty(),
                messageId = intent.getStringExtra(EXTRA_MESSAGE_ID).orEmpty(),
                timestampMs = intent.getLongExtra(EXTRA_TIMESTAMP_MS, 0L),
            )
        }
    }
}

/** Activity notices are not messages, so liking one would target nothing. */
internal fun canLikeNotification(kind: String): Boolean =
    kind != REACTION_LOG_KIND && !isAdminNoticeKind(kind)

/**
 * Reply must stay mutable where SystemUI writes the typed text into the intent:
 * pre-S it delivers a fill-in intent, and S+ requires mutability for RemoteInput.
 * Like carries no input, so it never needs it.
 */
internal fun replyPendingFlags(withRemoteInput: Boolean, sdkInt: Int): Int = when {
    withRemoteInput && sdkInt >= Build.VERSION_CODES.S -> PendingIntent.FLAG_MUTABLE
    // Pre-S a mutable PendingIntent needs no flag, and FLAG_IMMUTABLE makes SystemUI skip the fill-in.
    withRemoteInput -> 0
    else -> PendingIntent.FLAG_IMMUTABLE
}

/**
 * Reply and like for the newest line in a conversation notification. Both
 * share the notification's request code because each intent differs by action,
 * and the extras carry everything the receiver needs.
 */
internal fun notificationMessageActions(
    context: Context,
    target: NotificationTarget,
    requestCode: Int,
    canLike: Boolean,
): List<NotificationCompat.Action> {
    val actions = mutableListOf<NotificationCompat.Action>()
    if (canLike) {
        actions += NotificationCompat.Action.Builder(
            R.drawable.ic_notification_like,
            context.getString(R.string.notification_action_like),
            broadcast(context, ACTION_LIKE, target, requestCode),
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_THUMBS_UP)
            .build()
    }
    actions += NotificationCompat.Action.Builder(
        R.drawable.ic_notification_reply,
        context.getString(R.string.notification_action_reply),
        broadcast(context, ACTION_REPLY, target, requestCode, withRemoteInput = true),
    )
        .addRemoteInput(
            RemoteInput.Builder(EXTRA_REPLY_TEXT)
                .setLabel(context.getString(R.string.notification_action_reply))
                .build(),
        )
        .setAllowGeneratedReplies(true)
        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
        .build()
    return actions
}

private fun broadcast(
    context: Context,
    action: String,
    target: NotificationTarget,
    requestCode: Int,
    withRemoteInput: Boolean = false,
): PendingIntent {
    val intent = target.putInto(
        Intent(context, NotificationActionReceiver::class.java).setAction(action),
    )
    val mutability = replyPendingFlags(withRemoteInput, Build.VERSION.SDK_INT)
    return PendingIntent.getBroadcast(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or mutability,
    )
}

/**
 * Applies a notification action. It always runs off the UI thread and in a
 * process that may have just started, so every step re-reads Room instead of
 * trusting state captured when the notification was posted.
 */
internal class NotificationActions(
    private val chatStore: ChatStore,
    private val repository: ProtocolApi,
    private val outbox: Outbox,
    private val settings: () -> AppSettings,
) {
    suspend fun handle(action: String, target: NotificationTarget, replyText: String? = null) {
        when (action) {
            ACTION_REPLY -> reply(target, replyText)
            ACTION_LIKE -> like(target)
        }
    }

    private suspend fun reply(target: NotificationTarget, replyText: String?) {
        val text = replyText?.trim().orEmpty().take(MAX_MESSAGE_LENGTH)
        if (text.isEmpty()) return
        runCatching { outbox.enqueueText(target.threadId, text) }
            .onSuccess { markRead(target) }
            .onFailure { Log.w(TAG, "notification reply failed: ${it.javaClass.simpleName}") }
    }

    private suspend fun like(target: NotificationTarget) {
        val message = chatStore.message(target.messageId) ?: return
        if (message.isFromViewer || message.status == MessageStatus.DELETED) return
        val emoji = settings().doubleTapEmoji.trim()
        if (emoji.isEmpty()) return
        // Always add: a notification cannot unlike, and liking an already-liked line is a no-op.
        runCatching { repository.react(target.threadId, target.messageId, emoji, remove = false) }
            .onSuccess {
                chatStore.setReaction(
                    messageId = target.messageId,
                    senderFbid = chatStore.viewerFbid(),
                    emoji = emoji,
                    isFromViewer = true,
                )
            }
            .onFailure { Log.w(TAG, "notification like failed: ${it.javaClass.simpleName}") }
    }

    /**
     * Clears the local badge first: the message has been seen even when
     * Instagram will not accept a receipt for this thread.
     */
    private suspend fun markRead(target: NotificationTarget) {
        val message = chatStore.message(target.messageId)
        val watermark = message?.timestampMs?.takeIf { it > 0 } ?: target.timestampMs
        if (watermark <= 0) return
        chatStore.markThreadRead(target.threadId, watermark)

        // A tombstone keeps its server ID, and a receipt for it would name a message Instagram deleted.
        if (message?.status == MessageStatus.DELETED) return

        val folder = chatStore.threadWithParticipants(target.threadId)
            ?.thread
            ?.systemFolder
            .orEmpty()
        // Only a reply reaches here, and a reply publishes the receipt even when receipts are hidden.
        if (!publishesReadReceipt(folder, settings().sendReadReceipts, fromSend = true)) return
        if (target.messageId.isBlank()) return
        runCatching {
            repository.markRead(
                threadId = target.threadId,
                messageId = target.messageId,
                longThreadId = target.longThreadId,
                timestampMs = watermark,
            )
        }.onFailure { Log.w(TAG, "notification read receipt failed: ${it.javaClass.simpleName}") }
    }

    private companion object {
        const val TAG = "NotificationActions"
    }
}
