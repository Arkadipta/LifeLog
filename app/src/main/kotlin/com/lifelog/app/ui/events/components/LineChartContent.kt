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
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.point
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
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
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(data) {
        modelProducer.runTransaction {
            lineSeries {
                data.series.forEach { series ->
                    series(
                        x = series.points.map { it.timestampMs.toFloat() },
                        y = series.points.map { it.value.toFloat() }
                    )
                }
            }
        }
    }

    val onSurface = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline
    val guidelineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    val axisLabel = rememberTextComponent(color = onSurface)
    val axisLine = rememberLineComponent(color = outlineColor, thickness = 3.dp)
    val axisTick = rememberLineComponent(color = outlineColor, thickness = 3.dp)
    val guideline = rememberLineComponent(color = guidelineColor, thickness = 0.dp)

    val lines = remember(accentColor, data.series.size) {
        data.series.indices.map { i ->
            accentColor.copy(alpha = (1f - i * 0.25f).coerceAtLeast(0.4f))
        }
    }

    val dateFormatter = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }

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
                                    6.dp
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
            ),
            bottomAxis = HorizontalAxis.rememberBottom(
                label = axisLabel,
                line = axisLine,
                tick = axisTick,
                guideline = null,
                valueFormatter = { _, value, _ ->
                    dateFormatter.format(Date(value.toLong()))
                }
            )
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxSize()
    )
}
