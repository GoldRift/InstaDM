package com.goldrift.instadm.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

/** Avoid a mailbox refresh per message for a thread outside the first page. */
private const val UNKNOWN_THREAD_REFRESH_MS = 60_000L

/**
 * Applies DGW events to Room. It is process-scoped (not tied to the UI), so a
 * message that arrives while the foreground service is running is stored even
 * if the activity was never opened.
 */
class RealtimeRecorder(
    private val chatStore: ChatStore,
    private val scope: CoroutineScope,
    private val onIncomingMessage: (RealtimeEvent) -> Unit = {},
    private val syncThread: suspend (String) -> Unit = {},
    private val refreshMailbox: suspend () -> Unit,
) {
    private val unknownThreadRefreshAt = mutableMapOf<String, Long>()
    private val reactionSyncLocks = mutableMapOf<String, Mutex>()
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            RealtimeBus.events.collect { event -> apply(event) }
        }
    }

    suspend fun apply(event: RealtimeEvent) {
        when (event.type) {
            "message.new", "message.admin" -> newMessage(event)
            "message.edit" -> editMessage(event)
            "message.delete" -> deleteMessage(event)
            "reaction.add", "reaction.remove" -> reaction(event)
            "read.receipt" -> readReceipt(event)
        }
    }

    private suspend fun newMessage(event: RealtimeEvent) {
        val message = event.message ?: return
        if (event.threadId.isBlank() || message.id.isBlank()) return

        val known = chatStore.threadExists(event.threadId)
        chatStore.ingestRealtimeMessage(event.threadId, message)
        if (!known) refreshUnknownThread(event.threadId)
        onIncomingMessage(event)
    }

    private suspend fun editMessage(event: RealtimeEvent) {
        val message = event.message ?: return
        if (event.threadId.isBlank() || message.id.isBlank() || message.text.isBlank()) return
        chatStore.editMessageText(message.id, message.text)
    }

    private suspend fun deleteMessage(event: RealtimeEvent) {
        val message = event.message ?: return
        if (event.threadId.isBlank() || message.id.isBlank()) return
        chatStore.tombstoneMessage(message.id)
    }

    private suspend fun reaction(event: RealtimeEvent) {
        val messageID = event.message?.id.orEmpty()
        if (event.threadId.isBlank() || messageID.isBlank() || event.reaction.isBlank()) return

        if (event.type == "reaction.remove") {
            if (event.participantFbid != 0L) {
                chatStore.removeReaction(messageID, event.participantFbid)
            } else if (event.fromViewer) {
                chatStore.removeViewerReaction(messageID)
            }
            return
        }

        chatStore.setReaction(
            messageId = messageID,
            senderFbid = if (event.fromViewer) chatStore.viewerFbid() else event.participantFbid,
            emoji = event.reaction,
            isFromViewer = event.fromViewer,
        )
        syncReactionLog(event)
    }

    /**
     * Instagram pushes a like or reaction as a reaction delta only; the matching
     * "Liked a message" / "Reacted … to your message" entry is written
     * server-side and never arrives over DGW. Fetching the newest page is what
     * puts that entry in Room, so the inbox preview and the notification can use
     * its real text and timestamp instead of waiting for an unrelated refresh.
     */
    private fun syncReactionLog(event: RealtimeEvent) {
        if (event.fromViewer || event.timestampMs <= 0 || event.threadId.isBlank()) return
        scope.launch {
            reactionLock(event.threadId).withLock { publishReactionLog(event) }
        }
    }

    private suspend fun publishReactionLog(event: RealtimeEvent) {
        val log = chatStore.reactionLog(event.threadId, event.timestampMs)
            ?: run {
                runCatching { syncThread(event.threadId) }
                chatStore.reactionLog(event.threadId, event.timestampMs)
            }
            ?: return
        if (log.isFromViewer) return

        onIncomingMessage(
            RealtimeEvent(
                type = "message.admin",
                threadId = event.threadId,
                message = RealtimeMessage(
                    id = log.id,
                    text = log.text,
                    senderName = log.senderName,
                    senderFbid = log.senderFbid,
                    isFromViewer = false,
                    timestampMs = log.timestampMs,
                    kind = log.kind,
                ),
            ),
        )
    }

    // Reactions land one delta per emoji, so a burst of them must not start a
    // page fetch each; the second fetch also sees the first one's entry.
    private fun reactionLock(threadId: String): Mutex =
        synchronized(reactionSyncLocks) {
            reactionSyncLocks.getOrPut(threadId) { Mutex() }
        }

    private suspend fun readReceipt(event: RealtimeEvent) {
        if (event.threadId.isBlank() || event.timestampMs <= 0) return
        if (event.fromViewer) {
            chatStore.markThreadRead(event.threadId, event.timestampMs)
        } else {
            chatStore.markMessagesSeen(event.threadId, event.timestampMs)
        }
    }

    /**
     * A message for a thread outside the loaded inbox page has no title or
     * participants yet. Fetch the mailbox once per thread per minute so the row
     * becomes tappable instead of staying a header-less stub.
     */
    private suspend fun refreshUnknownThread(threadId: String) {
        val now = System.currentTimeMillis()
        val last = unknownThreadRefreshAt[threadId] ?: 0L
        if (now - last < UNKNOWN_THREAD_REFRESH_MS) return
        unknownThreadRefreshAt[threadId] = now
        runCatching { refreshMailbox() }
    }
}
