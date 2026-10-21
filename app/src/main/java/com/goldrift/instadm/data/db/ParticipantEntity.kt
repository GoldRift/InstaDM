package com.goldrift.instadm.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** A member of a thread, used for group avatars and sender name fallbacks. */
@Entity(
    tableName = "participants",
    primaryKeys = ["thread_id", "ig_user_id"],
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
data class ParticipantEntity(
    @ColumnInfo(name = "thread_id") val threadId: String,
    @ColumnInfo(name = "ig_user_id") val igUserId: String,
    val fbid: Long = 0,
    val username: String = "",
    val name: String = "",
    @ColumnInfo(name = "avatar_url") val avatarUrl: String = "",
    @ColumnInfo(name = "is_viewer") val isViewer: Boolean = false,
)
