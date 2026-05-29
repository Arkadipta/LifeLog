package com.lifelog.app.domain.query

/**
 * A single predicate evaluated against one field of an EventEntry.
 *
 * Each variant carries the field identity (id + display name) so the UI
 * can render conditions without needing to re-look up the EventType.
 */
sealed class FilterCondition {

    data class NumericFilter(
        val fieldId: Long,
        val fieldName: String,
        val operator: NumericOperator,
        val value: Double
    ) : FilterCondition()

    data class BooleanFilter(
        val fieldId: Long,
        val fieldName: String,
        val operator: BooleanOperator,
        val value: Boolean
    ) : FilterCondition()

    data class ChoiceFilter(
        val fieldId: Long,
        val fieldName: String,
        val operator: ChoiceOperator,
        val value: String
    ) : FilterCondition()

    data class MultiSelectFilter(
        val fieldId: Long,
        val fieldName: String,
        val operator: MultiSelectOperator,
        val value: String
    ) : FilterCondition()
}

enum class NumericOperator(val label: String, val symbol: String) {
    EQUALS("equals", "="),
    NOT_EQUALS("not equals", "≠"),
    GREATER_THAN("greater than", ">"),
    GREATER_THAN_OR_EQUAL("at least", "≥"),
    LESS_THAN("less than", "<"),
    LESS_THAN_OR_EQUAL("at most", "≤")
}

enum class BooleanOperator(val label: String) {
    EQUALS("is"),
    NOT_EQUALS("is not")
}

enum class ChoiceOperator(val label: String) {
    EQUALS("is"),
    NOT_EQUALS("is not")
}

enum class MultiSelectOperator(val label: String) {
    CONTAINS("contains"),
    DOES_NOT_CONTAIN("does not contain")
}
