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
    val contact: Contact,
    val messages: List<ChatMessage>,
)

data class ChatMessage(
    val id: Long,
    val text: String,
    val sender: MessageSender,
    val timestamp: String,
    val seen: Boolean = false,
)

/** Fixed demo conversations. No persistence or backend involved. */
fun sampleConversations(now: Long = System.currentTimeMillis()): List<Conversation> = listOf(
    Conversation(
        contact = Contact(id = "maya", name = "Maya Chen", initials = "MC"),
        messages = listOf(
            message(1, "hey! are we still on for tonight?", MessageSender.Them, now, 18),
            message(2, "absolutely. I found that little place on 5th", MessageSender.Me, now, 17, seen = true),
            message(3, "the one with the string lights?", MessageSender.Them, now, 16),
            message(4, "that's the one ✨", MessageSender.Me, now, 15, seen = true),
            message(5, "perfect. 8pm?", MessageSender.Them, now, 14),
            message(6, "I'll be there", MessageSender.Me, now, 13, seen = true),
        ),
    ),
    Conversation(
        contact = Contact(id = "dev", name = "Dev Patel", initials = "DP"),
        messages = listOf(
            message(101, "did you get a chance to look at the deck?", MessageSender.Them, now, 42),
            message(102, "skimming it now — the intro slide is great", MessageSender.Me, now, 40, seen = true),
            message(103, "awesome, ping me with notes", MessageSender.Them, now, 38),
        ),
    ),
    Conversation(
        contact = Contact(id = "sofia", name = "Sofia Rossi", initials = "SR"),
        messages = listOf(
            message(201, "the photos came out so good 📸", MessageSender.Me, now, 95, seen = true),
            message(202, "right?? the light was perfect", MessageSender.Them, now, 92),
            message(203, "sending you the raws tonight", MessageSender.Me, now, 90, seen = true),
            message(204, "you're the best", MessageSender.Them, now, 88),
        ),
    ),
    Conversation(
        contact = Contact(id = "liam", name = "Liam O'Brien", initials = "LO"),
        messages = listOf(
            message(301, "running 10 late, sorry!", MessageSender.Them, now, 180),
            message(302, "no worries, got us a table", MessageSender.Me, now, 178, seen = true),
            message(303, "you're a legend", MessageSender.Them, now, 175),
        ),
    ),
)

/** Rotated through by the local demo auto-reply. */
val CannedReplies = listOf(
    "perfect, see you at 8!",
    "just sent you the pin 📍",
    "omw, five minutes out",
    "haha yes, exactly that",
)

private fun message(
    id: Long,
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
