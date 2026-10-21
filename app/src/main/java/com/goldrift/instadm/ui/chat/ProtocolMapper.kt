package com.goldrift.instadm.ui.chat

import com.goldrift.instadm.data.db.ParticipantEntity
import com.goldrift.instadm.data.db.ThreadEntity
import com.goldrift.instadm.data.db.ThreadWithParticipants
import com.goldrift.instadm.data.messagePlaceholder
import com.goldrift.instadm.data.parseMedia
import org.json.JSONArray
import org.json.JSONObject
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
        preview = inboxPreview(thread),
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
        hasMoreOlder = previous?.hasMoreOlder ?: thread.hasMoreOlder,
        oldestCursor = previous?.oldestCursor?.takeIf { it.isNotBlank() }
            ?: thread.oldestCursor,
        isLoadingOlder = previous?.isLoadingOlder ?: false,
        olderError = previous?.olderError ?: false,
    )
}

/** One-line inbox preview built from the denormalized last-message columns. */
internal fun inboxPreview(thread: ThreadEntity): String {
    val label = thread.lastMessageText.ifBlank {
        if (thread.lastMessageKind.isBlank()) "" else messagePlaceholder(thread.lastMessageKind)
    }
    if (label.isBlank()) return ""
    return if (thread.lastMessageFromViewer) "You: $label" else label
}

private fun participantTitle(participants: List<ParticipantEntity>): String {
    val names = participants
        .filterNot { it.isViewer }
        .map { it.name.ifBlank { it.username } }
        .filter { it.isNotBlank() }
    return if (names.isEmpty()) "(unknown)" else names.joinToString(", ")
}

fun mapThread(json: JSONObject, conversation: Conversation): Conversation {
    val thread = json.optJSONObject("thread")
    val title = thread?.optString("title").orEmpty()
    val participants = mapParticipants(thread?.optJSONArray("participants"))
    return conversation.copy(
        contact = conversation.contact.copy(
            name = title.ifBlank { conversation.contact.name },
            initials = initialsOf(title.ifBlank { conversation.contact.name }),
            avatarUrl = thread?.optString("avatar_url").orEmpty().ifBlank {
                conversation.contact.avatarUrl
            },
        ),
        participants = participants.ifEmpty { conversation.participants },
        threadFbid = thread?.optString("thread_fbid").orEmpty()
            .ifBlank { conversation.threadFbid },
        longThreadId = thread?.optString("thread_id").orEmpty()
            .ifBlank { conversation.longThreadId },
        systemFolder = thread?.optString("system_folder").orEmpty()
            .ifBlank { conversation.systemFolder },
        messages = mapMessages(json.optJSONArray("messages")),
        messagesLoaded = true,
        hasMoreOlder = json.optBoolean("has_more_older"),
        oldestCursor = json.optString("oldest_cursor"),
        isLoadingOlder = false,
        olderError = false,
    )
}

/**
 * Maps a page fetched with `afterCursor`. Page JSON carries no `thread` object,
 * so thread metadata is kept from [conversation]. Older messages are prepended
 * and deduplicated against the page they overlap at the cursor boundary.
 */
fun mapOlderPage(json: JSONObject, conversation: Conversation): Conversation {
    val older = mapMessages(json.optJSONArray("messages"))
    return conversation.copy(
        messages = mergeOlderMessages(conversation.messages, older),
        hasMoreOlder = json.optBoolean("has_more_older"),
        oldestCursor = json.optString("oldest_cursor"),
        isLoadingOlder = false,
        olderError = false,
    )
}

/**
 * Prepends [older] to [existing] without duplicating messages the two pages
 * share. Existing entries win, so pending and seen flags are preserved.
 */
fun mergeOlderMessages(
    existing: List<ChatMessage>,
    older: List<ChatMessage>,
): List<ChatMessage> {
    if (older.isEmpty()) return existing
    val known = existing.mapTo(HashSet(existing.size), ChatMessage::id)
    val additions = older.filter { known.add(it.id) }
    if (additions.isEmpty()) return existing
    return additions + existing
}

private fun mapMessages(messages: JSONArray?): List<ChatMessage> {
    if (messages == null) return emptyList()
    val result = ArrayList<ChatMessage>(messages.length())
    for (index in 0 until messages.length()) {
        val message = messages.optJSONObject(index) ?: continue
        val kind = message.optString("kind")
        val timestampMs = message.optLong("timestamp_ms")
        val media = parseMedia(message.optJSONArray("media"))
        result += ChatMessage(
            id = message.optString("id").ifBlank { "message-$index" },
            text = message.optString("text").ifBlank {
                if (media.isEmpty()) messagePlaceholder(kind) else ""
            },
            sender = if (message.optBoolean("is_from_viewer")) {
                MessageSender.Me
            } else {
                MessageSender.Them
            },
            senderFbid = message.optLong("sender_fbid"),
            senderName = message.optString("sender_name"),
            timestamp = formatMessageTime(timestampMs),
            timestampMs = timestampMs,
            seen = message.optBoolean("seen"),
            media = media,
            reactions = parseReactions(message.optJSONArray("reactions")),
            offlineThreadingId = message.optString("offline_threading_id"),
        )
    }
    return result
}

private fun parseReactions(reactions: JSONArray?): List<MessageReaction> {
    if (reactions == null) return emptyList()
    val result = ArrayList<MessageReaction>(reactions.length())
    for (index in 0 until reactions.length()) {
        val reaction = reactions.optJSONObject(index) ?: continue
        val emoji = reaction.optString("emoji")
        if (emoji.isBlank()) continue
        result += MessageReaction(
            emoji = emoji,
            senderFbid = reaction.optLong("sender_fbid"),
            isFromViewer = reaction.optBoolean("is_from_viewer"),
        )
    }
    return result
}

/**
 * Maps the thread's participant list for avatar rendering. The viewer is
 * dropped: group collages show the other members, and one-to-one rows use
 * [Contact.avatarUrl] resolved by the Go core.
 */
private fun mapParticipants(participants: JSONArray?): List<ConversationParticipant> {
    if (participants == null) return emptyList()
    val result = ArrayList<ConversationParticipant>(participants.length())
    for (index in 0 until participants.length()) {
        val participant = participants.optJSONObject(index) ?: continue
        if (participant.optBoolean("is_viewer")) continue
        val name = participant.optString("name")
            .ifBlank { participant.optString("username") }
        val fbid = participant.optLong("fbid")
        if (name.isBlank() && fbid == 0L) continue
        result += ConversationParticipant(
            fbid = fbid,
            name = name,
            initials = initialsOf(name),
            avatarUrl = participant.optString("avatar_url"),
        )
    }
    return result
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
