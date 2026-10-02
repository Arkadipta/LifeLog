package com.lifelog.app.domain

import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.ChartType
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Chart windowing and bucketing across daylight-saving transitions (L4).
 *
 * Every test runs in America/New_York, which in 2026 springs forward on
 * Mar 8 (01:59 EST → 03:00 EDT, a 23-hour day) and falls back on Nov 1
 * (01:59 EDT → 01:00 EST, a 25-hour day).
 *
 * Before the fix, DAY/WEEK/MONTH counted days as fixed 24-hour blocks in both
 * the entry filter and the bucket layout, so once a window spanned a
 * transition every boundary in it sat an hour off local midnight: hourly
 * buckets overran into the next day or dropped the last hour of this one, and
 * daily/weekly buckets admitted an hour of the previous day while shifting
 * entries a column over.
 */
class ChartDstBucketsTest {

    private val NY: TimeZone = TimeZone.getTimeZone("America/New_York")
    private lateinit var originalTz: TimeZone

    @Before
    fun useNewYork() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(NY)
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTz)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val WEIGHT = 1L

    private val fields = listOf(EventField(id = WEIGHT, name = "Weight", type = FieldType.NUMERIC))

    /** Local wall-clock instant in New York, 2026. */
    private fun at(month: Int, day: Int, hour: Int, minute: Int = 0, year: Int = 2026): Long =
        Calendar.getInstance(NY).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun dayOfMonth(ms: Long): Int =
        Calendar.getInstance(NY).apply { timeInMillis = ms }.get(Calendar.DAY_OF_MONTH)

    private fun config(type: ChartType = ChartType.LINE, days: Int?) = ChartConfig(
        id = "dst",
        eventTypeId = 1L,
        type = type,
        numericFieldIds = listOf(WEIGHT),
        timeRangeDays = days
    )

    private fun entry(createdAt: Long, value: Double) = EventEntry(
        id = createdAt,
        eventTypeId = 1L,
        fieldValues = mapOf(WEIGHT to FieldValue.Numeric(value)),
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun cartesian(data: ChartData): ChartData.Cartesian {
        assertTrue("expected Cartesian, was $data", data is ChartData.Cartesian)
        return data as ChartData.Cartesian
    }

    private fun heatmap(data: ChartData): ChartData.Heatmap {
        assertTrue("expected Heatmap, was $data", data is ChartData.Heatmap)
        return data as ChartData.Heatmap
    }

    // ── Day range: hourly buckets tile the local day ──────────────────────────

    @Test
    fun `day chart on a spring-forward day lays out twenty-three hourly buckets`() {
        val anchor = at(Calendar.MARCH, 8, 23, 45)
        val entries = listOf(
            entry(at(Calendar.MARCH, 8, 0, 30), 1.0),
            entry(at(Calendar.MARCH, 8, 23, 15), 2.0)
        )
        val data = cartesian(ChartDataProcessor.process(config(days = 1), entries, fields, anchor))

        // The 24th fixed hour used to run past midnight into Mar 9, giving the
        // axis a phantom column labeled with the next day.
        assertEquals(23, data.bucketTimestamps.size)
        assertEquals(8, dayOfMonth(data.bucketTimestamps.last()))
        assertEquals(listOf(0, 22), data.series.single().points.map { it.bucketIndex })
    }

    @Test
    fun `day chart on a fall-back day keeps the twenty-fifth hour`() {
        val anchor = at(Calendar.NOVEMBER, 1, 23, 45)
        val entries = listOf(
            entry(at(Calendar.NOVEMBER, 1, 0, 30), 1.0),
            entry(at(Calendar.NOVEMBER, 1, 23, 15), 2.0)
        )
        val data = cartesian(ChartDataProcessor.process(config(days = 1), entries, fields, anchor))

        // 24 fixed hours ended at 23:00 EST, so the last 75 minutes of the day
        // fell outside every bucket and the 23:15 entry rendered nowhere.
        assertEquals(25, data.bucketTimestamps.size)
        val points = data.series.single().points
        assertEquals(listOf(0, 24), points.map { it.bucketIndex })
        assertEquals(2.0, points.last().value, 0.001)
    }

    @Test
    fun `day chart on an ordinary day still lays out twenty-four buckets`() {
        val anchor = at(Calendar.MARCH, 15, 23, 45)
        val entries = listOf(entry(at(Calendar.MARCH, 15, 12, 0), 1.0))
        val data = cartesian(ChartDataProcessor.process(config(days = 1), entries, fields, anchor))

        assertEquals(24, data.bucketTimestamps.size)
        assertEquals(listOf(12), data.series.single().points.map { it.bucketIndex })
    }

    // ── Week / month: day boundaries stay on local midnight ───────────────────

    @Test
    fun `week window and buckets stay on local midnight after a spring forward`() {
        // Anchored Mar 14, the week runs back to Mar 8 — the 23-hour day — so a
        // 6 * 24h step landed the boundary on Mar 7 at 23:00.
        val anchor = at(Calendar.MARCH, 14, 12, 0)
        val windowStart = at(Calendar.MARCH, 8, 0, 0)
        val entries = listOf(
            entry(windowStart, 10.0),
            entry(windowStart - 1, 99.0), // 1 ms earlier: belongs to no bucket
            entry(at(Calendar.MARCH, 14, 9, 0), 5.0)
        )
        val data = cartesian(ChartDataProcessor.process(config(days = 7), entries, fields, anchor))

        val points = data.series.single().points
        assertEquals(listOf(0, 6), points.map { it.bucketIndex })
        // The previous day's entry used to be admitted and averaged into the
        // oldest bucket (10/99 → 54.5) instead of being filtered out.
        assertEquals(10.0, points.first().value, 0.001)
    }

    @Test
    fun `month window steps twenty-eight calendar days back across a spring forward`() {
        val anchor = at(Calendar.MARCH, 28, 12, 0)
        val windowStart = at(Calendar.FEBRUARY, 28, 0, 0)
        val entries = listOf(
            entry(windowStart, 10.0),
            entry(windowStart - 1, 99.0), // Feb 27: one hour inside the old filter
            entry(at(Calendar.MARCH, 28, 9, 0), 5.0)
        )
        val data = cartesian(ChartDataProcessor.process(config(days = 30), entries, fields, anchor))

        val points = data.series.single().points
        assertEquals(listOf(0, 3), points.map { it.bucketIndex })
        assertEquals(10.0, points.first().value, 0.001)
    }

    @Test
    fun `week buckets give every calendar day its own column across a fall back`() {
        // Anchored Nov 5, the week reaches back over Nov 1 — the 25-hour day.
        // A 6 * 24h step overshot it, starting the grid at Oct 30 01:00: the
        // Oct 30 entry fell outside the window entirely and the two days before
        // the transition each reported into the previous day's column.
        val anchor = at(Calendar.NOVEMBER, 5, 12, 0)
        val days = listOf(
            Calendar.OCTOBER to 30, Calendar.OCTOBER to 31,
            Calendar.NOVEMBER to 1, Calendar.NOVEMBER to 2, Calendar.NOVEMBER to 3,
            Calendar.NOVEMBER to 4, Calendar.NOVEMBER to 5
        )
        val entries = days.mapIndexed { i, (month, day) ->
            entry(at(month, day, 0, 30), (i + 1).toDouble())
        }
        val data = cartesian(ChartDataProcessor.process(config(days = 7), entries, fields, anchor))

        val points = data.series.single().points
        assertEquals((0..6).toList(), points.map { it.bucketIndex })
        assertEquals((1..7).map { it.toDouble() }, points.map { it.value })
        assertEquals(days.map { it.second }, data.bucketTimestamps.map { dayOfMonth(it) })
    }

    // ── Already DST-safe, pinned so it stays that way ─────────────────────────

    @Test
    fun `heatmap keeps one cell per local day across a spring forward`() {
        // The heatmap grid already stepped in calendar days; this guards it
        // against a future refactor reintroducing fixed-24h arithmetic.
        val anchor = at(Calendar.MARCH, 14, 12, 0)
        val days = listOf(7, 8, 9) // straddles the transition on the 8th
        val entries = days.map { day -> entry(at(Calendar.MARCH, day, 12, 0), day.toDouble()) }
        val data = heatmap(
            ChartDataProcessor.process(config(ChartType.HEATMAP, days = 30), entries, fields, anchor)
        )

        assertEquals(3, data.daysWithData)
        val cells = data.columns.flatMap { it.days }.filterNotNull().filter { it.value != null }
        // One cell per day, each sitting on that day's local midnight.
        assertEquals(days.map { at(Calendar.MARCH, it, 0, 0) }, cells.map { it.dateMs }.sorted())
        assertEquals(listOf(1), cells.map { it.entryCount }.distinct())
    }
}
