package com.lifelog.app.domain

import com.lifelog.app.domain.model.DayOfMonthMode
import com.lifelog.app.domain.model.RecurrenceRule
import com.lifelog.app.domain.model.RecurrenceType
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for RecurrenceCalculator covering all recurrence types and edge cases.
 * Uses wall-clock Calendar math — DST and leap-year behaviour inherits from the JVM.
 */
class RecurrenceCalculatorTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun calOf(year: Int, month: Int, day: Int, hour: Int = 0, min: Int = 0): Long =
        Calendar.getInstance().apply {
            set(year, month, day, hour, min, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun calGet(epochMs: Long, field: Int): Int =
        Calendar.getInstance().apply { timeInMillis = epochMs }.get(field)

    // ── NONE ─────────────────────────────────────────────────────────────────

    @Test fun `NONE returns null from computeNextTrigger`() {
        val rule = RecurrenceRule(type = RecurrenceType.NONE, timeOfDayMinutes = 9 * 60)
        assertNull(RecurrenceCalculator.computeNextTrigger(rule, System.currentTimeMillis()))
    }

    @Test fun `NONE computeInitialTrigger returns same-day time if in future`() {
        // Use a time far in the past so "today at 23:59" is definitely in the future
        val midnight = calOf(2025, Calendar.MARCH, 15, 0, 0)
        val rule = RecurrenceRule(type = RecurrenceType.NONE, timeOfDayMinutes = 23 * 60 + 59)
        val trigger = RecurrenceCalculator.computeInitialTrigger(rule, midnight)
        assertNotNull(trigger)
        assertEquals(23, calGet(trigger!!, Calendar.HOUR_OF_DAY))
        assertEquals(59, calGet(trigger, Calendar.MINUTE))
        assertEquals(15, calGet(trigger, Calendar.DAY_OF_MONTH))
    }

    // ── DAILY ────────────────────────────────────────────────────────────────

    @Test fun `DAILY advances by 1 day`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, timeOfDayMinutes = 8 * 60)
        val trigger = RecurrenceCalculator.computeNextTrigger(rule, now)!!
        assertEquals(11, calGet(trigger, Calendar.DAY_OF_MONTH))
        assertEquals(8, calGet(trigger, Calendar.HOUR_OF_DAY))
    }

    @Test fun `DAILY same-day if target is still in future`() {
        val now = calOf(2025, Calendar.JUNE, 10, 6, 0)   // 6 AM
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, timeOfDayMinutes = 8 * 60) // 8 AM
        val trigger = RecurrenceCalculator.computeNextTrigger(rule, now)!!
        assertEquals(10, calGet(trigger, Calendar.DAY_OF_MONTH))
    }

    // ── WEEKLY ───────────────────────────────────────────────────────────────

    @Test fun `WEEKLY finds next matching day`() {
        // Monday = 1. Pick a Thursday (2025-Jun-12 is Thursday = 4).
        val thursday = calOf(2025, Calendar.JUNE, 12, 20, 0)
        val rule = RecurrenceRule(
            type = RecurrenceType.WEEKLY,
            timeOfDayMinutes = 9 * 60,
            daysOfWeek = listOf(1) // Monday
        )
        val trigger = RecurrenceCalculator.computeNextTrigger(rule, thursday)!!
        // Next Monday is June 16
        assertEquals(16, calGet(trigger, Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MONDAY, calGet(trigger, Calendar.DAY_OF_WEEK))
    }

    @Test fun `WEEKLY multiple days picks nearest`() {
        // Tuesday (Calendar.TUESDAY = 3, dow-index = 2). Days selected: Wed(3) and Fri(5).
        val tuesday = calOf(2025, Calendar.JUNE, 10, 12, 0)
        val rule = RecurrenceRule(
            type = RecurrenceType.WEEKLY,
            timeOfDayMinutes = 9 * 60,
            daysOfWeek = listOf(3, 5) // Wed, Fri
        )
        val trigger = RecurrenceCalculator.computeNextTrigger(rule, tuesday)!!
        assertEquals(11, calGet(trigger, Calendar.DAY_OF_MONTH)) // Wednesday June 11
    }

    // ── INTERVAL ─────────────────────────────────────────────────────────────

    @Test fun `INTERVAL adds intervalMinutes to after`() {
        val now = 1_000_000L
        val rule = RecurrenceRule(type = RecurrenceType.INTERVAL, intervalMinutes = 120)
        assertEquals(now + 120 * 60_000L, RecurrenceCalculator.computeNextTrigger(rule, now))
    }

    // ── TIME_SINCE_LAST ───────────────────────────────────────────────────────

    @Test fun `TIME_SINCE_LAST fires lastEntryAt + interval when in future`() {
        val now = System.currentTimeMillis()
        val entryAt = now - 1 * 3600_000L // 1 hour ago
        val rule = RecurrenceRule(type = RecurrenceType.TIME_SINCE_LAST, timeSinceLastMinutes = 4 * 60) // 4h
        val trigger = RecurrenceCalculator.computeNextTrigger(rule, now, lastEntryAt = entryAt)!!
        assertEquals(entryAt + 4 * 3600_000L, trigger)
    }

    @Test fun `TIME_SINCE_LAST returns null when already elapsed`() {
        val now = System.currentTimeMillis()
        val entryAt = now - 5 * 3600_000L // 5 hours ago
        val rule = RecurrenceRule(type = RecurrenceType.TIME_SINCE_LAST, timeSinceLastMinutes = 3 * 60) // 3h
        // 3h elapsed since 5h-ago entry → deadline was 2h ago → null
        assertNull(RecurrenceCalculator.computeNextTrigger(rule, now, lastEntryAt = entryAt))
    }

    @Test fun `computeInitialTrigger TIME_SINCE_LAST future eventDateTime`() {
        val now = System.currentTimeMillis()
        val futureEvent = now + 2 * 3600_000L // 2h from now
        val rule = RecurrenceRule(type = RecurrenceType.TIME_SINCE_LAST, timeSinceLastMinutes = 3 * 60)
        // Expected: futureEvent + 3h = now + 5h
        val trigger = RecurrenceCalculator.computeInitialTrigger(rule, now, eventDateTime = futureEvent)!!
        assertEquals(futureEvent + 3 * 3600_000L, trigger)
    }

    @Test fun `computeInitialTrigger TIME_SINCE_LAST past eventDateTime within window`() {
        val now = System.currentTimeMillis()
        val pastEvent = now - 1 * 3600_000L // 1h ago
        val rule = RecurrenceRule(type = RecurrenceType.TIME_SINCE_LAST, timeSinceLastMinutes = 4 * 60) // 4h
        // 4h - 1h = 3h remaining → trigger in 3h
        val trigger = RecurrenceCalculator.computeInitialTrigger(rule, now, eventDateTime = pastEvent)!!
        assertTrue(trigger > now)
        assertEquals(pastEvent + 4 * 3600_000L, trigger)
    }

    @Test fun `computeInitialTrigger TIME_SINCE_LAST past eventDateTime beyond window`() {
        val now = System.currentTimeMillis()
        val pastEvent = now - 5 * 3600_000L // 5h ago
        val rule = RecurrenceRule(type = RecurrenceType.TIME_SINCE_LAST, timeSinceLastMinutes = 3 * 60) // 3h
        // Already 5h since event, window was 3h → missed
        assertNull(RecurrenceCalculator.computeInitialTrigger(rule, now, eventDateTime = pastEvent))
    }

    // ── MONTHLY – DAY_OF_MONTH ────────────────────────────────────────────────

    @Test fun `MONTHLY DOM fires on specific date in same month`() {
        val now = calOf(2025, Calendar.JUNE, 1, 0, 0)
        val rule = RecurrenceRule(
            type = RecurrenceType.MONTHLY,
            timeOfDayMinutes = 9 * 60 + 30,
            dayOfMonthMode = DayOfMonthMode.DAY_OF_MONTH,
            daysOfMonth = listOf(12, 19)
        )
        val trigger = RecurrenceCalculator.computeNextTrigger(rule, now)!!
        assertEquals(12, calGet(trigger, Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JUNE, calGet(trigger, Calendar.MONTH))
    }

    @Test fun `MONTHLY DOM falls back to last day for short month`() {
        // Feb 2025 has 28 days — day 31 should map to 28
        val now = calOf(2025, Calendar.JANUARY, 31, 23, 0)
        val rule = RecurrenceRule(
            type = RecurrenceType.MONTHLY,
            timeOfDayMinutes = 0,
            dayOfMonthMode = DayOfMonthMode.DAY_OF_MONTH,
            daysOfMonth = listOf(31)
        )
        val trigger = RecurrenceCalculator.computeNextTrigger(rule, now)!!
        assertEquals(Calendar.FEBRUARY, calGet(trigger, Calendar.MONTH))
        assertEquals(28, calGet(trigger, Calendar.DAY_OF_MONTH))
    }

    @Test fun `MONTHLY DOM last day sentinel fires on correct last day`() {
        val now = calOf(2025, Calendar.MARCH, 1, 0, 0)
        val rule = RecurrenceRule(
            type = RecurrenceType.MONTHLY,
            timeOfDayMinutes = 0,
            dayOfMonthMode = DayOfMonthMode.DAY_OF_MONTH,
            daysOfMonth = listOf(-1) // last day
        )
        val trigger = RecurrenceCalculator.computeNextTrigger(rule, now)!!
        assertEquals(Calendar.MARCH, calGet(trigger, Calendar.MONTH))
        assertEquals(31, calGet(trigger, Calendar.DAY_OF_MONTH))
    }

    @Test fun `MONTHLY DOM leap year last day of February`() {
        // 2024 is a leap year — Feb should have 29 days
        val now = calOf(2024, Calendar.JANUARY, 31, 23, 0)
        val rule = RecurrenceRule(
            type = RecurrenceType.MONTHLY,
            timeOfDayMinutes = 0,
            dayOfMonthMode = DayOfMonthMode.DAY_OF_MONTH,
            daysOfMonth = listOf(-1)
        )
        val trigger = RecurrenceCalculator.computeNextTrigger(rule, now)!!
        assertEquals(Calendar.FEBRUARY, calGet(trigger, Calendar.MONTH))
        assertEquals(29, calGet(trigger, Calendar.DAY_OF_MONTH))
    }

    // ── MONTHLY – DAY_OF_WEEK ─────────────────────────────────────────────────

    @Test fun `MONTHLY DOW second Friday of month`() {
        val now = calOf(2025, Calendar.JUNE, 1, 0, 0)
        val rule = RecurrenceRule(
            type = RecurrenceType.MONTHLY,
            timeOfDayMinutes = 11 * 60 + 30,
            dayOfMonthMode = DayOfMonthMode.DAY_OF_WEEK,
            daysOfWeek = listOf(5),    // Friday = index 5
            weekPositions = listOf(2)  // second occurrence
        )
        val trigger = RecurrenceCalculator.computeNextTrigger(rule, now)!!
        assertEquals(Calendar.JUNE, calGet(trigger, Calendar.MONTH))
        // Second Friday of June 2025: Jun 6 = first Friday, Jun 13 = second
        assertEquals(13, calGet(trigger, Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.FRIDAY, calGet(trigger, Calendar.DAY_OF_WEEK))
    }

    @Test fun `MONTHLY DOW last Saturday of month`() {
        val now = calOf(2025, Calendar.JUNE, 1, 0, 0)
        val rule = RecurrenceRule(
            type = RecurrenceType.MONTHLY,
            timeOfDayMinutes = 10 * 60,
            dayOfMonthMode = DayOfMonthMode.DAY_OF_WEEK,
            daysOfWeek = listOf(6),    // Saturday
            weekPositions = listOf(-1) // last
        )
        val trigger = RecurrenceCalculator.computeNextTrigger(rule, now)!!
        // Saturdays in June 2025: 7, 14, 21, 28 → last = 28
        assertEquals(28, calGet(trigger, Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.SATURDAY, calGet(trigger, Calendar.DAY_OF_WEEK))
    }

    // ── MONTHLY with month filter ─────────────────────────────────────────────

    @Test fun `MONTHLY skips months not in filter`() {
        // Start in May; months filter = [0=Jan, 1=Feb, 6=Jul]
        val now = calOf(2025, Calendar.MAY, 1, 0, 0)
        val rule = RecurrenceRule(
            type = RecurrenceType.MONTHLY,
            timeOfDayMinutes = 9 * 60,
            dayOfMonthMode = DayOfMonthMode.DAY_OF_MONTH,
            daysOfMonth = listOf(1),
            months = listOf(0, 1, 6)
        )
        val trigger = RecurrenceCalculator.computeNextTrigger(rule, now)!!
        // Next allowed month after May is July
        assertEquals(Calendar.JULY, calGet(trigger, Calendar.MONTH))
        assertEquals(1, calGet(trigger, Calendar.DAY_OF_MONTH))
    }

    // ── YEARLY ───────────────────────────────────────────────────────────────

    @Test fun `YEARLY fires in correct month and year`() {
        val now = calOf(2025, Calendar.AUGUST, 1, 0, 0)
        val rule = RecurrenceRule(
            type = RecurrenceType.YEARLY,
            timeOfDayMinutes = 9 * 60,
            dayOfMonthMode = DayOfMonthMode.DAY_OF_MONTH,
            daysOfMonth = listOf(15),
            months = listOf(1, 6) // Feb, Jul
        )
        // August → next matching is Feb 15 2026
        val trigger = RecurrenceCalculator.computeNextTrigger(rule, now)!!
        assertEquals(2026, calGet(trigger, Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, calGet(trigger, Calendar.MONTH))
        assertEquals(15, calGet(trigger, Calendar.DAY_OF_MONTH))
    }

    // ── computeReactivationTrigger (re-enabling a disabled reminder) ─────────

    @Test fun `reactivation keeps stored trigger that is still in the future`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val stored = now + 3600_000L
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, timeOfDayMinutes = 8 * 60)
        assertEquals(stored, RecurrenceCalculator.computeReactivationTrigger(rule, stored, now))
    }

    @Test fun `reactivation recomputes elapsed DAILY trigger instead of arming the past`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)   // 10:00, past the 8:00 slot
        val stored = now - 2 * 3600_000L
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, timeOfDayMinutes = 8 * 60)
        val trigger = RecurrenceCalculator.computeReactivationTrigger(rule, stored, now)
        assertTrue(trigger > now)
        assertEquals(11, calGet(trigger, Calendar.DAY_OF_MONTH)) // tomorrow 8 AM
        assertEquals(8, calGet(trigger, Calendar.HOUR_OF_DAY))
    }

    @Test fun `reactivation treats stored trigger equal to now as elapsed`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val rule = RecurrenceRule(type = RecurrenceType.INTERVAL, intervalMinutes = 90)
        assertEquals(now + 90 * 60_000L, RecurrenceCalculator.computeReactivationTrigger(rule, now, now))
    }

    @Test fun `reactivation restarts INTERVAL countdown from now`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val rule = RecurrenceRule(type = RecurrenceType.INTERVAL, intervalMinutes = 120)
        val trigger = RecurrenceCalculator.computeReactivationTrigger(rule, now - 1, now)
        assertEquals(now + 120 * 60_000L, trigger)
    }

    @Test fun `reactivation restarts TIME_SINCE_LAST countdown from now`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val rule = RecurrenceRule(type = RecurrenceType.TIME_SINCE_LAST, timeSinceLastMinutes = 4 * 60)
        val trigger = RecurrenceCalculator.computeReactivationTrigger(rule, now - 5_000, now)
        assertEquals(now + 4 * 3600_000L, trigger)
    }

    @Test fun `reactivation re-arms elapsed one-shot for next time-of-day occurrence`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)   // 10:00, past the 9:00 slot
        val rule = RecurrenceRule(type = RecurrenceType.NONE, timeOfDayMinutes = 9 * 60)
        val trigger = RecurrenceCalculator.computeReactivationTrigger(rule, now - 3600_000L, now)
        assertEquals(11, calGet(trigger, Calendar.DAY_OF_MONTH)) // tomorrow 9 AM
        assertEquals(9, calGet(trigger, Calendar.HOUR_OF_DAY))
    }

    @Test fun `reactivation elapsed one-shot stays today when its time is still ahead`() {
        val now = calOf(2025, Calendar.JUNE, 10, 7, 0)    // 07:00, before the 9:00 slot
        val rule = RecurrenceRule(type = RecurrenceType.NONE, timeOfDayMinutes = 9 * 60)
        val trigger = RecurrenceCalculator.computeReactivationTrigger(rule, now - 3600_000L, now)
        assertEquals(10, calGet(trigger, Calendar.DAY_OF_MONTH)) // today 9 AM
        assertEquals(9, calGet(trigger, Calendar.HOUR_OF_DAY))
    }

    @Test fun `reactivation recomputes elapsed WEEKLY trigger to next matching day`() {
        // 2025-06-12 is a Thursday; rule fires Mondays at 9:00
        val thursday = calOf(2025, Calendar.JUNE, 12, 20, 0)
        val rule = RecurrenceRule(
            type = RecurrenceType.WEEKLY,
            timeOfDayMinutes = 9 * 60,
            daysOfWeek = listOf(1) // Monday
        )
        val trigger = RecurrenceCalculator.computeReactivationTrigger(rule, thursday - 7 * 86_400_000L, thursday)
        assertEquals(16, calGet(trigger, Calendar.DAY_OF_MONTH)) // Monday June 16
        assertEquals(Calendar.MONDAY, calGet(trigger, Calendar.DAY_OF_WEEK))
    }

    // ── computeRescheduleTrigger (bulk re-arm: boot, restore, clock changes) ─

    @Test fun `reschedule keeps a future trigger when the clock is unchanged`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val stored = now + 3600_000L
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, timeOfDayMinutes = 8 * 60)
        assertEquals(stored, RecurrenceCalculator.computeRescheduleTrigger(rule, stored, now))
    }

    @Test fun `reschedule recomputes an elapsed DAILY trigger instead of arming the past`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val stored = now - 26 * 3600_000L   // missed yesterday's 8:00 while powered off
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, timeOfDayMinutes = 8 * 60)
        val trigger = RecurrenceCalculator.computeRescheduleTrigger(rule, stored, now)
        assertTrue(trigger > now)
        assertEquals(11, calGet(trigger, Calendar.DAY_OF_MONTH)) // tomorrow 8 AM, no boot blast
        assertEquals(8, calGet(trigger, Calendar.HOUR_OF_DAY))
    }

    @Test fun `reschedule re-anchors a future wall-clock trigger after a clock change`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        // Epoch armed under the old zone: still ahead, but landing on 5:00 local instead of 8:00.
        val stored = calOf(2025, Calendar.JUNE, 11, 5, 0)
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, timeOfDayMinutes = 8 * 60)
        val trigger = RecurrenceCalculator.computeRescheduleTrigger(rule, stored, now, clockChanged = true)
        assertEquals(11, calGet(trigger, Calendar.DAY_OF_MONTH))
        assertEquals(8, calGet(trigger, Calendar.HOUR_OF_DAY))
    }

    @Test fun `reschedule keeps a future INTERVAL trigger across a clock change`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val stored = now + 45 * 60_000L   // elapsed-duration rule: the epoch stays valid
        val rule = RecurrenceRule(type = RecurrenceType.INTERVAL, intervalMinutes = 90)
        assertEquals(stored, RecurrenceCalculator.computeRescheduleTrigger(rule, stored, now, clockChanged = true))
    }

    @Test fun `reschedule keeps a future TIME_SINCE_LAST trigger across a clock change`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val stored = now + 2 * 3600_000L
        val rule = RecurrenceRule(type = RecurrenceType.TIME_SINCE_LAST, timeSinceLastMinutes = 4 * 60)
        assertEquals(stored, RecurrenceCalculator.computeRescheduleTrigger(rule, stored, now, clockChanged = true))
    }

    @Test fun `reschedule restarts an elapsed INTERVAL from now`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val rule = RecurrenceRule(type = RecurrenceType.INTERVAL, intervalMinutes = 120)
        assertEquals(now + 120 * 60_000L, RecurrenceCalculator.computeRescheduleTrigger(rule, now - 1, now))
    }

    @Test fun `reschedule restarts an elapsed TIME_SINCE_LAST countdown from now`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val rule = RecurrenceRule(type = RecurrenceType.TIME_SINCE_LAST, timeSinceLastMinutes = 4 * 60)
        assertEquals(now + 4 * 3600_000L, RecurrenceCalculator.computeRescheduleTrigger(rule, now - 5_000, now))
    }

    @Test fun `reschedule treats a stored trigger equal to now as elapsed`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val rule = RecurrenceRule(type = RecurrenceType.INTERVAL, intervalMinutes = 90)
        assertEquals(now + 90 * 60_000L, RecurrenceCalculator.computeRescheduleTrigger(rule, now, now))
    }

    @Test fun `reschedule keeps an elapsed one-shot so it fires once, late`() {
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val stored = now - 3600_000L
        val rule = RecurrenceRule(type = RecurrenceType.NONE, timeOfDayMinutes = 9 * 60)
        assertEquals(stored, RecurrenceCalculator.computeRescheduleTrigger(rule, stored, now))
    }

    @Test fun `reschedule keeps a future one-shot across a clock change`() {
        // The rule stores only a time-of-day; the epoch is the sole record of the chosen date.
        val now = calOf(2025, Calendar.JUNE, 10, 10, 0)
        val stored = now + 86_400_000L
        val rule = RecurrenceRule(type = RecurrenceType.NONE, timeOfDayMinutes = 9 * 60)
        assertEquals(stored, RecurrenceCalculator.computeRescheduleTrigger(rule, stored, now, clockChanged = true))
    }

    // ── describeRule ─────────────────────────────────────────────────────────

    @Test fun `describeRule WEEKLY correctly formats days`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.WEEKLY,
            timeOfDayMinutes = 8 * 60,
            daysOfWeek = listOf(1, 2, 3)
        )
        val desc = RecurrenceCalculator.describeRule(rule)
        assertTrue(desc.contains("Mon"))
        assertTrue(desc.contains("Tue"))
        assertTrue(desc.contains("Wed"))
    }

    @Test fun `describeRule INTERVAL hours only`() {
        val rule = RecurrenceRule(type = RecurrenceType.INTERVAL, intervalMinutes = 120)
        assertEquals("Every 2h", RecurrenceCalculator.describeRule(rule))
    }

    @Test fun `describeRule INTERVAL hours and minutes`() {
        val rule = RecurrenceRule(type = RecurrenceType.INTERVAL, intervalMinutes = 90)
        assertEquals("Every 1h 30m", RecurrenceCalculator.describeRule(rule))
    }

    @Test fun `describeRule TIME_SINCE_LAST`() {
        val rule = RecurrenceRule(type = RecurrenceType.TIME_SINCE_LAST, timeSinceLastMinutes = 48 * 60)
        assertEquals("48h after last entry", RecurrenceCalculator.describeRule(rule))
    }
}
