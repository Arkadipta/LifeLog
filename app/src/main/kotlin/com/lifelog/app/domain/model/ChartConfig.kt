package com.lifelog.app.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ChartConfig(
    val id: String = UUID.randomUUID().toString(),
    val eventTypeId: Long = 0,
    val title: String = "",
    val type: ChartType,
    val numericFieldIds: List<Long>,
    val groupByFieldId: Long? = null,
    val timeRangeDays: Int? = null,
    /** Legacy single-chart color — used as fallback when fieldColors is absent. */
    val colorArgb: Int? = null,
    /** Per-field colors. Absent key means "Auto" (inherit event accent color). */
    val fieldColors: Map<Long, Int> = emptyMap(),
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val aggregation: AggregationStrategy = AggregationStrategy.MEAN
)
