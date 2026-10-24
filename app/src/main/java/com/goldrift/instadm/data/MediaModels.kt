package com.goldrift.instadm.data

import org.json.JSONArray

/**
 * One inbound attachment from a normalized Go message. Download URLs are
 * signed and expire, so they are only used for inline thumbnails; opening a
 * full-size image or video goes through the Go downloader for a fresh URL.
 */
data class MessageMedia(
    val type: String,
    val url: String,
    val fallbackUrl: String = "",
    val previewUrl: String = "",
    val previewFallbackUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Int = 0,
    val partIndex: Int = 0,
    /** Local file for a downloaded or just-sent attachment, when known. */
    val localPath: String = "",
    /** Voice-note amplitudes (0..1) for the waveform. */
    val waveform: List<Float> = emptyList(),
    /**
     * Instagram view-once media. The Go core deliberately exposes no URL for
     * it, because the one allowed view belongs to the official app.
     */
    val viewOnce: Boolean = false,
    /** Raven replay policy for [viewOnce] media: `viewed` or `replayed`. */
    val viewMode: String = "",
    /**
     * Instagram media ID for a native reel share. The DM payload only has a
     * preview, so the Go core resolves the playable video from this ID.
     */
    val targetId: String = "",
    /** Reel shortcode from the share URL, used only as a media-info referer. */
    val shortcode: String = "",
) {
    val isAudio: Boolean
        get() = type == "audio"

    /** Playable voice note: audio with a local file or a downloadable URL. */
    val isVoiceNote: Boolean
        get() = isAudio && (localPath.isNotBlank() || fullUrl.isNotBlank())

    /** A native reel share with a preview and a resolvable media ID. */
    val isReel: Boolean
        get() = type == "reel"

    /** A post, story, or external XMA share rendered as a link card. */
    val isLink: Boolean
        get() = type == "link"

    val isVideo: Boolean
        get() = when (type) {
            "video", "gif", "reel" -> true
            // A link's URL is a web page, not a playable media file.
            "link" -> false
            else -> type != "audio" && fullUrl.isVideoUrl()
        }

    /** Attachments the chat bubble can render inline. */
    val isVisual: Boolean
        get() = !viewOnce && (isVideo || type in VISUAL_TYPES)

    val thumbnailUrl: String
        get() = previewUrl.ifBlank {
            previewFallbackUrl.ifBlank {
                // Video URLs cannot be decoded as images, and a link's URL is
                // an HTML page; show the play/link card instead of asking Coil
                // to load either one as an image.
                if (isVideo || isLink) "" else url.ifBlank { fallbackUrl }
            }
        }

    val fullUrl: String
        get() = url.ifBlank {
            fallbackUrl.ifBlank { previewUrl.ifBlank { previewFallbackUrl } }
        }

    val aspectRatio: Float
        get() {
            if (isReel && (width <= 0 || height <= 0)) return REEL_ASPECT_RATIO
            if (width <= 0 || height <= 0) return DEFAULT_ASPECT_RATIO
            return (width.toFloat() / height.toFloat())
                .coerceIn(MIN_ASPECT_RATIO, MAX_ASPECT_RATIO)
        }

    private fun String.isVideoUrl(): Boolean {
        val path = substringBefore('?').lowercase()
        return path.endsWith(".mp4") || path.endsWith(".m4v")
    }

    private companion object {
        val VISUAL_TYPES = setOf("image", "gif", "sticker", "media")
        const val DEFAULT_ASPECT_RATIO = 1f
        const val REEL_ASPECT_RATIO = 9f / 16f
        const val MIN_ASPECT_RATIO = 0.6f
        const val MAX_ASPECT_RATIO = 1.8f
    }
}

/** Parses the `media` array from a normalized Go message or realtime event. */
fun parseMedia(json: JSONArray?): List<MessageMedia> {
    if (json == null || json.length() == 0) return emptyList()
    val result = ArrayList<MessageMedia>(json.length())
    for (index in 0 until json.length()) {
        val item = json.optJSONObject(index) ?: continue
        val type = item.optString("type")
        val url = item.optString("url")
        val fallbackUrl = item.optString("fallback_url")
        val previewUrl = item.optString("preview_url")
        val previewFallbackUrl = item.optString("preview_fallback_url")
        val viewOnce = item.optBoolean("view_once")
        // View-once media has no URL on purpose; keep it so the bubble can
        // point at the official app instead of rendering an empty attachment.
        // A reel can also arrive without a preview; keep it so the play card
        // and the media-ID download still work.
        if (type != "reel" && !viewOnce && url.isBlank() && fallbackUrl.isBlank() &&
            previewUrl.isBlank() && previewFallbackUrl.isBlank()
        ) {
            continue
        }
        result += MessageMedia(
            type = type,
            url = url,
            fallbackUrl = fallbackUrl,
            previewUrl = previewUrl,
            previewFallbackUrl = previewFallbackUrl,
            width = item.optInt("width"),
            height = item.optInt("height"),
            durationMs = item.optInt("duration_ms"),
            partIndex = index,
            waveform = parseWaveform(item.optJSONArray("waveform")),
            viewOnce = viewOnce,
            viewMode = item.optString("view_mode"),
            targetId = item.optString("target_id"),
            shortcode = item.optString("shortcode"),
        )
    }
    return result
}

/** Parses a waveform array into amplitudes in the 0..1 range. */
fun parseWaveform(json: JSONArray?): List<Float> {
    if (json == null || json.length() == 0) return emptyList()
    val result = ArrayList<Float>(json.length())
    for (index in 0 until json.length()) {
        val value = json.optDouble(index, 0.0)
        if (value.isNaN()) {
            result += 0f
        } else {
            result += value.coerceIn(0.0, 1.0).toFloat()
        }
    }
    return result
}

/** Parses a persisted waveform column. Invalid JSON yields no waveform. */
fun parseWaveform(raw: String?): List<Float> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching { parseWaveform(JSONArray(raw)) }.getOrDefault(emptyList())
}

/** Serializes amplitudes for Room and the Go waveform field. */
fun waveformJson(waveform: List<Float>): String {
    if (waveform.isEmpty()) return ""
    return waveform.joinToString(prefix = "[", postfix = "]", separator = ",") { value ->
        value.coerceIn(0f, 1f).toString()
    }
}
