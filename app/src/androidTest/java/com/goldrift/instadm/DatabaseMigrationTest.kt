package com.goldrift.instadm

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.data.db.InstaDmDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @Test
    fun addsWaveformColumnToAttachments() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(V1_ATTACHMENTS)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            InstaDmDatabase.MIGRATION_1_2.migrate(db)
            db.query("SELECT waveform FROM attachments LIMIT 0").use { cursor ->
                assertEquals("waveform column is missing", 1, cursor.columnCount)
            }
        } finally {
            helper.close()
            context.deleteDatabase(TEST_DB)
        }
    }

    @Test
    fun addsReplySnapshotColumnsToMessages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(V2_MESSAGES)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO messages (" +
                    "id, thread_id, sender_fbid, sender_name, timestamp_ms, kind, text, " +
                    "reply_to_id, otid, is_from_viewer, status, seen, media_count, " +
                    "edited_at, deleted_at" +
                    ") VALUES ('m1', 't1', 0, '', 0, 'text', 'hi', '', '', 0, 'sent', 0, 0, 0, 0)",
            )
            InstaDmDatabase.MIGRATION_2_3.migrate(db)
            db.query(
                "SELECT reply_to_text, reply_to_sender, reply_to_is_from_viewer, " +
                    "reply_to_kind, reply_to_media_count FROM messages",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals("", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
                assertEquals("", cursor.getString(3))
                assertEquals(0, cursor.getInt(4))
            }
        } finally {
            helper.close()
            context.deleteDatabase(TEST_DB)
        }
    }

    @Test
    fun addsViewOnceColumnsToAttachments() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(TEST_DB)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(TEST_DB)
                .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(V3_ATTACHMENTS)
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build(),
        )
        try {
            val db = helper.writableDatabase
            db.execSQL(
                "INSERT INTO attachments (" +
                    "message_id, part_index, kind, remote_fbid, url, preview_url, width, " +
                    "height, duration_ms, mime_type, local_path, waveform, download_state" +
                    ") VALUES ('m1', 0, 'image', '', 'https://cdn.example.invalid/image.jpg', " +
                    "'', 800, 600, 0, '', '', NULL, 'none')",
            )
            InstaDmDatabase.MIGRATION_3_4.migrate(db)
            db.query(
                "SELECT view_once, view_mode, url FROM attachments",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
                assertEquals("", cursor.getString(1))
                assertEquals("https://cdn.example.invalid/image.jpg", cursor.getString(2))
            }
        } finally {
            helper.close()
            context.deleteDatabase(TEST_DB)
        }
    }

    private companion object {
        const val TEST_DB = "instadm-migration-test"
        const val V1_ATTACHMENTS =
            "CREATE TABLE IF NOT EXISTS `attachments` (" +
                "`message_id` TEXT NOT NULL, `part_index` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, `remote_fbid` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                "`preview_url` TEXT NOT NULL, `width` INTEGER NOT NULL, " +
                "`height` INTEGER NOT NULL, `duration_ms` INTEGER NOT NULL, " +
                "`mime_type` TEXT NOT NULL, `local_path` TEXT NOT NULL, " +
                "`download_state` TEXT NOT NULL, PRIMARY KEY(`message_id`, `part_index`), " +
                "FOREIGN KEY(`message_id`) REFERENCES `messages`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        const val V3_ATTACHMENTS =
            "CREATE TABLE IF NOT EXISTS `attachments` (" +
                "`message_id` TEXT NOT NULL, `part_index` INTEGER NOT NULL, " +
                "`kind` TEXT NOT NULL, `remote_fbid` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                "`preview_url` TEXT NOT NULL, `width` INTEGER NOT NULL, " +
                "`height` INTEGER NOT NULL, `duration_ms` INTEGER NOT NULL, " +
                "`mime_type` TEXT NOT NULL, `local_path` TEXT NOT NULL, " +
                "`waveform` TEXT, `download_state` TEXT NOT NULL, " +
                "PRIMARY KEY(`message_id`, `part_index`), " +
                "FOREIGN KEY(`message_id`) REFERENCES `messages`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        const val V2_MESSAGES =
            "CREATE TABLE IF NOT EXISTS `messages` (" +
                "`id` TEXT NOT NULL, `local_id` TEXT, `thread_id` TEXT NOT NULL, " +
                "`sender_fbid` INTEGER NOT NULL, `sender_name` TEXT NOT NULL, " +
                "`timestamp_ms` INTEGER NOT NULL, `kind` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                "`reply_to_id` TEXT NOT NULL, `otid` TEXT NOT NULL, " +
                "`is_from_viewer` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                "`seen` INTEGER NOT NULL, `media_count` INTEGER NOT NULL, " +
                "`edited_at` INTEGER NOT NULL, `deleted_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`thread_id`) REFERENCES `threads`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
    }
}
