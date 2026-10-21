package com.goldrift.instadm.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Send progress for outgoing messages. */
object MessageStatus {
    const val SENT = "sent"
    const val PENDING = "pending"
    const val SENDING = "sending"
    const val FAILED = "failed"
    /** Tombstone: the message was unsent or deleted and never comes back. */
    const val DELETED = "deleted"
}

/**
 * A message in a thread. [id] is Instagram's message ID once known, or a
 * `local:` ID while an outgoing message is pending. [localId] stays set after
 * the remote ID arrives so delayed echoes and retries can be matched.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["thread_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["thread_id", "timestamp_ms", "id"]),
        Index(value = ["local_id"], unique = true),
        Index("otid"),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "local_id") val localId: String? = null,
    @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "sender_fbid") val senderFbid: Long = 0,
    @ColumnInfo(name = "sender_name") val senderName: String = "",
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long = 0,
    val kind: String = "unknown",
    val text: String = "",
    @ColumnInfo(name = "reply_to_id") val replyToId: String = "",
    val otid: String = "",
    @ColumnInfo(name = "is_from_viewer") val isFromViewer: Boolean = false,
    val status: String = MessageStatus.SENT,
    val seen: Boolean = false,
    /** Whether the message has media, so the preview can show a placeholder. */
    @ColumnInfo(name = "media_count") val mediaCount: Int = 0,
    @ColumnInfo(name = "edited_at") val editedAt: Long = 0,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long = 0,
)
