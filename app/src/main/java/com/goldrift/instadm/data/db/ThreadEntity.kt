package com.goldrift.instadm.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One Instagram DM thread. [id] is the short chat ID used by
 * `IGDThreadDetailQuery` and send/pagination calls.
 *
 * The `last_message_*` columns are a denormalized copy of the newest message so
 * the inbox is a single indexed query. They come from the mailbox stub and are
 * updated whenever a message is written for the thread.
 */
@Entity(
    tableName = "threads",
    indices = [
        Index("last_activity_ms"),
        Index("long_thread_id"),
    ],
)
data class ThreadEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "long_thread_id") val longThreadId: String = "",
    @ColumnInfo(name = "thread_fbid") val threadFbid: String = "",
    @ColumnInfo(name = "thread_key") val threadKey: Long = 0,
    @ColumnInfo(name = "is_group") val isGroup: Boolean = false,
    val subtype: String = "",
    val title: String = "",
    @ColumnInfo(name = "avatar_url") val avatarUrl: String = "",
    val folder: String = "",
    @ColumnInfo(name = "system_folder") val systemFolder: String = "",
    @ColumnInfo(name = "last_activity_ms") val lastActivityMs: Long = 0,
    @ColumnInfo(name = "is_muted") val isMuted: Boolean = false,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean = false,
    @ColumnInfo(name = "marked_unread") val markedUnread: Boolean = false,
    /** Local read watermark for the viewer, from `slide_read_receipts`. */
    @ColumnInfo(name = "read_watermark_ms") val readWatermarkMs: Long = 0,
    /** Cursor for the next older page; only meaningful once messages are loaded. */
    @ColumnInfo(name = "oldest_cursor") val oldestCursor: String = "",
    @ColumnInfo(name = "has_more_older") val hasMoreOlder: Boolean = false,
    /** When the newest message page was fetched; 0 means never. */
    @ColumnInfo(name = "messages_synced_at") val messagesSyncedAt: Long = 0,
    @ColumnInfo(name = "last_message_id") val lastMessageId: String = "",
    @ColumnInfo(name = "last_message_ts") val lastMessageTs: Long = 0,
    @ColumnInfo(name = "last_message_text") val lastMessageText: String = "",
    @ColumnInfo(name = "last_message_kind") val lastMessageKind: String = "",
    @ColumnInfo(name = "last_message_from_viewer") val lastMessageFromViewer: Boolean = false,
    @ColumnInfo(name = "last_message_sender_fbid") val lastMessageSenderFbid: Long = 0,
    @ColumnInfo(name = "updated_at") val updatedAt: Long = 0,
)
