package com.yingjing.pfa.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/**
 * 13→14 无损迁移：用**已提交的 v13 导出 schema**（`app/schemas/.../13.json`）建出真实 v13 文件库，
 * 每张表写 2 行合成数据（含中文），迁移后逐列比对 + 新表可写 + 再次打开不丢。
 * 不读取任何真实用户数据库。
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigration13To14Test {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration13-${UUID.randomUUID()}.db"

    @After fun cleanup() { context.deleteDatabase(name) }

    private fun current() = Room.databaseBuilder(context, AppDatabase::class.java, name)
        .addMigrations(*DatabaseMigrations.ALL).allowMainThreadQueries().build()

    private fun schemaV13(): JSONObject {
        val path = "schemas/com.yingjing.pfa.data.local.AppDatabase/13.json"
        val file = listOf(File(path), File("app/$path")).first { it.exists() }
        return JSONObject(file.readText()).getJSONObject("database")
    }

    /** 按 13.json 的 createSql / 索引 / setupQueries 建库并设 user_version=13。返回表名列表。 */
    private fun buildV13(): List<String> {
        val schema = schemaV13()
        assertEquals(13, schema.getInt("version"))
        val tables = mutableListOf<String>()
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                tables += table
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            tables.forEach { seed(db, it) }
            db.version = 13
        }
        return tables
    }

    private fun seed(db: SQLiteDatabase, table: String) {
        val columns = db.rawQuery("PRAGMA table_info(`$table`)", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(1) to c.getString(2)) }
        }
        for (row in 1..2) {
            val values = columns.map { (column, type) ->
                when (type) {
                    "INTEGER" -> row.toLong()
                    "REAL" -> row + 0.125
                    else -> "fixture-$column-$row-中文"
                }
            }.toTypedArray()
            db.execSQL(
                "INSERT INTO `$table` (${columns.joinToString { "`${it.first}`" }}) VALUES (${columns.joinToString { "?" }})",
                values,
            )
        }
    }

    private fun rows(db: SupportSQLiteDatabase, table: String): List<List<String?>> =
        db.query("SELECT * FROM `$table` ORDER BY 1, 2").use { c ->
            buildList {
                while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) })
            }
        }

    private fun rowsRaw(table: String): List<List<String?>> =
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { db ->
            db.rawQuery("SELECT * FROM `$table` ORDER BY 1, 2", null).use { c ->
                buildList {
                    while (c.moveToNext()) add((0 until c.columnCount).map { if (c.isNull(it)) null else c.getString(it) })
                }
            }
        }

    @Test
    fun migrate13To14_preservesAllRows_createsSeriesTable_andReopens() = runTest {
        val tables = buildV13()
        assertTrue("v13 应含 9 张业务表", tables.size == 9 && "ai_report_records" in tables)
        val expected = tables.associateWith { rowsRaw(it) }

        val migrated = current()
        val sql = migrated.openHelper.writableDatabase
        assertEquals(14, sql.version)
        tables.forEach { assertEquals(it, expected[it], rows(sql, it)) }

        // 新表结构：3 列 + 复合主键 (seriesCode, epochDay)
        val info = sql.query("PRAGMA table_info(`market_series_points`)").use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getString(1), c.getString(2), c.getInt(5))) }
        }
        assertEquals(
            listOf(Triple("seriesCode", "TEXT", 1), Triple("epochDay", "INTEGER", 2), Triple("value", "REAL", 0)),
            info,
        )

        val dao = migrated.marketSeriesDao()
        dao.upsertAll(listOf(MarketSeriesPointEntity("fx_vix", 20_000, 26.36)))
        assertEquals(20_000L, dao.latestDay("fx_vix"))
        migrated.close()

        val reopened = current()
        try {
            assertEquals(listOf(MarketSeriesPointEntity("fx_vix", 20_000, 26.36)), reopened.marketSeriesDao().series("fx_vix"))
            tables.forEach { assertEquals(it, expected[it], rows(reopened.openHelper.writableDatabase, it)) }
        } finally {
            reopened.close()
        }
    }
}
