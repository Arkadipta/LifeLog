package com.lifelog.app.domain

import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.domain.query.*

/**
 * Pure, stateless engine that applies an [EntryQuery] to a list of [EventEntry] objects.
 *
 * Missing values on sort: entries where the sort field is absent always appear last,
 * regardless of sort direction. This ensures a stable, predictable ordering.
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
        val entryVal = fieldValue.value
        val condVal = condition.value
        return when (condition.operator) {
            NumericOperator.EQUALS -> entryVal == condVal
            NumericOperator.NOT_EQUALS -> entryVal != condVal
            NumericOperator.GREATER_THAN -> entryVal > condVal
            NumericOperator.GREATER_THAN_OR_EQUAL -> entryVal >= condVal
            NumericOperator.LESS_THAN -> entryVal < condVal
            NumericOperator.LESS_THAN_OR_EQUAL -> entryVal <= condVal
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
