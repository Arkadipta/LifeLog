package com.lifelog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class ChartTickGeneratorTest {

    private val HOUR = 3_600_000L
    private val DAY = 86_400_000L

    private fun at(
        year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0
    ): Long = Calendar.getInstance().apply {
        set(year, month, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun generate(timestamps: List<Long>, maxTicks: Int = 4) =
        ChartTickGenerator.generate(timestamps, maxTicks, Locale.US)

    private fun assertDistinct(labels: List<String>) {
        assertEquals("labels should be distinct: $labels", labels.toSet().size, labels.size)
    }

    @Test
    fun `empty input yields no ticks`() {
        assertTrue(generate(emptyList()).isEmpty())
    }

    @Test
    fun `single bucket yields single tick`() {
        val ticks = generate(listOf(at(2026, Calendar.MAY, 20)))
        assertEquals(1, ticks.size)
        assertEquals(0, ticks.single().bucketIndex)
        assertTrue(ticks.single().label.isNotBlank())
    }

    @Test
    fun `few buckets are all ticked`() {
        val base = at(2026, Calendar.MAY, 18)
        val ticks = generate(listOf(base, base + DAY, base + 2 * DAY))
        assertEquals(listOf(0, 1, 2), ticks.map { it.bucketIndex })
        assertDistinct(ticks.map { it.label })
    }

    @Test
    fun `hourly buckets within one day use time labels`() {
        val midnight = at(2026, Calendar.MAY, 20, 0, 30)
        val timestamps = (0 until 24).map { midnight + it * HOUR }
        val ticks = generate(timestamps)

        assertEquals(listOf(0, 6, 12, 18), ticks.map { it.bucketIndex })
        assertEquals(listOf("00:30", "06:30", "12:30", "18:30"), ticks.map { it.label })
    }

    @Test
    fun `hourly buckets crossing midnight include the weekday`() {
        val tenPm = at(2026, Calendar.MAY, 20, 22, 0)
        val timestamps = (0 until 6).map { tenPm + it * 2 * HOUR }  // 22:00 → 08:00
        val ticks = generate(timestamps)

        assertTrue(ticks.all { it.label.contains(" ") })  // "Wed 22:00" style
        assertDistinct(ticks.map { it.label })
    }

    @Test
    fun `week of days uses weekday labels`() {
        val monday = at(2026, Calendar.MAY, 11)
        val timestamps = (0 until 7).map { monday + it * DAY }
        val ticks = generate(timestamps)

        assertEquals(listOf(0, 2, 4, 6), ticks.map { it.bucketIndex })
        assertEquals(listOf("Mon 11", "Wed 13", "Fri 15", "Sun 17"), ticks.map { it.label })
    }

    @Test
    fun `year of months uses month labels`() {
        val timestamps = (0 until 12).map { offset ->
            Calendar.getInstance().apply {
                set(2025, Calendar.JUNE, 15, 12, 0, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.MONTH, offset)
            }.timeInMillis
        }
        val ticks = generate(timestamps)

        assertEquals(listOf("Jun", "Sep", "Dec", "Mar"), ticks.map { it.label })
    }

    @Test
    fun `clustered data refines duplicate labels to distinct ones`() {
        // Span > 180d picks "MMM", but the ticked buckets cluster in Jan/Feb,
        // so "MMM" (and "MMM yy") collide and the generator must land on "MMM d".
        val timestamps = listOf(
            at(2026, Calendar.JANUARY, 1),
            at(2026, Calendar.JANUARY, 20),
            at(2026, Calendar.JANUARY, 25),
            at(2026, Calendar.FEBRUARY, 10),
            at(2026, Calendar.FEBRUARY, 20),
            at(2026, Calendar.MARCH, 20),
            at(2026, Calendar.JULY, 1),
            at(2026, Calendar.JULY, 10)
        )
        val ticks = generate(timestamps)

        assertEquals(listOf(0, 2, 4, 6), ticks.map { it.bucketIndex })
        assertEquals(listOf("Jan 1", "Jan 25", "Feb 20", "Jul 1"), ticks.map { it.label })
    }

    @Test
    fun `month span uses day-of-month labels`() {
        val start = at(2026, Calendar.APRIL, 25)
        val timestamps = (0 until 4).map { start + it * 7 * DAY }
        val ticks = generate(timestamps)

        assertEquals(listOf("Apr 25", "May 2", "May 9", "May 16"), ticks.map { it.label })
    }

    @Test
    fun `multi year span includes the year`() {
        val timestamps = (0 until 20).map { at(2024, Calendar.JANUARY, 10) + it * 40 * DAY }
        val ticks = generate(timestamps)

        assertTrue(ticks.all { it.label.matches(Regex("[A-Za-z]{3} \\d{2}")) })
        assertDistinct(ticks.map { it.label })
    }

    @Test
    fun `wider charts get more ticks`() {
        val monday = at(2026, Calendar.MAY, 11)
        val timestamps = (0 until 14).map { monday + it * DAY }
        val ticks = generate(timestamps, maxTicks = 6)

        assertTrue("expected at least 5 ticks, got ${ticks.size}", ticks.size >= 5)
        assertDistinct(ticks.map { it.label })
    }
}
