package com.lifelog.app.data.db

import com.lifelog.app.data.db.entity.EventEntryEntity
import com.lifelog.app.data.db.entity.EventTypeEntity
import com.lifelog.app.domain.EventFilterUseCase
import com.lifelog.app.domain.model.EventFilterState
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.ui.events.entryListModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Pins what makes the Timeline cheap (P2): a row read out of the database keeps
 * its field values as the stored JSON, and everything a list does *around*
 * drawing a card — filtering by tag, searching notes and event names, grouping
 * days for the date jump — runs without decoding any of it. Only composing the
 * card pays.
 *
 * These are cost assertions, so they are written the only way cost is visible
 * from outside: [isDecodedForTest]. Revert [EventEntryEntity.toRow] to decode up
 * front and every "nothing decoded" case below fails.
 */
class EntryRowLazyDecodeTest {

    private val filterUseCase = EventFilterUseCase()

    private fun type(id: Long, name: String, category: String) = EventTypeEntity(
        id = id,
        name = name,
        description = "",
        category = category,
        colorArgb = 0x112233,
        iconName = "run"
    )

    private fun entity(
        id: Long,
        eventTypeId: Long = 1,
        note: String = "",
        createdAt: Long = 1_700_000_000_000L,
        valuesJson: String = """{"5":{"type":"numeric","value":12.0}}"""
    ) = EventEntryEntity(
        id = id,
        eventTypeId = eventTypeId,
        fieldValuesJson = valuesJson,
        note = note,
        createdAt = createdAt,
        updatedAt = createdAt
    )

    // ── The values are there when asked for ──────────────────────────────────

    @Test
    fun `a row decodes the same values toDomain would, on first read`() {
        val entity = entity(id = 1)
        val row = entity.toRow(type(1, "Run", "Fitness"))

        assertFalse("untouched row should not have decoded yet", row.isDecodedForTest())
        assertEquals(mapOf(5L to FieldValue.Numeric(12.0)), row.fieldValues)
        assertTrue(row.isDecodedForTest())
        assertEquals(entity.toDomain(type(1, "Run", "Fitness")).fieldValues, row.fieldValues)
    }

    @Test
    fun `a row carries the same type columns toDomain resolves`() {
        val type = type(3, "Meal", "Food")
        val row = entity(id = 2, eventTypeId = 3).toRow(type)
        val entry = entity(id = 2, eventTypeId = 3).toDomain(type)

        assertEquals(entry.id, row.id)
        assertEquals(entry.eventTypeId, row.eventTypeId)
        assertEquals(entry.eventTypeName, row.eventTypeName)
        assertEquals(entry.eventTypeCategory, row.eventTypeCategory)
        assertEquals(entry.eventTypeColor, row.eventTypeColor)
        assertEquals(entry.eventTypeIcon, row.eventTypeIcon)
        assertEquals(entry.note, row.note)
        assertEquals(entry.createdAt, row.createdAt)
    }

    @Test
    fun `a row for a deleted type falls back exactly like toDomain does`() {
        val row = entity(id = 4).toRow(null)
        val entry = entity(id = 4).toDomain(null)

        assertEquals(entry.eventTypeName, row.eventTypeName)
        assertEquals(entry.eventTypeCategory, row.eventTypeCategory)
        assertEquals(entry.eventTypeColor, row.eventTypeColor)
        assertEquals(entry.eventTypeIcon, row.eventTypeIcon)
        assertEquals(EventType.DEFAULT_COLOR, row.eventTypeColor)
    }

    @Test
    fun `corrupt values still degrade pair-by-pair when decoded late`() {
        // Same salvage policy as the eager mapper (A3) — deferring the decode
        // must not defer a crash to composition time instead.
        val json = """{"5":{"type":"numeric","value":12.0},"6":{"type":"from_the_future"},"oops":1}"""
        val row = entity(id = 5, valuesJson = json).toRow(null)

        assertEquals(mapOf(5L to FieldValue.Numeric(12.0)), row.fieldValues)
        assertEquals(entity(id = 5, valuesJson = json).toDomain().fieldValues, row.fieldValues)
    }

    @Test
    fun `an unparseable column decodes to no values instead of throwing`() {
        val row = entity(id = 6, valuesJson = "{not json at all").toRow(null)
        assertEquals(emptyMap<Long, FieldValue>(), row.fieldValues)
    }

    // ── Listing work never decodes ───────────────────────────────────────────

    private fun timelineRows() = listOf(
        entity(id = 1, eventTypeId = 1, note = "morning 5k").toRow(type(1, "Run", "Fitness")),
        entity(id = 2, eventTypeId = 2, note = "pasta").toRow(type(2, "Meal", "Food")),
        entity(id = 3, eventTypeId = 1, note = "hill repeats").toRow(type(1, "Run", "Fitness"))
    )

    @Test
    fun `filtering by tag decodes nothing`() {
        val rows = timelineRows()
        val filtered = filterUseCase.filterEntries(
            rows, "", EventFilterState(selectedTags = setOf("Fitness"))
        )

        assertEquals(listOf(1L, 3L), filtered.map { it.id })
        assertTrue("tag filter must not decode", rows.none { it.isDecodedForTest() })
    }

    @Test
    fun `searching notes and event names decodes nothing`() {
        val rows = timelineRows()

        assertEquals(listOf(2L), filterUseCase.filterEntries(rows, "pasta", EventFilterState()).map { it.id })
        assertEquals(listOf(1L, 3L), filterUseCase.filterEntries(rows, "run", EventFilterState()).map { it.id })
        assertTrue("search must not decode", rows.none { it.isDecodedForTest() })
    }

    @Test
    fun `grouping days for the date navigator decodes nothing`() {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            val day = 24 * 60 * 60 * 1000L
            val rows = listOf(
                entity(id = 1, createdAt = 3 * day),
                entity(id = 2, createdAt = 3 * day + 3600_000),
                entity(id = 3, createdAt = 2 * day)
            ).map { it.toRow(type(1, "Run", "Fitness")) }

            val model = entryListModel(rows)

            assertEquals(2, model.days.size)
            assertEquals(2, model.anchors.size)
            assertTrue("day grouping must not decode", rows.none { it.isDecodedForTest() })
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `reading one row's values leaves its neighbours encoded`() {
        val rows = timelineRows()
        rows[1].fieldValues

        assertFalse(rows[0].isDecodedForTest())
        assertTrue(rows[1].isDecodedForTest())
        assertFalse(rows[2].isDecodedForTest())
    }

    // ── Equality is by stored column, not by decode state ────────────────────

    @Test
    fun `two rows from the same database row stay equal after one decodes`() {
        val type = type(1, "Run", "Fitness")
        val a = entity(id = 1).toRow(type)
        val b = entity(id = 1).toRow(type)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        a.fieldValues
        assertEquals("decoding must not change identity", a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `rows differing only in stored values are not equal`() {
        val type = type(1, "Run", "Fitness")
        val a = entity(id = 1, valuesJson = """{"5":{"type":"numeric","value":12.0}}""").toRow(type)
        val b = entity(id = 1, valuesJson = """{"5":{"type":"numeric","value":13.0}}""").toRow(type)

        assertFalse(a == b)
    }
}
