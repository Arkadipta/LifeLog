package com.lifelog.app.domain.model

data class Reminder(
    val id: Long = 0,
    val eventTypeId: Long? = null,
    val eventTypeName: String? = null,
    val title: String,
    val message: String = "",
    val repeatType: RepeatType = RepeatType.NONE,
    val repeatIntervalMinutes: Int = 60,
    val daysOfWeek: List<Int> = emptyList(),
    val timeOfDayMinutes: Int = 8 * 60,
    val nextTriggerAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

enum class RepeatType(val displayName: String) {
    NONE("Once"),
    DAILY("Every day"),
    WEEKLY("Selected days"),
    INTERVAL("Every N hours")
}
