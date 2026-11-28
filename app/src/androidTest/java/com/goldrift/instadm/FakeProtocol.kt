package com.goldrift.instadm

import com.goldrift.instadm.data.MessageMedia
import com.goldrift.instadm.data.OutboxSender
import com.goldrift.instadm.data.ProtocolApi
import org.json.JSONObject
import java.io.File

/**
 * A [ProtocolApi] with no network. [cookies] is what [hasCookies] reports, and
 * [mailboxError] is thrown by the mailbox call so a test can drive the inbox
 * with a specific failure.
 */
internal class FakeProtocol(
    @Volatile var cookies: Boolean = false,
    @Volatile var mailboxError: Throwable? = null,
) : ProtocolApi {
    data class React(
        val threadId: String,
        val messageId: String,
        val emoji: String,
        val remove: Boolean,
    )

    data class Read(
        val threadId: String,
        val messageId: String,
        val longThreadId: String,
        val timestampMs: Long,
    )

    val reacts = mutableListOf<React>()
    val reads = mutableListOf<Read>()

    /** Thread detail returned by [loadThread], keyed by thread ID. */
    val threads = mutableMapOf<String, JSONObject>()
    val loadedThreads = mutableListOf<String>()
    val failingThreads = mutableSetOf<String>()

    override suspend fun hasCookies(): Boolean = cookies

    override suspend fun signOut() {
        cookies = false
    }

    override suspend fun signIn(cookiesJson: String): JSONObject = unsupported()

    override suspend fun loadMailbox(): JSONObject = mailboxError?.let { throw it } ?: unsupported()

    override suspend fun loadOlderConversations(cursor: String): JSONObject = unsupported()

    override suspend fun loadThread(threadId: String): JSONObject {
        loadedThreads += threadId
        if (threadId in failingThreads) error("thread fetch failed")
        return threads[threadId] ?: unsupported()
    }

    override suspend fun searchUsers(query: String): JSONObject = unsupported()

    override suspend fun sendNewDm(
        recipientFbid: String,
        text: String,
        otid: String,
    ): JSONObject = unsupported()

    override suspend fun loadOlder(threadId: String, cursor: String): JSONObject = unsupported()

    override suspend fun sendText(
        threadId: String,
        text: String,
        replyTo: String,
        otid: String,
        mentionsJson: String,
    ): JSONObject = unsupported()

    override suspend fun sendMedia(
        threadId: String,
        filePath: String,
        fileName: String,
        mimeType: String,
        otid: String,
        voice: Boolean,
        waveformJson: String,
    ): JSONObject = unsupported()

    override suspend fun markRead(
        threadId: String,
        messageId: String,
        longThreadId: String,
        timestampMs: Long,
    ): JSONObject {
        reads += Read(threadId, messageId, longThreadId, timestampMs)
        return JSONObject()
    }

    override suspend fun react(
        threadId: String,
        messageId: String,
        emoji: String,
        remove: Boolean,
    ): JSONObject {
        reacts += React(threadId, messageId, emoji, remove)
        return JSONObject()
    }

    override suspend fun edit(threadId: String, messageId: String, text: String): JSONObject =
        unsupported()

    override suspend fun unsend(threadId: String, messageId: String): JSONObject = unsupported()

    override suspend fun pinMessage(
        threadId: String,
        messageId: String,
        replaceOldest: Boolean,
    ): JSONObject = JSONObject()

    override suspend fun unpinMessage(threadId: String, messageId: String): JSONObject = JSONObject()

    override suspend fun downloadMedia(
        threadId: String,
        messageId: String,
        media: MessageMedia,
    ): File = unsupported()

    override suspend fun clearMediaCache() = Unit

    private fun unsupported(): Nothing = error("FakeProtocol has no session")
}

/** Leaves queued sends untouched, so a test can assert the outbox survives. */
internal object NeverSends : OutboxSender {
    override suspend fun sendText(
        threadId: String,
        text: String,
        replyTo: String,
        otid: String,
        mentionsJson: String,
    ): JSONObject = error("no network in this test")

    override suspend fun sendMedia(
        threadId: String,
        filePath: String,
        fileName: String,
        mimeType: String,
        otid: String,
        voice: Boolean,
        waveformJson: String,
    ): JSONObject = error("no network in this test")
}
