package com.lifelog.app.domain.csv

/**
 * The result of parsing a CSV document: the first row is treated as the header
 * (column names) and everything below it as data rows. Every data row is padded
 * or trimmed to [columnCount] so callers can index by column without bounds
 * checks.
 */
data class ParsedCsv(
    val headers: List<String>,
    val rows: List<List<String>>
) {
    val columnCount: Int get() = headers.size
    val dataRowCount: Int get() = rows.size

    /** All values in [columnIndex] across the data rows (blank-safe). */
    fun column(columnIndex: Int): List<String> =
        rows.map { it.getOrElse(columnIndex) { "" } }
}

/**
 * A small, dependency-free RFC-4180-style CSV reader. Handles quoted fields,
 * escaped quotes (`""`), commas and newlines inside quotes, and either `\n`,
 * `\r\n`, or lone `\r` line endings. A leading UTF-8 BOM is stripped.
 *
 * Cell values are returned verbatim (not trimmed) so no data is silently
 * altered; inference and conversion trim where it matters. Header names are
 * trimmed since they are used as labels.
 */
object CsvParser {

    private const val BOM = '﻿'

    fun parse(text: String): ParsedCsv {
        val clean = if (text.startsWith(BOM)) text.substring(1) else text
        val records = tokenize(clean)
        if (records.isEmpty()) return ParsedCsv(emptyList(), emptyList())

        val headers = records.first().map { it.trim() }
        if (headers.all { it.isBlank() }) return ParsedCsv(emptyList(), emptyList())

        val rows = records.drop(1)
            // A blank trailing line (common when files end with a newline) tokenizes
            // to a single empty field — drop those so they don't count as data.
            .filterNot { it.size == 1 && it[0].isBlank() }
            .map { normalizeWidth(it, headers.size) }

        return ParsedCsv(headers, rows)
    }

    private fun normalizeWidth(row: List<String>, width: Int): List<String> = when {
        row.size == width -> row
        row.size > width -> row.subList(0, width)
        else -> row + List(width - row.size) { "" }
    }

    private fun tokenize(text: String): List<List<String>> {
        val records = mutableListOf<List<String>>()
        var record = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0

        fun endField() {
            record.add(field.toString())
            field.setLength(0)
        }
        fun endRecord() {
            endField()
            records.add(record)
            record = mutableListOf()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        field.append('"'); i++
                    }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> endField()
                c == '\r' -> {
                    endRecord()
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                }
                c == '\n' -> endRecord()
                else -> field.append(c)
            }
            i++
        }
        // Flush the final record unless the text ended exactly on a newline
        // (in which case the trailing empty record is meaningless).
        if (field.isNotEmpty() || record.isNotEmpty()) endRecord()
        return records
    }
}
