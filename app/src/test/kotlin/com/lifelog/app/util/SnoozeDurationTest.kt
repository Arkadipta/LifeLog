package com.lifelog.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks down the snooze-duration formatting + decomposition contract used by the reminder editor
 * and the notification/alarm Snooze actions.
 */
class SnoozeDurationTest {

    // ── snoozeShortLabel ──────────────────────────────────────────────────────

    @Test fun shortLabel_minutes() = assertEquals("30m", snoozeShortLabel(30))
    @Test fun shortLabel_wholeHour() = assertEquals("1h", snoozeShortLabel(60))
    @Test fun shortLabel_multipleHours() = assertEquals("3h", snoozeShortLabel(180))
    @Test fun shortLabel_wholeDay() = assertEquals("1d", snoozeShortLabel(1440))
    @Test fun shortLabel_wholeWeek() = assertEquals("1w", snoozeShortLabel(10080))
    @Test fun shortLabel_mixedFallsBackToHoursMinutes() = assertEquals("1h 30m", snoozeShortLabel(90))
    @Test fun shortLabel_clampsBelowOne() = assertEquals("1m", snoozeShortLabel(0))

    // ── snoozeLongLabel ───────────────────────────────────────────────────────

    @Test fun longLabel_singularMinute() = assertEquals("1 minute", snoozeLongLabel(1))
    @Test fun longLabel_pluralMinutes() = assertEquals("30 minutes", snoozeLongLabel(30))
    @Test fun longLabel_singularHour() = assertEquals("1 hour", snoozeLongLabel(60))
    @Test fun longLabel_pluralHours() = assertEquals("2 hours", snoozeLongLabel(120))
    @Test fun longLabel_singularDay() = assertEquals("1 day", snoozeLongLabel(1440))
    @Test fun longLabel_singularWeek() = assertEquals("1 week", snoozeLongLabel(10080))
    @Test fun longLabel_mixedFallsBackToHoursMinutes() = assertEquals("1h 30m", snoozeLongLabel(90))

    // ── decomposeSnooze ───────────────────────────────────────────────────────

    @Test fun decompose_prefersWeeks() = assertEquals(1 to SnoozeUnit.WEEKS, decomposeSnooze(10080))
    @Test fun decompose_prefersDays() = assertEquals(1 to SnoozeUnit.DAYS, decomposeSnooze(1440))
    @Test fun decompose_prefersHours() = assertEquals(2 to SnoozeUnit.HOURS, decomposeSnooze(120))
    @Test fun decompose_fallsBackToMinutes() = assertEquals(90 to SnoozeUnit.MINUTES, decomposeSnooze(90))
    @Test fun decompose_clampsBelowOne() = assertEquals(1 to SnoozeUnit.MINUTES, decomposeSnooze(0))

    // ── SnoozeUnit multipliers ────────────────────────────────────────────────

    @Test fun unitMultipliers() {
        assertEquals(1, SnoozeUnit.MINUTES.minutes)
        assertEquals(60, SnoozeUnit.HOURS.minutes)
        assertEquals(1440, SnoozeUnit.DAYS.minutes)
        assertEquals(10080, SnoozeUnit.WEEKS.minutes)
    }
}
