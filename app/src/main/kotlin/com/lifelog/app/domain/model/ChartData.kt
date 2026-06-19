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
        data class Series(
            val fieldName: String,
            val colorArgb: Int?,
            val points: List<Point>,
            val unit: String = ""
        )
        data class Point(val bucketIndex: Int, val value: Double)
    }

    data class Pie(
        val slices: List<Slice>,
        /** Non-null when the window ends at the latest entry instead of now. */
        val anchoredEndMs: Long? = null,
        /** Unit of the summed numeric field, shown with the donut total. */
        val unit: String = ""
    ) : ChartData() {
        data class Slice(val label: String, val value: Double, val colorArgb: Int)
    }

    data object Empty : ChartData()
    data object InsufficientData : ChartData()

    /**
     * The chart's configuration references a field that no longer fits its role —
     * e.g. a plotted field whose type was changed away from Number, or a pie
     * group-by field that is no longer a choice/multi-select. The card stays
     * visible so the user can edit or delete the now-unusable chart.
     */
    data object StaleConfig : ChartData()
}
