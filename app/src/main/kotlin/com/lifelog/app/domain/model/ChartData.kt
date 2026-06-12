package com.lifelog.app.domain.model

sealed class ChartData {

    data class Cartesian(
        val type: ChartType,
        val series: List<Series>,
        val bucketTimestamps: List<Long>,
        /** Non-null when the window ends at the latest entry instead of now. */
        val anchoredEndMs: Long? = null
    ) : ChartData() {
        /** colorArgb == null means "use the event accent color" */
        data class Series(val fieldName: String, val colorArgb: Int?, val points: List<Point>)
        data class Point(val bucketIndex: Int, val value: Double)
    }

    data class Pie(
        val slices: List<Slice>,
        /** Non-null when the window ends at the latest entry instead of now. */
        val anchoredEndMs: Long? = null
    ) : ChartData() {
        data class Slice(val label: String, val value: Double, val colorArgb: Int)
    }

    data object Empty : ChartData()
    data object InsufficientData : ChartData()
}
