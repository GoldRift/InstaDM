package com.goldrift.instadm.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object OutboxState {
    const val PENDING = "pending"
    const val SENDING = "sending"
    const val SENT = "sent"
    const val FAILED = "failed"
}

/**
 * Durable queue for outgoing work. A send survives process death and retries
 * with the same OTID, so Instagram can deduplicate it.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["state", "created_at"]),
        Index(value = ["otid"], unique = true),
    ],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "thread_id") val threadId: String,
    /** text, media, reaction, edit, unsend, read */
    val kind: String,
    /** Kind-specific JSON payload, including absolute media paths. */
    @ColumnInfo(name = "payload_json") val payloadJson: String = "{}",
    @ColumnInfo(name = "local_message_id") val localMessageId: String? = null,
    val otid: String,
    val state: String = OutboxState.PENDING,
    val attempts: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
)
