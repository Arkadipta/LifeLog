package com.lifelog.app.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Picks which x-axis buckets get labeled and how the labels read.
 *
 * The label format follows the span the buckets actually cover — not the
 * nominal time range — so sparse data still gets meaningful labels. If a
 * format would produce duplicate labels (e.g. four ticks all reading
 * "May 26"), it is refined step by step until every label is distinct.
 */
object ChartTickGenerator {

    data class Tick(val bucketIndex: Int, val label: String)

    private const val MS_PER_HOUR = 3_600_000L
    private const val MS_PER_DAY = 86_400_000L

    /** Next-more-specific pattern, tried when labels collide. */
    private val REFINEMENT = mapOf(
        "HH:mm" to "EEE HH:mm",
        "EEE HH:mm" to "MMM d HH:mm",
        "EEE d" to "MMM d",
        "MMM" to "MMM yy",
        "MMM yy" to "MMM d",
        "MMM d" to "MMM d HH:mm"
    )

    fun generate(
        bucketTimestamps: List<Long>,
        maxTicks: Int,
        locale: Locale = Locale.getDefault()
    ): List<Tick> {
        if (bucketTimestamps.isEmpty() || maxTicks < 1) return emptyList()
        val indices = tickIndices(bucketTimestamps.size, maxTicks)
        var pattern = initialPattern(bucketTimestamps, locale)
        while (true) {
            val format = SimpleDateFormat(pattern, locale)
            val labels = indices.map { format.format(Date(bucketTimestamps[it])) }
            val next = REFINEMENT[pattern]
            if (labels.toSet().size == labels.size || next == null) {
                return indices.mapIndexed { i, bucketIndex -> Tick(bucketIndex, labels[i]) }
            }
            pattern = next
        }
    }

    /** Every bucket when few; otherwise evenly stepped starting at the first. */
    private fun tickIndices(bucketCount: Int, maxTicks: Int): List<Int> {
        if (bucketCount <= maxTicks) return (0 until bucketCount).toList()
        val step = (bucketCount + maxTicks - 1) / maxTicks
        return (0 until bucketCount step step).toList()
    }

    private fun initialPattern(timestamps: List<Long>, locale: Locale): String {
        val span = timestamps.last() - timestamps.first()
        val subDayBuckets = timestamps.zipWithNext().any { (a, b) -> b - a < 23 * MS_PER_HOUR }
        if (subDayBuckets) {
            val dayKey = SimpleDateFormat("yyyyDDD", locale)
            val sameDay = dayKey.format(Date(timestamps.first())) ==
                dayKey.format(Date(timestamps.last()))
            return if (sameDay) "HH:mm" else "EEE HH:mm"
        }
        return when {
            span <= 10 * MS_PER_DAY -> "EEE d"
            span <= 180 * MS_PER_DAY -> "MMM d"
            span <= 400 * MS_PER_DAY -> "MMM"
            else -> "MMM yy"
        }
    }
}
