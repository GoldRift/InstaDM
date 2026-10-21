package com.goldrift.instadm.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
    version = 2,
    exportSchema = true,
)
abstract class InstaDmDatabase : RoomDatabase() {
    abstract fun appMeta(): AppMetaDao
    abstract fun threads(): ThreadDao
    abstract fun messages(): MessageDao
    abstract fun outbox(): OutboxDao

    companion object {
        const val NAME = "instadm.db"

        /** v2 adds voice-note waveforms to attachments. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attachments ADD COLUMN waveform TEXT")
            }
        }

        fun build(context: Context): InstaDmDatabase =
            Room.databaseBuilder(context, InstaDmDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
