package com.goldrift.instadm.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PinnedMessageDao {
    @Query("SELECT * FROM pinned_messages WHERE thread_id = :threadId ORDER BY pinned_order ASC, timestamp_ms DESC")
    fun observeThread(threadId: String): Flow<List<PinnedMessageEntity>>

    @Query("SELECT * FROM pinned_messages WHERE thread_id = :threadId ORDER BY pinned_order ASC, timestamp_ms DESC")
    suspend fun forThread(threadId: String): List<PinnedMessageEntity>

    @Upsert
    suspend fun upsertAll(pins: List<PinnedMessageEntity>)

    @Upsert
    suspend fun upsert(pin: PinnedMessageEntity)

    @Query("DELETE FROM pinned_messages WHERE thread_id = :threadId")
    suspend fun deleteForThread(threadId: String)

    @Query("DELETE FROM pinned_messages WHERE thread_id = :threadId AND message_id = :messageId")
    suspend fun delete(threadId: String, messageId: String)

    @Query("DELETE FROM pinned_messages WHERE message_id = :messageId")
    suspend fun deleteByMessageId(messageId: String)

    @Query("UPDATE pinned_messages SET text = :text WHERE message_id = :messageId")
    suspend fun updateText(messageId: String, text: String)
}
