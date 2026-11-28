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
        PinnedMessageEntity::class,
        OutboxEntity::class,
    ],
    version = 11,
    exportSchema = true,
)
abstract class InstaDmDatabase : RoomDatabase() {
    abstract fun appMeta(): AppMetaDao
    abstract fun threads(): ThreadDao
    abstract fun messages(): MessageDao
    abstract fun pinnedMessages(): PinnedMessageDao
    abstract fun outbox(): OutboxDao

    companion object {
        const val NAME = "instadm.db"

        /** v2 adds voice-note waveforms to attachments. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE attachments ADD COLUMN waveform TEXT")
            }
        }

        /** v3 stores the quoted snapshot embedded in a reply. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reply_to_text TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN reply_to_sender TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN reply_to_is_from_viewer " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL("ALTER TABLE messages ADD COLUMN reply_to_kind TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN reply_to_media_count " +
                        "INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** v4 remembers view-once attachments so the bubble can show a notice. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE attachments ADD COLUMN view_once INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE attachments ADD COLUMN view_mode TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        /** v5 stores the media ID for native reel shares. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE attachments ADD COLUMN target_id TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE attachments ADD COLUMN shortcode TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        /** v6 keeps a shared item's public URL so reel cards can show their link. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE attachments ADD COLUMN target_url TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        /**
         * v7 re-arms older-history paging for threads whose cursor a newest-page
         * refresh had reset. The corrected paging advances the cursor instead of
         * treating the next duplicate page as the start of the archive.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE threads SET has_more_older = 1 " +
                        "WHERE oldest_cursor != '' AND has_more_older = 0",
                )
            }
        }

        /**
         * v8 re-arms paging for threads an early-ending page had marked as
         * complete. A page that stored messages no longer ends a thread, so
         * these threads fetch on until a page returns nothing.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE threads SET has_more_older = 1 " +
                        "WHERE oldest_cursor != '' AND has_more_older = 0",
                )
            }
        }

        /** v9 stores a music share's track title and artist line. */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE attachments ADD COLUMN title TEXT NOT NULL DEFAULT ''",
                )
                db.execSQL(
                    "ALTER TABLE attachments ADD COLUMN subtitle TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        /** v10 adds per-thread pinned message snapshots. */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pinned_messages` (" +
                        "`thread_id` TEXT NOT NULL, " +
                        "`message_id` TEXT NOT NULL, " +
                        "`sender_fbid` INTEGER NOT NULL DEFAULT 0, " +
                        "`sender_name` TEXT NOT NULL DEFAULT '', " +
                        "`is_from_viewer` INTEGER NOT NULL DEFAULT 0, " +
                        "`timestamp_ms` INTEGER NOT NULL DEFAULT 0, " +
                        "`kind` TEXT NOT NULL DEFAULT '', " +
                        "`text` TEXT NOT NULL DEFAULT '', " +
                        "`media_count` INTEGER NOT NULL DEFAULT 0, " +
                        "`pinned_order` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`thread_id`, `message_id`), " +
                        "FOREIGN KEY(`thread_id`) REFERENCES `threads`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_pinned_messages_thread_id` " +
                        "ON `pinned_messages` (`thread_id`)",
                )
            }
        }

        /** v11 stores normalized mention spans alongside message text. */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN mentions_json TEXT NOT NULL DEFAULT ''",
                )
            }
        }

        fun build(context: Context): InstaDmDatabase =
            Room.databaseBuilder(context, InstaDmDatabase::class.java, NAME)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                )
                .build()
    }
}
