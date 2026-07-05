package com.lifelog.app.util

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/*
 * These formatters are shared across threads: UI composition on main plus
 * Glance widget composition on its background workers. DateTimeFormatter is
 * immutable and thread-safe, so the singletons are fine to share — do not
 * swap them back to SimpleDateFormat, whose shared instances garble output
 * under exactly that concurrency. Locale is captured once at init; the zone
 * is resolved per call.
 */

/**
 * [pattern] rendered in [locale], except a trailing `a` becomes literal
 * "AM"/"PM" regardless of locale — e.g. Spanish would otherwise write
 * "a. m.", and the app's layouts assume the two-letter English markers.
 */
internal fun displayFormatter(pattern: String, locale: Locale): DateTimeFormatter =
    if (pattern.endsWith("a"))
        DateTimeFormatterBuilder()
            .appendPattern(pattern.dropLast(1))
            .appendText(ChronoField.AMPM_OF_DAY, mapOf(0L to "AM", 1L to "PM"))
            .toFormatter(locale)
    else
        DateTimeFormatter.ofPattern(pattern, locale)

private val dateTimeFormat = displayFormatter("MMM d, yyyy  h:mm a", Locale.getDefault())
private val dateFormat = displayFormatter("MMM d, yyyy", Locale.getDefault())
private val timeFormat = displayFormatter("h:mm a", Locale.getDefault())
private val clock12Format = displayFormatter("h:mm", Locale.getDefault())
private val clock24Format = displayFormatter("HH:mm", Locale.getDefault())
private val meridiemFormat = displayFormatter("a", Locale.getDefault())
private val widgetStamp12Format = displayFormatter("d MMM yyyy, hh:mm a", Locale.getDefault())
private val widgetStamp24Format = displayFormatter("d MMM yyyy, HH:mm", Locale.getDefault())

private fun Long.atSystemZone() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault())

fun Long.toDisplayDateTime(): String = dateTimeFormat.format(atSystemZone())
fun Long.toDisplayDate(): String = dateFormat.format(atSystemZone())
fun Long.toDisplayTime(): String = timeFormat.format(atSystemZone())

/**
 * The exact timestamp shown as the primary value on timeline-widget entries —
 * "20 Jun 2026, 09:14 AM", or "20 Jun 2026, 21:14" where the device prefers
 * 24-hour time. [is24Hour] comes from DateFormat.is24HourFormat(context).
 */
fun Long.toWidgetTimestamp(is24Hour: Boolean): String =
    (if (is24Hour) widgetStamp24Format else widgetStamp12Format).format(atSystemZone())

/**
 * Clock reading split for the entry time tile: "14:32" to null in 24-hour
 * mode, "2:32" to "PM" otherwise. [is24Hour] comes from the device setting.
 */
fun Long.toClockParts(is24Hour: Boolean): Pair<String, String?> {
    val local = atSystemZone()
    return if (is24Hour) clock24Format.format(local) to null
    else clock12Format.format(local) to meridiemFormat.format(local)
}

/**
 * The UTC start-of-day epoch millis for this timestamp's *local* calendar
 * date. Material 3's DatePicker speaks UTC for its selection and selectable-day
 * checks, so entry days are keyed through this to line up with the picker
 * regardless of the device time zone.
 */
fun Long.toUtcDateMillis(): Long {
    val local = Calendar.getInstance().apply { timeInMillis = this@toUtcDateMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            local.get(Calendar.YEAR),
            local.get(Calendar.MONTH),
            local.get(Calendar.DAY_OF_MONTH)
        )
    }.timeInMillis
}

/**
 * This timestamp moved onto the calendar date named by [utcDateMillis] — a
 * DatePicker selection, i.e. midnight UTC of the chosen day — keeping its
 * local wall-clock time (seconds cleared). Inverse of [toUtcDateMillis]: the
 * day is decomposed in UTC and re-applied in the device zone, so the result
 * stays on the picked day in every time zone, where reading [utcDateMillis]
 * through a local Calendar would drift a day west of UTC.
 */
fun Long.withUtcDate(utcDateMillis: Long): Long {
    val utcDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcDateMillis
    }
    val local = Calendar.getInstance().apply { timeInMillis = this@withUtcDate }
    return Calendar.getInstance().apply {
        clear()
        set(
            utcDay.get(Calendar.YEAR),
            utcDay.get(Calendar.MONTH),
            utcDay.get(Calendar.DAY_OF_MONTH),
            local.get(Calendar.HOUR_OF_DAY),
            local.get(Calendar.MINUTE)
        )
    }.timeInMillis
}

fun Long.isToday(): Boolean {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = this@isToday }
    return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
}

fun Long.relativeTimeLabel(): String {
    val now = System.currentTimeMillis()
    val diff = now - this

    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    val week = 7 * day
    val month = 30 * day
    val year = 365 * day

    return when {
        diff < minute -> "Just now"

        diff < hour -> {
            val mins = diff / minute
            "${mins}m ago"
        }

        diff < day -> {
            val hours = diff / hour
            "${hours}h ago"
        }

        diff < 2 * day -> "Yesterday"

        diff < week -> {
            val days = diff / day
            "${days}d ago"
        }

        diff < month -> {
            val weeks = diff / week
            "${weeks}w ago"
        }

        diff < year -> {
            val months = diff / month
            "${months}mo ago"
        }

        else -> {
            val years = diff / year
            "${years}y ago"
        }
    }
}

fun minutesFromMidnightToLabel(minutes: Int): String {
    // mod keeps out-of-range input wrapping into the day, like the lenient
    // Calendar this replaced, where LocalTime.of would throw.
    val clamped = minutes.mod(24 * 60)
    return timeFormat.format(LocalTime.of(clamped / 60, clamped % 60))
}
