package com.lifelog.app.domain.model

sealed class ChartData {

    data class Line(
        val series: List<Series>,
        val timeRange: TimeRange,
        val bucketTimestamps: List<Long>
    ) : ChartData() {
        data class Series(val fieldName: String, val points: List<Point>)
        /** bucketIndex is the position of this point in bucketTimestamps */
        data class Point(val bucketIndex: Int, val value: Double)
    }

    data class Bar(
        val series: List<Series>,
        val timeRange: TimeRange,
        val bucketTimestamps: List<Long>
    ) : ChartData() {
        data class Series(val fieldName: String, val points: List<Point>)
        data class Point(val bucketIndex: Int, val value: Double)
    }

    data class Pie(val slices: List<Slice>) : ChartData() {
        data class Slice(val label: String, val value: Double, val colorArgb: Int)
    }

    data object Empty : ChartData()
    data object InsufficientData : ChartData()
}
