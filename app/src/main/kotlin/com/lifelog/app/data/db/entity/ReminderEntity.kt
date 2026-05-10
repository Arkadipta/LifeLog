package com.lifelog.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventTypeId: Long? = null,
    val title: String,
    val message: String = "",
    val repeatType: String = "NONE",
    val repeatIntervalMinutes: Int = 60,
    val daysOfWeekJson: String = "[]",
    val timeOfDayMinutes: Int = 8 * 60,
    val nextTriggerAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
