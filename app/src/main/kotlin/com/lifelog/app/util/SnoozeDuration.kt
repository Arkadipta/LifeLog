package com.lifelog.app.util

/**
 * Snooze-duration helpers shared by the reminder editor and the notification/alarm Snooze actions.
 *
 * Durations are expressed in minutes, consistent with the rest of the reminder model
 * (timeOfDayMinutes, intervalMinutes, timeSinceLastMinutes). The supported range is 1 minute … 1
 * week; the formatters still degrade gracefully for any positive value.
 */

private const val MIN_PER_HOUR = 60
private const val MIN_PER_DAY = 24 * 60
private const val MIN_PER_WEEK = 7 * 24 * 60

/** Common quick-pick snooze durations (minutes): 5m, 10m, 15m, 30m, 1h, 3h, 1d, 1 week. */
val SNOOZE_PRESETS_MINUTES: List<Int> = listOf(5, 10, 15, 30, 60, 180, MIN_PER_DAY, MIN_PER_WEEK)

/**
 * Compact label for space-constrained surfaces (notification action, alarm button, chips):
 * "30m", "1h", "1d", "1w"; non-round values fall back to "1h 30m".
 */
fun snoozeShortLabel(minutes: Int): String {
    val m = minutes.coerceAtLeast(1)
    return when {
        m % MIN_PER_WEEK == 0 -> "${m / MIN_PER_WEEK}w"
        m % MIN_PER_DAY == 0  -> "${m / MIN_PER_DAY}d"
        m % MIN_PER_HOUR == 0 -> "${m / MIN_PER_HOUR}h"
        m < MIN_PER_HOUR      -> "${m}m"
        else                  -> "${m / MIN_PER_HOUR}h ${m % MIN_PER_HOUR}m"
    }
}

/**
 * Verbose label for the editor's picker card and sheet: "30 minutes", "1 hour", "1 day", "1 week";
 * non-round values fall back to "1h 30m".
 */
fun snoozeLongLabel(minutes: Int): String {
    val m = minutes.coerceAtLeast(1)
    return when {
        m % MIN_PER_WEEK == 0 -> pluralize(m / MIN_PER_WEEK, "week")
        m % MIN_PER_DAY == 0  -> pluralize(m / MIN_PER_DAY, "day")
        m % MIN_PER_HOUR == 0 -> pluralize(m / MIN_PER_HOUR, "hour")
        m < MIN_PER_HOUR      -> pluralize(m, "minute")
        else                  -> "${m / MIN_PER_HOUR}h ${m % MIN_PER_HOUR}m"
    }
}

private fun pluralize(value: Int, unit: String): String =
    "$value $unit${if (value == 1) "" else "s"}"

/**
 * Unit options for the editor's custom snooze input, ordered shortest → longest. Labels are kept
 * short (Min/Hr/Day/Wk) so all four fit on one line in the segmented selector on small screens.
 */
enum class SnoozeUnit(val displayName: String, val minutes: Int) {
    MINUTES("Min", 1),
    HOURS("Hr", MIN_PER_HOUR),
    DAYS("Day", MIN_PER_DAY),
    WEEKS("Wk", MIN_PER_WEEK)
}

/**
 * Splits a snooze duration into a whole [value] of the largest unit that divides it evenly, for
 * seeding the custom value/unit fields (e.g. 10080 → 1×WEEKS, 90 → 90×MINUTES).
 */
fun decomposeSnooze(totalMinutes: Int): Pair<Int, SnoozeUnit> {
    val m = totalMinutes.coerceAtLeast(1)
    return when {
        m % MIN_PER_WEEK == 0 -> (m / MIN_PER_WEEK) to SnoozeUnit.WEEKS
        m % MIN_PER_DAY == 0  -> (m / MIN_PER_DAY) to SnoozeUnit.DAYS
        m % MIN_PER_HOUR == 0 -> (m / MIN_PER_HOUR) to SnoozeUnit.HOURS
        else                  -> m to SnoozeUnit.MINUTES
    }
}
