package com.yingjing.pfa.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** 正式发布前的数据库无损迁移集合。 */
object DatabaseMigrations {
    val MIGRATION_12_13: Migration = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_report_records` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `userId` INTEGER NOT NULL,
                    `kind` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `model` TEXT,
                    `markdown` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_ai_report_records_userId_kind_createdAt`
                ON `ai_report_records` (`userId`, `kind`, `createdAt`)
                """.trimIndent(),
            )
        }
    }

    /**
     * 13→14：仅新增市场情绪日线缓存表（恐慌指数 + 叠加价格），不触碰任何既有表。
     * 表内为可重新下载的公开行情，不含用户数据。
     */
    val MIGRATION_13_14: Migration = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `market_series_points` (
                    `seriesCode` TEXT NOT NULL,
                    `epochDay` INTEGER NOT NULL,
                    `value` REAL NOT NULL,
                    PRIMARY KEY(`seriesCode`, `epochDay`)
                )
                """.trimIndent(),
            )
        }
    }

    /** 全部已验证迁移（按版本顺序）；DatabaseModule 与测试共用，避免漏注册。 */
    val ALL: Array<Migration> get() = arrayOf(MIGRATION_12_13, MIGRATION_13_14)
}
