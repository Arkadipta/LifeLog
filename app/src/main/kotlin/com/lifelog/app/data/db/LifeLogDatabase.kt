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
    version = 2,
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
    }
}
