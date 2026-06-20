package com.lifelog.app.domain

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
}
