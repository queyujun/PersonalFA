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
}
