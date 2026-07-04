package com.lifelog.app.domain

import com.lifelog.app.domain.model.AggregationStrategy
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.ChartType
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ChartDataProcessorTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val WEIGHT = 1L
    private val MISSING = 2L
    private val MEAL = 9L

    private val HOUR = 3_600_000L
    private val DAY = 86_400_000L

    /** Deterministic "now": local-time noon, May 20 2026. */
    private val NOW = Calendar.getInstance().apply {
        set(2026, Calendar.MAY, 20, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val fields = listOf(
        EventField(id = WEIGHT, name = "Weight", type = FieldType.NUMERIC),
        EventField(id = MEAL, name = "Meal", type = FieldType.CHOICE)
    )

    private fun config(
        type: ChartType = ChartType.LINE,
        days: Int? = 7,
        fieldIds: List<Long> = listOf(WEIGHT),
        groupBy: Long? = null
    ) = ChartConfig(
        id = "c1",
        eventTypeId = 1L,
        type = type,
        numericFieldIds = fieldIds,
        groupByFieldId = groupBy,
        timeRangeDays = days
    )

    private fun entry(
        createdAt: Long,
        value: Double? = null,
        meal: String? = null
    ): EventEntry {
        val values = buildMap<Long, FieldValue> {
            value?.let { put(WEIGHT, FieldValue.Numeric(it)) }
            meal?.let { put(MEAL, FieldValue.Choice(it)) }
        }
        return EventEntry(
            id = createdAt,
            eventTypeId = 1L,
            fieldValues = values,
            createdAt = createdAt,
            updatedAt = createdAt
        )
    }

    private fun cartesian(data: ChartData): ChartData.Cartesian {
        assertTrue("expected Cartesian, was $data", data is ChartData.Cartesian)
        return data as ChartData.Cartesian
    }

    // ── Anchoring ─────────────────────────────────────────────────────────────

    @Test
    fun `recent data keeps window anchored to now`() {
        val entries = listOf(entry(NOW - DAY, 70.0), entry(NOW - 2 * DAY, 71.0))
        val data = cartesian(ChartDataProcessor.process(config(), entries, fields, NOW))

        assertNull(data.anchoredEndMs)
        assertEquals(7, data.bucketTimestamps.size)
        assertEquals(2, data.series.single().points.size)
    }

    @Test
    fun `stale data anchors window to latest entry`() {
        val latest = NOW - 10 * DAY
        val entries = listOf(entry(latest, 70.0), entry(latest - 2 * DAY, 71.0))
        val data = cartesian(ChartDataProcessor.process(config(), entries, fields, NOW))

        assertEquals(latest, data.anchoredEndMs)
        assertEquals(7, data.bucketTimestamps.size)
        // Both entries fall inside the anchored week (latest and latest-2d).
        assertEquals(2, data.series.single().points.size)
    }

    @Test
    fun `entries without chartable values do not affect anchoring`() {
        val chartable = NOW - 20 * DAY
        val entries = listOf(
            entry(NOW - 2 * DAY),          // recent but has no Weight value
            entry(chartable, 70.0)
        )
        val data = cartesian(ChartDataProcessor.process(config(), entries, fields, NOW))

        assertEquals(chartable, data.anchoredEndMs)
        assertEquals(1, data.series.single().points.size)
    }

    @Test
    fun `no chartable entries gives insufficient data`() {
        val entries = listOf(entry(NOW - DAY), entry(NOW - 2 * DAY))
        val data = ChartDataProcessor.process(config(), entries, fields, NOW)

        assertEquals(ChartData.InsufficientData, data)
    }

    // ── Stale configurations ──────────────────────────────────────────────────

    @Test
    fun `field retyped away from numeric makes the chart stale`() {
        val entries = listOf(entry(NOW - DAY, 70.0), entry(NOW - 2 * DAY, 71.0))
        val retyped = listOf(
            EventField(id = WEIGHT, name = "Weight", type = FieldType.TEXT),
            EventField(id = MEAL, name = "Meal", type = FieldType.CHOICE)
        )
        // Legacy numeric values still exist, but the field is no longer numeric —
        // staleness takes precedence over rendering the deprecated data.
        val data = ChartDataProcessor.process(config(), entries, retyped, NOW)

        assertEquals(ChartData.StaleConfig, data)
    }

    @Test
    fun `chart referencing a missing field is stale`() {
        val entries = listOf(entry(NOW - DAY, 70.0))
        val data = ChartDataProcessor.process(config(fieldIds = listOf(999L)), entries, fields, NOW)

        assertEquals(ChartData.StaleConfig, data)
    }

    @Test
    fun `pie chart with a non-categorical group field is stale`() {
        val entries = listOf(entry(NOW - DAY, 70.0, meal = "Lunch"))
        // group-by points at the numeric Weight field, not a choice/multi-select.
        val data = ChartDataProcessor.process(
            config(type = ChartType.PIE, fieldIds = listOf(WEIGHT), groupBy = WEIGHT),
            entries, fields, NOW
        )

        assertEquals(ChartData.StaleConfig, data)
    }

    @Test
    fun `valid numeric config is not stale`() {
        val entries = listOf(entry(NOW - DAY, 70.0))
        val data = ChartDataProcessor.process(config(), entries, fields, NOW)

        assertTrue("expected a rendered chart, was $data", data is ChartData.Cartesian)
    }

    @Test
    fun `year view anchors to latest entry month`() {
        val latest = Calendar.getInstance().apply {
            timeInMillis = NOW
            add(Calendar.MONTH, -14)
        }.timeInMillis
        val entries = listOf(entry(latest, 70.0))
        val data = cartesian(ChartDataProcessor.process(config(days = 365), entries, fields, NOW))

        assertEquals(latest, data.anchoredEndMs)
        assertEquals(12, data.bucketTimestamps.size)
        // The anchor month is the final bucket of the anchored year.
        assertEquals(11, data.series.single().points.single().bucketIndex)
    }

    @Test
    fun `pie anchors to latest entry`() {
        val latest = NOW - 30 * DAY
        val entries = listOf(entry(latest, 5.0, meal = "Lunch"))
        val cfg = config(type = ChartType.PIE, days = 7, groupBy = MEAL)
        val data = ChartDataProcessor.process(cfg, entries, fields, NOW)

        assertTrue(data is ChartData.Pie)
        data as ChartData.Pie
        assertEquals(latest, data.anchoredEndMs)
        assertEquals(listOf("Lunch"), data.slices.map { it.label })
    }

    @Test
    fun `pie within window is not anchored`() {
        val entries = listOf(entry(NOW - DAY, 5.0, meal = "Lunch"))
        val cfg = config(type = ChartType.PIE, days = 7, groupBy = MEAL)
        val data = ChartDataProcessor.process(cfg, entries, fields, NOW) as ChartData.Pie

        assertNull(data.anchoredEndMs)
    }

    // ── ALL-range binning ─────────────────────────────────────────────────────

    @Test
    fun `all range with single entry yields one bucket`() {
        val entries = listOf(entry(NOW - 100 * DAY, 70.0))
        val data = cartesian(ChartDataProcessor.process(config(days = null), entries, fields, NOW))

        assertNull(data.anchoredEndMs)
        assertEquals(1, data.bucketTimestamps.size)
        assertEquals(0, data.series.single().points.single().bucketIndex)
    }

    @Test
    fun `all range bins daily for short histories`() {
        val entries = (0..5).map { entry(NOW - it * DAY, 70.0 + it) }
        val data = cartesian(ChartDataProcessor.process(config(days = null), entries, fields, NOW))

        assertEquals(6, data.bucketTimestamps.size)
        assertEquals(6, data.series.single().points.size)
    }

    @Test
    fun `all range bins monthly for long histories`() {
        val entries = listOf(entry(NOW - 300 * DAY, 70.0), entry(NOW, 75.0))
        val data = cartesian(ChartDataProcessor.process(config(days = null), entries, fields, NOW))

        assertEquals(11, data.bucketTimestamps.size)
    }

    // ── Existing behavior kept ────────────────────────────────────────────────

    @Test
    fun `bucket values aggregate with mean by default`() {
        val entries = listOf(entry(NOW - HOUR, 10.0), entry(NOW - 2 * HOUR, 20.0))
        val data = cartesian(ChartDataProcessor.process(config(), entries, fields, NOW))

        assertEquals(15.0, data.series.single().points.single().value, 0.001)
    }

    @Test
    fun `field missing from event definition is stale`() {
        val entries = listOf(entry(NOW - DAY).copy(fieldValues = mapOf(MISSING to FieldValue.Numeric(1.0))))
        val data = ChartDataProcessor.process(config(fieldIds = listOf(MISSING)), entries, fields, NOW)

        // A config pointing at a field that no longer exists is a stale config,
        // surfaced so the user can edit/delete it (previously a silent Empty).
        assertEquals(ChartData.StaleConfig, data)
    }

    @Test
    fun `series and pie carry the field unit`() {
        val unitFields = listOf(
            EventField(id = WEIGHT, name = "Weight", type = FieldType.NUMERIC, unit = "kg"),
            EventField(id = MEAL, name = "Meal", type = FieldType.CHOICE)
        )
        val entries = listOf(entry(NOW - DAY, 70.0, meal = "Lunch"))

        val line = cartesian(ChartDataProcessor.process(config(), entries, unitFields, NOW))
        assertEquals("kg", line.series.single().unit)

        val pie = ChartDataProcessor.process(
            config(type = ChartType.PIE, groupBy = MEAL), entries, unitFields, NOW
        ) as ChartData.Pie
        assertEquals("kg", pie.unit)
    }

    @Test
    fun `show units off blanks series and pie units`() {
        val unitFields = listOf(
            EventField(id = WEIGHT, name = "Weight", type = FieldType.NUMERIC, unit = "kg"),
            EventField(id = MEAL, name = "Meal", type = FieldType.CHOICE)
        )
        val entries = listOf(entry(NOW - DAY, 70.0, meal = "Lunch"))

        val line = cartesian(
            ChartDataProcessor.process(config().copy(showUnits = false), entries, unitFields, NOW)
        )
        assertEquals("", line.series.single().unit)

        val pie = ChartDataProcessor.process(
            config(type = ChartType.PIE, groupBy = MEAL).copy(showUnits = false),
            entries, unitFields, NOW
        ) as ChartData.Pie
        assertEquals("", pie.unit)
    }

    @Test
    fun `empty entry list gives insufficient data`() {
        val data = ChartDataProcessor.process(config(), emptyList(), fields, NOW)
        assertEquals(ChartData.InsufficientData, data)
    }

    @Test
    fun `anchored timestamps cover the latest entry`() {
        val latest = NOW - 10 * DAY
        val data = cartesian(
            ChartDataProcessor.process(config(), listOf(entry(latest, 70.0)), fields, NOW)
        )
        assertNotNull(data.bucketTimestamps.find { it in (latest - DAY)..(latest + DAY) })
    }

    // ── Heatmap ────────────────────────────────────────────────────────────────

    private val HABIT = 3L

    private val heatmapFields = listOf(
        EventField(id = WEIGHT, name = "Weight", type = FieldType.NUMERIC, unit = "kg"),
        EventField(id = HABIT, name = "Exercised", type = FieldType.BOOLEAN),
        EventField(id = MEAL, name = "Meal", type = FieldType.CHOICE)
    )

    private fun heatmapConfig(
        fieldIds: List<Long> = listOf(WEIGHT),
        days: Int? = 365,
        agg: AggregationStrategy = AggregationStrategy.MEAN,
        showUnits: Boolean = true
    ) = ChartConfig(
        id = "h1",
        eventTypeId = 1L,
        type = ChartType.HEATMAP,
        numericFieldIds = fieldIds,
        timeRangeDays = days,
        aggregation = agg,
        showUnits = showUnits
    )

    private fun boolEntry(createdAt: Long, value: Boolean) = EventEntry(
        id = createdAt,
        eventTypeId = 1L,
        fieldValues = mapOf(HABIT to FieldValue.Bool(value)),
        createdAt = createdAt,
        updatedAt = createdAt
    )

    private fun heatmap(data: ChartData): ChartData.Heatmap {
        assertTrue("expected Heatmap, was $data", data is ChartData.Heatmap)
        return data as ChartData.Heatmap
    }

    private fun midnight(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun ChartData.Heatmap.dayAt(ms: Long): ChartData.Heatmap.Day? {
        val target = midnight(ms)
        return columns.flatMap { it.days }.firstOrNull { it != null && it.dateMs == target }
    }

    @Test
    fun `heatmap aggregates a day with the chosen strategy`() {
        val entries = listOf(entry(NOW, 20.0), entry(NOW - 3 * HOUR, 10.0))

        val mean = heatmap(ChartDataProcessor.process(heatmapConfig(), entries, heatmapFields, NOW))
        assertEquals(15.0, mean.dayAt(NOW)!!.value!!, 0.001)

        val sum = heatmap(
            ChartDataProcessor.process(
                heatmapConfig(agg = AggregationStrategy.SUM), entries, heatmapFields, NOW
            )
        )
        assertEquals(30.0, sum.dayAt(NOW)!!.value!!, 0.001)
    }

    @Test
    fun `heatmap latest picks the last entry of the day`() {
        // Earlier value 10 at 09:00, later value 20 at noon — LATEST is 20.
        val entries = listOf(entry(NOW, 20.0), entry(NOW - 3 * HOUR, 10.0))
        val data = heatmap(
            ChartDataProcessor.process(
                heatmapConfig(agg = AggregationStrategy.LATEST), entries, heatmapFields, NOW
            )
        )
        assertEquals(20.0, data.dayAt(NOW)!!.value!!, 0.001)
    }

    @Test
    fun `heatmap counts contributing entries per day`() {
        val entries = listOf(entry(NOW, 20.0), entry(NOW - 2 * HOUR, 10.0), entry(NOW - 4 * HOUR, 30.0))
        val data = heatmap(ChartDataProcessor.process(heatmapConfig(), entries, heatmapFields, NOW))
        assertEquals(3, data.dayAt(NOW)!!.entryCount)
    }

    @Test
    fun `heatmap converts yes-no to one and zero before aggregating`() {
        val entries = listOf(
            boolEntry(NOW, true), boolEntry(NOW - HOUR, true), boolEntry(NOW - 2 * HOUR, false)
        )
        val sum = heatmap(
            ChartDataProcessor.process(
                heatmapConfig(fieldIds = listOf(HABIT), agg = AggregationStrategy.SUM),
                entries, heatmapFields, NOW
            )
        )
        assertEquals(2.0, sum.dayAt(NOW)!!.value!!, 0.001)
        assertEquals(3, sum.dayAt(NOW)!!.entryCount)
    }

    @Test
    fun `heatmap distinguishes a zero value from a missing day`() {
        // One entry today with value 0; yesterday has no entry at all.
        val data = heatmap(
            ChartDataProcessor.process(heatmapConfig(), listOf(entry(NOW, 0.0)), heatmapFields, NOW)
        )
        assertEquals(0.0, data.dayAt(NOW)!!.value!!, 0.001) // zero is a real value
        assertNull(data.dayAt(NOW - DAY)!!.value)           // missing day is null, and in-grid
    }

    @Test
    fun `heatmap flags diverging scale only when a day is negative`() {
        val positive = heatmap(
            ChartDataProcessor.process(heatmapConfig(), listOf(entry(NOW, 5.0)), heatmapFields, NOW)
        )
        assertTrue(!positive.diverging)

        val negative = heatmap(
            ChartDataProcessor.process(heatmapConfig(), listOf(entry(NOW, -5.0)), heatmapFields, NOW)
        )
        assertTrue(negative.diverging)
    }

    @Test
    fun `heatmap reports min and max over day values`() {
        val entries = listOf(entry(NOW, 5.0), entry(NOW - 2 * DAY, -3.0), entry(NOW - 4 * DAY, 9.0))
        val data = heatmap(ChartDataProcessor.process(heatmapConfig(), entries, heatmapFields, NOW))
        assertEquals(-3.0, data.minValue, 0.001)
        assertEquals(9.0, data.maxValue, 0.001)
        assertEquals(3, data.daysWithData)
    }

    @Test
    fun `heatmap columns are always full weeks ending at the anchor day`() {
        val data = heatmap(
            ChartDataProcessor.process(heatmapConfig(days = 365), listOf(entry(NOW, 70.0)), heatmapFields, NOW)
        )
        // Every column is a 7-slot week.
        assertTrue(data.columns.all { it.days.size == 7 })
        // A year of weeks.
        assertTrue("columns=${data.columns.size}", data.columns.size in 52..54)
        // The latest in-grid day is the anchor day (today).
        val lastDay = data.columns.flatMap { it.days }.filterNotNull().maxOf { it.dateMs }
        assertEquals(midnight(NOW), lastDay)
        assertEquals(7, data.weekdayLabels.size)
        assertTrue(data.monthLabels.isNotEmpty())
    }

    @Test
    fun `heatmap all-range ends at the latest entry not today`() {
        val latest = NOW - 100 * DAY
        val entries = listOf(entry(NOW - 300 * DAY, 70.0), entry(latest, 72.0))
        val data = heatmap(
            ChartDataProcessor.process(heatmapConfig(days = null), entries, heatmapFields, NOW)
        )
        val lastDay = data.columns.flatMap { it.days }.filterNotNull().maxOf { it.dateMs }
        assertEquals(midnight(latest), lastDay)
        assertNotNull(data.dayAt(NOW - 300 * DAY)!!.value)
        assertNull(data.anchoredEndMs) // ALL spans the data, no "as of" caption
    }

    @Test
    fun `heatmap honors the field unit and the show-units toggle`() {
        val withUnit = heatmap(
            ChartDataProcessor.process(heatmapConfig(), listOf(entry(NOW, 70.0)), heatmapFields, NOW)
        )
        assertEquals("kg", withUnit.unit)

        val hidden = heatmap(
            ChartDataProcessor.process(
                heatmapConfig(showUnits = false), listOf(entry(NOW, 70.0)), heatmapFields, NOW
            )
        )
        assertEquals("", hidden.unit)

        // Yes/No fields have no unit even when units are shown.
        val boolean = heatmap(
            ChartDataProcessor.process(
                heatmapConfig(fieldIds = listOf(HABIT)), listOf(boolEntry(NOW, true)), heatmapFields, NOW
            )
        )
        assertEquals("", boolean.unit)
    }

    @Test
    fun `heatmap is valid for numeric and yes-no fields`() {
        assertTrue(
            ChartDataProcessor.process(heatmapConfig(), listOf(entry(NOW, 70.0)), heatmapFields, NOW)
                is ChartData.Heatmap
        )
        assertTrue(
            ChartDataProcessor.process(
                heatmapConfig(fieldIds = listOf(HABIT)), listOf(boolEntry(NOW, true)), heatmapFields, NOW
            ) is ChartData.Heatmap
        )
    }

    @Test
    fun `heatmap on a text or choice field is stale`() {
        val choice = ChartDataProcessor.process(
            heatmapConfig(fieldIds = listOf(MEAL)), listOf(entry(NOW, 70.0, meal = "Lunch")), heatmapFields, NOW
        )
        assertEquals(ChartData.StaleConfig, choice)
    }

    // ── Window ↔ display alignment ────────────────────────────────────────────
    // The entry filter must admit exactly the span the chart lays out, no more
    // (invisible entries holding the anchor / skewing heatmap scales) and no
    // less (entries clipped out of the oldest bucket).

    @Test
    fun `month window admits exactly the bucketed four weeks plus today`() {
        val windowStart = midnight(NOW) - 4 * 7 * DAY // oldest bucket start
        val entries = listOf(
            entry(windowStart, 10.0),     // first instant of the oldest bucket
            entry(windowStart - 1, 99.0), // 1ms earlier: belongs to no bucket
            entry(NOW - DAY, 5.0)
        )
        val data = cartesian(ChartDataProcessor.process(config(days = 30), entries, fields, NOW))

        assertNull(data.anchoredEndMs)
        val points = data.series.single().points
        assertEquals(listOf(0, 3), points.map { it.bucketIndex })
        // The out-of-window entry aggregates into nothing (bucket 0 holds 10.0
        // alone, not a 10/99 mean).
        assertEquals(10.0, points.first().value, 0.001)
    }

    @Test
    fun `week window starts at the oldest daily bucket`() {
        val windowStart = midnight(NOW) - 6 * DAY
        val entries = listOf(
            entry(windowStart, 10.0),
            entry(windowStart - 1, 99.0),
            entry(NOW - HOUR, 5.0)
        )
        val data = cartesian(ChartDataProcessor.process(config(days = 7), entries, fields, NOW))

        val points = data.series.single().points
        assertEquals(listOf(0, 6), points.map { it.bucketIndex })
        assertEquals(10.0, points.first().value, 0.001)
    }

    @Test
    fun `year window admits exactly the twelve bucketed months`() {
        // NOW is May 20 2026, so the oldest bucket is June 2025.
        val june1 = Calendar.getInstance().apply {
            set(2025, Calendar.JUNE, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val entries = listOf(
            entry(june1, 10.0),
            entry(june1 - 1, 99.0), // May 31 2025: within 365*24h of NOW, unbucketed
            entry(NOW - DAY, 5.0)
        )
        val data = cartesian(ChartDataProcessor.process(config(days = 365), entries, fields, NOW))

        assertNull(data.anchoredEndMs)
        val points = data.series.single().points
        assertEquals(listOf(0, 11), points.map { it.bucketIndex })
        assertEquals(10.0, points.first().value, 0.001)
    }

    @Test
    fun `data just outside the month buckets slides the window back`() {
        // 29d1h ago: inside the old 30*24h filter but before the oldest bucket,
        // which used to hold the anchor at now and render an empty chart.
        val only = NOW - 29 * DAY - HOUR
        val data = cartesian(
            ChartDataProcessor.process(config(days = 30), listOf(entry(only, 70.0)), fields, NOW)
        )

        assertEquals(only, data.anchoredEndMs)
        assertEquals(70.0, data.series.single().points.single().value, 0.001)
    }

    @Test
    fun `year data older than the bucketed months slides the window back`() {
        // ~360 days ago (May 25 2025): within 365*24h of NOW but before the
        // June 2025 start of the bucketed year.
        val only = NOW - 360 * DAY
        val data = cartesian(
            ChartDataProcessor.process(config(days = 365), listOf(entry(only, 70.0)), fields, NOW)
        )

        assertEquals(only, data.anchoredEndMs)
        assertEquals(11, data.series.single().points.single().bucketIndex)
    }

    @Test
    fun `day chart slides back to the most recent active day`() {
        // Only entry is yesterday 22:00 — inside the old 24h filter but before
        // today's hourly buckets.
        val only = NOW - 14 * HOUR
        val data = cartesian(
            ChartDataProcessor.process(config(days = 1), listOf(entry(only, 70.0)), fields, NOW)
        )

        assertEquals(only, data.anchoredEndMs)
        assertEquals(24, data.bucketTimestamps.size)
        assertEquals(70.0, data.series.single().points.single().value, 0.001)
    }

    @Test
    fun `leap year window keeps the first day of the oldest month`() {
        // Mar 31 2024 buckets Apr 2023..Mar 2024 — 366 days across the leap
        // February, one more than the old 365*24h filter admitted, so entries
        // on Apr 1 2023 were clipped out of the bucket they belong to.
        val anchor = Calendar.getInstance().apply {
            set(2024, Calendar.MARCH, 31, 23, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val aprilNoon = Calendar.getInstance().apply {
            set(2023, Calendar.APRIL, 1, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val entries = listOf(entry(aprilNoon, 10.0), entry(anchor - DAY, 5.0))
        val data = cartesian(ChartDataProcessor.process(config(days = 365), entries, fields, anchor))

        assertEquals(listOf(0, 11), data.series.single().points.map { it.bucketIndex })
        assertEquals(10.0, data.series.single().points.first().value, 0.001)
    }

    @Test
    fun `heatmap scale ignores entries older than its day grid`() {
        // 29d18h ago falls on the 30th day back — outside a 30-day grid ending
        // today — but inside the old 30*24h filter, where it inflated max and
        // the day count without a legitimate cell.
        val outside = NOW - 29 * DAY - 18 * HOUR
        val entries = listOf(entry(outside, 100.0), entry(NOW, 5.0), entry(NOW - 2 * DAY, 9.0))
        val data = heatmap(
            ChartDataProcessor.process(heatmapConfig(days = 30), entries, heatmapFields, NOW)
        )

        assertEquals(9.0, data.maxValue, 0.001)
        assertEquals(5.0, data.minValue, 0.001)
        assertEquals(2, data.daysWithData)
    }

    @Test
    fun `pie covers the last calendar days of its range`() {
        val edgeDay = Calendar.getInstance().apply {
            timeInMillis = midnight(NOW)
            add(Calendar.DAY_OF_YEAR, -29)
        }.timeInMillis
        val entries = listOf(
            entry(edgeDay, 3.0, meal = "Edge"),                     // first admitted instant
            entry(NOW - 29 * DAY - 18 * HOUR, 100.0, meal = "Old"), // 30th day back: out
            entry(NOW - DAY, 5.0, meal = "Lunch")
        )
        val cfg = config(type = ChartType.PIE, days = 30, groupBy = MEAL)
        val data = ChartDataProcessor.process(cfg, entries, fields, NOW) as ChartData.Pie

        assertEquals(setOf("Edge", "Lunch"), data.slices.map { it.label }.toSet())
    }
}
