package com.goldrift.instadm.data

import java.util.Locale

/**
 * Mirrors the upload limits and MIME allow-list enforced by the Go core
 * (`go-core/igcore/media.go`). Keep both sides in sync: the Go side is the
 * authority, and these checks only exist to fail fast with a friendly message.
 */
object MediaPolicy {
    const val MAX_IMAGE_BYTES = 8L * 1024 * 1024
    const val MAX_MEDIA_BYTES = 25L * 1024 * 1024

    private val ALLOWED_MIME_TYPES = setOf(
        "image/jpeg",
        "image/png",
        "image/gif",
        "image/webp",
        "video/mp4",
        "video/quicktime",
        "video/webm",
        "video/ogg",
        "audio/mpeg",
        "audio/mp4",
        "audio/wav",
        "audio/x-wav",
    )

    private val MIME_ALIASES = mapOf(
        "image/jpg" to "image/jpeg",
        "image/pjpeg" to "image/jpeg",
        "video/x-m4v" to "video/mp4",
        "audio/mp3" to "audio/mpeg",
        "audio/m4a" to "audio/mp4",
        "audio/x-m4a" to "audio/mp4",
        "audio/wave" to "audio/wav",
    )

    private val EXTENSION_MIME_TYPES = mapOf(
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "png" to "image/png",
        "gif" to "image/gif",
        "webp" to "image/webp",
        "mp4" to "video/mp4",
        "m4v" to "video/mp4",
        "mov" to "video/quicktime",
        "webm" to "video/webm",
        "ogv" to "video/ogg",
        "mp3" to "audio/mpeg",
        "m4a" to "audio/mp4",
        "aac" to "audio/mp4",
        "wav" to "audio/wav",
    )

    /**
     * Returns a normalized MIME type. Providers often report aliases such as
     * `image/jpg` or nothing at all, so fall back to the file extension.
     * `application/octet-stream` is kept because the Go core sniffs the bytes.
     */
    fun normalizeMimeType(raw: String?, fileName: String): String {
        var mimeType = raw
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.US)
            .orEmpty()
        mimeType = MIME_ALIASES[mimeType] ?: mimeType

        if (mimeType.isBlank() || mimeType == "application/octet-stream") {
            val extension = fileName.substringAfterLast('.', "").lowercase(Locale.US)
            mimeType = EXTENSION_MIME_TYPES[extension]
                ?: mimeType.ifBlank { "application/octet-stream" }
        }
        return mimeType
    }

    /** Documents are allowed because the Go core accepts any application MIME type. */
    fun isAllowed(mimeType: String): Boolean =
        mimeType in ALLOWED_MIME_TYPES || mimeType.startsWith("application/")

    /** The normalized message kind used by placeholders and preview cards. */
    fun kind(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "image"
        mimeType.startsWith("video/") -> "video"
        mimeType.startsWith("audio/") -> "audio"
        else -> "media"
    }

    fun maxBytes(mimeType: String): Long =
        if (mimeType.startsWith("image/")) MAX_IMAGE_BYTES else MAX_MEDIA_BYTES
}

/** Compact binary size label used in upload errors. */
fun formatMediaBytes(bytes: Long): String {
    val megabytes = bytes.toDouble() / (1024.0 * 1024.0)
    return if (megabytes >= 10.0) {
        String.format(Locale.US, "%.0f MB", megabytes)
    } else {
        String.format(Locale.US, "%.1f MB", megabytes)
    }
}
