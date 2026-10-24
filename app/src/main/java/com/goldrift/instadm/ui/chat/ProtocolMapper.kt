package com.goldrift.instadm.ui.chat

import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.data.ReplySnapshot
import com.goldrift.instadm.data.db.AttachmentEntity
import com.goldrift.instadm.data.db.MessageEntity
import com.goldrift.instadm.data.db.MessageStatus
import com.goldrift.instadm.data.db.MessageWithMedia
import com.goldrift.instadm.data.db.ParticipantEntity
import com.goldrift.instadm.data.db.ThreadEntity
import com.goldrift.instadm.data.db.ThreadWithParticipants
import com.goldrift.instadm.data.parseWaveform
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Maps a persisted inbox row into the Compose model. Messages loaded earlier in
 * the process are carried over so a database update does not discard them.
 */
fun ThreadWithParticipants.toConversation(previous: Conversation?): Conversation {
    val thread = this.thread
    val name = thread.title.ifBlank { participantTitle(participants) }
    return Conversation(
        id = thread.id,
        contact = Contact(
            id = thread.id,
            name = name,
            initials = initialsOf(name),
            avatarUrl = thread.avatarUrl,
        ),
        messages = previous?.messages.orEmpty(),
        lastActivityMs = thread.lastActivityMs,
        preview = ConversationPreview(
            kind = thread.lastMessageKind,
            text = thread.lastMessageText,
            fromViewer = thread.lastMessageFromViewer,
            senderName = if (thread.isGroup) {
                participantName(participants, thread.lastMessageSenderFbid)
            } else {
                ""
            },
        ),
        isGroup = thread.isGroup,
        participants = participants.filterNot { it.isViewer }.map { participant ->
            ConversationParticipant(
                fbid = participant.fbid,
                name = participant.name.ifBlank { participant.username },
                initials = initialsOf(participant.name.ifBlank { participant.username }),
                avatarUrl = participant.avatarUrl,
            )
        },
        messagesLoaded = previous?.messagesLoaded ?: false,
        threadFbid = thread.threadFbid,
        longThreadId = thread.longThreadId,
        systemFolder = thread.systemFolder,
        messagesSyncedAt = thread.messagesSyncedAt,
        hasMoreOlder = thread.hasMoreOlder,
        oldestCursor = thread.oldestCursor,
        isLoadingOlder = previous?.isLoadingOlder ?: false,
        olderError = previous?.olderError ?: false,
    )
}

/** Maps a persisted message and its media into the Compose model. */
fun MessageWithMedia.toChatMessage(): ChatMessage = ChatMessage(
    id = message.id,
    text = message.text,
    sender = if (message.isFromViewer) MessageSender.Me else MessageSender.Them,
    timestamp = formatMessageTime(message.timestampMs),
    timestampMs = message.timestampMs,
    kind = message.kind,
    seen = message.seen,
    pending = message.status == MessageStatus.PENDING ||
        message.status == MessageStatus.SENDING,
    failed = message.status == MessageStatus.FAILED,
    senderFbid = message.senderFbid,
    senderName = message.senderName,
    media = attachments.sortedBy { it.partIndex }.map { it.toMessageMedia() },
    reactions = reactions.map {
        MessageReaction(
            emoji = it.emoji,
            senderFbid = it.senderFbid,
            isFromViewer = it.isFromViewer,
        )
    },
    reply = message.replySnapshot(),
    offlineThreadingId = message.otid,
)

private fun MessageEntity.replySnapshot(): ReplySnapshot? {
    if (replyToId.isBlank() && replyToText.isBlank()) return null
    return ReplySnapshot(
        id = replyToId,
        text = replyToText,
        senderName = replyToSender,
        isFromViewer = replyToIsFromViewer,
        kind = replyToKind,
        mediaCount = replyToMediaCount,
    )
}

/** Snapshots a loaded message so the composer and outbox can quote it. */
fun ChatMessage.toReplySnapshot(): ReplySnapshot = ReplySnapshot(
    id = id,
    text = text,
    senderName = senderName,
    isFromViewer = sender == MessageSender.Me,
    kind = kind,
    mediaCount = media.size,
)

private fun AttachmentEntity.toMessageMedia(): MessageMedia = MessageMedia(
    type = kind,
    url = url,
    previewUrl = previewUrl,
    width = width,
    height = height,
    durationMs = durationMs.toInt(),
    partIndex = partIndex,
    localPath = localPath,
    waveform = parseWaveform(waveform),
    viewOnce = viewOnce,
    viewMode = viewMode,
)

/** Display name of a group participant, used to attribute the newest message. */
private fun participantName(participants: List<ParticipantEntity>, fbid: Long): String {
    if (fbid == 0L) return ""
    return participants.firstOrNull { it.fbid == fbid && !it.isViewer }
        ?.let { it.name.ifBlank { it.username } }
        .orEmpty()
}

private fun participantTitle(participants: List<ParticipantEntity>): String {
    val names = participants
        .filterNot { it.isViewer }
        .map { it.name.ifBlank { it.username } }
        .filter { it.isNotBlank() }
    return if (names.isEmpty()) "(unknown)" else names.joinToString(", ")
}

fun formatMessageTime(milliseconds: Long): String {
    if (milliseconds <= 0) return currentTimeLabel()
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(milliseconds))
}

fun formatInboxTime(milliseconds: Long): String {
    if (milliseconds <= 0) return ""
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = milliseconds }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val pattern = if (sameDay) "h:mm a" else "MMM d"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(milliseconds))
}
