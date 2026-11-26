package com.goldrift.instadm.ui.share

import com.goldrift.instadm.ui.chat.Conversation
import com.goldrift.instadm.ui.chat.MAX_MESSAGE_LENGTH

internal sealed interface ShareCompletion {
    data object None : ShareCompletion
    data class OpenChat(val threadId: String) : ShareCompletion
    data class ReturnToInbox(val count: Int) : ShareCompletion
}

internal fun filterShareConversations(
    conversations: List<Conversation>,
    query: String,
): List<Conversation> {
    val needle = query.trim()
    if (needle.isEmpty()) return conversations
    return conversations.filter { conversation ->
        conversation.contact.name.contains(needle, ignoreCase = true) ||
            (conversation.isGroup && conversation.participants.any {
                it.name.contains(needle, ignoreCase = true)
            })
    }
}

internal fun toggleShareRecipient(
    selectedIds: List<String>,
    threadId: String,
): List<String> {
    if (threadId.isBlank()) return selectedIds
    return if (threadId in selectedIds) {
        selectedIds.filterNot { it == threadId }
    } else {
        selectedIds + threadId
    }
}

internal fun pruneSelectedRecipients(
    selectedIds: List<String>,
    conversations: List<Conversation>,
): List<String> {
    if (selectedIds.isEmpty()) return emptyList()
    val validIds = conversations.mapTo(HashSet(conversations.size)) { it.id }
    return selectedIds.filter { it in validIds }
}

/** Counts Unicode code points after trimming, matching the Go core's rune limit. */
internal fun shareTextLength(rawText: String): Int {
    val trimmed = rawText.trim()
    if (trimmed.isEmpty()) return 0
    return trimmed.codePointCount(0, trimmed.length)
}

internal fun canSendShare(
    selectedCount: Int,
    noteText: String,
    stagedMediaCount: Int,
    preparingMedia: Boolean,
    isSending: Boolean,
    maxTextLength: Int = MAX_MESSAGE_LENGTH,
): Boolean {
    if (selectedCount <= 0 || preparingMedia || isSending) return false
    val textLength = shareTextLength(noteText)
    if (textLength > maxTextLength) return false
    return textLength > 0 || stagedMediaCount > 0
}

internal fun resolveShareCompletion(queuedThreadIds: List<String>): ShareCompletion =
    when (queuedThreadIds.size) {
        0 -> ShareCompletion.None
        1 -> ShareCompletion.OpenChat(queuedThreadIds.single())
        else -> ShareCompletion.ReturnToInbox(queuedThreadIds.size)
    }
