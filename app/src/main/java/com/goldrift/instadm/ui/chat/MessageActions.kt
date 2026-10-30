package com.goldrift.instadm.ui.chat

import com.goldrift.instadm.data.REACTION_LOG_KIND

/**
 * One reaction on a message. Instagram keeps at most one reaction per sender,
 * so a new emoji from the same sender replaces the previous one.
 */
data class MessageReaction(
    val emoji: String,
    val senderFbid: Long = 0,
    val isFromViewer: Boolean = false,
)

/** A display row of identical reactions, in first-seen order. */
data class ReactionGroup(
    val emoji: String,
    val count: Int,
    val fromViewer: Boolean,
)

/** Instagram allows editing one of the viewer's messages for 15 minutes. */
const val EDIT_WINDOW_MS = 15 * 60_000L

/** Maximum text length accepted by the Go core for sends, edits, and new DMs. */
const val MAX_MESSAGE_LENGTH = 1000

fun ChatMessage.canReact(): Boolean = isServerMessage()

/**
 * Admin notices such as "Joined the chat" and the like/reaction activity logs
 * are entries Instagram does not let the viewer quote.
 */
fun ChatMessage.canReply(): Boolean = isServerMessage() && kind != "admin" && !isReactionLog()

/**
 * Likes and reactions arrive as a reaction on the target message plus an
 * activity entry ("Liked a message", "Reacted ❤ to your message"). The
 * transcript hides the entry; inbox previews and notifications still show it.
 */
fun ChatMessage.isReactionLog(): Boolean = kind == REACTION_LOG_KIND

fun ChatMessage.canUnsend(): Boolean = sender == MessageSender.Me && isServerMessage()

fun ChatMessage.canEdit(nowMs: Long = System.currentTimeMillis()): Boolean =
    sender == MessageSender.Me &&
        isServerMessage() &&
        text.isNotBlank() &&
        (timestampMs <= 0 || nowMs - timestampMs <= EDIT_WINDOW_MS)

fun ChatMessage.reactionGroups(): List<ReactionGroup> =
    reactions
        .filter { it.emoji.isNotBlank() }
        .groupBy { it.emoji }
        .map { (emoji, group) ->
            ReactionGroup(
                emoji = emoji,
                count = group.size,
                fromViewer = group.any { it.isFromViewer },
            )
        }

/**
 * Applies one reaction event to a message list. Returns the same list instance
 * when nothing changed so Compose can skip recomposition.
 */
fun applyReactionToMessages(
    messages: List<ChatMessage>,
    messageId: String,
    emoji: String,
    senderFbid: Long,
    fromViewer: Boolean,
    remove: Boolean,
): List<ChatMessage> {
    if (messageId.isBlank() || emoji.isBlank()) return messages
    val index = messages.indexOfFirst { it.id == messageId }
    if (index < 0) return messages

    val message = messages[index]
    val reactions = applyReactionDelta(message.reactions, emoji, senderFbid, fromViewer, remove)
    if (reactions === message.reactions) return messages

    val updated = messages.toMutableList()
    updated[index] = message.copy(reactions = reactions)
    return updated
}

/**
 * Adds or removes one sender's reaction. An add replaces that sender's prior
 * emoji; a remove drops whatever that sender currently has.
 */
fun applyReactionDelta(
    reactions: List<MessageReaction>,
    emoji: String,
    senderFbid: Long,
    fromViewer: Boolean,
    remove: Boolean,
): List<MessageReaction> {
    if (emoji.isBlank()) return reactions
    // Without a sender FBID an incoming reaction cannot be attributed safely.
    if (!fromViewer && senderFbid == 0L) return reactions

    fun belongs(reaction: MessageReaction): Boolean =
        if (fromViewer) reaction.isFromViewer else reaction.senderFbid == senderFbid

    if (remove) {
        if (reactions.none(::belongs)) return reactions
        return reactions.filterNot(::belongs)
    }

    val existing = reactions.indexOfFirst(::belongs)
    if (existing >= 0 && reactions[existing].emoji == emoji) return reactions

    val updated = reactions.toMutableList()
    val reaction = MessageReaction(emoji, senderFbid, fromViewer)
    if (existing >= 0) {
        updated[existing] = reaction
    } else {
        updated += reaction
    }
    return updated
}

/**
 * Optimistically toggles the viewer's reaction. The second value is true when
 * the Go call should remove the emoji instead of adding it.
 */
fun toggleViewerReaction(
    message: ChatMessage,
    emoji: String,
): Pair<List<MessageReaction>, Boolean> {
    val remove = message.reactions.any { it.isFromViewer && it.emoji == emoji }
    val senderFbid = message.reactions.firstOrNull { it.isFromViewer }?.senderFbid ?: 0
    return applyReactionDelta(message.reactions, emoji, senderFbid, fromViewer = true, remove = remove) to remove
}

/** Puts the viewer's previous reaction back after a failed toggle. */
fun restoreViewerReaction(
    reactions: List<MessageReaction>,
    original: MessageReaction?,
): List<MessageReaction> {
    val withoutViewer = reactions.filterNot { it.isFromViewer }
    return if (original == null) withoutViewer else withoutViewer + original
}
