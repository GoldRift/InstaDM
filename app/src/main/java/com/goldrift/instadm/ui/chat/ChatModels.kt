package com.goldrift.instadm.ui.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MessageSender { Me, Them }

/** A person the user can exchange direct messages with. */
data class Contact(
    val id: String,
    val name: String,
    val initials: String,
)

/** A single DM thread shown in the inbox. */
data class Conversation(
    val id: String,
    val contact: Contact,
    val messages: List<ChatMessage> = emptyList(),
    val lastActivityMs: Long = 0,
    val preview: String = "",
    val isGroup: Boolean = false,
)

data class ChatMessage(
    val id: String,
    val text: String,
    val sender: MessageSender,
    val timestamp: String,
    val seen: Boolean = false,
    val pending: Boolean = false,
)

/** Fixed demo conversations used by Compose previews. */
fun sampleConversations(now: Long = System.currentTimeMillis()): List<Conversation> = listOf(
    Conversation(
        id = "maya",
        contact = Contact(id = "maya", name = "Maya Chen", initials = "MC"),
        messages = listOf(
            message("maya-1", "hey! are we still on for tonight?", MessageSender.Them, now, 18),
            message("maya-2", "absolutely. I found that little place on 5th", MessageSender.Me, now, 17, seen = true),
            message("maya-3", "the one with the string lights?", MessageSender.Them, now, 16),
            message("maya-4", "that's the one ✨", MessageSender.Me, now, 15, seen = true),
            message("maya-5", "perfect. 8pm?", MessageSender.Them, now, 14),
            message("maya-6", "I'll be there", MessageSender.Me, now, 13, seen = true),
        ),
        lastActivityMs = now - 13 * 60_000L,
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
    ),
)

private fun message(
    id: String,
    text: String,
    sender: MessageSender,
    now: Long,
    minutesAgo: Int,
    seen: Boolean = false,
): ChatMessage = ChatMessage(
    id = id,
    text = text,
    sender = sender,
    timestamp = timeLabel(now, minutesAgo),
    seen = seen,
)

private fun timeLabel(now: Long, minutesAgo: Int): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now - minutesAgo * 60_000L))

fun currentTimeLabel(): String =
    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
