package com.goldrift.instadm.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** One emoji reaction per sender per message, matching Instagram's model. */
@Entity(
    tableName = "reactions",
    primaryKeys = ["message_id", "sender_fbid"],
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
data class ReactionEntity(
    @ColumnInfo(name = "message_id") val messageId: String,
    @ColumnInfo(name = "sender_fbid") val senderFbid: Long,
    val emoji: String,
    @ColumnInfo(name = "is_from_viewer") val isFromViewer: Boolean = false,
    @ColumnInfo(name = "timestamp_ms") val timestampMs: Long = 0,
)
