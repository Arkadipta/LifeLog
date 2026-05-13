package com.lifelog.app.ui.events.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.TimeRange
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BarChartContent(data: ChartData.Bar, accentColor: Color, modifier: Modifier = Modifier) {
    val totalPoints = data.bucketTimestamps.size
    val tickStep = (totalPoints / 4).coerceAtLeast(1)

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            columnSeries {
                data.series.forEach { series ->
                    series(
                        x = series.points.map { it.bucketIndex.toFloat() },
                        y = series.points.map { it.value.toFloat() }
                    )
                }
            }
        }
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline
    val guidelineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    val axisLabel = rememberTextComponent(color = onSurface)
    val axisLine = rememberLineComponent(color = outlineColor, thickness = 1.dp)
    val axisTick = rememberLineComponent(color = outlineColor, thickness = 1.dp)
    val guideline = rememberLineComponent(color = guidelineColor, thickness = 0.5.dp)

    val columnColors = remember(accentColor, data.series.size) {
        data.series.indices.map { i ->
            accentColor.copy(alpha = (1f - i * 0.25f).coerceAtLeast(0.4f))
        }
    }

    val xValueFormatter = rememberXFormatter(data.timeRange, data.bucketTimestamps, tickStep)

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    columnColors.map { color ->
                        rememberLineComponent(
                            color = color,
                            shape = CorneredShape.rounded(25)
                        )
                    }
                )
            ),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabel,
                line = axisLine,
                tick = axisTick,
                guideline = guideline,
                itemPlacer = remember { VerticalAxis.ItemPlacer.count(count = { 4 }) }
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel,
                line = axisLine,
                tick = axisTick,
                guideline = null,
                valueFormatter = { _, value, _ -> xValueFormatter(value.toInt()) }
            )
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun rememberXFormatter(
    timeRange: TimeRange,
    bucketTimestamps: List<Long>,
    tickStep: Int
): (Int) -> String {
    return remember(timeRange, bucketTimestamps, tickStep) {
        val fmt = xDateFormatter(timeRange)
        val timestamps = bucketTimestamps
        val step = tickStep
        { idx: Int ->
            if (idx % step == 0 && idx in timestamps.indices) fmt.format(Date(timestamps[idx]))
            else ""
        }
    }
}

private fun xDateFormatter(timeRange: TimeRange): SimpleDateFormat = when (timeRange) {
    TimeRange.DAY -> SimpleDateFormat("HH:mm", Locale.getDefault())
    TimeRange.WEEK -> SimpleDateFormat("EEE", Locale.getDefault())
    TimeRange.MONTH -> SimpleDateFormat("MMM d", Locale.getDefault())
    TimeRange.YEAR -> SimpleDateFormat("MMM", Locale.getDefault())
    TimeRange.ALL -> SimpleDateFormat("MMM yy", Locale.getDefault())
}
