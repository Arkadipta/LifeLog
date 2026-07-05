package com.lifelog.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Locks down DateUtils display formatting after the M10 move off shared
 * SimpleDateFormat singletons (not thread-safe; raced between main-thread UI
 * and Glance's background widget composition) onto java.time. Two contracts:
 * [displayFormatter] renders each pattern exactly as the legacy formatters
 * did — including the forced English "AM"/"PM" markers — and the public
 * extensions stay stable under concurrent use.
 */
class DateUtilsFormatTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val morning = ZonedDateTime.of(2026, 6, 20, 9, 14, 0, 0, zone)
    private val evening = ZonedDateTime.of(2026, 6, 20, 21, 14, 0, 0, zone)

    // ── Pattern translation (exact strings, explicit locale) ─────────────────

    @Test
    fun displayDateTime_matchesLegacyRendering() {
        val fmt = displayFormatter("MMM d, yyyy  h:mm a", Locale.US)
        assertEquals("Jun 20, 2026  9:14 AM", fmt.format(morning))
        assertEquals("Jun 20, 2026  9:14 PM", fmt.format(evening))
    }

    @Test
    fun displayDate_matchesLegacyRendering() {
        assertEquals("Jun 20, 2026", displayFormatter("MMM d, yyyy", Locale.US).format(morning))
    }

    @Test
    fun displayTime_matchesLegacyRendering() {
        assertEquals("9:14 AM", displayFormatter("h:mm a", Locale.US).format(morning))
    }

    @Test
    fun widgetStamp12Hour_padsHourAndCarriesMeridiem() {
        val fmt = displayFormatter("d MMM yyyy, hh:mm a", Locale.US)
        assertEquals("20 Jun 2026, 09:14 AM", fmt.format(morning))
        assertEquals("20 Jun 2026, 09:14 PM", fmt.format(evening))
    }

    @Test
    fun widgetStamp24Hour_usesDayClock() {
        val fmt = displayFormatter("d MMM yyyy, HH:mm", Locale.US)
        assertEquals("20 Jun 2026, 21:14", fmt.format(evening))
    }

    @Test
    fun meridiem_forcedToEnglishInAnyLocale() {
        // Spanish renders am/pm as "a. m."/"p. m."; the override must keep the
        // two-letter English markers the layouts assume.
        val fmt = displayFormatter("h:mm a", Locale.forLanguageTag("es"))
        assertEquals("9:14 AM", fmt.format(morning))
        assertEquals("9:14 PM", fmt.format(evening))
    }

    @Test
    fun meridiemOnlyPattern_isJustTheMarker() {
        val fmt = displayFormatter("a", Locale.forLanguageTag("es"))
        assertEquals("AM", fmt.format(morning))
        assertEquals("PM", fmt.format(evening))
    }

    // ── Public API wiring (device locale/zone, so shape-level asserts) ───────

    @Test
    fun clockParts_24HourHasNoMeridiem() {
        val (clock, meridiem) = morning.toInstant().toEpochMilli().toClockParts(is24Hour = true)
        assertTrue("clock '$clock' should be HH:mm", clock.matches(Regex("""\d{2}:\d{2}""")))
        assertNull(meridiem)
    }

    @Test
    fun clockParts_12HourCarriesForcedEnglishMeridiem() {
        val (clock, meridiem) = morning.toInstant().toEpochMilli().toClockParts(is24Hour = false)
        assertTrue("clock '$clock' should be h:mm", clock.matches(Regex("""\d{1,2}:\d{2}""")))
        assertTrue("meridiem was '$meridiem'", meridiem == "AM" || meridiem == "PM")
    }

    @Test
    fun minutesFromMidnight_rendersTwelveHourLabel() {
        assertEquals("12:00 AM", minutesFromMidnightToLabel(0))
        assertEquals("1:30 PM", minutesFromMidnightToLabel(13 * 60 + 30))
        assertEquals("11:59 PM", minutesFromMidnightToLabel(23 * 60 + 59))
    }

    @Test
    fun minutesFromMidnight_wrapsOutOfRangeLikeLenientCalendar() {
        assertEquals(minutesFromMidnightToLabel(0), minutesFromMidnightToLabel(24 * 60))
        assertEquals(minutesFromMidnightToLabel(23 * 60 + 30), minutesFromMidnightToLabel(-30))
    }

    // ── The actual M10 property ───────────────────────────────────────────────

    @Test
    fun formatters_stableUnderConcurrentUse() {
        val timestamps = longArrayOf(
            morning.toInstant().toEpochMilli(),
            evening.toInstant().toEpochMilli(),
            ZonedDateTime.of(2025, 12, 31, 23, 59, 0, 0, zone).toInstant().toEpochMilli(),
            ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone).toInstant().toEpochMilli()
        )

        fun renderAll(ts: Long) = listOf(
            ts.toDisplayDateTime(),
            ts.toDisplayDate(),
            ts.toDisplayTime(),
            ts.toWidgetTimestamp(is24Hour = false),
            ts.toWidgetTimestamp(is24Hour = true),
            ts.toClockParts(is24Hour = false).toString()
        )

        val expected = timestamps.map(::renderAll)
        val mismatches = AtomicInteger()
        (0 until 8).map { t ->
            thread {
                repeat(500) { i ->
                    val idx = (t + i) % timestamps.size
                    if (renderAll(timestamps[idx]) != expected[idx]) mismatches.incrementAndGet()
                }
            }
        }.forEach { it.join() }

        assertEquals(0, mismatches.get())
    }
}
