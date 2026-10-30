package com.goldrift.instadm.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A thread row plus its members, used by the inbox and the chat header. */
data class ThreadWithParticipants(
    @Embedded val thread: ThreadEntity,
    @Relation(parentColumn = "id", entityColumn = "thread_id")
    val participants: List<ParticipantEntity>,
)

/**
 * Incoming messages newer than the viewer's read watermark, per thread. Threads
 * with nothing unread are absent from the result.
 */
data class ThreadUnreadCount(
    @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "unread_count") val unreadCount: Int,
)

@Dao
interface ThreadDao {
    @Transaction
    @Query("SELECT * FROM threads ORDER BY last_activity_ms DESC")
    fun observeInbox(): Flow<List<ThreadWithParticipants>>

    /**
     * Counts incoming messages above each thread's read watermark. The
     * watermark only advances once the viewer's receipt is accepted, so the
     * badge clears when the open thread is actually marked read rather than
     * when it is merely tapped.
     */
    @Query(
        """
        SELECT m.thread_id AS thread_id, COUNT(*) AS unread_count
        FROM messages AS m
        INNER JOIN threads AS t ON t.id = m.thread_id
        WHERE m.is_from_viewer = 0
          AND m.status != 'deleted'
          AND m.timestamp_ms > t.read_watermark_ms
        GROUP BY m.thread_id
        """,
    )
    fun observeUnreadCounts(): Flow<List<ThreadUnreadCount>>

    @Query(
        """
        SELECT COUNT(*)
        FROM messages AS m
        INNER JOIN threads AS t ON t.id = m.thread_id
        WHERE m.thread_id = :threadId
          AND m.is_from_viewer = 0
          AND m.status != 'deleted'
          AND m.timestamp_ms > t.read_watermark_ms
        """,
    )
    suspend fun unreadCount(threadId: String): Int

    @Transaction
    @Query("SELECT * FROM threads WHERE id = :threadId")
    fun observeThread(threadId: String): Flow<ThreadWithParticipants?>

    @Transaction
    @Query("SELECT * FROM threads WHERE id = :threadId")
    suspend fun threadWithParticipants(threadId: String): ThreadWithParticipants?

    @Query("SELECT * FROM threads WHERE id = :threadId")
    suspend fun thread(threadId: String): ThreadEntity?

    @Query("SELECT * FROM threads WHERE id IN (:threadIds)")
    suspend fun threads(threadIds: List<String>): List<ThreadEntity>

    @Upsert
    suspend fun upsert(thread: ThreadEntity)

    @Upsert
    suspend fun upsertAll(threads: List<ThreadEntity>)

    @Query("DELETE FROM participants WHERE thread_id = :threadId")
    suspend fun deleteParticipants(threadId: String)

    @Upsert
    suspend fun upsertParticipants(participants: List<ParticipantEntity>)

    /**
     * Updates the inbox preview from a newly loaded message. The timestamp
     * guard keeps a late older page from overwriting a newer realtime message.
     */
    @Query(
        """
        UPDATE threads SET
            last_message_id = :messageId,
            last_message_ts = :timestampMs,
            last_message_text = :text,
            last_message_kind = :kind,
            last_message_from_viewer = :fromViewer,
            last_message_sender_fbid = :senderFbid,
            last_activity_ms = MAX(last_activity_ms, :timestampMs),
            updated_at = :now
        WHERE id = :threadId AND last_message_ts <= :timestampMs
        """,
    )
    suspend fun updateLastMessage(
        threadId: String,
        messageId: String,
        timestampMs: Long,
        text: String,
        kind: String,
        fromViewer: Boolean,
        senderFbid: Long,
        now: Long,
    )

    /** Unconditional preview update after an edit or tombstone. */
    @Query(
        """
        UPDATE threads SET
            last_message_id = :messageId,
            last_message_ts = :timestampMs,
            last_message_text = :text,
            last_message_kind = :kind,
            last_message_from_viewer = :fromViewer,
            last_message_sender_fbid = :senderFbid,
            updated_at = :now
        WHERE id = :threadId
        """,
    )
    suspend fun setLastMessage(
        threadId: String,
        messageId: String,
        timestampMs: Long,
        text: String,
        kind: String,
        fromViewer: Boolean,
        senderFbid: Long,
        now: Long,
    )

    @Query(
        "UPDATE threads SET last_message_id = '', last_message_ts = 0, " +
            "last_message_text = '', last_message_kind = '', " +
            "last_message_from_viewer = 0, last_message_sender_fbid = 0, " +
            "updated_at = :now WHERE id = :threadId",
    )
    suspend fun clearLastMessage(threadId: String, now: Long)

    @Query(
        """
        UPDATE threads SET
            oldest_cursor = :cursor,
            has_more_older = :hasMore,
            messages_synced_at = :syncedAt,
            updated_at = :now
        WHERE id = :threadId
        """,
    )
    suspend fun updateNewestPage(
        threadId: String,
        cursor: String,
        hasMore: Boolean,
        syncedAt: Long,
        now: Long,
    )

    @Query(
        """
        UPDATE threads SET
            oldest_cursor = :cursor,
            has_more_older = :hasMore,
            updated_at = :now
        WHERE id = :threadId
        """,
    )
    suspend fun updateOlderPage(
        threadId: String,
        cursor: String,
        hasMore: Boolean,
        now: Long,
    )

    @Query(
        "UPDATE threads SET read_watermark_ms = MAX(read_watermark_ms, :watermark), " +
            "marked_unread = 0, updated_at = :now WHERE id = :threadId",
    )
    suspend fun updateReadWatermark(threadId: String, watermark: Long, now: Long)

    @Query("DELETE FROM threads")
    suspend fun clear()
}
