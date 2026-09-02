package com.lifelog.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Every entry here is offered as its own chip in the reminder editor's Repeat row, which renders
 * [entries] directly — a type that exists but cannot be picked is a type whose sub-editor and
 * scheduling code go untested (DAILY and YEARLY were both in that state).
 */
@Serializable
enum class RecurrenceType(val displayName: String) {
    NONE("Once"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
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
 *  YEARLY     – same as MONTHLY, restricted to the selected months
 *  INTERVAL   – every intervalMinutes from last trigger
 *  TIME_SINCE_LAST – timeSinceLastMinutes after the most recent linked entry
 *
 * MONTHLY / YEARLY – DAY_OF_MONTH mode:
 *   daysOfMonth: 1-31 specific dates; -1 = "last day of month"
 *
 * MONTHLY / YEARLY – DAY_OF_WEEK mode:
 *   daysOfWeek: which day(s); weekPositions: 1=First…4=Fourth,-1=Last (empty=all)
 *
 * Not every combination of these fields describes something that can actually fire — a MONTHLY
 * rule with no day picked has no occurrence at all. `RecurrenceCalculator.validate` is the one
 * statement of which combinations are legal, and the editor refuses to save a rule it rejects;
 * rows that predate a rule (or arrive by restore) still land in the calculator's fallbacks.
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
) {
    companion object {
        /**
         * Floor for an INTERVAL rule. Every occurrence is a separate exact alarm that wakes the
         * device and posts a notification, so a rule of "0h 0m" — which the editor used to accept
         * and quietly coerce to 1 — is a minute-by-minute alarm loop, not a reminder.
         */
        const val MIN_INTERVAL_MINUTES = 5

        /** Floor for TIME_SINCE_LAST. Fires once per logged entry, so it only has to be non-zero. */
        const val MIN_TIME_SINCE_LAST_MINUTES = 1
    }
}
