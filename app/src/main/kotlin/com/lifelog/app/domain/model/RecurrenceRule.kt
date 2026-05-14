package com.lifelog.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class RecurrenceType(val displayName: String) {
    NONE("Once"),
    DAILY("Daily"),
    WEEKLY("Daily / Weekly"),
    MONTHLY("Monthly / Yearly"),
    YEARLY("Yearly"),
    INTERVAL("Every N hours"),
    TIME_SINCE_LAST("Since last entry")
}

@Serializable
enum class DayOfMonthMode { DAY_OF_MONTH, DAY_OF_WEEK }

enum class DeliveryType(val displayName: String) {
    NOTIFICATION("Notification"),
    ALARM("Alarm")
}

/**
 * RRULE-like serializable recurrence descriptor.
 *
 * Field semantics by type:
 *  NONE       – fires once; timeOfDayMinutes sets the trigger time
 *  DAILY      – every day at timeOfDayMinutes
 *  WEEKLY     – daysOfWeek (0=Sun…6=Sat) at timeOfDayMinutes
 *  MONTHLY    – see dayOfMonthMode; months filters (0=Jan…11=Dec, empty=all)
 *  YEARLY     – same as MONTHLY but always filtered to the selected months
 *  INTERVAL   – every intervalMinutes from last trigger
 *  TIME_SINCE_LAST – timeSinceLastMinutes after the most recent linked entry
 *
 * MONTHLY / YEARLY – DAY_OF_MONTH mode:
 *   daysOfMonth: 1-31 specific dates; -1 = "last day of month"
 *
 * MONTHLY / YEARLY – DAY_OF_WEEK mode:
 *   daysOfWeek: which day(s); weekPositions: 1=First…4=Fourth,-1=Last (empty=all)
 */
@Serializable
data class RecurrenceRule(
    val type: RecurrenceType = RecurrenceType.DAILY,
    val timeOfDayMinutes: Int = 8 * 60,
    val intervalMinutes: Int = 60,
    val timeSinceLastMinutes: Int = 24 * 60,
    val daysOfWeek: List<Int> = emptyList(),
    val months: List<Int> = emptyList(),
    val daysOfMonth: List<Int> = emptyList(),
    val weekPositions: List<Int> = emptyList(),
    val dayOfMonthMode: DayOfMonthMode = DayOfMonthMode.DAY_OF_MONTH
)
