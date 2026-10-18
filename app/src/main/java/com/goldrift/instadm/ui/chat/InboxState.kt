package com.goldrift.instadm.ui.chat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * In-memory source of truth for the inbox and every DM thread.
 *
 * Held above the navigation host so sent messages and the demo auto-replies
 * survive moving between the inbox and a conversation.
 */
@Stable
class InboxState(
    initialConversations: List<Conversation>,
    private val scope: CoroutineScope,
) {
    private val items = mutableStateListOf<Conversation>().apply {
        addAll(initialConversations)
    }
    private val typing = mutableStateMapOf<String, Boolean>()
    private val replyIndexes = mutableMapOf<String, Int>()
    private val replyJobs = mutableMapOf<String, Job>()
    private var nextMessageId = (initialConversations.flatMap { it.messages }.maxOfOrNull { it.id } ?: 0L) + 1

    val conversations: List<Conversation>
        get() = items

    fun conversation(contactId: String): Conversation? =
        items.firstOrNull { it.contact.id == contactId }

    fun isTyping(contactId: String): Boolean = typing[contactId] == true

    fun send(contactId: String, rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return

        val sent = ChatMessage(
            id = nextMessageId++,
            text = text,
            sender = MessageSender.Me,
            timestamp = currentTimeLabel(),
        )
        append(contactId, sent)
        typing[contactId] = false
        replyJobs.remove(contactId)?.cancel()

        // Local canned reply: mark the sent message as seen, show typing, then answer.
        replyJobs[contactId] = scope.launch {
            delay(650)
            updateMessage(contactId, sent.id) { it.copy(seen = true) }

            delay(450)
            typing[contactId] = true
            delay(1_500)

            val index = replyIndexes.getOrDefault(contactId, 0)
            append(
                contactId,
                ChatMessage(
                    id = nextMessageId++,
                    text = CannedReplies[index % CannedReplies.size],
                    sender = MessageSender.Them,
                    timestamp = currentTimeLabel(),
                ),
            )
            replyIndexes[contactId] = index + 1
            typing[contactId] = false
        }
    }

    private fun append(contactId: String, message: ChatMessage) {
        update(contactId) { it.copy(messages = it.messages + message) }
    }

    private fun updateMessage(
        contactId: String,
        messageId: Long,
        transform: (ChatMessage) -> ChatMessage,
    ) {
        update(contactId) { conversation ->
            conversation.copy(
                messages = conversation.messages.map { message ->
                    if (message.id == messageId) transform(message) else message
                },
            )
        }
    }

    private fun update(contactId: String, transform: (Conversation) -> Conversation) {
        val index = items.indexOfFirst { it.contact.id == contactId }
        if (index >= 0) items[index] = transform(items[index])
    }
}
