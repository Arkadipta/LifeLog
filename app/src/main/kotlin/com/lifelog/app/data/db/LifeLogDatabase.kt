package com.lifelog.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lifelog.app.data.db.dao.ChartConfigDao
import com.lifelog.app.data.db.dao.EventEntryDao
import com.lifelog.app.data.db.dao.EventFieldDao
import com.lifelog.app.data.db.dao.EventTypeDao
import com.lifelog.app.data.db.dao.ReminderDao
import com.lifelog.app.data.db.entity.ChartConfigEntity
import com.lifelog.app.data.db.entity.EventEntryEntity
import com.lifelog.app.data.db.entity.EventFieldEntity
import com.lifelog.app.data.db.entity.EventTypeEntity
import com.lifelog.app.data.db.entity.ReminderEntity

@Database(
    entities = [
        EventTypeEntity::class,
        EventFieldEntity::class,
        EventEntryEntity::class,
        ReminderEntity::class,
        ChartConfigEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class LifeLogDatabase : RoomDatabase() {
    abstract fun eventTypeDao(): EventTypeDao
    abstract fun eventFieldDao(): EventFieldDao
    abstract fun eventEntryDao(): EventEntryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun chartConfigDao(): ChartConfigDao

    companion object {
        const val DATABASE_NAME = "lifelog.db"

        /** Current Room schema version. MUST equal the `version` in @Database above. */
        const val SCHEMA_VERSION = 4
    }
}
