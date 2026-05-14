package com.lifelog.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.ChartType
import com.lifelog.app.domain.model.TimeRange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.min
import kotlin.math.pow

/**
 * Renders [ChartData] to a transparent [Bitmap] using the hardware Canvas API.
 * The background is left fully transparent so the widget's own surface colour
 * shows through without any jarring colour mismatch.
 * All logical sizes are in dp-equivalent units, scaled by [density].
 */
object ChartBitmapRenderer {

    fun render(
        data: ChartData,
        widthPx: Int,
        heightPx: Int,
        isDark: Boolean,
        density: Float = 2f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        // No drawColor call → bitmap stays fully transparent
        val canvas = Canvas(bitmap)

        when (data) {
            is ChartData.Pie -> renderPie(canvas, data, widthPx, heightPx, isDark, density)
            is ChartData.Cartesian -> when (data.type) {
                ChartType.BAR -> renderCartesian(canvas, data, widthPx, heightPx, isDark, density, isBar = true)
                else          -> renderCartesian(canvas, data, widthPx, heightPx, isDark, density, isBar = false)
            }
            else -> renderPlaceholder(canvas, widthPx, heightPx, isDark, density)
        }

        return bitmap
    }

    // ── Pie ───────────────────────────────────────────────────────────────────

    private fun renderPie(
        canvas: Canvas, data: ChartData.Pie,
        width: Int, height: Int,
        isDark: Boolean, density: Float
    ) {
        val total = data.slices.sumOf { it.value }.toFloat()
        if (total == 0f) { renderPlaceholder(canvas, width, height, isDark, density); return }

        val legendItemH = 16f * density
        val legendPad   = 6f  * density
        val legendH = if (data.slices.size > 1)
            (data.slices.size.coerceAtMost(4) * legendItemH + legendPad * 2f).coerceAtMost(height * 0.28f)
        else 0f

        val chartAreaH = height - legendH
        val diameter   = min(width.toFloat(), chartAreaH) * 0.78f
        val left = (width - diameter) / 2f
        val top  = (chartAreaH - diameter) / 2f
        val oval = RectF(left, top, left + diameter, top + diameter)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var startAngle = -90f
        data.slices.forEach { slice ->
            paint.color = slice.colorArgb
            val sweep = (slice.value / total * 360f).toFloat()
            canvas.drawArc(oval, startAngle, sweep, true, paint)
            startAngle += sweep
        }

        // Thin separator between slices — semi-transparent, works on any bg
        paint.style       = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color       = if (isDark) 0x40FFFFFF.toInt() else 0x40000000
        startAngle = -90f
        data.slices.forEach { slice ->
            val sweep = (slice.value / total * 360f).toFloat()
            canvas.drawArc(oval, startAngle, sweep, true, paint)
            startAngle += sweep
        }
        paint.style = Paint.Style.FILL

        // Legend
        if (legendH > 0f) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize  = 10f * density
                typeface  = Typeface.DEFAULT
                color     = labelColorFor(isDark)
            }
            val dp = Paint(Paint.ANTI_ALIAS_FLAG)
            val dotR  = 4f * density
            val startY = chartAreaH + legendPad + tp.textSize

            data.slices.take(4).forEachIndexed { i, slice ->
                val y    = startY + i * legendItemH
                val dotX = 12f * density
                dp.color  = slice.colorArgb
                canvas.drawCircle(dotX, y - tp.textSize / 3f, dotR, dp)
                tp.color = labelColorFor(isDark)
                val lbl = slice.label.let { if (it.length > 14) it.take(13) + "…" else it }
                canvas.drawText(lbl, dotX + dotR + 5f * density, y, tp)
            }
        }
    }

    // ── Bar + Line (unified) ──────────────────────────────────────────────────

    private fun renderCartesian(
        canvas: Canvas, data: ChartData.Cartesian,
        width: Int, height: Int,
        isDark: Boolean, density: Float,
        isBar: Boolean
    ) {
        val allPoints = data.series.flatMap { it.points }
        if (allPoints.isEmpty()) { renderPlaceholder(canvas, width, height, isDark, density); return }

        val maxVal  = allPoints.maxOf { it.value }.coerceAtLeast(1.0)
        val niceMax = niceValue(maxVal)
        val step    = niceMax / 4.0          // 4 grid bands

        // Measure the widest Y label so we can set padL precisely
        val labelTextSize = 9f * density
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize  = labelTextSize
            typeface  = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
            color     = labelColorFor(isDark)
        }
        val widestYLabel = formatValue(niceMax)
        val yLabelW = labelPaint.measureText(widestYLabel)

        val padL = yLabelW + 6f * density     // label + small gap
        val padR = 6f  * density
        val padT = 6f  * density
        val padB = 16f * density              // room for X labels
        val chartW = width  - padL - padR
        val chartH = height - padT - padB

        // ── Grid + Y-axis ticks + labels ─────────────────────────────────────
        val gridPaint = Paint().apply {
            color       = gridColorFor(isDark)
            strokeWidth = 0.75f * density
            isAntiAlias = true
        }
        val axisPaint = Paint().apply {
            color       = axisColorFor(isDark)
            strokeWidth = 1f * density
            isAntiAlias = true
        }
        val tickLen = 3f * density

        for (i in 0..4) {
            val frac = i / 4f
            val y    = padT + chartH * (1f - frac)
            if (i > 0) canvas.drawLine(padL, y, padL + chartW, y, gridPaint)

            // Y tick
            canvas.drawLine(padL - tickLen, y, padL, y, axisPaint)

            // Y label
            val value = step * i
            labelPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(formatValue(value), padL - tickLen - 2f * density, y + labelTextSize / 3f, labelPaint)
        }

        // Y-axis and X-axis lines
        canvas.drawLine(padL, padT, padL, padT + chartH, axisPaint)
        canvas.drawLine(padL, padT + chartH, padL + chartW, padT + chartH, axisPaint)

        // ── X-axis ticks + labels ─────────────────────────────────────────────
        val bucketCount = data.bucketTimestamps.size
        if (bucketCount > 0) {
            val xTickIndices = when {
                bucketCount <= 3  -> (0 until bucketCount).toList()
                bucketCount <= 8  -> listOf(0, bucketCount / 2, bucketCount - 1)
                else              -> listOf(0, bucketCount / 4, bucketCount / 2, bucketCount * 3 / 4, bucketCount - 1)
            }
            val fmt = xLabelFormat(data.timeRange)
            labelPaint.textAlign = Paint.Align.CENTER
            val xLabelY = padT + chartH + tickLen + labelTextSize + 2f * density

            xTickIndices.forEach { idx ->
                val frac = if (bucketCount == 1) 0.5f else idx.toFloat() / (bucketCount - 1)
                val x    = padL + frac * chartW
                canvas.drawLine(x, padT + chartH, x, padT + chartH + tickLen, axisPaint)
                val ts   = data.bucketTimestamps.getOrNull(idx) ?: return@forEach
                val lbl  = SimpleDateFormat(fmt, Locale.getDefault()).format(Date(ts))
                canvas.drawText(lbl, x, xLabelY, labelPaint)
            }
        }

        // ── Data ──────────────────────────────────────────────────────────────
        if (isBar) drawBars(canvas, data, padL, padT, chartW, chartH, niceMax, density)
        else       drawLines(canvas, data, padL, padT, chartW, chartH, niceMax, density, isDark)
    }

    private fun drawBars(
        canvas: Canvas, data: ChartData.Cartesian,
        padL: Float, padT: Float, chartW: Float, chartH: Float,
        maxVal: Double, density: Float
    ) {
        val bucketCount = data.bucketTimestamps.size.coerceAtLeast(1)
        val barGroupW   = chartW / bucketCount
        val seriesCount = data.series.size
        val barW        = (barGroupW / (seriesCount + 0.5f)).coerceAtLeast(2f * density)
        val gap         = ((barGroupW - barW * seriesCount) / (seriesCount + 1)).coerceAtLeast(1f)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        data.series.forEachIndexed { si, series ->
            paint.color = series.colorArgb ?: 0xFF6750A4.toInt()
            series.points.forEach { pt ->
                val barH   = (pt.value / maxVal * chartH).toFloat()
                val groupX = padL + pt.bucketIndex * barGroupW
                val barX   = groupX + gap + si * (barW + gap)
                canvas.drawRoundRect(
                    RectF(barX, padT + chartH - barH, barX + barW, padT + chartH),
                    2f * density, 2f * density, paint
                )
            }
        }
    }

    private fun drawLines(
        canvas: Canvas, data: ChartData.Cartesian,
        padL: Float, padT: Float, chartW: Float, chartH: Float,
        maxVal: Double, density: Float, isDark: Boolean
    ) {
        val bucketCount = (data.bucketTimestamps.size - 1).coerceAtLeast(1)
        fun ptX(idx: Int)    = padL + idx.toFloat() / bucketCount * chartW
        fun ptY(v: Double)   = (padT + chartH - (v / maxVal * chartH)).toFloat()

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style       = Paint.Style.STROKE
            strokeWidth = 2f * density
            strokeJoin  = Paint.Join.ROUND
            strokeCap   = Paint.Cap.ROUND
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        data.series.forEach { series ->
            val color = series.colorArgb ?: if (isDark) 0xFFD0BCFF.toInt() else 0xFF6750A4.toInt()
            linePaint.color = color
            dotPaint.color  = color

            if (series.points.size == 1) {
                canvas.drawCircle(ptX(series.points[0].bucketIndex), ptY(series.points[0].value), 4f * density, dotPaint)
            } else {
                val path = Path()
                series.points.forEachIndexed { i, pt ->
                    val x = ptX(pt.bucketIndex); val y = ptY(pt.value)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, linePaint)
                series.points.forEach { pt ->
                    canvas.drawCircle(ptX(pt.bucketIndex), ptY(pt.value), 3f * density, dotPaint)
                }
            }
        }
    }

    // ── Placeholder ───────────────────────────────────────────────────────────

    private fun renderPlaceholder(canvas: Canvas, width: Int, height: Int, isDark: Boolean, density: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color     = labelColorFor(isDark)
            textSize  = 11f * density
            textAlign = Paint.Align.CENTER
            typeface  = Typeface.DEFAULT
        }
        canvas.drawText("No chart data", width / 2f, height / 2f + paint.textSize / 3f, paint)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Round up to a "nice" number for axis max (1, 2, 5, 10, 20, …) */
    private fun niceValue(v: Double): Double {
        if (v <= 0) return 1.0
        val exp   = 10.0.pow(kotlin.math.floor(log10(v)))
        val frac  = v / exp
        val nice  = when {
            frac <= 1.0 -> 1.0
            frac <= 2.0 -> 2.0
            frac <= 5.0 -> 5.0
            else        -> 10.0
        }
        return nice * exp
    }

    /** Compact number label: "1.2k", "350", "4.5" */
    private fun formatValue(v: Double): String = when {
        abs(v) >= 10_000 -> "${(v / 1000).toInt()}k"
        abs(v) >= 1_000  -> "${"%.1f".format(v / 1000)}k"
        v == kotlin.math.floor(v) -> v.toLong().toString()
        else             -> "%.1f".format(v)
    }

    /** SimpleDateFormat pattern appropriate for the TimeRange */
    private fun xLabelFormat(range: TimeRange): String = when (range) {
        TimeRange.DAY   -> "HH:mm"
        TimeRange.WEEK  -> "EEE"
        TimeRange.MONTH -> "MMM d"
        TimeRange.YEAR  -> "MMM"
        TimeRange.ALL   -> "MMM yy"
    }

    // ── Colour palette ────────────────────────────────────────────────────────

    private fun gridColorFor(isDark: Boolean)  = if (isDark) 0x38CAC4D0.toInt() else 0x2849454F.toInt()
    private fun axisColorFor(isDark: Boolean)  = if (isDark) 0x99CAC4D0.toInt() else 0x9949454F.toInt()
    private fun labelColorFor(isDark: Boolean) = if (isDark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()
}
