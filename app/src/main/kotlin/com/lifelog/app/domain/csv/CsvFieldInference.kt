package com.lifelog.app.domain.csv

import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue

/**
 * Heuristics for turning a CSV column into a field, and for converting individual
 * cells into [FieldValue]s during import.
 *
 * Inference is deliberately conservative: it only ever proposes [FieldType.NUMERIC],
 * [FieldType.BOOLEAN], or [FieldType.TEXT]. Single-Select and Tags/Multi-Select are
 * never inferred automatically — the user must opt in (per the import spec), at
 * which point [choiceOptions]/[tagOptions] seed the allowed values from the data.
 */
object CsvFieldInference {

    private val TRUE_TOKENS = setOf("true", "yes", "y", "1", "t")
    private val FALSE_TOKENS = setOf("false", "no", "n", "0", "f")

    /** Delimiters allowed inside a single cell when a column is treated as tags. */
    private val TAG_DELIMITERS = charArrayOf(';', '|', '\n')

    /**
     * Infer a field type from every non-blank value in the column. Boolean is
     * checked before numeric so that a 0/1 column is read as Yes/No (per spec),
     * not as numbers.
     */
    fun infer(values: List<String>): FieldType {
        val nonBlank = values.map { it.trim() }.filter { it.isNotEmpty() }
        if (nonBlank.isEmpty()) return FieldType.TEXT
        if (nonBlank.all { isBooleanToken(it) }) return FieldType.BOOLEAN
        if (nonBlank.all { it.toDoubleOrNull() != null }) return FieldType.NUMERIC
        return FieldType.TEXT
    }

    /** Distinct non-blank cell values, in first-seen order — options for Single-Select. */
    fun choiceOptions(values: List<String>): List<String> =
        values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /** Distinct tags across all cells (cells may hold several `;`/`|`-separated tags). */
    fun tagOptions(values: List<String>): List<String> =
        values.flatMap { splitTags(it) }.distinct()

    /**
     * Convert one raw cell to a [FieldValue] for the chosen [type]. Blank cells
     * yield [CellConversion.Skipped] (no value stored). When a value can't be
     * represented as the chosen type it is kept verbatim as text
     * ([CellConversion.FellBackToText]) so nothing is lost and the caller can
     * surface a warning.
     */
    fun convertCell(raw: String, type: FieldType): CellConversion {
        val value = raw.trim()
        if (value.isEmpty()) return CellConversion.Skipped

        return when (type) {
            FieldType.TEXT -> CellConversion.Converted(FieldValue.Text(value))
            FieldType.NUMERIC -> value.toDoubleOrNull()
                ?.let { CellConversion.Converted(FieldValue.Numeric(it)) }
                ?: CellConversion.FellBackToText(FieldValue.Text(value))
            FieldType.BOOLEAN -> parseBoolean(value)
                ?.let { CellConversion.Converted(FieldValue.Bool(it)) }
                ?: CellConversion.FellBackToText(FieldValue.Text(value))
            FieldType.CHOICE -> CellConversion.Converted(FieldValue.Choice(value))
            FieldType.MULTI_SELECT -> {
                val tags = splitTags(value)
                if (tags.isEmpty()) CellConversion.Skipped
                else CellConversion.Converted(FieldValue.MultiSelect(tags))
            }
        }
    }

    private fun isBooleanToken(value: String): Boolean {
        val token = value.lowercase()
        return token in TRUE_TOKENS || token in FALSE_TOKENS
    }

    private fun parseBoolean(value: String): Boolean? = when (value.lowercase()) {
        in TRUE_TOKENS -> true
        in FALSE_TOKENS -> false
        else -> null
    }

    private fun splitTags(cell: String): List<String> =
        cell.split(*TAG_DELIMITERS).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

/** Outcome of converting a single CSV cell into a [FieldValue]. */
sealed interface CellConversion {
    val value: FieldValue?

    /** Converted cleanly to the requested type. */
    data class Converted(override val value: FieldValue) : CellConversion

    /** Couldn't fit the requested type, kept as text (caller should warn). */
    data class FellBackToText(override val value: FieldValue) : CellConversion

    /** Blank cell — nothing to store. */
    data object Skipped : CellConversion {
        override val value: FieldValue? = null
    }
}
