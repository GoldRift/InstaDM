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
     * watermark advances when the viewer's receipt is accepted, and locally at
     * view time for folders that take no receipt and for hidden activity rows
     * that take no receipt; see
     * [com.goldrift.instadm.data.ChatStore.markActivityReadIfSettled] and
     * [com.goldrift.instadm.data.ChatStore.markThreadReadIncludingActivity].
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

    /**
     * Unread incoming messages above the read watermark, excluding [excludingKind].
     * Activity rows the transcript hides are settled separately from the
     * messages a receipt names, so a pending receipt is never jumped over.
     */
    @Query(
        """
        SELECT COUNT(*)
        FROM messages AS m
        INNER JOIN threads AS t ON t.id = m.thread_id
        WHERE m.thread_id = :threadId
          AND m.is_from_viewer = 0
          AND m.status != 'deleted'
          AND m.kind != :excludingKind
          AND m.timestamp_ms > t.read_watermark_ms
        """,
    )
    suspend fun unreadMessageCount(threadId: String, excludingKind: String): Int

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

    @Query("DELETE FROM participants WHERE thread_id IN (:threadIds)")
    suspend fun deleteParticipantsForThreads(threadIds: List<String>)

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
        "UPDATE threads SET messages_synced_at = :syncedAt, updated_at = :now WHERE id = :threadId",
    )
    suspend fun markSynced(threadId: String, syncedAt: Long, now: Long)

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

    /**
     * Advances the watermark from a fetched viewer receipt. The server's
     * marked-unread flag rides on every fetched view, so a receipt only records
     * the watermark; local reads clear the flag through [updateReadWatermark].
     */
    @Query(
        """
        UPDATE threads SET
            read_watermark_ms = MAX(read_watermark_ms, :watermark),
            updated_at = :now
        WHERE id = :threadId
        """,
    )
    suspend fun applyViewerReceipt(threadId: String, watermark: Long, now: Long)

    /**
     * Threads with cached incoming messages above the read watermark, newest
     * activity first. The reconcile pass refetches them to learn reads made
     * outside this app. The badge is already visible, so these take the slots
     * the cold-start query leaves free.
     */
    @Query(
        """
        SELECT m.thread_id
        FROM messages AS m
        INNER JOIN threads AS t ON t.id = m.thread_id
        WHERE m.is_from_viewer = 0
          AND m.status != 'deleted'
          AND m.timestamp_ms > t.read_watermark_ms
        GROUP BY m.thread_id
        ORDER BY t.last_activity_ms DESC
        LIMIT :limit
        """,
    )
    suspend fun unreadThreadIds(limit: Int): List<String>

    /**
     * Threads whose badge cannot be computed from the cache: the incoming
     * preview is newer than both the read watermark and the newest cached
     * message, so a message this process never fetched is hidden behind it.
     * Newest activity first. These take the reconcile slots first, or a backlog
     * of already-unread threads would starve them. A thread that only carries a
     * marked-unread flag and no incoming preview is absent; the flag itself is
     * the badge.
     */
    @Query(
        """
        SELECT t.id
        FROM threads AS t
        WHERE t.last_message_from_viewer = 0
          AND t.last_message_ts > t.read_watermark_ms
          AND t.last_message_ts > COALESCE(
              (SELECT MAX(m.timestamp_ms) FROM messages AS m
               WHERE m.thread_id = t.id AND m.status != 'deleted'), 0)
        ORDER BY t.last_activity_ms DESC
        LIMIT :limit
        """,
    )
    suspend fun threadIdsWithUnseenPreview(limit: Int): List<String>

    @Query("DELETE FROM threads")
    suspend fun clear()
}
