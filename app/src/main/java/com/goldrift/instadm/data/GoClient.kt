package com.goldrift.instadm.data

import com.goldrift.instadm.core.mobile.Client
import com.goldrift.instadm.core.mobile.Mobile
import org.json.JSONObject
import java.io.Closeable

/**
 * Thin JSON wrapper around the gomobile client. The Go side owns the session,
 * cookies, and socket; this class never logs cookie content.
 */
class GoClient(cookiesJson: String) : Closeable {
    private val client: Client = requireNotNull(Mobile.new_(cookiesJson)) {
        "Go client returned null"
    }

    fun version(): String = Mobile.version()

    fun mailbox(timeoutSeconds: Int = 90): JSONObject =
        JSONObject(client.mailboxJSON(timeoutSeconds))

    fun thread(
        threadId: String,
        limit: Int = 20,
        afterCursor: String = "",
        timeoutSeconds: Int = 90,
    ): JSONObject = JSONObject(
        client.threadJSON(threadId, limit, afterCursor, timeoutSeconds),
    )

    fun sendText(
        threadId: String,
        text: String,
        replyTo: String = "",
        otid: String = "",
        timeoutSeconds: Int = 90,
    ): JSONObject = JSONObject(
        client.sendTextJSON(threadId, text, replyTo, otid, timeoutSeconds),
    )

    fun sendMedia(
        threadId: String,
        filename: String,
        mimeType: String,
        data: ByteArray,
        replyTo: String = "",
        otid: String = "",
        timeoutSeconds: Int = 180,
    ): JSONObject = JSONObject(
        client.sendMediaJSON(threadId, filename, mimeType, data, replyTo, otid, timeoutSeconds),
    )

    fun markRead(
        threadId: String,
        messageId: String = "",
        timeoutSeconds: Int = 90,
    ): JSONObject = JSONObject(client.markReadJSON(threadId, messageId, timeoutSeconds))

    fun react(
        threadId: String,
        messageId: String,
        emoji: String,
        remove: Boolean = false,
        timeoutSeconds: Int = 90,
    ): JSONObject = JSONObject(
        client.reactJSON(threadId, messageId, emoji, remove, timeoutSeconds),
    )

    fun edit(
        threadId: String,
        messageId: String,
        text: String,
        otid: String = "",
        timeoutSeconds: Int = 90,
    ): JSONObject = JSONObject(client.editJSON(threadId, messageId, text, otid, timeoutSeconds))

    fun unsend(
        threadId: String,
        messageId: String,
        timeoutSeconds: Int = 90,
    ): JSONObject = JSONObject(client.unsendJSON(threadId, messageId, timeoutSeconds))

    fun mute(
        threadId: String,
        muteSeconds: Int = -1,
        timeoutSeconds: Int = 90,
    ): JSONObject = JSONObject(client.muteJSON(threadId, muteSeconds, timeoutSeconds))

    fun pin(
        threadId: String,
        pin: Boolean = true,
        timeoutSeconds: Int = 90,
    ): JSONObject = JSONObject(client.pinJSON(threadId, pin, timeoutSeconds))

    fun sendNewDM(
        recipientFbid: String,
        text: String,
        otid: String = "",
        timeoutSeconds: Int = 90,
    ): JSONObject = JSONObject(client.sendNewDMJSON(recipientFbid, text, otid, timeoutSeconds))

    fun downloadMedia(
        threadId: String,
        messageId: String,
        part: Int = 0,
        destPath: String,
        timeoutSeconds: Int = 180,
    ): JSONObject = JSONObject(
        client.downloadMediaJSON(threadId, messageId, part, destPath, timeoutSeconds),
    )

    override fun close() {
        client.close()
    }
}
