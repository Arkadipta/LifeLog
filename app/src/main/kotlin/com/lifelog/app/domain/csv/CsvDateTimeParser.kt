package com.lifelog.app.domain.csv

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Best-effort detection of a timestamp from a free-form string, used to turn the
 * user-selected "date/time" column into entry timestamps (epoch millis).
 *
 * A prioritised list of common patterns is tried; the first that consumes the
 * **entire** trimmed value wins. Zoneless patterns are interpreted in the device
 * time zone (matching how the rest of the app stores local timestamps); patterns
 * with an explicit offset/`Z` honour it. Pure-digit values are treated as epoch
 * seconds (10 digits) or milliseconds (13 digits).
 *
 * Returns `null` when nothing matches so callers can report the offending row.
 */
object CsvDateTimeParser {

    // Ordered most-specific first. ISO-8601 and year-first forms are preferred
    // over ambiguous locale forms; month-first (US) is tried before day-first.
    private val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd HH:mm",
        "yyyy/MM/dd",
        "MM/dd/yyyy HH:mm:ss",
        "MM/dd/yyyy HH:mm",
        "MM/dd/yyyy hh:mm a",
        "MM/dd/yyyy",
        "dd/MM/yyyy HH:mm:ss",
        "dd/MM/yyyy HH:mm",
        "dd/MM/yyyy",
        "dd-MM-yyyy HH:mm:ss",
        "dd-MM-yyyy",
        "dd.MM.yyyy HH:mm:ss",
        "dd.MM.yyyy",
        "MMM d, yyyy h:mm a",
        "MMM d, yyyy",
        "MMM d yyyy",
        "d MMM yyyy"
    )

    fun parse(raw: String): Long? {
        val value = raw.trim()
        if (value.isEmpty()) return null

        parseEpoch(value)?.let { return it }

        for (pattern in patterns) {
            val format = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
            val position = ParsePosition(0)
            val date = format.parse(value, position)
            if (date != null && position.index == value.length) return date.time
        }
        return null
    }

    /** Pure-digit epoch values: 13 digits → millis, 10 digits → seconds. */
    private fun parseEpoch(value: String): Long? {
        if (!value.matches(EPOCH_REGEX)) return null
        val number = value.toLongOrNull() ?: return null
        return when (value.removePrefix("-").length) {
            13 -> number
            10 -> number * 1000L
            else -> null
        }
    }

    private val EPOCH_REGEX = Regex("-?\\d{10}|-?\\d{13}")
}
