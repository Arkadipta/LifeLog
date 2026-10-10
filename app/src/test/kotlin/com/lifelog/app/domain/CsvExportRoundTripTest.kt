package com.lifelog.app.domain

import com.lifelog.app.domain.csv.CellConversion
import com.lifelog.app.domain.csv.CsvDateTimeParser
import com.lifelog.app.domain.csv.CsvFieldInference
import com.lifelog.app.domain.csv.CsvParser
import com.lifelog.app.domain.csv.CsvWriter
import com.lifelog.app.domain.csv.ParsedCsv
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

/**
 * Pins the export→import contract: whatever [CsvWriter] writes, the import
 * wizard's pieces ([CsvParser], [CsvDateTimeParser], [CsvFieldInference]) must
 * read back without losing data — including field names with commas/quotes,
 * values with embedded newlines, and multi-select tags.
 */
class CsvExportRoundTripTest {

    private val fields = listOf(
        EventField(id = 1, name = "Sets, Reps", type = FieldType.NUMERIC),
        EventField(id = 2, name = "He said \"quote\"", type = FieldType.TEXT),
        EventField(id = 3, name = "Fasting", type = FieldType.BOOLEAN),
        EventField(id = 4, name = "Meal Type", type = FieldType.CHOICE, options = listOf("Breakfast", "Lunch")),
        EventField(id = 5, name = "Sides", type = FieldType.MULTI_SELECT, options = listOf("Fries", "Salad"))
    )
    private val event = EventType(id = 7, name = "Meal", fields = fields)

    private fun export(vararg entries: EventEntry): ParsedCsv {
        val sb = StringBuilder()
        CsvWriter.writeEntries(sb, event, entries.toList())
        return CsvParser.parse(sb.toString())
    }

    private fun entry(values: Map<Long, FieldValue>, note: String = "", at: Long = WHOLE_SECOND) =
        EventEntry(eventTypeId = 7, fieldValues = values, note = note, createdAt = at, updatedAt = at)

    // ── Headers ────────────────────────────────────────────────────────────────

    @Test
    fun `header keeps field names verbatim and drops the internal id`() {
        val parsed = export(entry(emptyMap()))

        assertEquals(
            listOf("created_at", "updated_at", "note") + fields.map { it.name },
            parsed.headers
        )
        assertFalse(parsed.headers.contains("id"))
    }

    @Test
    fun `a user field literally named id still gets its column`() {
        val idField = EventField(id = 9, name = "id", type = FieldType.TEXT)
        val sb = StringBuilder()
        CsvWriter.writeEntries(
            sb,
            EventType(id = 7, name = "Badges", fields = listOf(idField)),
            listOf(entry(mapOf(9L to FieldValue.Text("badge-42"))))
        )
        val parsed = CsvParser.parse(sb.toString())

        assertEquals(listOf("created_at", "updated_at", "note", "id"), parsed.headers)
        assertEquals("badge-42", parsed.rows[0][3])
    }

    // ── Cell fidelity ──────────────────────────────────────────────────────────

    @Test
    fun `commas quotes and newlines inside values survive`() {
        val nastyNote = "line one\nline two, with \"quotes\" and \r inside"
        val nastyText = "a,b\n\"c\""
        val parsed = export(
            entry(mapOf(2L to FieldValue.Text(nastyText)), note = nastyNote)
        )

        assertEquals(1, parsed.dataRowCount)
        assertEquals(nastyNote, parsed.rows[0][2])
        assertEquals(nastyText, parsed.rows[0][4])
    }

    @Test
    fun `multi select cells split back into the original tags`() {
        val tags = listOf("Fries", "Soup, hot", "Salad")
        val parsed = export(entry(mapOf(5L to FieldValue.MultiSelect(tags))))

        val cell = parsed.rows[0][7]
        val converted = CsvFieldInference.convertCell(cell, FieldType.MULTI_SELECT)
        assertEquals(FieldValue.MultiSelect(tags), (converted as CellConversion.Converted).value)
    }

    @Test
    fun `boolean and numeric display forms convert back to typed values`() {
        val parsed = export(
            entry(
                mapOf(
                    1L to FieldValue.Numeric(12.0),
                    3L to FieldValue.Bool(true)
                )
            ),
            entry(
                mapOf(
                    1L to FieldValue.Numeric(2.5),
                    3L to FieldValue.Bool(false)
                )
            )
        )

        assertEquals(FieldType.NUMERIC, CsvFieldInference.infer(parsed.column(3)))
        assertEquals(FieldType.BOOLEAN, CsvFieldInference.infer(parsed.column(5)))

        assertEquals("12", parsed.rows[0][3])
        assertEquals(
            FieldValue.Numeric(12.0),
            (CsvFieldInference.convertCell(parsed.rows[0][3], FieldType.NUMERIC) as CellConversion.Converted).value
        )
        assertEquals(
            FieldValue.Numeric(2.5),
            (CsvFieldInference.convertCell(parsed.rows[1][3], FieldType.NUMERIC) as CellConversion.Converted).value
        )
        assertEquals(
            FieldValue.Bool(true),
            (CsvFieldInference.convertCell(parsed.rows[0][5], FieldType.BOOLEAN) as CellConversion.Converted).value
        )
        assertEquals(
            FieldValue.Bool(false),
            (CsvFieldInference.convertCell(parsed.rows[1][5], FieldType.BOOLEAN) as CellConversion.Converted).value
        )
    }

    @Test
    fun `absent values export as blank cells that import skips`() {
        val parsed = export(entry(emptyMap()))

        for (column in 3 until parsed.columnCount) {
            assertEquals("", parsed.rows[0][column])
        }
        assertTrue(
            CsvFieldInference.convertCell(parsed.rows[0][3], FieldType.NUMERIC) is CellConversion.Skipped
        )
    }

    // ── Timestamps ─────────────────────────────────────────────────────────────

    @Test
    fun `timestamps parse back to the same instant`() {
        val parsed = export(entry(emptyMap(), at = WHOLE_SECOND))

        assertEquals(WHOLE_SECOND, CsvDateTimeParser.parse(parsed.rows[0][0]))
        assertEquals(WHOLE_SECOND, CsvDateTimeParser.parse(parsed.rows[0][1]))
    }

    private companion object {
        /** A fixed local wall-clock instant with no sub-second part (the format has second precision). */
        val WHOLE_SECOND: Long = GregorianCalendar(2026, Calendar.JULY, 4, 18, 30, 45).timeInMillis
    }
}
