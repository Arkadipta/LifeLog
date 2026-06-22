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

    /**
     * GitHub-style contribution heatmap. Entries are aggregated into one value
     * per calendar day, then laid out into week columns (GitHub layout: each
     * column is a week, each row a day of the week). The grid is built here so
     * the renderer stays presentation-only; cell color is derived at draw time
     * from [minValue]/[maxValue]/[diverging] against the Material 3 palette.
     */
    data class Heatmap(
        /** Week columns, oldest first. Each holds exactly 7 day slots (row 0 = first weekday). */
        val columns: List<Week>,
        /** Month names to print above the grid, keyed by the column they start over. */
        val monthLabels: List<MonthLabel>,
        /** Day-of-week label for each of the 7 rows (e.g. "Mon"), in row order. */
        val weekdayLabels: List<String>,
        val minValue: Double,
        val maxValue: Double,
        /** True when any day aggregates to a negative value → use a diverging scale. */
        val diverging: Boolean,
        /** Number of days that actually have data, for accessibility/summary. */
        val daysWithData: Int,
        val unit: String = "",
        val fieldName: String = "",
        val aggregation: AggregationStrategy = AggregationStrategy.MEAN,
        /** Non-null when the window ends at the latest entry instead of now. */
        val anchoredEndMs: Long? = null
    ) : ChartData() {
        /** One week column. A null slot is padding outside the grid range (e.g. future days). */
        data class Week(val days: List<Day?>)

        /**
         * A single day cell. [value] is null when no entry exists that day (a
         * "missing" cell, rendered neutrally and distinctly from a zero value).
         */
        data class Day(
            val dateMs: Long,
            val value: Double?,
            val entryCount: Int
        )

        data class MonthLabel(val columnIndex: Int, val label: String)
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
