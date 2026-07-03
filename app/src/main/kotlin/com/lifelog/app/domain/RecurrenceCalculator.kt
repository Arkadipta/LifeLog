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
        RecurrenceType.DAILY, RecurrenceType.WEEKLY -> nextWeeklyTrigger(rule, after)
        RecurrenceType.MONTHLY, RecurrenceType.YEARLY -> nextMonthlyTrigger(rule, after)
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
        RecurrenceType.NONE -> nextTimeOfDayOccurrence(now, rule.timeOfDayMinutes)
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

    /**
     * Compute the trigger to arm when a disabled reminder is re-enabled.
     *
     * The stored [storedNextTriggerAt] is kept while it is still ahead, so briefly toggling a
     * reminder off and back on preserves its original schedule. Once it has elapsed, the rule
     * is re-evaluated from [now] instead — arming a past trigger would make the alarm fire the
     * moment the switch is flipped. A one-shot (NONE) re-arms for the next occurrence of its
     * time-of-day; TIME_SINCE_LAST restarts its countdown from [now] (the next logged entry
     * resets it anyway).
     */
    fun computeReactivationTrigger(
        rule: RecurrenceRule,
        storedNextTriggerAt: Long,
        now: Long = System.currentTimeMillis()
    ): Long {
        if (storedNextTriggerAt > now) return storedNextTriggerAt
        return computeNextTrigger(rule, after = now)
            ?: nextTimeOfDayOccurrence(now, rule.timeOfDayMinutes)
    }

    /**
     * Compute the trigger to arm when every reminder is re-armed in bulk — boot, app update,
     * database restore, app-start recovery, or a timezone / wall-clock change.
     *
     * A stored trigger that is still ahead is kept, unless [clockChanged] is set and the rule
     * is wall-clock based (DAILY/WEEKLY/MONTHLY/YEARLY): that epoch was derived under the old
     * clock and no longer lands on the intended local time, so it is re-anchored from the rule.
     * An elapsed trigger is recomputed from [now] instead of re-armed — arming stale epochs
     * would fire every missed reminder at once; INTERVAL and TIME_SINCE_LAST restart their
     * countdowns, matching [computeReactivationTrigger].
     *
     * One-shots (NONE) keep their stored epoch in every case: the rule holds only a time-of-day,
     * so the epoch is the sole record of the chosen date. A missed one-shot therefore fires
     * once, late, and then deactivates in the receiver — better a late nudge than a silent skip.
     */
    fun computeRescheduleTrigger(
        rule: RecurrenceRule,
        storedNextTriggerAt: Long,
        now: Long = System.currentTimeMillis(),
        clockChanged: Boolean = false
    ): Long {
        if (rule.type == RecurrenceType.NONE) return storedNextTriggerAt
        val reAnchor = clockChanged && rule.type.isWallClock
        if (storedNextTriggerAt > now && !reAnchor) return storedNextTriggerAt
        return computeNextTrigger(rule, after = now)
            ?: nextTimeOfDayOccurrence(now, rule.timeOfDayMinutes)
    }

    // ── Time-since-last ──────────────────────────────────────────────────────

    private fun timeSinceLastTrigger(rule: RecurrenceRule, now: Long, lastEntryAt: Long?): Long? {
        val base = lastEntryAt ?: now
        val trigger = base + rule.timeSinceLastMinutes * 60_000L
        return if (trigger > now) trigger else null
    }

    // ── Weekly (also handles DAILY: empty daysOfWeek = every day) ───────────

    private fun nextWeeklyTrigger(rule: RecurrenceRule, after: Long): Long {
        if (rule.daysOfWeek.isEmpty()) {
            return nextTimeOfDayOccurrence(after, rule.timeOfDayMinutes)
        }
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
        return nextTimeOfDayOccurrence(after, rule.timeOfDayMinutes)
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

    /** Rules whose trigger encodes an intended local wall-clock time rather than an elapsed duration. */
    private val RecurrenceType.isWallClock: Boolean
        get() = when (this) {
            RecurrenceType.DAILY, RecurrenceType.WEEKLY,
            RecurrenceType.MONTHLY, RecurrenceType.YEARLY -> true
            RecurrenceType.NONE, RecurrenceType.INTERVAL, RecurrenceType.TIME_SINCE_LAST -> false
        }

    /** [base]'s day at [timeOfDayMinutes] if that is still ahead, else the same time a day later. */
    private fun nextTimeOfDayOccurrence(base: Long, timeOfDayMinutes: Int): Long {
        val candidate = calAtTime(base, timeOfDayMinutes)
        return if (candidate > base) candidate else calAtTime(base, timeOfDayMinutes, dayOffset = 1)
    }

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
            RecurrenceType.DAILY, RecurrenceType.WEEKLY -> {
                if (rule.daysOfWeek.isEmpty()) "Daily at $time"
                else {
                    val days = rule.daysOfWeek.joinToString(", ") { DAY_SHORT.getOrElse(it) { "?" } }
                    "Weekly on $days at $time"
                }
            }
            RecurrenceType.MONTHLY, RecurrenceType.YEARLY -> buildMonthlyDescription(rule, time)
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
            rule.months.isEmpty() -> if (rule.type == RecurrenceType.YEARLY) "every year" else "every month"
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
