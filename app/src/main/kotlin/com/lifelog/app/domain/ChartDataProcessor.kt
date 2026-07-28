package com.lifelog.app.domain

import com.lifelog.app.domain.model.AggregationStrategy
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.ChartType
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.domain.model.TimeRange
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
        // A config that points at fields no longer suited to it (e.g. a plotted
        // field retyped away from Number) can't be rendered meaningfully. Flag it
        // so the card prompts an edit/delete instead of silently vanishing.
        if (isStaleConfig(config, fields)) return ChartData.StaleConfig

        val relevant = entries.filter { hasChartableValue(it, config) }
        if (relevant.isEmpty()) return ChartData.InsufficientData

        val anchorMs = resolveAnchor(relevant, config, nowMs)
        val windowed = applyTimeRange(relevant, config, anchorMs)
        if (windowed.isEmpty()) return ChartData.InsufficientData
        val anchoredEndMs = anchorMs.takeIf { it != nowMs }

        return when (config.type) {
            ChartType.LINE, ChartType.BAR ->
                buildCartesianData(config, windowed, fields, anchorMs, anchoredEndMs)
            ChartType.PIE -> buildPieData(config, windowed, fields, anchoredEndMs)
            ChartType.HEATMAP -> buildHeatmapData(config, windowed, fields, anchorMs, anchoredEndMs)
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
            // Heatmaps accept a single Numeric or Yes/No field (Yes=1, No=0).
            ChartType.HEATMAP -> {
                val id = config.numericFieldIds.firstOrNull() ?: return false
                val v = entry.fieldValues[id]
                v is FieldValue.Numeric || v is FieldValue.Bool
            }
            else -> config.numericFieldIds.any { entry.fieldValues[it] is FieldValue.Numeric }
        }

    /**
     * Timestamp the window ends at. Normally now, but when the now-anchored
     * window would show nothing, the window slides back to end at the most
     * recent chartable entry so sparse histories still produce a chart.
     */
    private fun resolveAnchor(relevant: List<EventEntry>, config: ChartConfig, nowMs: Long): Long {
        val days = config.timeRangeDays ?: return nowMs
        val windowStart = windowStartMs(config, days, nowMs)
        return if (relevant.any { it.createdAt >= windowStart }) nowMs
               else relevant.maxOf { it.createdAt }
    }

    private fun applyTimeRange(
        entries: List<EventEntry>,
        config: ChartConfig,
        anchorMs: Long
    ): List<EventEntry> {
        val days = config.timeRangeDays ?: return entries
        val windowStart = windowStartMs(config, days, anchorMs)
        return entries.filter { it.createdAt >= windowStart }
    }

    /**
     * First instant the chart displays when its window ends at [anchorMs] —
     * the entry filter must admit exactly what the chart lays out. Cartesian
     * ranges start at the oldest bucket of [buildBuckets] (same calendar-day
     * arithmetic); heatmaps start at their day grid, a last-N-calendar-days
     * convention pie charts share. A looser filter admits entries that render
     * nowhere yet hold the anchor at now (blank chart instead of sliding back)
     * and skew heatmap min/max/day counts; a tighter one clips the oldest year
     * bucket when the window spans a leap February.
     *
     * Every bound steps in calendar days, never in fixed 24-hour blocks: a DST
     * day is 23 or 25 hours long, so counting days in milliseconds slides the
     * boundary off local midnight for the whole window past the transition.
     */
    private fun windowStartMs(config: ChartConfig, days: Int, anchorMs: Long): Long =
        when (config.type) {
            ChartType.LINE, ChartType.BAR -> when (TimeRange.fromDays(days)) {
                TimeRange.DAY -> midnightOf(anchorMs)                     // today
                TimeRange.WEEK -> addDays(midnightOf(anchorMs), -6)       // 6 days + today
                TimeRange.MONTH -> addDays(midnightOf(anchorMs), -4 * 7)  // 4 weeks + today
                TimeRange.YEAR -> firstOfMonth(anchorMs, monthsAgo = 11)  // 12 calendar months
                // A day count with no bucket layout bins like ALL — spanning
                // whatever data exists — so it takes no lower bound either.
                TimeRange.ALL -> Long.MIN_VALUE
            }
            // Last [days] calendar days ending on the anchor's day.
            ChartType.PIE, ChartType.HEATMAP -> addDays(midnightOf(anchorMs), -(days - 1))
        }

    // ── Bucketing ─────────────────────────────────────────────────────────────

    /**
     * Returns ordered (representativeTimestampMs, entriesInBucket) pairs for the
     * given time range ending at [anchorMs]. All buckets are returned even if
     * empty, so every series shares the same bucket index space.
     *
     * Day boundaries come from [addDays], never from a fixed 24 hours, so the
     * grid stays pinned to local midnight across a DST transition — otherwise
     * every bucket after one drifts an hour and entries land a column off. The
     * representative timestamp is the bucket's own midpoint for the same reason.
     */
    private fun buildBuckets(
        entries: List<EventEntry>,
        timeRange: TimeRange,
        anchorMs: Long
    ): List<Pair<Long, List<EventEntry>>> {
        return when (timeRange) {
            // Hourly buckets tile the anchor's local day exactly, so a DST day
            // yields 23 or 25 of them rather than 24 that overrun or fall short.
            TimeRange.DAY -> {
                val dayStart = midnightOf(anchorMs)
                val dayEnd = addDays(dayStart, 1)
                buildList {
                    var start = dayStart
                    while (start < dayEnd) {
                        // minOf keeps the trailing bucket inside the day in the
                        // half-hour-offset zones where a shift isn't a whole hour.
                        val end = minOf(start + MS_PER_HOUR, dayEnd)
                        add(Pair(midpoint(start, end), entries.filter { it.createdAt in start until end }))
                        start = end
                    }
                }
            }

            TimeRange.WEEK -> {
                val anchorMidnight = midnightOf(anchorMs)
                (6 downTo 0).map { daysAgo ->
                    val start = addDays(anchorMidnight, -daysAgo)
                    val end = addDays(start, 1)
                    Pair(midpoint(start, end), entries.filter { it.createdAt in start until end })
                }
            }

            TimeRange.MONTH -> {
                val anchorMidnight = midnightOf(anchorMs)
                (3 downTo 0).map { weeksAgo ->
                    val start = addDays(anchorMidnight, -(weeksAgo + 1) * 7)
                    val end = if (weeksAgo == 0) addDays(anchorMidnight, 1)
                               else addDays(anchorMidnight, -weeksAgo * 7)
                    Pair(midpoint(start, end), entries.filter { it.createdAt in start until end })
                }
            }

            TimeRange.YEAR -> {
                (11 downTo 0).map { monthsAgo ->
                    val start = firstOfMonth(anchorMs, monthsAgo)
                    val end = firstOfMonth(anchorMs, monthsAgo - 1)
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
                // only very long histories fall back to 20 even bins. These bins
                // start at the oldest entry rather than at midnight, so they are
                // elapsed-time spans by design and take no DST adjustment.
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

    // ── Heatmap builder ───────────────────────────────────────────────────────

    /**
     * Aggregates entries into one value per calendar day, then lays the days out
     * into GitHub-style week columns. Heatmaps always bucket daily regardless of
     * the selected lookback — the lookback only sets how far back the grid runs.
     */
    private fun buildHeatmapData(
        config: ChartConfig,
        entries: List<EventEntry>,
        fields: List<EventField>,
        anchorMs: Long,
        anchoredEndMs: Long?
    ): ChartData {
        val fieldId = config.numericFieldIds.firstOrNull() ?: return ChartData.Empty
        val field = fields.firstOrNull { it.id == fieldId } ?: return ChartData.Empty
        val isBoolean = field.type == FieldType.BOOLEAN

        // Collect the day's values in chronological order so LATEST resolves to
        // the last entry of the day.
        val perDayValues = linkedMapOf<Long, MutableList<Double>>()
        for (entry in entries.sortedBy { it.createdAt }) {
            val value = heatmapValue(entry.fieldValues[fieldId], isBoolean) ?: continue
            val day = midnightOf(entry.createdAt)
            perDayValues.getOrPut(day) { mutableListOf() }.add(value)
        }
        if (perDayValues.isEmpty()) return ChartData.InsufficientData

        val perDayAggregated = perDayValues.mapValues { aggregate(it.value, config.aggregation) }

        // Grid runs from the window start (or earliest data for ALL) to the end
        // day, with the start backed up to the first day of its week so every
        // column is a full Sun–Sat (locale-aware) stack. ALL ends at the latest
        // entry — mirroring the cartesian ALL range — to avoid an empty tail when
        // the newest data is old; fixed windows end at the anchor (today, or the
        // latest entry when the now-anchored window would be empty) and start at
        // the same windowStartMs boundary the entry filter applied.
        val firstDataDay = perDayValues.keys.min()
        val lastDay = if (config.timeRangeDays == null) midnightOf(perDayValues.keys.max())
                      else midnightOf(anchorMs)
        val windowStartDay = if (config.timeRangeDays == null) firstDataDay
                             else windowStartMs(config, config.timeRangeDays, anchorMs)
        val firstWeekday = Calendar.getInstance().firstDayOfWeek
        val gridStart = startOfWeek(minOf(windowStartDay, lastDay), firstWeekday)

        val columns = mutableListOf<ChartData.Heatmap.Week>()
        var week = arrayOfNulls<ChartData.Heatmap.Day>(7)
        var cursor = gridStart
        while (cursor <= lastDay) {
            val row = weekdayRow(cursor, firstWeekday)
            week[row] = ChartData.Heatmap.Day(
                dateMs = cursor,
                value = perDayAggregated[cursor],
                entryCount = perDayValues[cursor]?.size ?: 0
            )
            if (row == 6) {
                columns.add(ChartData.Heatmap.Week(week.toList()))
                week = arrayOfNulls(7)
            }
            cursor = addDays(cursor, 1)
        }
        if (week.any { it != null }) columns.add(ChartData.Heatmap.Week(week.toList()))

        val dayValues = perDayAggregated.values
        return ChartData.Heatmap(
            columns = columns,
            monthLabels = monthLabels(gridStart, columns.size),
            weekdayLabels = weekdayLabels(firstWeekday),
            minValue = dayValues.min(),
            maxValue = dayValues.max(),
            diverging = dayValues.min() < 0.0,
            daysWithData = perDayValues.size,
            unit = if (config.showUnits && !isBoolean) field.unit else "",
            fieldName = field.name,
            aggregation = config.aggregation,
            anchoredEndMs = anchoredEndMs
        )
    }

    /** Yes/No → 1/0; numeric passes through; anything else is not heatmap-able. */
    private fun heatmapValue(value: FieldValue?, isBoolean: Boolean): Double? =
        if (isBoolean) (value as? FieldValue.Bool)?.let { if (it.value) 1.0 else 0.0 }
        else (value as? FieldValue.Numeric)?.value

    /** Row index 0..6 for a day, honoring the locale's first day of week. */
    private fun weekdayRow(dayMs: Long, firstWeekday: Int): Int {
        val dow = Calendar.getInstance().apply { timeInMillis = dayMs }.get(Calendar.DAY_OF_WEEK)
        return (dow - firstWeekday + 7) % 7
    }

    /** Midnight of the first day of [dayMs]'s week. */
    private fun startOfWeek(dayMs: Long, firstWeekday: Int): Long =
        addDays(dayMs, -weekdayRow(dayMs, firstWeekday))

    /** Short weekday names in row order (e.g. Sun..Sat or Mon..Sun by locale). */
    private fun weekdayLabels(firstWeekday: Int): List<String> {
        val names = DateFormatSymbols.getInstance().shortWeekdays // [1]=Sun..[7]=Sat
        return (0 until 7).map { row -> names[(firstWeekday - 1 + row) % 7 + 1] }
    }

    /** A month label at the first column each calendar month appears over. */
    private fun monthLabels(gridStart: Long, columnCount: Int): List<ChartData.Heatmap.MonthLabel> {
        val fmt = SimpleDateFormat("MMM", Locale.getDefault())
        val labels = mutableListOf<ChartData.Heatmap.MonthLabel>()
        var lastMonth = -1
        for (col in 0 until columnCount) {
            val cal = Calendar.getInstance().apply { timeInMillis = addDays(gridStart, col * 7) }
            val month = cal.get(Calendar.MONTH)
            if (month != lastMonth) {
                labels.add(ChartData.Heatmap.MonthLabel(col, fmt.format(cal.time)))
                lastMonth = month
            }
        }
        return labels
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * True when [config] references fields that no longer fit their chart role:
     * a plotted field that is missing or no longer [FieldType.NUMERIC], or — for
     * pie charts — a group-by field that is missing or no longer a choice /
     * multi-select. Such a chart cannot be rendered and should prompt edit/delete.
     */
    private fun isStaleConfig(config: ChartConfig, fields: List<EventField>): Boolean {
        val byId = fields.associateBy { it.id }
        if (config.numericFieldIds.isEmpty()) return true
        // Heatmaps plot exactly one Numeric or Yes/No field, so they have their
        // own eligibility rule before the numeric-only check below.
        if (config.type == ChartType.HEATMAP) {
            val type = config.numericFieldIds.singleOrNull()?.let { byId[it]?.type }
            return type != FieldType.NUMERIC && type != FieldType.BOOLEAN
        }
        if (config.numericFieldIds.any { byId[it]?.type != FieldType.NUMERIC }) return true
        if (config.type == ChartType.PIE) {
            val groupType = config.groupByFieldId?.let { byId[it]?.type }
            if (groupType != FieldType.CHOICE && groupType != FieldType.MULTI_SELECT) return true
        }
        return false
    }

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

    /**
     * DST-safe day arithmetic: shifts a midnight timestamp by whole calendar
     * days, keeping the result on local midnight where a fixed 24-hour step
     * would land an hour off on either side of a transition. Shared by the
     * window filter, the cartesian buckets, and the heatmap grid — they have
     * to agree on where a day starts.
     */
    private fun addDays(dayMs: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = dayMs
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    /** Label position for a bucket: its own midpoint, so 23/25-hour days sit right. */
    private fun midpoint(startMs: Long, endMs: Long): Long = startMs + (endMs - startMs) / 2

    /** Midnight on the 1st of the month [monthsAgo] whole months before [anchorMs]'s. */
    private fun firstOfMonth(anchorMs: Long, monthsAgo: Int): Long = Calendar.getInstance().apply {
        timeInMillis = anchorMs
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, -monthsAgo)
    }.timeInMillis
}
