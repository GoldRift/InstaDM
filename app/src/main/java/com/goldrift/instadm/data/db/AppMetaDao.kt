package com.goldrift.instadm.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AppMetaDao {
    @Query("SELECT value FROM app_meta WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Upsert
    suspend fun put(entry: AppMetaEntity)

    @Upsert
    suspend fun putAll(entries: List<AppMetaEntity>)

    @Query("DELETE FROM app_meta")
    suspend fun clear()
}
