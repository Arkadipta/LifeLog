package com.lifelog.app.domain

import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.domain.query.*
import kotlin.math.abs
import kotlin.math.max

/**
 * Pure, stateless engine that applies an [EntryQuery] to a list of [EventEntry] objects.
 *
 * Missing values on sort: entries where the sort field is absent always appear last,
 * regardless of sort direction. This ensures a stable, predictable ordering.
 *
 * Unevaluable values are excluded: an entry whose field is absent — or holds a value the
 * condition cannot be judged against (see [compareNumeric]) — matches no filter, including
 * the negative ones (NOT_EQUALS, DOES_NOT_CONTAIN). "Not 120" means "holds a number, and
 * that number isn't 120", not "fails to hold 120".
 */
object EntryQueryEngine {

    fun apply(entries: List<EventEntry>, query: EntryQuery): List<EventEntry> {
        val filtered = if (query.filters.isEmpty()) {
            entries
        } else {
            entries.filter { entry -> evaluate(entry, query.filters, query.logicalOperator) }
        }
        return sort(filtered, query.sort)
    }

    private fun evaluate(
        entry: EventEntry,
        conditions: List<FilterCondition>,
        operator: LogicalOperator
    ): Boolean = when (operator) {
        LogicalOperator.AND -> conditions.all { evaluateCondition(entry, it) }
        LogicalOperator.OR -> conditions.any { evaluateCondition(entry, it) }
    }

    private fun evaluateCondition(entry: EventEntry, condition: FilterCondition): Boolean =
        when (condition) {
            is FilterCondition.NumericFilter -> evaluateNumeric(entry, condition)
            is FilterCondition.BooleanFilter -> evaluateBoolean(entry, condition)
            is FilterCondition.ChoiceFilter -> evaluateChoice(entry, condition)
            is FilterCondition.MultiSelectFilter -> evaluateMultiSelect(entry, condition)
        }

    private fun evaluateNumeric(entry: EventEntry, condition: FilterCondition.NumericFilter): Boolean {
        val fieldValue = entry.fieldValues[condition.fieldId] as? FieldValue.Numeric ?: return false
        val order = compareNumeric(fieldValue.value, condition.value) ?: return false
        return when (condition.operator) {
            NumericOperator.EQUALS -> order == 0
            NumericOperator.NOT_EQUALS -> order != 0
            NumericOperator.GREATER_THAN -> order > 0
            NumericOperator.GREATER_THAN_OR_EQUAL -> order >= 0
            NumericOperator.LESS_THAN -> order < 0
            NumericOperator.LESS_THAN_OR_EQUAL -> order <= 0
        }
    }

    private fun evaluateBoolean(entry: EventEntry, condition: FilterCondition.BooleanFilter): Boolean {
        val fieldValue = entry.fieldValues[condition.fieldId] as? FieldValue.Bool ?: return false
        return when (condition.operator) {
            BooleanOperator.EQUALS -> fieldValue.value == condition.value
            BooleanOperator.NOT_EQUALS -> fieldValue.value != condition.value
        }
    }

    private fun evaluateChoice(entry: EventEntry, condition: FilterCondition.ChoiceFilter): Boolean {
        val fieldValue = entry.fieldValues[condition.fieldId] as? FieldValue.Choice ?: return false
        return when (condition.operator) {
            ChoiceOperator.EQUALS -> fieldValue.value == condition.value
            ChoiceOperator.NOT_EQUALS -> fieldValue.value != condition.value
        }
    }

    private fun evaluateMultiSelect(entry: EventEntry, condition: FilterCondition.MultiSelectFilter): Boolean {
        val fieldValue = entry.fieldValues[condition.fieldId] as? FieldValue.MultiSelect ?: return false
        val contains = condition.value in fieldValue.values
        return when (condition.operator) {
            MultiSelectOperator.CONTAINS -> contains
            MultiSelectOperator.DOES_NOT_CONTAIN -> !contains
        }
    }

    private fun sort(entries: List<EventEntry>, spec: SortSpecification?): List<EventEntry> {
        if (spec == null) return entries
        val comparator: Comparator<EventEntry> = when (val field = spec.field) {
            is SortField.Timestamp -> compareBy { it.createdAt }
            is SortField.NumericField -> Comparator { a, b ->
                val aVal = (a.fieldValues[field.fieldId] as? FieldValue.Numeric)?.value
                val bVal = (b.fieldValues[field.fieldId] as? FieldValue.Numeric)?.value
                when {
                    aVal == null && bVal == null -> 0
                    aVal == null -> 1   // missing values always last
                    bVal == null -> -1
                    else -> aVal.compareTo(bVal)
                }
            }
        }
        // Double.compareTo is a total order (NaN sorts last, -0.0 before 0.0), so a
        // non-finite value sorts predictably instead of corrupting the comparator the
        // way a raw `<` would. Sorting deliberately keeps exact ordering: the filter
        // tolerance below exists to answer "is this the number the user typed?", and
        // collapsing near-equal values into ties here would only make the order arbitrary.
        return if (spec.direction == SortDirection.DESCENDING) {
            entries.sortedWith(comparator.reversed())
                .let { sorted ->
                    // Re-apply "missing last" after reversal for numeric fields
                    if (spec.field is SortField.NumericField) {
                        val fieldId = (spec.field as SortField.NumericField).fieldId
                        val present = sorted.filter { it.fieldValues[fieldId] is FieldValue.Numeric }
                        val absent = sorted.filter { it.fieldValues[fieldId] !is FieldValue.Numeric }
                        present + absent
                    } else sorted
                }
        } else {
            entries.sortedWith(comparator)
        }
    }
}

/**
 * Relative tolerance for numeric filter comparisons — nine significant digits, which is
 * more precision than any hand-logged measurement carries and well inside the ~15 digits
 * a Double actually holds.
 */
private const val NUMERIC_TOLERANCE = 1e-9

/**
 * Three-way comparison of a stored value against a filter value, tolerant of
 * floating-point noise: -1 below, 0 indistinguishable, 1 above, and **null when the
 * question is unanswerable** because either side is NaN or infinite.
 *
 * Every numeric operator is expressed through this one function so the six of them
 * cannot disagree: exactly one of `<`, `≈`, `>` holds for any pair, so `>` and `<=`
 * always partition the entries and `EQUALS`/`NOT_EQUALS` are exact complements. Giving
 * only `EQUALS` a tolerance would have created a new contradiction — a value that is
 * "equal to 130" while also being "greater than 130".
 *
 * The tolerance is relative, floored at 1.0 so values near zero get an absolute 1e-9
 * window rather than a vanishing one. Two numbers this close cannot be told apart by the
 * entry form, which parses what the user typed, or by the CSV wizard, which parses what
 * the file held — so treating them as different could only ever surprise someone.
 *
 * Non-finite values reach the database through CSV import: `"NaN".toDoubleOrNull()` and
 * `"Infinity".toDoubleOrNull()` both succeed, so a column of them is inferred NUMERIC and
 * stored verbatim. Returning null routes those to the engine's exclude-what-you-cannot-
 * judge rule. Left to IEEE semantics a NaN row would have been silently swept into every
 * NOT_EQUALS result — the one place a corrupt value could masquerade as a real answer.
 */
internal fun compareNumeric(value: Double, other: Double): Int? {
    if (!value.isFinite() || !other.isFinite()) return null
    if (value == other) return 0
    val scale = max(abs(value), abs(other)).coerceAtLeast(1.0)
    if (abs(value - other) <= NUMERIC_TOLERANCE * scale) return 0
    return if (value < other) -1 else 1
}
