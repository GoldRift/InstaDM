package com.goldrift.instadm.ui.chat

import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.data.messagePlaceholder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MessageSender { Me, Them }

/** A person the user can exchange direct messages with. */
data class Contact(
    val id: String,
    val name: String,
    val initials: String,
    /** Profile picture URL; empty when Instagram has none. */
    val avatarUrl: String = "",
)

/** A group member whose picture can appear in the conversation avatar. */
data class ConversationParticipant(
    val fbid: Long,
    val name: String,
    val initials: String,
    val avatarUrl: String = "",
)

/** A single DM thread shown in the inbox. */
data class Conversation(
    val id: String,
    val contact: Contact,
    val messages: List<ChatMessage> = emptyList(),
    val lastActivityMs: Long = 0,
    val preview: String = "",
    val isGroup: Boolean = false,
    /** Other members, excluding the viewer; used for the group avatar collage. */
    val participants: List<ConversationParticipant> = emptyList(),
    val messagesLoaded: Boolean = false,
    val threadFbid: String = "",
    /** Instagram's long thread ID (`thread_id`), needed for read receipts. */
    val longThreadId: String = "",
    val systemFolder: String = "",
    /** When the newest page was fetched; 0 means it was never loaded. */
    val messagesSyncedAt: Long = 0,
    /** True while Instagram reports more messages older than [oldestCursor]. */
    val hasMoreOlder: Boolean = false,
    /** Cursor for the next older page; blank until the newest page is loaded. */
    val oldestCursor: String = "",
    val isLoadingOlder: Boolean = false,
    /** A failed older-page fetch; automatic paging pauses until the user retries. */
    val olderError: Boolean = false,
)

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: MessageSender,
    val timestamp: String,
    val timestampMs: Long = 0,
    val seen: Boolean = false,
    val pending: Boolean = false,
    val failed: Boolean = false,
    /** FBID of the participant who sent an incoming message (0 for the viewer). */
    val senderFbid: Long = 0,
    /** Display name of an incoming sender, for the bubble avatar fallback. */
    val senderName: String = "",
    val media: List<MessageMedia> = emptyList(),
    val reactions: List<MessageReaction> = emptyList(),
    /** Local preview for media that has not reached the server yet. */
    val localMedia: PendingMedia? = null,
    /** Offline threading ID used to match the realtime echo of a send. */
    val offlineThreadingId: String = "",
)

/**
 * A picked attachment shown optimistically in an outgoing bubble. The content
 * URI stays readable for the lifetime of the activity, which is long enough for
 * the upload and a retry.
 */
data class PendingMedia(
    val uri: String,
    val kind: String,
    val fileName: String,
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Int = 0,
) {
    val aspectRatio: Float
        get() {
            if (width <= 0 || height <= 0) return 1f
            return (width.toFloat() / height.toFloat()).coerceIn(0.6f, 1.8f)
        }
}


/** True for a message Instagram has assigned an ID to. */
fun ChatMessage.isServerMessage(): Boolean =
    !pending &&
        id.isNotBlank() &&
        !id.startsWith("local-") &&
        !id.startsWith("message-")

/** One-line label for inbox previews; falls back to the media placeholder. */
fun ChatMessage.previewLabel(): String = text.ifBlank {
    localMedia?.kind?.let { messagePlaceholder(it) }
        ?: media.firstOrNull()?.let { messagePlaceholder(it.type) }
        ?: ""
}

/** Fixed demo conversations used by Compose previews. */
fun sampleConversations(now: Long = System.currentTimeMillis()): List<Conversation> = listOf(
    Conversation(
        id = "maya",
        contact = Contact(id = "maya", name = "Maya Chen", initials = "MC"),
        messages = listOf(
            message("maya-1", "hey! are we still on for tonight?", MessageSender.Them, now, 18),
            message("maya-2", "absolutely. I found that little place on 5th", MessageSender.Me, now, 17, seen = true),
            message("maya-3", "the one with the string lights?", MessageSender.Them, now, 16),
            message(
                id = "maya-4",
                text = "that's the one ✨",
                sender = MessageSender.Me,
                now = now,
                minutesAgo = 15,
                seen = true,
                reactions = listOf(MessageReaction("❤️", isFromViewer = true)),
            ),
            message("maya-5", "perfect. 8pm?", MessageSender.Them, now, 14),
            message("maya-6", "I'll be there", MessageSender.Me, now, 13, seen = true),
        ),
        lastActivityMs = now - 13 * 60_000L,
        messagesLoaded = true,
    ),
    Conversation(
        id = "dev",
        contact = Contact(id = "dev", name = "Dev Patel", initials = "DP"),
        messages = listOf(
            message("dev-101", "did you get a chance to look at the deck?", MessageSender.Them, now, 42),
            message("dev-102", "skimming it now — the intro slide is great", MessageSender.Me, now, 40, seen = true),
            message("dev-103", "awesome, ping me with notes", MessageSender.Them, now, 38),
        ),
        lastActivityMs = now - 38 * 60_000L,
        messagesLoaded = true,
    ),
    Conversation(
        id = "sofia",
        contact = Contact(id = "sofia", name = "Sofia Rossi", initials = "SR"),
        messages = listOf(
            message("sofia-201", "the photos came out so good 📸", MessageSender.Me, now, 95, seen = true),
            message("sofia-202", "right?? the light was perfect", MessageSender.Them, now, 92),
            message("sofia-203", "sending you the raws tonight", MessageSender.Me, now, 90, seen = true),
            message("sofia-204", "you're the best", MessageSender.Them, now, 88),
        ),
        lastActivityMs = now - 88 * 60_000L,
        messagesLoaded = true,
    ),
    Conversation(
        id = "liam",
        contact = Contact(id = "liam", name = "Liam O'Brien", initials = "LO"),
        messages = listOf(
            message("liam-301", "running 10 late, sorry!", MessageSender.Them, now, 180),
            message("liam-302", "no worries, got us a table", MessageSender.Me, now, 178, seen = true),
            message("liam-303", "you're a legend", MessageSender.Them, now, 175),
        ),
        lastActivityMs = now - 175 * 60_000L,
        messagesLoaded = true,
    ),
)

private fun message(
    id: String,
    text: String,
    sender: MessageSender,
    now: Long,
    minutesAgo: Int,
    seen: Boolean = false,
    reactions: List<MessageReaction> = emptyList(),
): ChatMessage = ChatMessage(
    id = id,
    text = text,
    sender = sender,
    timestamp = timeLabel(now, minutesAgo),
    timestampMs = now - minutesAgo * 60_000L,
    seen = seen,
    reactions = reactions,
)

private fun timeLabel(now: Long, minutesAgo: Int): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now - minutesAgo * 60_000L))

fun currentTimeLabel(): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

/** Two-letter initials used by avatars, or "?" for an empty name. */
fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    return parts.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
}
