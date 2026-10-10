package com.lifelog.app.domain.csv

import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.FieldValue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes a per-event CSV that the import wizard can read back: [CsvParser]
 * parses it (commas, quotes, and newlines inside cells are quoted per RFC 4180),
 * [CsvDateTimeParser] understands the timestamp cells, and multi-select cells
 * are `;`-joined so [CsvFieldInference]'s tag splitting recovers the tags.
 *
 * Columns: `created_at`, `updated_at`, `note`, then one column per field with
 * its name verbatim (escaped, never sanitized — a renamed header can't be
 * matched back to its field). Internal row ids are deliberately not exported:
 * they mean nothing outside this device's database and would only come back
 * as a junk numeric column on re-import.
 */
object CsvWriter {

    fun writeEntries(out: Appendable, eventType: EventType, entries: List<EventEntry>) {
        // Fresh per call (shared SimpleDateFormats aren't thread-safe) and
        // Locale.US for ASCII digits, matching what CsvDateTimeParser reads.
        val timestampFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)

        val header = listOf("created_at", "updated_at", "note") + eventType.fields.map { it.name }
        out.append(header.joinToString(",") { escape(it) }).append('\n')

        entries.forEach { entry ->
            val cells = mutableListOf(
                timestampFormat.format(Date(entry.createdAt)),
                timestampFormat.format(Date(entry.updatedAt)),
                entry.note
            )
            eventType.fields.forEach { field ->
                cells.add(entry.fieldValues[field.id]?.toCsvCell() ?: "")
            }
            out.append(cells.joinToString(",") { escape(it) }).append('\n')
        }
    }

    /**
     * Multi-select tags join with `;` — not [FieldValue.displayString]'s `, `,
     * which the wizard's tag delimiters would fuse into one tag. Every other
     * type's display form already converts back cleanly.
     */
    private fun FieldValue.toCsvCell(): String = when (this) {
        is FieldValue.MultiSelect -> values.joinToString("; ")
        else -> displayString()
    }

    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}
