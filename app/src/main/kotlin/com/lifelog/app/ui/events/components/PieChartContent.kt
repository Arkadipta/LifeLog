package com.lifelog.app.ui.events.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.lifelog.app.domain.model.ChartData
import java.text.DecimalFormat
import kotlin.math.min

/**
 * Donut-style pie: ring segments with small gaps and the total in the hole.
 * The color key is rendered by the card's shared legend.
 */
@Composable
fun PieChartContent(data: ChartData.Pie, modifier: Modifier = Modifier) {
    val total = data.slices.sumOf { it.value }
    if (total <= 0.0) return

    val totalText = remember(total) { DecimalFormat("#,##0.#").format(total) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outer = min(size.width, size.height) * 0.95f
            val ringWidth = outer * 0.15f
            val diameter = outer - ringWidth
            val topLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            val gapDegrees = if (data.slices.size > 1) 2.5f else 0f
            var startAngle = -90f

            data.slices.forEach { slice ->
                val sweep = (slice.value / total * 360.0).toFloat()
                val gap = min(gapDegrees, sweep * 0.25f)
                drawArc(
                    color = Color(slice.colorArgb),
                    startAngle = startAngle + gap / 2f,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = ringWidth)
                )
                startAngle += sweep
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = totalText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
            Text(
                text = data.unit.ifBlank { "Total" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
