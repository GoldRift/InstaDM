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
