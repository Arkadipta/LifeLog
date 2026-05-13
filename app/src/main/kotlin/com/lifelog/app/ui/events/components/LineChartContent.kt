package com.lifelog.app.ui.events.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.TimeRange
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.fill
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LineChartContent(data: ChartData.Line, accentColor: Color, modifier: Modifier = Modifier) {
    val totalPoints = data.bucketTimestamps.size
    val tickStep = (totalPoints / 4).coerceAtLeast(1)

    // Sparse: only 1 data point across all series → render as horizontal level line
    val allPoints = data.series.flatMap { it.points }
    if (allPoints.size <= 1) {
        SparseHorizontalLine(
            value = allPoints.firstOrNull()?.value ?: 0.0,
            color = accentColor,
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            lineSeries {
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

    val lines = remember(accentColor, data.series.size) {
        data.series.indices.map { i ->
            accentColor.copy(alpha = (1f - i * 0.25f).coerceAtLeast(0.4f))
        }
    }

    val xValueFormatter = rememberXFormatter(data.timeRange, data.bucketTimestamps, tickStep)

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    lines.map { color ->
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(color)),
                            pointProvider = LineCartesianLayer.PointProvider.single(
                                LineCartesianLayer.point(
                                    rememberShapeComponent(
                                        color = color,
                                        shape = CorneredShape.rounded(50)
                                    ),
                                    4.dp
                                )
                            )
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

/** Dashed horizontal line for single-point sparse data. */
@Composable
private fun SparseHorizontalLine(value: Double, color: Color, modifier: Modifier = Modifier) {
    val label = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height / 2f
            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(bottom = 4.dp)
        )
    }
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
