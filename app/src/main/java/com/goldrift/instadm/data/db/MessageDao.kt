package com.goldrift.instadm.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A message with its attachments and reactions for rendering. */
data class MessageWithMedia(
    @Embedded val message: MessageEntity,
    @Relation(parentColumn = "id", entityColumn = "message_id")
    val attachments: List<AttachmentEntity>,
    @Relation(parentColumn = "id", entityColumn = "message_id")
    val reactions: List<ReactionEntity>,
)

@Dao
interface MessageDao {
    @Transaction
    @Query(
        "SELECT * FROM messages WHERE thread_id = :threadId AND status != 'deleted' " +
            "ORDER BY timestamp_ms ASC, id ASC",
    )
    fun observeThread(threadId: String): Flow<List<MessageWithMedia>>

    @Transaction
    @Query(
        "SELECT * FROM messages WHERE thread_id = :threadId AND status != 'deleted' " +
            "ORDER BY timestamp_ms ASC, id ASC LIMIT :limit",
    )
    suspend fun messages(threadId: String, limit: Int): List<MessageWithMedia>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun message(messageId: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE thread_id = :threadId")
    suspend fun count(threadId: String): Int

    @Query("SELECT * FROM messages WHERE local_id = :localId LIMIT 1")
    suspend fun byLocalId(localId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE otid = :otid LIMIT 1")
    suspend fun byOtid(otid: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE thread_id = :threadId AND status != 'deleted' " +
        "ORDER BY timestamp_ms DESC, id DESC LIMIT 1")
    suspend fun newest(threadId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE thread_id = :threadId AND kind = :kind " +
        "AND timestamp_ms = :timestampMs AND status != 'deleted' LIMIT 1")
    suspend fun activityLog(threadId: String, kind: String, timestampMs: Long): MessageEntity?

    @Query("SELECT * FROM attachments WHERE message_id = :messageId")
    suspend fun attachments(messageId: String): List<AttachmentEntity>

    @Upsert
    suspend fun upsertMessage(message: MessageEntity)

    @Upsert
    suspend fun upsertMessages(messages: List<MessageEntity>)

    @Upsert
    suspend fun upsertAttachments(attachments: List<AttachmentEntity>)

    @Upsert
    suspend fun upsertReactions(reactions: List<ReactionEntity>)

    @Query("DELETE FROM attachments WHERE message_id = :messageId")
    suspend fun deleteAttachments(messageId: String)

    @Query(
        "UPDATE attachments SET local_path = :path, download_state = 'ready' " +
            "WHERE message_id = :messageId AND part_index = :partIndex",
    )
    suspend fun markAttachmentReady(messageId: String, partIndex: Int, path: String)

    @Query(
        "UPDATE attachments SET local_path = :path, url = :uri, preview_url = :uri, " +
            "download_state = 'ready' WHERE message_id = :messageId AND part_index = :partIndex",
    )
    suspend fun setAttachmentFile(messageId: String, partIndex: Int, path: String, uri: String)

    @Query("DELETE FROM reactions WHERE message_id = :messageId")
    suspend fun deleteReactions(messageId: String)

    @Query("DELETE FROM reactions WHERE message_id = :messageId AND sender_fbid = :senderFbid")
    suspend fun deleteReaction(messageId: String, senderFbid: Long)

    @Query("DELETE FROM reactions WHERE message_id = :messageId AND is_from_viewer = 1")
    suspend fun deleteViewerReaction(messageId: String)

    @Query(
        "DELETE FROM messages WHERE thread_id = :threadId AND id NOT IN (" +
            "SELECT id FROM messages WHERE thread_id = :threadId " +
            "ORDER BY timestamp_ms DESC, id DESC LIMIT :keep)",
    )
    suspend fun prune(threadId: String, keep: Int)

    @Query("UPDATE messages SET status = 'deleted', deleted_at = :at, text = '' WHERE id = :messageId")
    suspend fun tombstone(messageId: String, at: Long)

    @Query(
        "UPDATE messages SET text = :text, edited_at = :at " +
            "WHERE id = :messageId AND status != 'deleted'",
    )
    suspend fun editText(messageId: String, text: String, at: Long)

    @Query(
        "UPDATE messages SET seen = 1 WHERE thread_id = :threadId AND is_from_viewer = 1 " +
            "AND timestamp_ms <= :watermark AND status != 'deleted'",
    )
    suspend fun markSeen(threadId: String, watermark: Long)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun setStatus(messageId: String, status: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun delete(messageId: String)

    @Query("DELETE FROM messages")
    suspend fun clear()
}
