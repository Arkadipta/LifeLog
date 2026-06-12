package com.lifelog.app.ui.events.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.ChartTickGenerator
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.ChartType
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
import com.patrykandpatrick.vico.core.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.data.ExtraStore
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import kotlin.math.roundToInt

@Composable
fun CartesianChartContent(data: ChartData.Cartesian, eventAccentColor: Color, modifier: Modifier = Modifier) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            when (data.type) {
                ChartType.BAR -> columnSeries {
                    data.series.forEach { s ->
                        series(s.points.map { it.bucketIndex }, s.points.map { it.value })
                    }
                }
                else -> lineSeries {
                    data.series.forEach { s ->
                        series(s.points.map { it.bucketIndex }, s.points.map { it.value })
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

    val seriesColors = resolveSeriesColors(data.series, eventAccentColor)
    val bucketCount = data.bucketTimestamps.size
    // The x-range always spans every bucket in the selected window, so the
    // axis stays meaningful even when only a few buckets contain data.
    val rangeProvider = remember(bucketCount) { fullSpanRangeProvider(bucketCount) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val maxTicks = (maxWidth / 56.dp).toInt().coerceIn(3, 6)
        val ticks = remember(data.bucketTimestamps, maxTicks) {
            ChartTickGenerator.generate(data.bucketTimestamps, maxTicks)
        }
        val tickValues = remember(ticks) { ticks.map { it.bucketIndex.toDouble() } }
        val tickLabels = remember(ticks) { ticks.associate { it.bucketIndex to it.label } }

        val layer = when (data.type) {
            ChartType.BAR -> rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                    seriesColors.map { color ->
                        rememberLineComponent(
                            color = color,
                            thickness = columnThickness(bucketCount, data.series.size),
                            shape = CorneredShape.rounded(25)
                        )
                    }
                ),
                rangeProvider = rangeProvider
            )
            else -> rememberLineCartesianLayer(
                lineProvider = LineCartesianLayer.LineProvider.series(
                    seriesColors.map { color ->
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
                ),
                rangeProvider = rangeProvider
            )
        }

        CartesianChartHost(
            chart = rememberCartesianChart(
                layer,
                startAxis = VerticalAxis.rememberStart(
                    label = axisLabel, line = axisLine, tick = axisTick, guideline = guideline,
                    itemPlacer = RobustCountItemPlacer
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = axisLabel, line = axisLine, tick = axisTick, guideline = null,
                    itemPlacer = remember(tickValues) { ExplicitTickItemPlacer(tickValues) },
                    valueFormatter = { _, value, _ -> tickLabels[value.roundToInt()] ?: "" }
                )
            ),
            modelProducer = modelProducer,
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** Bars thin out as buckets multiply so dense windows never overlap. */
private fun columnThickness(bucketCount: Int, seriesCount: Int): Dp {
    val perBucket = when {
        bucketCount <= 8 -> 20.dp
        bucketCount <= 16 -> 12.dp
        else -> 6.dp
    }
    return (perBucket / seriesCount.coerceAtLeast(1)).coerceAtLeast(3.dp)
}

/** Fixed x-range covering all buckets; y stays on Vico's auto scaling. */
private fun fullSpanRangeProvider(bucketCount: Int): CartesianLayerRangeProvider =
    object : CartesianLayerRangeProvider {
        private val autoY = CartesianLayerRangeProvider.auto()
        private val padHalf = bucketCount <= 1

        override fun getMinX(minX: Double, maxX: Double, extraStore: ExtraStore) =
            if (padHalf) -0.5 else 0.0

        override fun getMaxX(minX: Double, maxX: Double, extraStore: ExtraStore) =
            if (padHalf) 0.5 else (bucketCount - 1).toDouble()

        override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore) =
            autoY.getMinY(minY, maxY, extraStore)

        override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore) =
            autoY.getMaxY(minY, maxY, extraStore)
    }
