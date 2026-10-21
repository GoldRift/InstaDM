package com.goldrift.instadm.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(entry: OutboxEntity): Long

    @Query(
        "SELECT * FROM outbox WHERE state IN ('pending', 'failed') " +
            "ORDER BY created_at ASC, id ASC",
    )
    suspend fun retryable(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE state = 'sending'")
    suspend fun inFlight(): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE id = :id")
    suspend fun entry(id: Long): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE thread_id = :threadId AND state IN ('pending', 'sending')")
    suspend fun activeForThread(threadId: String): List<OutboxEntity>

    @Query("SELECT * FROM outbox WHERE state IN ('pending', 'sending') ORDER BY created_at ASC")
    fun observeActive(): Flow<List<OutboxEntity>>

    @Query(
        "UPDATE outbox SET state = :state, attempts = :attempts, last_error = :error, " +
            "updated_at = :now WHERE id = :id",
    )
    suspend fun updateState(
        id: Long,
        state: String,
        attempts: Int,
        error: String,
        now: Long,
    )

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM outbox")
    suspend fun clear()
}
