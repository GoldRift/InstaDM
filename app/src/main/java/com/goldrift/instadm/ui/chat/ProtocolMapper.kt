package com.goldrift.instadm.ui.chat

import com.goldrift.instadm.data.messagePlaceholder
import com.goldrift.instadm.data.parseMedia
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Maps the normalized Go JSON into the Compose models. */
fun mapMailbox(
    json: JSONObject,
    existing: Map<String, Conversation>,
): List<Conversation> {
    val threads = json.optJSONArray("threads") ?: return emptyList()
    val result = ArrayList<Conversation>(threads.length())
    for (index in 0 until threads.length()) {
        val thread = threads.optJSONObject(index) ?: continue
        val threadID = thread.optString("id")
        if (threadID.isBlank()) continue

        val title = threadTitle(thread)
        val previous = existing[threadID]
        result += Conversation(
            id = threadID,
            contact = Contact(
                id = threadID,
                name = title,
                initials = initials(title),
            ),
            messages = previous?.messages.orEmpty(),
            lastActivityMs = thread.optLong("last_activity_ms"),
            preview = previous?.preview.orEmpty(),
            isGroup = thread.optBoolean("is_group"),
            // Reload the thread when it is reopened after a mailbox refresh so
            // read receipts and newer messages are not left stale.
            messagesLoaded = false,
            threadFbid = thread.optString("thread_fbid").ifBlank {
                previous?.threadFbid.orEmpty()
            },
            systemFolder = thread.optString("system_folder").ifBlank {
                previous?.systemFolder.orEmpty()
            },
        )
    }
    return result.sortedByDescending { it.lastActivityMs }
}

fun mapThread(json: JSONObject, conversation: Conversation): Conversation {
    val thread = json.optJSONObject("thread")
    return conversation.copy(
        threadFbid = thread?.optString("thread_fbid").orEmpty()
            .ifBlank { conversation.threadFbid },
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

private fun threadTitle(thread: JSONObject): String {
    val title = thread.optString("title")
    if (title.isNotBlank()) return title

    val participants = thread.optJSONArray("participants")
    if (participants != null) {
        for (index in 0 until participants.length()) {
            val participant = participants.optJSONObject(index) ?: continue
            val name = participant.optString("name")
                .ifBlank { participant.optString("username") }
            if (name.isNotBlank()) return name
        }
    }
    return "Instagram user"
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    return parts.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "?" }
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
