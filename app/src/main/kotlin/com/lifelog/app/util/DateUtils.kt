package com.lifelog.app.util

import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun simpleDateFormat(pattern: String) =
    SimpleDateFormat(pattern, Locale.getDefault()).apply {
        dateFormatSymbols = DateFormatSymbols(Locale.getDefault()).apply {
            amPmStrings = arrayOf("AM", "PM")
        }
    }

private val dateTimeFormat = simpleDateFormat("MMM d, yyyy  h:mm a")
private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
private val timeFormat = simpleDateFormat("h:mm a")
private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
private val clock12Format = simpleDateFormat("h:mm")
private val clock24Format = SimpleDateFormat("HH:mm", Locale.getDefault())
private val meridiemFormat = simpleDateFormat("a")
private val widgetStamp12Format = simpleDateFormat("d MMM yyyy, hh:mm a")
private val widgetStamp24Format = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())

fun Long.toDisplayDateTime(): String = dateTimeFormat.format(Date(this))
fun Long.toDisplayDate(): String = dateFormat.format(Date(this))
fun Long.toDisplayTime(): String = timeFormat.format(Date(this))
fun Long.toIso8601(): String = iso8601Format.format(Date(this))

/**
 * The exact timestamp shown as the primary value on timeline-widget entries —
 * "20 Jun 2026, 09:14 AM", or "20 Jun 2026, 21:14" where the device prefers
 * 24-hour time. [is24Hour] comes from DateFormat.is24HourFormat(context).
 */
fun Long.toWidgetTimestamp(is24Hour: Boolean): String =
    (if (is24Hour) widgetStamp24Format else widgetStamp12Format).format(Date(this))

/**
 * Clock reading split for the entry time tile: "14:32" to null in 24-hour
 * mode, "2:32" to "PM" otherwise. [is24Hour] comes from the device setting.
 */
fun Long.toClockParts(is24Hour: Boolean): Pair<String, String?> =
    if (is24Hour) clock24Format.format(Date(this)) to null
    else clock12Format.format(Date(this)) to meridiemFormat.format(Date(this))

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

fun Long.isToday(): Boolean {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = this@isToday }
    return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
}

//fun Long.relativeTimeLabel(): String {
//    val now = System.currentTimeMillis()
//    val diff = now - this
//    return when {
//        diff < 60_000 -> "Just now"
//        diff < 3_600_000 -> "${diff / 60_000}m ago"
//        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
//        isToday() -> "Today"
//        diff < 2 * 86_400_000 -> "Yesterday"
//        else -> toDisplayDate()
//    }
//}

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
    val h = minutes / 60
    val m = minutes % 60
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, h)
        set(Calendar.MINUTE, m)
    }
    return timeFormat.format(cal.time)
}
