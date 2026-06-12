package com.lifelog.app.domain

import com.lifelog.app.domain.model.AggregationStrategy
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.ChartType
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.domain.model.TimeRange
import java.util.Calendar

object ChartDataProcessor {

    private const val MS_PER_HOUR = 3_600_000L
    private const val MS_PER_DAY = 86_400_000L

    private val PALETTE = listOf(
        0xFF6750A4, 0xFF409CFF, 0xFF4CAF50, 0xFFFF9800, 0xFFF44336,
        0xFF9C27B0, 0xFF00BCD4, 0xFFFFEB3B, 0xFF795548, 0xFF607D8B
    ).map { it.toInt() }

    fun process(
        config: ChartConfig,
        entries: List<EventEntry>,
        fields: List<EventField>,
        nowMs: Long = System.currentTimeMillis()
    ): ChartData {
        val relevant = entries.filter { hasChartableValue(it, config) }
        if (relevant.isEmpty()) return ChartData.InsufficientData

        val anchorMs = resolveAnchor(relevant, config.timeRangeDays, nowMs)
        val windowed = applyTimeRange(relevant, config.timeRangeDays, anchorMs)
        if (windowed.isEmpty()) return ChartData.InsufficientData
        val anchoredEndMs = anchorMs.takeIf { it != nowMs }

        return when (config.type) {
            ChartType.LINE, ChartType.BAR ->
                buildCartesianData(config, windowed, fields, anchorMs, anchoredEndMs)
            ChartType.PIE -> buildPieData(config, windowed, fields, anchoredEndMs)
        }
    }

    // ── Windowing ─────────────────────────────────────────────────────────────

    /** True when the entry holds a value this chart can plot. */
    private fun hasChartableValue(entry: EventEntry, config: ChartConfig): Boolean =
        when (config.type) {
            ChartType.PIE -> {
                val numericId = config.numericFieldIds.firstOrNull() ?: return false
                val groupId = config.groupByFieldId ?: return false
                val numeric = (entry.fieldValues[numericId] as? FieldValue.Numeric)?.value
                val group = entry.fieldValues[groupId]
                numeric != null && numeric > 0 &&
                    (group is FieldValue.Choice || group is FieldValue.MultiSelect)
            }
            else -> config.numericFieldIds.any { entry.fieldValues[it] is FieldValue.Numeric }
        }

    /**
     * Timestamp the window ends at. Normally now, but when the now-anchored
     * window would be empty, the window slides back to end at the most recent
     * chartable entry so sparse histories still produce a chart.
     */
    private fun resolveAnchor(relevant: List<EventEntry>, days: Int?, nowMs: Long): Long {
        if (days == null) return nowMs
        val cutoff = nowMs - days * MS_PER_DAY
        return if (relevant.any { it.createdAt >= cutoff }) nowMs
               else relevant.maxOf { it.createdAt }
    }

    private fun applyTimeRange(entries: List<EventEntry>, days: Int?, anchorMs: Long): List<EventEntry> {
        if (days == null) return entries
        val cutoff = anchorMs - days * MS_PER_DAY
        return entries.filter { it.createdAt >= cutoff }
    }

    // ── Bucketing ─────────────────────────────────────────────────────────────

    /**
     * Returns ordered (representativeTimestampMs, entriesInBucket) pairs for the
     * given time range ending at [anchorMs]. All buckets are returned even if
     * empty, so every series shares the same bucket index space.
     */
    private fun buildBuckets(
        entries: List<EventEntry>,
        timeRange: TimeRange,
        anchorMs: Long
    ): List<Pair<Long, List<EventEntry>>> {
        return when (timeRange) {
            TimeRange.DAY -> {
                val dayStart = midnightOf(anchorMs)
                (0 until 24).map { hour ->
                    val start = dayStart + hour * MS_PER_HOUR
                    val end = start + MS_PER_HOUR
                    Pair(start + MS_PER_HOUR / 2, entries.filter { it.createdAt in start until end })
                }
            }

            TimeRange.WEEK -> {
                val anchorMidnight = midnightOf(anchorMs)
                (6 downTo 0).map { daysAgo ->
                    val start = anchorMidnight - daysAgo * MS_PER_DAY
                    val end = start + MS_PER_DAY
                    Pair(start + MS_PER_DAY / 2, entries.filter { it.createdAt in start until end })
                }
            }

            TimeRange.MONTH -> {
                val anchorMidnight = midnightOf(anchorMs)
                (3 downTo 0).map { weeksAgo ->
                    val start = anchorMidnight - (weeksAgo + 1) * 7 * MS_PER_DAY
                    val end = if (weeksAgo == 0) anchorMidnight + MS_PER_DAY
                               else anchorMidnight - weeksAgo * 7 * MS_PER_DAY
                    Pair((start + end) / 2, entries.filter { it.createdAt in start until end })
                }
            }

            TimeRange.YEAR -> {
                val anchor = Calendar.getInstance().apply { timeInMillis = anchorMs }
                (11 downTo 0).map { monthsAgo ->
                    val startCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, anchor.get(Calendar.YEAR))
                        set(Calendar.MONTH, anchor.get(Calendar.MONTH))
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                        add(Calendar.MONTH, -monthsAgo)
                    }
                    val endCal = Calendar.getInstance().apply {
                        timeInMillis = startCal.timeInMillis
                        add(Calendar.MONTH, 1)
                    }
                    val start = startCal.timeInMillis
                    val end = endCal.timeInMillis
                    Pair((start + end) / 2, entries.filter { it.createdAt in start until end })
                }
            }

            TimeRange.ALL -> {
                val sorted = entries.sortedBy { it.createdAt }
                if (sorted.isEmpty()) return emptyList()
                val minTime = sorted.first().createdAt
                val maxTime = sorted.last().createdAt
                if (minTime == maxTime) return listOf(Pair(minTime, sorted))
                val span = maxTime - minTime
                // Bin by natural calendar units so axis labels stay distinct;
                // only very long histories fall back to 20 even bins.
                val binSize = when {
                    span <= 26 * MS_PER_HOUR -> MS_PER_HOUR
                    span <= 20 * MS_PER_DAY -> MS_PER_DAY
                    span <= 140 * MS_PER_DAY -> 7 * MS_PER_DAY
                    span <= 600 * MS_PER_DAY -> 30 * MS_PER_DAY
                    else -> (span + 19) / 20
                }
                val binCount = (span / binSize + 1).toInt()
                (0 until binCount).map { i ->
                    val start = minTime + i * binSize
                    val end = start + binSize
                    Pair(start + binSize / 2, entries.filter { it.createdAt in start until end })
                }
            }
        }
    }

    // ── Cartesian (Line + Bar) builder ────────────────────────────────────────

    private fun buildCartesianData(
        config: ChartConfig,
        entries: List<EventEntry>,
        fields: List<EventField>,
        anchorMs: Long,
        anchoredEndMs: Long?
    ): ChartData {
        val fieldMap = fields.associateBy { it.id }
        val timeRange = TimeRange.fromDays(config.timeRangeDays)
        val buckets = buildBuckets(entries.sortedBy { it.createdAt }, timeRange, anchorMs)
        val timestamps = buckets.map { it.first }

        val series = config.numericFieldIds.mapNotNull { fieldId ->
            val field = fieldMap[fieldId] ?: return@mapNotNull null
            val seriesColor = config.fieldColors[fieldId] ?: config.colorArgb
            val points = buckets.mapIndexedNotNull { idx, (_, bucketEntries) ->
                val values = numericValues(bucketEntries, fieldId)
                if (values.isEmpty()) null
                else ChartData.Cartesian.Point(idx, aggregate(values, config.aggregation))
            }
            if (points.isEmpty()) null
            else ChartData.Cartesian.Series(
                field.name, seriesColor, points,
                if (config.showUnits) field.unit else ""
            )
        }

        return if (series.isEmpty()) ChartData.Empty
               else ChartData.Cartesian(config.type, series, timestamps, anchoredEndMs)
    }

    // ── Pie builder ───────────────────────────────────────────────────────────

    private fun buildPieData(
        config: ChartConfig,
        entries: List<EventEntry>,
        fields: List<EventField>,
        anchoredEndMs: Long?
    ): ChartData {
        val numericFieldId = config.numericFieldIds.firstOrNull() ?: return ChartData.Empty
        val groupFieldId = config.groupByFieldId ?: return ChartData.Empty

        val accumulator = mutableMapOf<String, Double>()
        for (entry in entries) {
            val numeric = (entry.fieldValues[numericFieldId] as? FieldValue.Numeric)?.value
                ?: continue
            if (numeric <= 0) continue
            when (val groupValue = entry.fieldValues[groupFieldId]) {
                is FieldValue.Choice ->
                    accumulator[groupValue.value] = (accumulator[groupValue.value] ?: 0.0) + numeric
                is FieldValue.MultiSelect ->
                    groupValue.values.forEach { tag ->
                        accumulator[tag] = (accumulator[tag] ?: 0.0) + numeric
                    }
                else -> Unit
            }
        }

        if (accumulator.isEmpty()) return ChartData.InsufficientData

        val sorted = accumulator.entries.sortedByDescending { it.value }
        val slices = sorted.mapIndexed { i, (label, value) ->
            ChartData.Pie.Slice(label, value, PALETTE[i % PALETTE.size])
        }
        val unit = if (config.showUnits) {
            fields.firstOrNull { it.id == numericFieldId }?.unit.orEmpty()
        } else ""
        return ChartData.Pie(slices, anchoredEndMs, unit)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun numericValues(entries: List<EventEntry>, fieldId: Long): List<Double> =
        entries.mapNotNull { (it.fieldValues[fieldId] as? FieldValue.Numeric)?.value }

    private fun aggregate(values: List<Double>, strategy: AggregationStrategy): Double =
        when (strategy) {
            AggregationStrategy.MEAN -> values.average()
            AggregationStrategy.SUM -> values.sum()
            AggregationStrategy.MIN -> values.min()
            AggregationStrategy.MAX -> values.max()
            AggregationStrategy.MEDIAN -> {
                val sorted = values.sorted()
                val mid = sorted.size / 2
                if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0
                else sorted[mid]
            }
            AggregationStrategy.LATEST -> values.last()
        }

    private fun midnightOf(timestampMs: Long): Long = Calendar.getInstance().apply {
        timeInMillis = timestampMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
