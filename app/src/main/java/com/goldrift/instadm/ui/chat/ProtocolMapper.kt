package com.goldrift.instadm.ui.chat

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
        )
    }
    return result.sortedByDescending { it.lastActivityMs }
}

fun mapThread(json: JSONObject, conversation: Conversation): Conversation =
    conversation.copy(messages = mapMessages(json.optJSONArray("messages")))

private fun mapMessages(messages: JSONArray?): List<ChatMessage> {
    if (messages == null) return emptyList()
    val result = ArrayList<ChatMessage>(messages.length())
    for (index in 0 until messages.length()) {
        val message = messages.optJSONObject(index) ?: continue
        val kind = message.optString("kind")
        val text = message.optString("text").ifBlank { placeholder(kind) }
        result += ChatMessage(
            id = message.optString("id").ifBlank { "message-$index" },
            text = text,
            sender = if (message.optBoolean("is_from_viewer")) {
                MessageSender.Me
            } else {
                MessageSender.Them
            },
            timestamp = formatMessageTime(message.optLong("timestamp_ms")),
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

private fun placeholder(kind: String): String = when (kind) {
    "image" -> "[image]"
    "video" -> "[video]"
    "audio" -> "[audio]"
    "gif" -> "[gif]"
    "sticker" -> "[sticker]"
    "media" -> "[media]"
    "link" -> "[link]"
    else -> if (kind.isBlank()) "" else "[$kind]"
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
