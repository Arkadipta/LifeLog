package com.lifelog.app.domain.model

enum class TimeRange(val displayName: String, val days: Int?) {
    DAY("Day", 1),
    WEEK("Week", 7),
    MONTH("Month", 30),
    YEAR("Year", 365),
    ALL("All", null);

    companion object {
        fun fromDays(days: Int?): TimeRange =
            entries.firstOrNull { it.days == days } ?: ALL
    }
}
