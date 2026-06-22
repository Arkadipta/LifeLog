package com.lifelog.app.ui.events.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifelog.app.domain.model.AggregationStrategy
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.ui.theme.LifeLogTheme
import com.lifelog.app.ui.theme.Sizing
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MonthLabelFontSize = 9.sp
private val GutterWidth = 26.dp
private val CellGap = 3.dp
private val MinCell = 9.dp
private val MaxCell = 16.dp
private val StripHeight = 30.dp
private val CellShape = RoundedCornerShape(2.dp)

/**
 * GitHub-style contribution heatmap: week columns laid left (oldest) to right
 * (newest), one row per day of week. Cell color comes from the Material 3 tonal
 * scale in [rememberHeatmapPalette]; tapping a cell reveals its date, aggregated
 * value, and contributing-entry count in the strip below the grid.
 *
 * The grid is pre-built in the domain layer, so this stays presentation-only and
 * lets [LazyRow] virtualize the columns for multi-year histories.
 */
@Composable
fun HeatmapChartContent(data: ChartData.Heatmap, modifier: Modifier = Modifier) {
    // Selection resets whenever the underlying data changes.
    var selected by remember(data) { mutableStateOf<ChartData.Heatmap.Day?>(null) }
    val palette = rememberHeatmapPalette(diverging = data.diverging)
    val monthByColumn = remember(data) { data.monthLabels.associate { it.columnIndex to it.label } }

    val listState = rememberLazyListState()
    // Open scrolled to the most recent week (the true end of the list).
    LaunchedEffect(data.columns.size) {
        if (data.columns.isNotEmpty()) listState.scrollToItem(data.columns.lastIndex, 100_000)
    }

    // Size the month-label band from the (font-scaled) text height plus a fixed
    // gap, so the labels never clip into the grid — at any density or font scale.
    val monthLabelHeight = with(LocalDensity.current) { MonthLabelFontSize.toDp() } * 1.4f + 8.dp

    Column(modifier = modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val cell = ((maxHeight - monthLabelHeight - CellGap * 6) / 7f)
                .coerceIn(MinCell, MaxCell)

            Row(modifier = Modifier.fillMaxWidth()) {
                WeekdayGutter(cell = cell, monthLabelHeight = monthLabelHeight, labels = data.weekdayLabels)
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(CellGap)
                ) {
                    itemsIndexed(data.columns) { index, week ->
                        WeekColumn(
                            week = week,
                            monthLabel = monthByColumn[index],
                            monthLabelHeight = monthLabelHeight,
                            minValue = data.minValue,
                            maxValue = data.maxValue,
                            palette = palette,
                            cell = cell,
                            selectedDateMs = selected?.dateMs,
                            onSelect = { day ->
                                selected = if (selected?.dateMs == day.dateMs) null else day
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(CellGap))
        Box(
            modifier = Modifier.fillMaxWidth().height(StripHeight),
            contentAlignment = Alignment.CenterStart
        ) {
            val sel = selected
            if (sel == null) HeatmapLegend(palette)
            else SelectedDayDetail(day = sel, data = data, palette = palette)
        }
    }
}

@Composable
private fun WeekdayGutter(cell: Dp, monthLabelHeight: Dp, labels: List<String>) {
    Column(modifier = Modifier.width(GutterWidth)) {
        Spacer(Modifier.height(monthLabelHeight))
        Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
            labels.forEachIndexed { row, label ->
                Box(
                    modifier = Modifier.height(cell).fillMaxWidth().padding(end = 4.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    // Alternating rows only (Mon/Wed/Fri-style) to avoid crowding.
                    if (row % 2 == 1) {
                        Text(
                            text = label,
                            fontSize = 8.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekColumn(
    week: ChartData.Heatmap.Week,
    monthLabel: String?,
    monthLabelHeight: Dp,
    minValue: Double,
    maxValue: Double,
    palette: HeatmapPalette,
    cell: Dp,
    selectedDateMs: Long?,
    onSelect: (ChartData.Heatmap.Day) -> Unit
) {
    Column {
        // The label is top-aligned, so the band's extra height becomes a gap
        // above the grid — keeping descenders ("p", "y") clear of the cells.
        Box(modifier = Modifier.height(monthLabelHeight).width(cell)) {
            if (monthLabel != null) {
                // Unbounded so the label overflows past this narrow column into
                // the (empty) month slot of the columns to its right, like GitHub.
                Text(
                    text = monthLabel,
                    fontSize = MonthLabelFontSize,
                    maxLines = 1,
                    softWrap = false,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.wrapContentWidth(Alignment.Start, unbounded = true)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(CellGap)) {
            week.days.forEach { day ->
                HeatmapCell(
                    day = day,
                    minValue = minValue,
                    maxValue = maxValue,
                    palette = palette,
                    cell = cell,
                    isSelected = day != null && day.dateMs == selectedDateMs,
                    onSelect = onSelect
                )
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    day: ChartData.Heatmap.Day?,
    minValue: Double,
    maxValue: Double,
    palette: HeatmapPalette,
    cell: Dp,
    isSelected: Boolean,
    onSelect: (ChartData.Heatmap.Day) -> Unit
) {
    // Null = padding outside the grid range (e.g. future days) → blank space.
    if (day == null) {
        Spacer(Modifier.size(cell))
        return
    }
    val color = palette.colorFor(day.value, minValue, maxValue)
    val borderColor = if (isSelected) MaterialTheme.colorScheme.onSurface else palette.cellBorder
    val borderWidth = if (isSelected) 1.5.dp else 1.dp
    val label = remember(day) { cellDescription(day) }

    Box(
        modifier = Modifier
            .size(cell)
            .clip(CellShape)
            .background(color, CellShape)
            .border(borderWidth, borderColor, CellShape)
            .clickable(onClickLabel = "Show day details") { onSelect(day) }
            .semantics { contentDescription = label }
    )
}

@Composable
private fun HeatmapLegend(palette: HeatmapPalette) {
    val swatches = palette.legendSwatches()
    // Diverging scales read negative → positive; single-hue reads low → high.
    val (startLabel, endLabel) = if (palette.diverging) "−" to "+" else "Less" to "More"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = startLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        swatches.forEach { swatch ->
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(CellShape)
                    .background(swatch, CellShape)
                    .border(1.dp, palette.cellBorder, CellShape)
            )
        }
        Text(
            text = endLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SelectedDayDetail(
    day: ChartData.Heatmap.Day,
    data: ChartData.Heatmap,
    palette: HeatmapPalette
) {
    val swatch = palette.colorFor(day.value, data.minValue, data.maxValue)
    val text = remember(day, data.unit) { selectedDetailText(day, data.unit) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CellShape)
                .background(swatch, CellShape)
                .border(1.dp, palette.cellBorder, CellShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Formatting ────────────────────────────────────────────────────────────────

// Shared single-thread (composition) formatters — avoids reallocating per cell.
private val cellDateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())
private val valueFormat = DecimalFormat("#,##0.##")

private fun cellDescription(day: ChartData.Heatmap.Day): String {
    val date = cellDateFormat.format(Date(day.dateMs))
    return if (day.value == null) "$date: no entry"
    else "$date: ${valueFormat.format(day.value)}, ${entryCountLabel(day.entryCount)}"
}

private fun selectedDetailText(day: ChartData.Heatmap.Day, unit: String): String {
    val date = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(day.dateMs))
    if (day.value == null) return "$date  ·  No entry"
    val value = valueFormat.format(day.value) + if (unit.isNotBlank()) " $unit" else ""
    return "$date  ·  $value  ·  ${entryCountLabel(day.entryCount)}"
}

private fun entryCountLabel(count: Int): String =
    if (count == 1) "1 entry" else "$count entries"

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 300, heightDp = 224)
@Composable
private fun HeatmapPositivePreview() {
    LifeLogTheme {
        ChartCardPreviewFrame {
            HeatmapChartContent(data = sampleHeatmap(diverging = false))
        }
    }
}

@Preview(showBackground = true, widthDp = 300, heightDp = 224)
@Composable
private fun HeatmapDivergingPreview() {
    LifeLogTheme {
        ChartCardPreviewFrame {
            HeatmapChartContent(data = sampleHeatmap(diverging = true))
        }
    }
}

@Composable
private fun ChartCardPreviewFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 300.dp, height = Sizing.chartCard)
            .padding(12.dp)
    ) { content() }
}

/** Deterministic grid for previews: 22 weeks of pseudo-random daily values. */
private fun sampleHeatmap(diverging: Boolean): ChartData.Heatmap {
    val day = 86_400_000L
    val base = 1_704_067_200_000L // 2024-01-01 UTC, fixed for reproducibility
    val weeks = (0 until 22).map { w ->
        ChartData.Heatmap.Week(
            (0 until 7).map { r ->
                val idx = w * 7 + r
                when {
                    w == 21 && r > 2 -> null                 // future padding
                    (idx * 13) % 7 == 0 -> ChartData.Heatmap.Day(base + idx * day, null, 0)
                    else -> {
                        val swing = ((idx * 31) % 21) - if (diverging) 10 else 0
                        ChartData.Heatmap.Day(base + idx * day, swing.toDouble(), (idx % 4) + 1)
                    }
                }
            }
        )
    }
    val values = weeks.flatMap { it.days }.mapNotNull { it?.value }
    return ChartData.Heatmap(
        columns = weeks,
        monthLabels = listOf(
            ChartData.Heatmap.MonthLabel(0, "Jan"),
            ChartData.Heatmap.MonthLabel(5, "Feb"),
            ChartData.Heatmap.MonthLabel(9, "Mar"),
            ChartData.Heatmap.MonthLabel(13, "Apr"),
            ChartData.Heatmap.MonthLabel(18, "May")
        ),
        weekdayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"),
        minValue = values.min(),
        maxValue = values.max(),
        diverging = diverging,
        daysWithData = values.size,
        unit = if (diverging) "P/L" else "cups",
        fieldName = if (diverging) "Net P/L" else "Water",
        aggregation = AggregationStrategy.SUM
    )
}
