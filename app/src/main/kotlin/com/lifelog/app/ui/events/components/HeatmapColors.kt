package com.lifelog.app.ui.events.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Resolves heatmap cell colors from the Material 3 palette using tonal
 * intensity (deliberately not the chart color picker). Two scales:
 *
 *  - Positive-only data → a single-hue ramp built from the theme's primary hue,
 *    light tone (low) to dark tone (high). In dark themes the ramp runs dim→bright.
 *  - Data with negatives → a diverging red↔neutral↔green scale so negative and
 *    positive periods are immediately distinguishable.
 *
 * Missing days (no entry) render as a ghosted neutral, kept visually distinct
 * from a real zero value, which always carries a scale tint.
 */
@Immutable
class HeatmapPalette(
    /** No-entry cell — a subtle neutral, distinct from any data color. */
    val missing: Color,
    /** Hairline border drawn on every cell so empty days still read as slots. */
    val cellBorder: Color,
    val diverging: Boolean,
    private val singleHue: List<Color>,
    private val negative: List<Color>,
    private val positive: List<Color>,
    private val zeroTint: Color
) {
    /** Color for an aggregated day [value]; null value = a missing day. */
    fun colorFor(value: Double?, min: Double, max: Double): Color {
        if (value == null) return missing
        return if (diverging) diverging(value, min, max) else single(value, min, max)
    }

    private fun single(value: Double, min: Double, max: Double): Color {
        if (max <= min) return singleHue.last() // constant series → full emphasis
        val t = ((value - min) / (max - min)).coerceIn(0.0, 1.0)
        return singleHue[bucket(t, singleHue.size)]
    }

    private fun diverging(value: Double, min: Double, max: Double): Color = when {
        value < 0.0 -> negative[bucket(if (min < 0.0) value / min else 0.0, negative.size)]
        value > 0.0 -> positive[bucket(if (max > 0.0) value / max else 0.0, positive.size)]
        else -> zeroTint
    }

    /** Swatches for the card legend, low→high (single) or negative→positive (diverging). */
    fun legendSwatches(): List<Color> =
        if (diverging) negative.reversed() + zeroTint + positive else singleHue

    private fun bucket(t: Double, size: Int): Int =
        (t.coerceIn(0.0, 1.0) * (size - 1)).roundToInt()
}

@Composable
fun rememberHeatmapPalette(diverging: Boolean): HeatmapPalette {
    val primary = MaterialTheme.colorScheme.primary
    val missing = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val border = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    return remember(primary, isDark, diverging) {
        val primaryHue = primary.toHsl().first
        HeatmapPalette(
            missing = missing,
            cellBorder = border,
            diverging = diverging,
            // Borrow only the hue from primary; impose saturation so the ramp
            // stays colorful (and distinct from the neutral missing cell) even
            // under a desaturated dynamic-color theme.
            singleHue = tonalRamp(primaryHue, steps = 5, isDark = isDark),
            negative = tonalRamp(hue = 12f, steps = 4, isDark = isDark),
            positive = tonalRamp(hue = 142f, steps = 4, isDark = isDark),
            // Zero sits at the diverging center: a pale warm neutral that still
            // reads as "filled", unlike the cool ghosted missing cell.
            zeroTint = hsl(48f, 0.18f, if (isDark) 0.34f else 0.84f)
        )
    }
}

/**
 * A Material-style tonal ramp of one hue, ordered low→high emphasis. Light
 * themes go from a light, soft tone to a dark, saturated one; dark themes go
 * dim→bright so each step reads against a dark surface.
 */
private fun tonalRamp(hue: Float, steps: Int, isDark: Boolean): List<Color> {
    val lLow = if (isDark) 0.26f else 0.90f
    val lHigh = if (isDark) 0.70f else 0.30f
    val sLow = if (isDark) 0.45f else 0.42f
    val sHigh = if (isDark) 0.85f else 0.80f
    return (0 until steps).map { i ->
        val t = if (steps <= 1) 1f else i.toFloat() / (steps - 1)
        hsl(hue, lerp(sLow, sHigh, t), lerp(lLow, lHigh, t))
    }
}

// ── HSL helpers (Compose has no HSL conversion) ───────────────────────────────

private fun hsl(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hp = ((h % 360f) + 360f) % 360f / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val (r, g, b) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c / 2f
    return Color(r + m, g + m, b + m)
}

private fun Color.toHsl(): Triple<Float, Float, Float> {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val d = max - min
    val l = (max + min) / 2f
    val s = if (d == 0f) 0f else d / (1f - abs(2f * l - 1f))
    val h = when {
        d == 0f -> 0f
        max == red -> 60f * (((green - blue) / d) % 6f)
        max == green -> 60f * (((blue - red) / d) + 2f)
        else -> 60f * (((red - green) / d) + 4f)
    }
    return Triple((h + 360f) % 360f, s, l)
}
