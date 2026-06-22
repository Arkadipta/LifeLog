package com.lifelog.app.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventTypeId: Long? = null,
    val title: String,
    val message: String = "",
    // ── legacy columns (kept for migration; superseded by recurrenceRuleJson) ──
    val repeatType: String = "NONE",
    val repeatIntervalMinutes: Int = 60,
    val daysOfWeekJson: String = "[]",
    val timeOfDayMinutes: Int = 8 * 60,
    // ── v3 columns ────────────────────────────────────────────────────────────
    val deliveryType: String = "NOTIFICATION",
    val recurrenceType: String = "DAILY",      // top-level copy for SQL queries
    val recurrenceRuleJson: String = "",        // full RecurrenceRule JSON (empty → migrate from legacy)
    // ── v4 column ─────────────────────────────────────────────────────────────
    val snoozeMinutes: Int = 10,                 // per-reminder snooze duration (pre-v4 rows → 10)
    // ─────────────────────────────────────────────────────────────────────────
    val nextTriggerAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
