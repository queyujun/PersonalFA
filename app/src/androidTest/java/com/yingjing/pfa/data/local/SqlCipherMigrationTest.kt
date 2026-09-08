package com.yingjing.pfa.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Native SQLCipher integration; all keys and rows are disposable synthetic test values. */
@RunWith(AndroidJUnit4::class)
class SqlCipherMigrationTest {
    @Test fun encrypted_v12_migrates_and_reopens_with_same_key() {
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "cipher-migration-${UUID.randomUUID()}.db"
        val key = "synthetic-test-key-${UUID.randomUUID()}"
        val schema = InstrumentationRegistry.getInstrumentation().context.assets.open(
            "com.yingjing.pfa.data.local.historical.HistoricalV12Database/12.json",
        ).bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val factory = SupportOpenHelperFactory(key.toByteArray())
        val helper = factory.create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(12) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    val entities = schema.getJSONArray("entities")
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val table = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                        val indices = entity.getJSONArray("indices")
                        for (j in 0 until indices.length()) {
                            db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                        }
                    }
                    val setup = schema.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    db.execSQL("INSERT INTO app_meta (`key`, `value`) VALUES ('migration-test', 'preserved')")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected fixture upgrade")
            }).build())
        try {
            helper.writableDatabase
            helper.close()
            repeat(2) {
                val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
                    .openHelperFactory(SupportOpenHelperFactory(key.toByteArray()))
                    .addMigrations(DatabaseMigrations.MIGRATION_12_13).allowMainThreadQueries().build()
                try {
                    val db = room.openHelper.writableDatabase
                    assertEquals(13, db.version)
                    db.query("SELECT value FROM app_meta WHERE `key`='migration-test'").use { c ->
                        assertTrue(c.moveToFirst()); assertEquals("preserved", c.getString(0))
                    }
                    db.execSQL("INSERT INTO ai_report_records (userId, kind, title, model, markdown, createdAt) VALUES (1, 'report', 'test', NULL, 'test', 1)")
                } finally { room.close() }
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
