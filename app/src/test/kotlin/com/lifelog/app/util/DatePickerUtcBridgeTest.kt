package com.lifelog.app.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Locks down the bridge between entry timestamps (local instants) and the
 * Material 3 DatePicker, which reports days as midnight UTC: [toUtcDateMillis]
 * feeds the picker's initial selection, [withUtcDate] merges its result back.
 * The H2 audit finding — merging the picker's UTC day through a local Calendar
 * lands the entry on the previous day west of UTC — is the regression these
 * tests exist to catch, so every assertion runs under an explicitly pinned
 * default time zone.
 */
class DatePickerUtcBridgeTest {

    private lateinit var originalTz: TimeZone

    @Before
    fun saveTimeZone() {
        originalTz = TimeZone.getDefault()
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTz)
    }

    private fun inZone(zoneId: String, block: () -> Unit) {
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        block()
    }

    /** Local timestamp from calendar parts, in the currently pinned zone. */
    private fun localAt(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0
    ): Long = Calendar.getInstance().apply {
        clear()
        set(year, month - 1, day, hour, minute, second)
    }.timeInMillis

    /** What the picker reports for a chosen day: midnight UTC of that date. */
    private fun utcMidnight(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day)
        }.timeInMillis

    /** Local calendar decomposition (y, m, d, h, min, s, ms) for asserting. */
    private fun localParts(millis: Long): List<Int> =
        Calendar.getInstance().apply { timeInMillis = millis }.let {
            listOf(
                it.get(Calendar.YEAR),
                it.get(Calendar.MONTH) + 1,
                it.get(Calendar.DAY_OF_MONTH),
                it.get(Calendar.HOUR_OF_DAY),
                it.get(Calendar.MINUTE),
                it.get(Calendar.SECOND),
                it.get(Calendar.MILLISECOND)
            )
        }

    @Test
    fun `merged entry lands on the picked day with its wall-clock time in every zone`() {
        val zones = listOf(
            "Pacific/Pago_Pago", // UTC-11
            "America/Los_Angeles", // UTC-8/-7 — the audited previous-day shift
            "UTC",
            "Asia/Kolkata", // UTC+5:30, half-hour offset
            "Pacific/Kiritimati" // UTC+14
        )
        for (zone in zones) inZone(zone) {
            val createdAt = localAt(2026, 7, 1, 14, 37, second = 42)
            val merged = createdAt.withUtcDate(utcMidnight(2026, 7, 4))
            // Day comes from the picker, hour/minute from the entry, seconds cleared.
            assertEquals("zone $zone", listOf(2026, 7, 4, 14, 37, 0, 0), localParts(merged))
        }
    }

    @Test
    fun `initial selection names the local calendar day even at its edges`() {
        inZone("America/Los_Angeles") {
            // 23:55 local is already the next day in UTC; the picker must still
            // open on the 4th.
            assertEquals(
                utcMidnight(2026, 7, 4),
                localAt(2026, 7, 4, 23, 55).toUtcDateMillis()
            )
        }
        inZone("Asia/Kolkata") {
            // 00:05 local is still the previous day in UTC.
            assertEquals(
                utcMidnight(2026, 7, 4),
                localAt(2026, 7, 4, 0, 5).toUtcDateMillis()
            )
        }
    }

    @Test
    fun `confirming the picker without changing the day keeps date and time`() {
        // The no-change flow: the sheet seeds the picker with
        // createdAt.toUtcDateMillis() and Set merges the same day straight back.
        for (zone in listOf("America/Los_Angeles", "Asia/Kolkata")) inZone(zone) {
            for ((hour, minute) in listOf(0 to 5, 23 to 55)) {
                val createdAt = localAt(2026, 7, 4, hour, minute)
                val merged = createdAt.withUtcDate(createdAt.toUtcDateMillis())
                assertEquals(
                    "zone $zone at $hour:$minute",
                    listOf(2026, 7, 4, hour, minute, 0, 0),
                    localParts(merged)
                )
            }
        }
    }

    @Test
    fun `wall-clock time inside the DST spring-forward gap rolls forward within the day`() {
        inZone("America/Los_Angeles") {
            // 02:30 does not exist on Mar 8 2026 (02:00 jumps to 03:00); the
            // lenient Calendar renders it as 03:30 PDT — still the picked day.
            val createdAt = localAt(2026, 3, 5, 2, 30)
            val merged = createdAt.withUtcDate(utcMidnight(2026, 3, 8))
            assertEquals(listOf(2026, 3, 8, 3, 30, 0, 0), localParts(merged))
        }
    }
}
