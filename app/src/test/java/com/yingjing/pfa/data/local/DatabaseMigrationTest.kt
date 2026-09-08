package com.yingjing.pfa.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yingjing.pfa.data.local.historical.HistoricalV12Database
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** File-backed fixtures are built by Room from the actual 395072d entities, never from v13. */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-${UUID.randomUUID()}.db"
    private val tables = listOf("app_meta", "users", "holdings", "net_worth_snapshots",
        "category_snapshots", "alerts", "house_price_index", "subscriptions")

    @After fun cleanup() { context.deleteDatabase(name) }

    private fun current() = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(DatabaseMigrations.MIGRATION_12_13).allowMainThreadQueries().build()

    private fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> =
        db.query("SELECT * FROM `$table` ORDER BY 1").use { cursor ->
            buildList { while (cursor.moveToNext()) add((0 until cursor.columnCount).map {
                if (cursor.isNull(it)) null else cursor.getString(it)
            }) }
        }

    private fun seed(db: SupportSQLiteDatabase, table: String) {
        val columns = db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(1) to cursor.getString(2)) }
        }
        for (row in 1..2) {
            val values = columns.map { (column, type) -> when (type) {
                "INTEGER" -> row.toLong()
                "REAL" -> row + 0.125
                else -> "fixture-$column-$row-中文"
            } }.toTypedArray()
            db.execSQL("INSERT INTO `$table` (${columns.joinToString { "`${it.first}`" }}) VALUES (${columns.joinToString { "?" }})", values)
        }
    }

    @Test fun migrate_preserves_every_column_and_index_and_allows_ai_writes_and_reopen() = runTest {
        val historical = Room.databaseBuilder(context, HistoricalV12Database::class.java, name)
            .allowMainThreadQueries().build()
        val old = historical.openHelper.writableDatabase
        val actualTables = old.query("SELECT name FROM sqlite_master WHERE type='table' AND name NOT IN ('android_metadata','room_master_table','sqlite_sequence') ORDER BY name").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertEquals(tables.sorted(), actualTables)
        actualTables.forEach { seed(old, it) }
        val expected = actualTables.associateWith { rows(old, it) }
        val indexes = rows(old, "sqlite_master").filter { it[0] == "index" }
        historical.close()
        val migrated = current()
        val sql = migrated.openHelper.writableDatabase
        assertEquals(13, sql.version)
        actualTables.forEach { assertEquals(it, expected[it], rows(sql, it)) }
        assertTrue(rows(sql, "sqlite_master").filter { it[0] == "index" }.containsAll(indexes))
        sql.query("PRAGMA index_info(index_ai_report_records_userId_kind_createdAt)").use { c ->
            val columns = buildList { while (c.moveToNext()) add(c.getString(2)) }
            assertEquals(listOf("userId", "kind", "createdAt"), columns)
        }
        assertTrue(migrated.aiReportRecordDao().getAllForBackup().isEmpty())
        val record = AiReportRecordEntity(userId = 1, kind = "report", title = "fixture", model = null, markdown = "**保留**", createdAt = 12)
        val id = migrated.aiReportRecordDao().insert(record)
        assertTrue(id > 0)
        migrated.close()
        val reopened = current()
        try {
            assertEquals(record.copy(id = id), reopened.aiReportRecordDao().getById(id))
            actualTables.forEach { assertEquals(expected[it], rows(reopened.openHelper.writableDatabase, it)) }
        } finally { reopened.close() }
    }

    @Test fun unsupported_upgrade_keeps_file_version_and_data() = rejectVersion(11)
    @Test fun unsupported_downgrade_keeps_file_version_and_data() = rejectVersion(14)

    private fun rejectVersion(version: Int) {
        // Synthetic unsupported-version sentinel, not evidence of any historical schema.
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE sentinel (value TEXT NOT NULL)")
            db.execSQL("INSERT INTO sentinel VALUES ('preserve-me')")
            db.version = version
        }
        val db = current()
        try {
            assertThrows(IllegalStateException::class.java) { db.openHelper.writableDatabase }
        } finally { db.close() }
        assertTrue(context.getDatabasePath(name).exists())
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { preserved ->
            assertEquals(version, preserved.version)
            preserved.rawQuery("SELECT value FROM sentinel", null).use { c ->
                assertTrue(c.moveToFirst()); assertEquals("preserve-me", c.getString(0))
            }
        }
    }
}
