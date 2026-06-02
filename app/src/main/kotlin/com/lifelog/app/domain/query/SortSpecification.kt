package com.lifelog.app.domain.query

enum class SortDirection(val label: String) {
    ASCENDING("Ascending"),
    DESCENDING("Descending")
}

sealed class SortField {
    abstract val displayName: String

    object Timestamp : SortField() {
        override val displayName: String = "Timestamp"
    }

    data class NumericField(
        val fieldId: Long,
        override val displayName: String
    ) : SortField()
}

data class SortSpecification(
    val field: SortField,
    val direction: SortDirection
)
