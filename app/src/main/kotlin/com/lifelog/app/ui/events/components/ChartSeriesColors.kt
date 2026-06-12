package com.lifelog.app.ui.events.components

import androidx.compose.ui.graphics.Color
import com.lifelog.app.domain.model.ChartData

/**
 * Fallback hues for series left on "Auto" beyond the first, so several auto
 * series in one chart stay distinguishable from the accent and each other.
 */
private val AUTO_SERIES_FALLBACKS = listOf(
    Color(0xFF409CFF), Color(0xFF4CAF50), Color(0xFFFF9800),
    Color(0xFF9C27B0), Color(0xFF00BCD4), Color(0xFFF44336)
)

/**
 * Resolves each series to a concrete color: explicit choice wins; the first
 * "Auto" series inherits the event accent; later autos take fixed fallbacks.
 */
internal fun resolveSeriesColors(
    series: List<ChartData.Cartesian.Series>,
    eventAccentColor: Color
): List<Color> {
    var autoCount = 0
    return series.map { s ->
        val explicit = s.colorArgb?.let { Color(it) }
        if (explicit != null) {
            explicit
        } else {
            val fallback = if (autoCount == 0) eventAccentColor
                           else AUTO_SERIES_FALLBACKS[(autoCount - 1) % AUTO_SERIES_FALLBACKS.size]
            autoCount++
            fallback
        }
    }
}
