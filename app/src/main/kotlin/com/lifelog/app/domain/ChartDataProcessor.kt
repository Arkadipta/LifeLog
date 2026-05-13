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

    private val PALETTE = listOf(
        0xFF6750A4, 0xFF409CFF, 0xFF4CAF50, 0xFFFF9800, 0xFFF44336,
        0xFF9C27B0, 0xFF00BCD4, 0xFFFFEB3B, 0xFF795548, 0xFF607D8B
    ).map { it.toInt() }

    fun process(
        config: ChartConfig,
        entries: List<EventEntry>,
        fields: List<EventField>
    ): ChartData {
        val timeRange = TimeRange.fromDays(config.timeRangeDays)
        val filtered = applyTimeRange(entries, config.timeRangeDays)
        if (filtered.isEmpty()) return ChartData.InsufficientData

        return when (config.type) {
            ChartType.LINE, ChartType.BAR -> buildCartesianData(config, filtered, fields, timeRange)
            ChartType.PIE -> buildPieData(config, filtered, fields)
        }
    }

    // ── Bucketing ─────────────────────────────────────────────────────────────

    /**
     * Returns ordered (representativeTimestampMs, entriesInBucket) pairs for the
     * given time range. All buckets are returned even if empty, so every series
     * shares the same bucket index space.
     */
    private fun buildBuckets(
        entries: List<EventEntry>,
        timeRange: TimeRange
    ): List<Pair<Long, List<EventEntry>>> {
        return when (timeRange) {
            TimeRange.DAY -> {
                val dayStart = midnightToday()
                (0 until 24).map { hour ->
                    val start = dayStart + hour * 3_600_000L
                    val end = start + 3_600_000L
                    Pair(start + 1_800_000L, entries.filter { it.createdAt in start until end })
                }
            }

            TimeRange.WEEK -> {
                val todayMidnight = midnightToday()
                (6 downTo 0).map { daysAgo ->
                    val start = todayMidnight - daysAgo * 86_400_000L
                    val end = start + 86_400_000L
                    Pair(start + 43_200_000L, entries.filter { it.createdAt in start until end })
                }
            }

            TimeRange.MONTH -> {
                val todayMidnight = midnightToday()
                (3 downTo 0).map { weeksAgo ->
                    val start = todayMidnight - (weeksAgo + 1) * 7 * 86_400_000L
                    val end = if (weeksAgo == 0) todayMidnight + 86_400_000L
                               else todayMidnight - weeksAgo * 7 * 86_400_000L
                    Pair((start + end) / 2, entries.filter { it.createdAt in start until end })
                }
            }

            TimeRange.YEAR -> {
                val today = Calendar.getInstance()
                (11 downTo 0).map { monthsAgo ->
                    val startCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, today.get(Calendar.YEAR))
                        set(Calendar.MONTH, today.get(Calendar.MONTH))
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
                val binCount = 20
                val binSize = (maxTime - minTime + 1) / binCount
                (0 until binCount).map { i ->
                    val start = minTime + i * binSize
                    val end = if (i == binCount - 1) maxTime + 1 else start + binSize
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
        timeRange: TimeRange
    ): ChartData {
        val fieldMap = fields.associateBy { it.id }
        val buckets = buildBuckets(entries.sortedBy { it.createdAt }, timeRange)
        val timestamps = buckets.map { it.first }

        val series = config.numericFieldIds.mapNotNull { fieldId ->
            val field = fieldMap[fieldId] ?: return@mapNotNull null
            val points = buckets.mapIndexedNotNull { idx, (_, bucketEntries) ->
                val values = numericValues(bucketEntries, fieldId)
                if (values.isEmpty()) null
                else ChartData.Cartesian.Point(idx, aggregate(values, config.aggregation))
            }
            if (points.isEmpty()) null else ChartData.Cartesian.Series(field.name, points)
        }

        return if (series.isEmpty()) ChartData.Empty
               else ChartData.Cartesian(config.type, series, timeRange, timestamps)
    }

    // ── Pie builder ───────────────────────────────────────────────────────────

    private fun buildPieData(
        config: ChartConfig,
        entries: List<EventEntry>,
        fields: List<EventField>
    ): ChartData {
        val numericFieldId = config.numericFieldIds.firstOrNull() ?: return ChartData.Empty
        val groupFieldId = config.groupByFieldId ?: return ChartData.Empty
        fields.firstOrNull { it.id == groupFieldId } ?: return ChartData.Empty

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
        return ChartData.Pie(slices)
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

    private fun applyTimeRange(entries: List<EventEntry>, days: Int?): List<EventEntry> {
        if (days == null) return entries
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        return entries.filter { it.createdAt >= cutoff }
    }

    private fun midnightToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
