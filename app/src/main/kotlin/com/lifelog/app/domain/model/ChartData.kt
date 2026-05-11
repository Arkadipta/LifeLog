package com.lifelog.app.domain.model

sealed class ChartData {

    data class Line(val series: List<Series>) : ChartData() {
        data class Series(val fieldName: String, val points: List<Point>)
        data class Point(val timestampMs: Long, val value: Double)
    }

    data class Bar(val series: List<Series>) : ChartData() {
        data class Series(val fieldName: String, val points: List<Point>)
        data class Point(val timestampMs: Long, val value: Double)
    }

    data class Pie(val slices: List<Slice>) : ChartData() {
        data class Slice(val label: String, val value: Double, val colorArgb: Int)
    }

    data object Empty : ChartData()
    data object InsufficientData : ChartData()
}
