package com.lifelog.app.util

import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

fun Long.toDisplayDateTime(): String = dateTimeFormat.format(Date(this))
fun Long.toDisplayDate(): String = dateFormat.format(Date(this))
fun Long.toDisplayTime(): String = timeFormat.format(Date(this))
fun Long.toIso8601(): String = iso8601Format.format(Date(this))

fun Long.isToday(): Boolean {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = this@isToday }
    return now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
}

fun Long.relativeTimeLabel(): String {
    val now = System.currentTimeMillis()
    val diff = now - this
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        isToday() -> "Today"
        diff < 2 * 86_400_000 -> "Yesterday"
        else -> toDisplayDate()
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
