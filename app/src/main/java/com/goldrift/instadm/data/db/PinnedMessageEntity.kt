package com.goldrift.instadm.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Snapshot of a message pinned in a thread. Storing the compact fields
 * alongside the message ID lets the pins sheet render pins that are older than
 * the currently loaded transcript window.
 */
@Entity(
    tableName = "pinned_messages",
    primaryKeys = ["thread_id", "message_id"],
    foreignKeys = [
        ForeignKey(
            entity = ThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["thread_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("thread_id")],
)
data class PinnedMessageEntity(
    @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "sender_fbid") val senderFbid: Long = 0,
    @ColumnInfo(name = "sender_name") val senderName: String = "",
    @ColumnInfo(name = "is_from_viewer") val isFromViewer: Boolean = false,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long = 0,
    val kind: String = "",
    val text: String = "",
    @ColumnInfo(name = "media_count") val mediaCount: Int = 0,
    @ColumnInfo(name = "pinned_order") val pinnedOrder: Int = 0,
)
