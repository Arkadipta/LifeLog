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
import com.lifelog.app.domain.model.ChartType
import com.lifelog.app.domain.model.TimeRange
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
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
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CartesianChartContent(data: ChartData.Cartesian, accentColor: Color, modifier: Modifier = Modifier) {
    when (data.type) {
        ChartType.BAR -> BarChartContent(data, accentColor, modifier)
        else -> LineChartContent(data, accentColor, modifier)
    }
}

@Composable
private fun BarChartContent(data: ChartData.Cartesian, accentColor: Color, modifier: Modifier) {
    val tickStep = (data.bucketTimestamps.size / 4).coerceAtLeast(1)
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            columnSeries {
                data.series.forEach { series ->
                    val pts = series.points
                    if (pts.size == 1) {
                        val p = pts.first()
                        val synthIdx = if (p.bucketIndex > 0) p.bucketIndex - 1 else p.bucketIndex + 1
                        series(listOf(synthIdx.toFloat(), p.bucketIndex.toFloat()), listOf(0f, p.value.toFloat()))
                    } else {
                        series(pts.map { it.bucketIndex.toFloat() }, pts.map { it.value.toFloat() })
                    }
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
        data.series.indices.map { i -> accentColor.copy(alpha = (1f - i * 0.25f).coerceAtLeast(0.4f)) }
    }
    val xValueFormatter = rememberXFormatter(data.timeRange, data.bucketTimestamps)

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    columnColors.map { color ->
                        rememberLineComponent(color = color, thickness = 24.dp, shape = CorneredShape.rounded(25))
                    }
                )
            ),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabel, line = axisLine, tick = axisTick, guideline = guideline,
                itemPlacer = RobustCountItemPlacer
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel, line = axisLine, tick = axisTick, guideline = null,
                itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = tickStep),
                valueFormatter = { _, value, _ -> xValueFormatter(value.toInt()) }
            )
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun LineChartContent(data: ChartData.Cartesian, accentColor: Color, modifier: Modifier) {
    val tickStep = (data.bucketTimestamps.size / 4).coerceAtLeast(1)
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            lineSeries {
                data.series.forEach { series ->
                    val pts = series.points
                    if (pts.size == 1) {
                        val p = pts.first()
                        val synthIdx = if (p.bucketIndex > 0) p.bucketIndex - 1 else p.bucketIndex + 1
                        series(listOf(synthIdx.toFloat(), p.bucketIndex.toFloat()), listOf(p.value.toFloat(), p.value.toFloat()))
                    } else {
                        series(pts.map { it.bucketIndex.toFloat() }, pts.map { it.value.toFloat() })
                    }
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
        data.series.indices.map { i -> accentColor.copy(alpha = (1f - i * 0.25f).coerceAtLeast(0.4f)) }
    }
    val xValueFormatter = rememberXFormatter(data.timeRange, data.bucketTimestamps)

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    lines.map { color ->
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(fill(color)),
                            pointProvider = LineCartesianLayer.PointProvider.single(
                                LineCartesianLayer.point(
                                    rememberShapeComponent(color = color, shape = CorneredShape.rounded(50)),
                                    4.dp
                                )
                            )
                        )
                    }
                )
            ),
            startAxis = VerticalAxis.rememberStart(
                label = axisLabel, line = axisLine, tick = axisTick, guideline = guideline,
                itemPlacer = RobustCountItemPlacer
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel, line = axisLine, tick = axisTick, guideline = null,
                itemPlacer = HorizontalAxis.ItemPlacer.aligned(spacing = tickStep),
                valueFormatter = { _, value, _ -> xValueFormatter(value.toInt()) }
            )
        ),
        modelProducer = modelProducer,
        scrollState = rememberVicoScrollState(scrollEnabled = false),
        modifier = modifier.fillMaxSize()
    )
}

@Composable
private fun rememberXFormatter(timeRange: TimeRange, bucketTimestamps: List<Long>): (Int) -> String {
    return remember(timeRange, bucketTimestamps) {
        val fmt = xDateFormatter(timeRange)
        val timestamps = bucketTimestamps
        { idx: Int ->
            if (idx in timestamps.indices) fmt.format(Date(timestamps[idx])) else " "
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
