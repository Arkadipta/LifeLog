package com.lifelog.app.ui.events.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.lifelog.app.domain.model.ChartData
import kotlin.math.min

/** Pie plot only — the color key is rendered by the card's shared legend. */
@Composable
fun PieChartContent(data: ChartData.Pie, modifier: Modifier = Modifier) {
    val total = data.slices.sumOf { it.value }.toFloat()
    if (total == 0f) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val diameter = min(size.width, size.height) * 0.9f
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        val arcSize = Size(diameter, diameter)
        var startAngle = -90f

        data.slices.forEach { slice ->
            val sweep = (slice.value / total * 360f).toFloat()
            drawArc(
                color = Color(slice.colorArgb),
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = true,
                topLeft = topLeft,
                size = arcSize
            )
            startAngle += sweep
        }
    }
}
