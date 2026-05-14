package com.lifelog.app.domain

import com.lifelog.app.domain.model.DayOfMonthMode
import com.lifelog.app.domain.model.RecurrenceRule
import com.lifelog.app.domain.model.RecurrenceType
import java.util.Calendar

object RecurrenceCalculator {

    /**
     * Compute the next trigger after [after] for a recurring reminder.
     *
     * @param rule          the recurrence descriptor
     * @param after         "now" reference point (epoch ms); next trigger must be > this
     * @param lastEntryAt   used by TIME_SINCE_LAST to base the trigger on the most recent entry
     * @param eventDateTime for TIME_SINCE_LAST: the datetime on the entry that caused the reset
     */
    fun computeNextTrigger(
        rule: RecurrenceRule,
        after: Long = System.currentTimeMillis(),
        lastEntryAt: Long? = null,
        eventDateTime: Long? = null
    ): Long? = when (rule.type) {
        RecurrenceType.NONE -> null
        RecurrenceType.INTERVAL -> after + rule.intervalMinutes * 60_000L
        RecurrenceType.TIME_SINCE_LAST -> timeSinceLastTrigger(rule, after, lastEntryAt)
        RecurrenceType.DAILY -> nextDailyTrigger(rule, after)
        RecurrenceType.WEEKLY -> nextWeeklyTrigger(rule, after)
        RecurrenceType.MONTHLY -> nextMonthlyTrigger(rule, after)
        RecurrenceType.YEARLY -> nextYearlyTrigger(rule, after)
    }

    /**
     * Compute the very first trigger when a reminder is created.
     *
     * For TIME_SINCE_LAST, [eventDateTime] is the datetime of the entry the user just logged.
     * Handles the three edge cases from the spec:
     *  • eventDateTime is null / "now"       → fires in N minutes
     *  • eventDateTime is T ms in the future → fires T + N ms from now
     *  • eventDateTime is T ms in the past   → fires N - T ms from now (or null if already missed)
     */
    fun computeInitialTrigger(
        rule: RecurrenceRule,
        now: Long = System.currentTimeMillis(),
        eventDateTime: Long? = null
    ): Long? = when (rule.type) {
        RecurrenceType.NONE -> {
            val candidate = calAtTime(now, rule.timeOfDayMinutes)
            if (candidate > now) candidate else calAtTime(now, rule.timeOfDayMinutes, dayOffset = 1)
        }
        RecurrenceType.DAILY, RecurrenceType.WEEKLY,
        RecurrenceType.MONTHLY, RecurrenceType.YEARLY -> {
            val candidate = calAtTime(now, rule.timeOfDayMinutes)
            if (candidate > now) candidate else computeNextTrigger(rule, now)
        }
        RecurrenceType.INTERVAL -> now + rule.intervalMinutes * 60_000L
        RecurrenceType.TIME_SINCE_LAST -> {
            val base = eventDateTime ?: now
            val trigger = base + rule.timeSinceLastMinutes * 60_000L
            if (trigger > now) trigger else null
        }
    }

    // ── Time-since-last ──────────────────────────────────────────────────────

    private fun timeSinceLastTrigger(rule: RecurrenceRule, now: Long, lastEntryAt: Long?): Long? {
        val base = lastEntryAt ?: now
        val trigger = base + rule.timeSinceLastMinutes * 60_000L
        return if (trigger > now) trigger else null
    }

    // ── Daily ────────────────────────────────────────────────────────────────

    private fun nextDailyTrigger(rule: RecurrenceRule, after: Long): Long {
        val t = calAtTime(after, rule.timeOfDayMinutes)
        return if (t > after) t else calAtTime(after, rule.timeOfDayMinutes, dayOffset = 1)
    }

    // ── Weekly ───────────────────────────────────────────────────────────────

    private fun nextWeeklyTrigger(rule: RecurrenceRule, after: Long): Long {
        if (rule.daysOfWeek.isEmpty()) return nextDailyTrigger(rule, after)
        val h = rule.timeOfDayMinutes / 60
        val m = rule.timeOfDayMinutes % 60
        for (offset in 0..7) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = after
                add(Calendar.DAY_OF_YEAR, offset)
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dow = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun..6=Sat
            if (dow in rule.daysOfWeek && cal.timeInMillis > after) return cal.timeInMillis
        }
        return nextDailyTrigger(rule, after)
    }

    // ── Monthly ──────────────────────────────────────────────────────────────

    private fun nextMonthlyTrigger(rule: RecurrenceRule, after: Long): Long {
        val base = Calendar.getInstance().apply { timeInMillis = after }
        val startYear = base.get(Calendar.YEAR)
        val startMonth = base.get(Calendar.MONTH)

        for (offset in 0..24) {
            val monthCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, startYear)
                set(Calendar.MONTH, startMonth)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, offset)
            }
            val month = monthCal.get(Calendar.MONTH)
            if (rule.months.isNotEmpty() && month !in rule.months) continue

            triggersInMonth(rule, monthCal).filter { it > after }.minOrNull()?.let { return it }
        }
        return after + 30L * 24 * 3600 * 1000
    }

    // ── Yearly ───────────────────────────────────────────────────────────────

    private fun nextYearlyTrigger(rule: RecurrenceRule, after: Long): Long {
        val base = Calendar.getInstance().apply { timeInMillis = after }
        val currentYear = base.get(Calendar.YEAR)
        val monthsToUse = if (rule.months.isEmpty()) (0..11).toList() else rule.months.sorted()

        for (yearOffset in 0..5) {
            val year = currentYear + yearOffset
            for (month in monthsToUse) {
                val monthCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year); set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                triggersInMonth(rule, monthCal).filter { it > after }.minOrNull()?.let { return it }
            }
        }
        return after + 365L * 24 * 3600 * 1000
    }

    // ── Month trigger helpers ─────────────────────────────────────────────────

    private fun triggersInMonth(rule: RecurrenceRule, monthStart: Calendar): List<Long> =
        when (rule.dayOfMonthMode) {
            DayOfMonthMode.DAY_OF_MONTH -> domTriggers(rule, monthStart)
            DayOfMonthMode.DAY_OF_WEEK -> dowTriggers(rule, monthStart)
        }

    private fun domTriggers(rule: RecurrenceRule, monthCal: Calendar): List<Long> {
        if (rule.daysOfMonth.isEmpty()) return emptyList()
        val maxDay = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val h = rule.timeOfDayMinutes / 60
        val m = rule.timeOfDayMinutes % 60
        val year = monthCal.get(Calendar.YEAR)
        val month = monthCal.get(Calendar.MONTH)
        return rule.daysOfMonth.map { dom ->
            val day = if (dom == -1) maxDay else dom.coerceIn(1, maxDay)
            Calendar.getInstance().apply {
                set(year, month, day, h, m, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
    }

    private fun dowTriggers(rule: RecurrenceRule, monthCal: Calendar): List<Long> {
        if (rule.daysOfWeek.isEmpty()) return emptyList()
        val maxDay = monthCal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val h = rule.timeOfDayMinutes / 60
        val m = rule.timeOfDayMinutes % 60
        val year = monthCal.get(Calendar.YEAR)
        val month = monthCal.get(Calendar.MONTH)

        data class Occ(val day: Int, val position: Int, val isLast: Boolean)

        val occurrencesByDow = mutableMapOf<Int, MutableList<Occ>>()
        for (day in 1..maxDay) {
            val cal = Calendar.getInstance().apply { set(year, month, day, 12, 0, 0) }
            val dow = cal.get(Calendar.DAY_OF_WEEK) - 1
            if (dow !in rule.daysOfWeek) continue
            val list = occurrencesByDow.getOrPut(dow) { mutableListOf() }
            val position = list.size + 1
            val isLast = day + 7 > maxDay
            list.add(Occ(day, position, isLast))
        }

        val results = mutableListOf<Long>()
        for ((_, occs) in occurrencesByDow) {
            for (occ in occs) {
                val matchesPos = rule.weekPositions.isEmpty() ||
                    occ.position in rule.weekPositions ||
                    (occ.isLast && -1 in rule.weekPositions)
                if (matchesPos) {
                    results.add(Calendar.getInstance().apply {
                        set(year, month, occ.day, h, m, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis)
                }
            }
        }
        return results
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    private fun calAtTime(base: Long, timeOfDayMinutes: Int, dayOffset: Int = 0): Long =
        Calendar.getInstance().apply {
            timeInMillis = base
            set(Calendar.HOUR_OF_DAY, timeOfDayMinutes / 60)
            set(Calendar.MINUTE, timeOfDayMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (dayOffset != 0) add(Calendar.DAY_OF_YEAR, dayOffset)
        }.timeInMillis

    fun describeRule(rule: RecurrenceRule): String {
        val time = minutesToTimeLabel(rule.timeOfDayMinutes)
        return when (rule.type) {
            RecurrenceType.NONE -> "Once at $time"
            RecurrenceType.DAILY -> "Daily at $time"
            RecurrenceType.WEEKLY -> {
                val days = rule.daysOfWeek.joinToString(", ") {
                    listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").getOrElse(it) { "?" }
                }
                "Weekly on $days at $time"
            }
            RecurrenceType.MONTHLY -> buildMonthlyDescription(rule, time)
            RecurrenceType.YEARLY -> buildYearlyDescription(rule, time)
            RecurrenceType.INTERVAL -> {
                val h = rule.intervalMinutes / 60; val m = rule.intervalMinutes % 60
                if (m == 0) "Every ${h}h" else "Every ${h}h ${m}m"
            }
            RecurrenceType.TIME_SINCE_LAST -> {
                val h = rule.timeSinceLastMinutes / 60; val m = rule.timeSinceLastMinutes % 60
                if (m == 0) "${h}h after last entry" else "${h}h ${m}m after last entry"
            }
        }
    }

    private fun buildMonthlyDescription(rule: RecurrenceRule, time: String): String {
        val monthPart = when {
            rule.months.isEmpty() -> "every month"
            rule.months == (0..11 step 2).toList() -> "even months"
            rule.months == (1..11 step 2).toList() -> "odd months"
            else -> rule.months.joinToString(", ") { MONTH_SHORT[it] }
        }
        val datePart = when (rule.dayOfMonthMode) {
            DayOfMonthMode.DAY_OF_MONTH -> rule.daysOfMonth.joinToString(", ") {
                if (it == -1) "last day" else ordinal(it)
            }
            DayOfMonthMode.DAY_OF_WEEK -> {
                val days = rule.daysOfWeek.joinToString("/") { DAY_SHORT[it] }
                val pos = rule.weekPositions.joinToString(", ") {
                    if (it == -1) "Last" else WEEK_POS_LABEL[it - 1]
                }
                "$pos $days"
            }
        }
        return "$datePart of $monthPart at $time"
    }

    private fun buildYearlyDescription(rule: RecurrenceRule, time: String): String {
        val monthPart = if (rule.months.isEmpty()) "every year"
        else rule.months.joinToString(", ") { MONTH_SHORT[it] }
        val datePart = when (rule.dayOfMonthMode) {
            DayOfMonthMode.DAY_OF_MONTH -> rule.daysOfMonth.joinToString(", ") {
                if (it == -1) "last day" else ordinal(it)
            }
            DayOfMonthMode.DAY_OF_WEEK -> {
                val days = rule.daysOfWeek.joinToString("/") { DAY_SHORT[it] }
                val pos = rule.weekPositions.joinToString(", ") {
                    if (it == -1) "Last" else WEEK_POS_LABEL[it - 1]
                }
                "$pos $days"
            }
        }
        return "$datePart of $monthPart at $time"
    }

    private fun minutesToTimeLabel(minutes: Int): String {
        val h = minutes / 60; val m = minutes % 60
        val amPm = if (h < 12) "AM" else "PM"
        val hour = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return if (m == 0) "$hour $amPm" else "$hour:${m.toString().padStart(2, '0')} $amPm"
    }

    private fun ordinal(n: Int) = when {
        n % 100 in 11..13 -> "${n}th"
        n % 10 == 1 -> "${n}st"
        n % 10 == 2 -> "${n}nd"
        n % 10 == 3 -> "${n}rd"
        else -> "${n}th"
    }

    private val MONTH_SHORT = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
    private val DAY_SHORT = listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
    private val WEEK_POS_LABEL = listOf("First","Second","Third","Fourth")
}
