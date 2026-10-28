package com.goldrift.instadm.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One attachment of a message. URLs are signed and expire; a 403 triggers a
 * refresh through the Go core. [localPath] is the downloaded copy, if any.
 */
@Entity(
    tableName = "attachments",
    primaryKeys = ["message_id", "part_index"],
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("message_id")],
)
data class AttachmentEntity(
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "part_index") val partIndex: Int,
    val kind: String,
    @ColumnInfo(name = "remote_fbid") val remoteFbid: String = "",
    val url: String = "",
    @ColumnInfo(name = "preview_url") val previewUrl: String = "",
    val width: Int = 0,
    val height: Int = 0,
    @ColumnInfo(name = "duration_ms") val durationMs: Long = 0,
    @ColumnInfo(name = "mime_type") val mimeType: String = "",
    @ColumnInfo(name = "local_path") val localPath: String = "",
    /** JSON array of amplitudes (0..1) for voice notes, when Instagram sends one. */
    val waveform: String? = null,
    /**
     * Instagram view-once media. [url] stays empty: the one allowed view
     * belongs to the official app, so the bubble only shows a notice.
     */
    @ColumnInfo(name = "view_once") val viewOnce: Boolean = false,
    /** Raven replay policy for [viewOnce] media: `viewed` or `replayed`. */
    @ColumnInfo(name = "view_mode") val viewMode: String = "",
    /**
     * Instagram media ID for a native reel share. Persisting it lets the app
     * download the reel without re-querying a thread that may no longer
     * contain the message.
     */
    @ColumnInfo(name = "target_id") val targetId: String = "",
    /** Reel shortcode from the share URL, used as a media-info referer. */
    val shortcode: String = "",
    /** Public URL of a shared post, story, reel, or page, shown under its preview. */
    @ColumnInfo(name = "target_url") val targetUrl: String = "",
    @ColumnInfo(name = "download_state") val downloadState: String = DOWNLOAD_NONE,
) {
    companion object {
        const val DOWNLOAD_NONE = "none"
        const val DOWNLOAD_QUEUED = "queued"
        const val DOWNLOADING = "downloading"
        const val DOWNLOAD_READY = "ready"
        const val DOWNLOAD_FAILED = "failed"
    }
}
