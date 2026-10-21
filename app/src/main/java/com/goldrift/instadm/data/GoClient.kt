package com.goldrift.instadm.data

import com.goldrift.instadm.core.mobile.Client
import com.goldrift.instadm.core.mobile.Mobile
import com.goldrift.instadm.core.mobile.WatchCallback
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

    /** Returns the cookie jar, including values Instagram rotated in responses. */
    fun exportCookiesJson(): String = client.exportCookiesJSON()

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
        voice: Boolean = false,
        waveformJson: String = "",
    ): JSONObject = JSONObject(
        if (voice) {
            client.sendVoiceJSON(
                threadId,
                filename,
                mimeType,
                data,
                waveformJson,
                replyTo,
                otid,
                timeoutSeconds,
            )
        } else {
            client.sendMediaJSON(threadId, filename, mimeType, data, replyTo, otid, timeoutSeconds)
        },
    )

    /**
     * Marks a thread read up to [messageId]. Passing the conversation's
     * [longThreadId] and the target's [timestampMs] avoids a thread re-query in
     * the Go core.
     */
    fun markRead(
        threadId: String,
        messageId: String = "",
        longThreadId: String = "",
        timestampMs: Long = 0,
        timeoutSeconds: Int = 90,
    ): JSONObject = JSONObject(
        client.markReadJSON(threadId, messageId, longThreadId, timestampMs, timeoutSeconds),
    )

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

    /**
     * Starts the Go watch loop. Events arrive as JSON on a Go-owned thread, so
     * the callback must be fast and must not call back into this client.
     */
    fun startWatch(callback: WatchCallback) {
        client.startWatch(callback)
    }

    fun stopWatch() {
        client.stopWatch()
    }

    override fun close() {
        client.close()
    }
}
