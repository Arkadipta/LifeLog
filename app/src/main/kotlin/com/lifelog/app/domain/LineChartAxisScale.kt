package com.lifelog.app.domain

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Computes the y-axis range and tick values for line charts.
 *
 * Unlike bar charts (which must start at zero so bar heights stay
 * proportional), line charts emphasize trend and variation. The axis is
 * therefore fit to the data: it spans `[dataMin - margin, dataMax + margin]`
 * so a narrow series (e.g. 79.5–80.1) fills the plot instead of being flattened
 * against a 0–80 scale. Ticks are placed on "nice" round values that fall
 * inside that range, capped at [DEFAULT_MAX_TICKS] for a clean, uncluttered axis
 * with consistent label widths.
 *
 * When a chart has several series, callers pass the global minimum and maximum
 * across all of them, so the scale frames every line together (e.g. systolic
 * 110–130 and diastolic 70–90 share one ~65–135 axis) rather than each series
 * being scaled independently.
 */
object LineChartAxisScale {

    const val DEFAULT_MAX_TICKS = 4

    /** Fraction of the data span added as breathing room above and below. */
    private const val MARGIN_FRACTION = 0.08

    /** Mantissas that make a tick step read as "round" (×10^k). */
    private val STEP_MANTISSAS = listOf(1.0, 2.0, 2.5, 5.0)

    private const val EPS = 1e-9

    data class Scale(
        val min: Double,
        val max: Double,
        val ticks: List<Double>,
        /** Fractional digits to render every tick label with, for uniform widths. */
        val decimals: Int,
    )

    /**
     * @param dataMin smallest value across all visible series
     * @param dataMax largest value across all visible series
     * @return the fitted scale, or `null` when the inputs are not finite (the
     *   caller should then fall back to automatic scaling).
     */
    fun compute(
        dataMin: Double,
        dataMax: Double,
        maxTicks: Int = DEFAULT_MAX_TICKS,
    ): Scale? {
        if (!dataMin.isFinite() || !dataMax.isFinite() || maxTicks < 1) return null

        val lo = minOf(dataMin, dataMax)
        val hi = maxOf(dataMin, dataMax)

        // Flat series: no variation to show, so center the constant value and
        // label it once. A padded window keeps the line off the frame edges.
        if (hi - lo <= EPS) {
            val pad = if (abs(lo) <= EPS) 1.0 else abs(lo) * MARGIN_FRACTION
            return Scale(lo - pad, lo + pad, listOf(lo), decimalsOf(if (abs(lo) <= EPS) 1.0 else lo))
        }

        val margin = (hi - lo) * MARGIN_FRACTION
        val axisMin = lo - margin
        val axisMax = hi + margin

        val step = niceStep(axisMin, axisMax, maxTicks)
        val ticks = ticksWithin(axisMin, axisMax, step)
        return Scale(axisMin, axisMax, ticks, decimalsOf(step))
    }

    /** Smallest "nice" step whose multiples land at most [maxTicks] times in `[lo, hi]`. */
    private fun niceStep(lo: Double, hi: Double, maxTicks: Int): Double {
        var step = niceCeil((hi - lo) / maxTicks)
        // Aligning ticks to step multiples (floor/ceil at the edges) can add one
        // beyond the estimate, so grow the step until the realized count fits.
        while (countMultiples(lo, hi, step) > maxTicks) {
            step = niceCeil(step * (1 + 1e-6))
        }
        return step
    }

    /** Smallest value of the form m·10^k (m ∈ [STEP_MANTISSAS]) that is ≥ [value]. */
    private fun niceCeil(value: Double): Double {
        require(value > 0)
        var exp = floor(log10(value)).toInt()
        while (true) {
            val base = 10.0.pow(exp)
            for (m in STEP_MANTISSAS) {
                val candidate = m * base
                if (candidate >= value * (1 - EPS)) return candidate
            }
            exp++
        }
    }

    private fun countMultiples(lo: Double, hi: Double, step: Double): Int {
        val first = ceil(lo / step - EPS)
        val last = floor(hi / step + EPS)
        return (last - first + 1).toInt().coerceAtLeast(0)
    }

    private fun ticksWithin(lo: Double, hi: Double, step: Double): List<Double> {
        val first = ceil(lo / step - EPS).toLong()
        val last = floor(hi / step + EPS).toLong()
        if (last < first) return emptyList()
        return (first..last).map { it * step }
    }

    /** Fractional digits needed to print [step] exactly (e.g. 20→0, 2.5→1, 0.25→2). */
    private fun decimalsOf(step: Double): Int {
        var d = 0
        var s = abs(step)
        while (d < 6 && abs(s - Math.round(s)) > EPS) {
            s *= 10
            d++
        }
        return d
    }
}
