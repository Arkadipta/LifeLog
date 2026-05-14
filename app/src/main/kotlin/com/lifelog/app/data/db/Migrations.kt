package com.lifelog.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `chart_configs` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `eventTypeId` INTEGER NOT NULL,
                `configJson` TEXT NOT NULL DEFAULT '{}',
                `sortOrder` INTEGER NOT NULL DEFAULT 0,
                `createdAt` INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(`eventTypeId`) REFERENCES `event_types`(`id`) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_chart_configs_eventTypeId` " +
            "ON `chart_configs` (`eventTypeId`)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE reminders ADD COLUMN deliveryType TEXT NOT NULL DEFAULT 'NOTIFICATION'")
        db.execSQL("ALTER TABLE reminders ADD COLUMN recurrenceType TEXT NOT NULL DEFAULT 'DAILY'")
        db.execSQL("ALTER TABLE reminders ADD COLUMN recurrenceRuleJson TEXT NOT NULL DEFAULT ''")
    }
}
