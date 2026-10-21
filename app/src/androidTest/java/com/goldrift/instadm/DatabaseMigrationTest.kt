package com.goldrift.instadm

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.goldrift.instadm.data.db.InstaDmDatabase
import org.junit.Assert.assertEquals
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
    }
}
