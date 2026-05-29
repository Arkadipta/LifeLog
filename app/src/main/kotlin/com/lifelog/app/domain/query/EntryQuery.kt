package com.lifelog.app.domain.query

/**
 * Describes a complete query to apply to a list of EventEntry objects.
 *
 * [filters] is a flat list of conditions. All conditions are combined with [logicalOperator].
 * [sort] is optional; when null entries retain their natural (repository) order.
 *
 * Architecture note: keeping filters flat with a single top-level operator covers all
 * documented use cases (Calories > 300 AND Food Category == Dinner; Systolic > 130 OR
 * Diastolic > 90). A full expression tree (nested AND/OR/NOT) is supported by the
 * EntryQueryEngine via CompositeFilter and can be introduced without breaking this API.
 */
data class EntryQuery(
    val filters: List<FilterCondition> = emptyList(),
    val logicalOperator: LogicalOperator = LogicalOperator.AND,
    val sort: SortSpecification? = null
) {
    val isActive: Boolean get() = filters.isNotEmpty() || sort != null
    val isEmpty: Boolean get() = !isActive

    companion object {
        val Empty = EntryQuery()
    }
}

enum class LogicalOperator(val label: String) {
    AND("AND"),
    OR("OR")
}
