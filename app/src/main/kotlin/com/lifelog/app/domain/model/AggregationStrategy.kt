package com.lifelog.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class AggregationStrategy(val displayName: String) {
    MEAN("Average"),
    SUM("Sum"),
    MIN("Min"),
    MAX("Max"),
    MEDIAN("Median"),
    LATEST("Latest")
}
