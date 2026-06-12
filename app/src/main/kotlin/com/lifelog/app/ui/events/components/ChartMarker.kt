package com.lifelog.app.ui.events.components

import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifelog.app.domain.ChartTickGenerator
import com.lifelog.app.domain.model.ChartData
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.dimensions
import com.patrykandpatrick.vico.core.cartesian.CartesianDrawingContext
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.core.cartesian.marker.CartesianMarkerValueFormatter
import com.patrykandpatrick.vico.core.common.shape.CorneredShape
import com.patrykandpatrick.vico.core.common.shape.MarkerCorneredShape
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Tap-to-inspect tooltip in the style of an M3 plain tooltip: bucket date on
 * the first line, exact per-series values (color-coded, with units) on the
 * second, plus a guideline through the touched bucket.
 */
@Composable
internal fun rememberChartMarker(
    data: ChartData.Cartesian,
    seriesColors: List<Color>
): CartesianMarker {
    val label = rememberTextComponent(
        color = MaterialTheme.colorScheme.inverseOnSurface,
        textSize = 11.sp,
        textAlignment = Layout.Alignment.ALIGN_CENTER,
        lineCount = 2,
        padding = dimensions(horizontal = 10.dp, vertical = 5.dp),
        background = rememberShapeComponent(
            color = MaterialTheme.colorScheme.inverseSurface,
            shape = MarkerCorneredShape(CorneredShape.rounded(8f))
        )
    )
    val guideline = rememberLineComponent(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        thickness = 1.dp
    )
    val formatter = remember(data, seriesColors) {
        ChartMarkerValueFormatter(data, seriesColors.map { it.toArgb() })
    }
    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = formatter,
        guideline = guideline
    )
}

/**
 * Reads values straight from [ChartData] for the touched bucket: Vico's
 * grouped-column targeting passes only the nearest column, but inspection
 * should read the whole bucket across every series.
 */
private class ChartMarkerValueFormatter(
    private val data: ChartData.Cartesian,
    private val seriesColorsArgb: List<Int>
) : CartesianMarkerValueFormatter {

    private val dateFormat = SimpleDateFormat(
        ChartTickGenerator.markerPattern(data.bucketTimestamps), Locale.getDefault()
    )
    private val decimalFormat = DecimalFormat("#,##0.##;−#,##0.##")

    /** Set when every series shares one unit — appended once after the values. */
    private val sharedUnit = data.series.map { it.unit }.distinct().singleOrNull()
        ?.takeIf { it.isNotBlank() }

    override fun format(
        context: CartesianDrawingContext,
        targets: List<CartesianMarker.Target>
    ): CharSequence {
        val builder = SpannableStringBuilder()
        val bucketIndex = targets.first().x.roundToInt()

        data.bucketTimestamps.getOrNull(bucketIndex)?.let { timestamp ->
            builder.append(dateFormat.format(Date(timestamp)))
            builder.append("\n")
        }

        val rows = data.series.mapIndexedNotNull { i, series ->
            val point = series.points.firstOrNull { it.bucketIndex == bucketIndex }
                ?: return@mapIndexedNotNull null
            Triple(point.value, seriesColorsArgb.getOrNull(i), series.unit)
        }
        rows.forEachIndexed { i, (value, color, unit) ->
            val suffix = if (sharedUnit == null && unit.isNotBlank()) " $unit" else ""
            val text = decimalFormat.format(value) + suffix
            if (color != null) {
                builder.append(text, ForegroundColorSpan(color), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                builder.append(text)
            }
            if (i != rows.lastIndex) builder.append(", ")
        }
        sharedUnit?.let { builder.append(" $it") }
        return builder
    }
}
