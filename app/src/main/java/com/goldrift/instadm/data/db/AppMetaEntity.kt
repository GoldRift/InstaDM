package com.goldrift.instadm.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Small key-value store for non-secret app metadata: viewer identity, the last
 * inbox sync time, and protocol diagnostics. Cookies never go here.
 */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)
