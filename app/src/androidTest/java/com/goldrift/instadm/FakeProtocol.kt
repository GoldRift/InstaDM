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
    override suspend fun hasCookies(): Boolean = cookies

    override suspend fun signOut() {
        cookies = false
    }

    override suspend fun signIn(cookiesJson: String): JSONObject = unsupported()

    override suspend fun loadMailbox(): JSONObject = mailboxError?.let { throw it } ?: unsupported()

    override suspend fun loadOlderConversations(cursor: String): JSONObject = unsupported()

    override suspend fun loadThread(threadId: String): JSONObject = unsupported()

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
    ): JSONObject = unsupported()

    override suspend fun sendMedia(
        threadId: String,
        fileName: String,
        mimeType: String,
        data: ByteArray,
        otid: String,
        voice: Boolean,
        waveformJson: String,
    ): JSONObject = unsupported()

    override suspend fun markRead(
        threadId: String,
        messageId: String,
        longThreadId: String,
        timestampMs: Long,
    ): JSONObject = unsupported()

    override suspend fun react(
        threadId: String,
        messageId: String,
        emoji: String,
        remove: Boolean,
    ): JSONObject = unsupported()

    override suspend fun edit(threadId: String, messageId: String, text: String): JSONObject =
        unsupported()

    override suspend fun unsend(threadId: String, messageId: String): JSONObject = unsupported()

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
    ): JSONObject = error("no network in this test")

    override suspend fun sendMedia(
        threadId: String,
        fileName: String,
        mimeType: String,
        data: ByteArray,
        otid: String,
        voice: Boolean,
        waveformJson: String,
    ): JSONObject = error("no network in this test")
}
