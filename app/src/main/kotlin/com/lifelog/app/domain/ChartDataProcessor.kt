package com.lifelog.app.domain

import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.ChartType
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldValue

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
        val filtered = applyTimeRange(entries, config.timeRangeDays)
        if (filtered.isEmpty()) return ChartData.InsufficientData

        return when (config.type) {
            ChartType.LINE -> buildLineData(config, filtered, fields)
            ChartType.BAR -> buildBarData(config, filtered, fields)
            ChartType.PIE -> buildPieData(config, filtered, fields)
        }
    }

    private fun buildLineData(
        config: ChartConfig,
        entries: List<EventEntry>,
        fields: List<EventField>
    ): ChartData {
        val fieldMap = fields.associateBy { it.id }
        val sorted = entries.sortedBy { it.createdAt }
        val series = config.numericFieldIds.mapNotNull { fieldId ->
            val field = fieldMap[fieldId] ?: return@mapNotNull null
            val points = sorted.mapNotNull { entry ->
                val v = entry.fieldValues[fieldId] as? FieldValue.Numeric ?: return@mapNotNull null
                ChartData.Line.Point(entry.createdAt, v.value)
            }
            if (points.isEmpty()) null else ChartData.Line.Series(field.name, points)
        }
        return if (series.isEmpty()) ChartData.Empty else ChartData.Line(series)
    }

    private fun buildBarData(
        config: ChartConfig,
        entries: List<EventEntry>,
        fields: List<EventField>
    ): ChartData {
        val fieldMap = fields.associateBy { it.id }
        val sorted = entries.sortedBy { it.createdAt }
        val series = config.numericFieldIds.mapNotNull { fieldId ->
            val field = fieldMap[fieldId] ?: return@mapNotNull null
            val points = sorted.mapNotNull { entry ->
                val v = entry.fieldValues[fieldId] as? FieldValue.Numeric ?: return@mapNotNull null
                ChartData.Bar.Point(entry.createdAt, v.value)
            }
            if (points.isEmpty()) null else ChartData.Bar.Series(field.name, points)
        }
        return if (series.isEmpty()) ChartData.Empty else ChartData.Bar(series)
    }

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

    private fun applyTimeRange(entries: List<EventEntry>, days: Int?): List<EventEntry> {
        if (days == null) return entries
        val cutoff = System.currentTimeMillis() - days * 86_400_000L
        return entries.filter { it.createdAt >= cutoff }
    }
}
