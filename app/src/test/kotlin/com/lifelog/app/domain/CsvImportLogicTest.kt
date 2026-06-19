package com.lifelog.app.domain

import com.lifelog.app.domain.csv.CellConversion
import com.lifelog.app.domain.csv.CsvDateTimeParser
import com.lifelog.app.domain.csv.CsvFieldInference
import com.lifelog.app.domain.csv.CsvParser
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

class CsvImportLogicTest {

    // ── CsvParser ──────────────────────────────────────────────────────────────

    @Test
    fun parser_readsHeaderAndRows() {
        val parsed = CsvParser.parse("name,age\nAlice,30\nBob,25")
        assertEquals(listOf("name", "age"), parsed.headers)
        assertEquals(2, parsed.dataRowCount)
        assertEquals(listOf("Alice", "30"), parsed.rows[0])
        assertEquals(listOf("Bob", "25"), parsed.rows[1])
    }

    @Test
    fun parser_handlesQuotedCommasAndNewlines() {
        val csv = "name,note\n\"Smith, John\",\"line1\nline2\"\nplain,\"say \"\"hi\"\"\""
        val parsed = CsvParser.parse(csv)
        assertEquals(2, parsed.dataRowCount)
        assertEquals(listOf("Smith, John", "line1\nline2"), parsed.rows[0])
        assertEquals(listOf("plain", "say \"hi\""), parsed.rows[1])
    }

    @Test
    fun parser_handlesCrlfAndTrailingNewlineAndPadsShortRows() {
        val parsed = CsvParser.parse("a,b,c\r\n1,2,3\r\nx,y\r\n")
        assertEquals(2, parsed.dataRowCount)
        assertEquals(listOf("1", "2", "3"), parsed.rows[0])
        // Short row is padded out to the header width
        assertEquals(listOf("x", "y", ""), parsed.rows[1])
    }

    @Test
    fun parser_stripsBomAndTrimsHeaders() {
        val parsed = CsvParser.parse("﻿ when , value \n2020-01-01,5")
        assertEquals(listOf("when", "value"), parsed.headers)
        assertEquals(1, parsed.dataRowCount)
    }

    @Test
    fun parser_headerOnlyHasNoDataRows() {
        val parsed = CsvParser.parse("a,b,c\n")
        assertEquals(3, parsed.columnCount)
        assertEquals(0, parsed.dataRowCount)
    }

    @Test
    fun parser_emptyInputIsEmpty() {
        val parsed = CsvParser.parse("")
        assertEquals(0, parsed.columnCount)
        assertEquals(0, parsed.dataRowCount)
    }

    // ── CsvDateTimeParser ────────────────────────────────────────────────────────

    private fun localMillis(
        year: Int, month: Int, day: Int,
        hour: Int = 0, minute: Int = 0, second: Int = 0
    ): Long = GregorianCalendar(year, month - 1, day, hour, minute, second).apply {
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun date_parsesIsoWithSeconds() {
        assertEquals(localMillis(2024, 3, 14, 9, 30, 15), CsvDateTimeParser.parse("2024-03-14T09:30:15"))
    }

    @Test
    fun date_parsesIsoSpaceSeparatedAndDateOnly() {
        assertEquals(localMillis(2024, 3, 14, 9, 30, 0), CsvDateTimeParser.parse("2024-03-14 09:30"))
        assertEquals(localMillis(2024, 3, 14), CsvDateTimeParser.parse("2024-03-14"))
    }

    @Test
    fun date_parsesUsSlashFormat() {
        assertEquals(localMillis(2024, 3, 14), CsvDateTimeParser.parse("03/14/2024"))
    }

    @Test
    fun date_parsesEpochSecondsAndMillis() {
        assertEquals(1_700_000_000_000L, CsvDateTimeParser.parse("1700000000000"))
        assertEquals(1_700_000_000_000L, CsvDateTimeParser.parse("1700000000"))
    }

    @Test
    fun date_parsesZuluAsUtc() {
        // 2024-01-01T00:00:00Z == 1704067200000 epoch millis regardless of device zone
        assertEquals(1_704_067_200_000L, CsvDateTimeParser.parse("2024-01-01T00:00:00Z"))
    }

    @Test
    fun date_rejectsGarbageAndBlank() {
        assertNull(CsvDateTimeParser.parse("not a date"))
        assertNull(CsvDateTimeParser.parse(""))
        assertNull(CsvDateTimeParser.parse("2024-13-45"))
    }

    @Test
    fun date_requiresFullMatch() {
        // Trailing junk must not be silently accepted by a partial pattern match
        assertNull(CsvDateTimeParser.parse("2024-03-14T09:30:15xyz"))
    }

    // ── CsvFieldInference ────────────────────────────────────────────────────────

    @Test
    fun infer_numericColumn() {
        assertEquals(FieldType.NUMERIC, CsvFieldInference.infer(listOf("1", "2.5", "-3", "")))
    }

    @Test
    fun infer_booleanColumnFromWords() {
        assertEquals(FieldType.BOOLEAN, CsvFieldInference.infer(listOf("true", "false", "yes", "no")))
    }

    @Test
    fun infer_zeroOneColumnIsBooleanNotNumeric() {
        // Per spec, a column of only 0/1 is Yes/No, not a number
        assertEquals(FieldType.BOOLEAN, CsvFieldInference.infer(listOf("0", "1", "1", "0")))
    }

    @Test
    fun infer_mixedColumnIsText() {
        assertEquals(FieldType.TEXT, CsvFieldInference.infer(listOf("12", "hello", "3")))
    }

    @Test
    fun infer_emptyColumnIsText() {
        assertEquals(FieldType.TEXT, CsvFieldInference.infer(listOf("", "  ")))
    }

    @Test
    fun infer_neverReturnsChoiceOrMultiSelect() {
        // Repeated categorical values must NOT auto-become Single-Select/Tags
        val type = CsvFieldInference.infer(listOf("red", "green", "red", "blue", "green"))
        assertEquals(FieldType.TEXT, type)
    }

    @Test
    fun options_choiceCollectsDistinctValues() {
        assertEquals(
            listOf("red", "green", "blue"),
            CsvFieldInference.choiceOptions(listOf("red", "green", "red", "", "blue", "green"))
        )
    }

    @Test
    fun options_tagsSplitDelimitedCells() {
        assertEquals(
            listOf("a", "b", "c"),
            CsvFieldInference.tagOptions(listOf("a;b", "b|c", "a"))
        )
    }

    // ── convertCell ──────────────────────────────────────────────────────────────

    @Test
    fun convert_numericOk() {
        val r = CsvFieldInference.convertCell("42.5", FieldType.NUMERIC)
        assertTrue(r is CellConversion.Converted)
        assertEquals(FieldValue.Numeric(42.5), r.value)
    }

    @Test
    fun convert_numericFallsBackToText() {
        val r = CsvFieldInference.convertCell("n/a", FieldType.NUMERIC)
        assertTrue(r is CellConversion.FellBackToText)
        assertEquals(FieldValue.Text("n/a"), r.value)
    }

    @Test
    fun convert_booleanVariants() {
        assertEquals(FieldValue.Bool(true), CsvFieldInference.convertCell("Yes", FieldType.BOOLEAN).value)
        assertEquals(FieldValue.Bool(false), CsvFieldInference.convertCell("0", FieldType.BOOLEAN).value)
    }

    @Test
    fun convert_blankIsSkipped() {
        assertEquals(CellConversion.Skipped, CsvFieldInference.convertCell("   ", FieldType.NUMERIC))
    }

    @Test
    fun convert_multiSelectSplitsTags() {
        val r = CsvFieldInference.convertCell("a; b |c", FieldType.MULTI_SELECT)
        assertEquals(FieldValue.MultiSelect(listOf("a", "b", "c")), r.value)
    }

    @Test
    fun convert_choiceKeepsWholeCell() {
        val r = CsvFieldInference.convertCell("High Protein", FieldType.CHOICE)
        assertEquals(FieldValue.Choice("High Protein"), r.value)
    }
}
