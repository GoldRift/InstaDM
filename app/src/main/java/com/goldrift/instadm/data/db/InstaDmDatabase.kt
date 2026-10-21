package com.goldrift.instadm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local cache of the inbox and open threads. The database is the UI's source
 * of truth; the Go core stays the owner of the session and protocol cursor.
 *
 * The file lives in the standard `databases/` directory and is excluded from
 * backup in `backup_rules.xml` and `data_extraction_rules.xml`.
 */
@Database(
    entities = [
        AppMetaEntity::class,
        ThreadEntity::class,
        ParticipantEntity::class,
        MessageEntity::class,
        AttachmentEntity::class,
        ReactionEntity::class,
        OutboxEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class InstaDmDatabase : RoomDatabase() {
    abstract fun appMeta(): AppMetaDao
    abstract fun threads(): ThreadDao
    abstract fun messages(): MessageDao
    abstract fun outbox(): OutboxDao

    companion object {
        const val NAME = "instadm.db"

        fun build(context: Context): InstaDmDatabase =
            Room.databaseBuilder(context, InstaDmDatabase::class.java, NAME)
                .build()
    }
}
