package com.goldrift.instadm.data

import org.json.JSONObject

/** Normalized realtime event delivered by the Go watch loop. */
data class RealtimeEvent(
    val type: String,
    val threadId: String = "",
    val message: RealtimeMessage? = null,
    val timestampMs: Long = 0,
    val error: String = "",
    val seqId: Long = 0,
    val rawType: String = "",
    val participantFbid: Long = 0,
    val fromViewer: Boolean = false,
) {
    companion object {
        fun fromJson(raw: String): RealtimeEvent? = runCatching {
            val json = JSONObject(raw)
            val type = json.optString("type")
            if (type.isBlank()) return@runCatching null

            val message = json.optJSONObject("message")?.let { payload ->
                RealtimeMessage(
                    id = payload.optString("id"),
                    text = payload.optString("text"),
                    senderName = payload.optString("sender_name"),
                    isFromViewer = payload.optBoolean("is_from_viewer"),
                    timestampMs = payload.optLong("timestamp_ms"),
                    kind = payload.optString("kind"),
                    media = parseMedia(payload.optJSONArray("media")),
                )
            }

            RealtimeEvent(
                type = type,
                threadId = json.optString("thread_id"),
                message = message,
                timestampMs = json.optLong("timestamp_ms"),
                error = json.optString("error"),
                seqId = json.optLong("seq_id"),
                rawType = json.optString("raw_type"),
                participantFbid = json.optLong("participant_fbid"),
                fromViewer = json.optBoolean("from_viewer"),
            )
        }.getOrNull()
    }
}

data class RealtimeMessage(
    val id: String,
    val text: String,
    val senderName: String,
    val isFromViewer: Boolean,
    val timestampMs: Long,
    val kind: String,
    val media: List<MessageMedia> = emptyList(),
)

enum class RealtimeStatus { Stopped, Connecting, Connected, Reconnecting, NoSession, Error }

fun messagePreview(text: String, kind: String): String =
    text.ifBlank { messagePlaceholder(kind) }

fun messagePlaceholder(kind: String): String = when (kind) {
    "image" -> "[image]"
    "video" -> "[video]"
    "audio" -> "[audio]"
    "gif" -> "[gif]"
    "sticker" -> "[sticker]"
    "media" -> "[media]"
    "link" -> "[link]"
    else -> if (kind.isBlank()) "" else "[$kind]"
}
