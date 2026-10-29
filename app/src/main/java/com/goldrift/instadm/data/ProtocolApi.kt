package com.goldrift.instadm.data

import org.json.JSONObject
import java.io.File

/**
 * The protocol calls the UI and send queue need. [ProtocolRepository] talks to
 * the Go core; the demo build swaps in a local implementation.
 */
interface ProtocolApi {
    suspend fun hasCookies(): Boolean

    suspend fun signIn(cookiesJson: String): JSONObject

    suspend fun loadMailbox(): JSONObject

    suspend fun loadThread(threadId: String): JSONObject

    /** Fetches the page of messages older than [cursor] (an `oldest_cursor` value). */
    suspend fun loadOlder(threadId: String, cursor: String): JSONObject

    suspend fun sendText(
        threadId: String,
        text: String,
        replyTo: String = "",
        otid: String = "",
    ): JSONObject

    suspend fun sendMedia(
        threadId: String,
        fileName: String,
        mimeType: String,
        data: ByteArray,
        otid: String,
        voice: Boolean = false,
        waveformJson: String = "",
    ): JSONObject

    suspend fun markRead(
        threadId: String,
        messageId: String = "",
        longThreadId: String = "",
        timestampMs: Long = 0,
    ): JSONObject

    suspend fun react(
        threadId: String,
        messageId: String,
        emoji: String,
        remove: Boolean,
    ): JSONObject

    suspend fun edit(threadId: String, messageId: String, text: String): JSONObject

    suspend fun unsend(threadId: String, messageId: String): JSONObject

    suspend fun downloadMedia(
        threadId: String,
        messageId: String,
        media: MessageMedia,
    ): File

    suspend fun clearMediaCache()
}
