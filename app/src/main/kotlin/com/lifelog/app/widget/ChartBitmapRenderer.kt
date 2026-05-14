package com.lifelog.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.ChartType
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders [ChartData] to an Android [Bitmap] using the hardware Canvas API.
 * Designed for use in Glance widgets where Compose Canvas is unavailable.
 */
object ChartBitmapRenderer {

    fun render(
        data: ChartData,
        widthPx: Int,
        heightPx: Int,
        isDark: Boolean
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgColor = if (isDark) 0xFF1C1B1F.toInt() else 0xFFFFFBFE.toInt()
        canvas.drawColor(bgColor)

        when (data) {
            is ChartData.Pie -> renderPie(canvas, data, widthPx, heightPx, isDark)
            is ChartData.Cartesian -> when (data.type) {
                ChartType.BAR -> renderBar(canvas, data, widthPx, heightPx, isDark)
                else -> renderLine(canvas, data, widthPx, heightPx, isDark)
            }
            else -> renderPlaceholder(canvas, widthPx, heightPx, isDark)
        }

        return bitmap
    }

    // ── Pie ───────────────────────────────────────────────────────────────────

    private fun renderPie(
        canvas: Canvas,
        data: ChartData.Pie,
        width: Int,
        height: Int,
        isDark: Boolean
    ) {
        val total = data.slices.sumOf { it.value }.toFloat()
        if (total == 0f) { renderPlaceholder(canvas, width, height, isDark); return }

        val legendHeight = if (data.slices.size > 1) (height * 0.28f).coerceAtMost(80f) else 0f
        val chartHeight = height - legendHeight
        val diameter = min(width.toFloat(), chartHeight) * 0.82f
        val left = (width - diameter) / 2f
        val top = (chartHeight - diameter) / 2f
        val oval = RectF(left, top, left + diameter, top + diameter)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var startAngle = -90f

        data.slices.forEach { slice ->
            val sweep = (slice.value / total * 360f).toFloat()
            paint.color = slice.colorArgb
            canvas.drawArc(oval, startAngle, sweep, true, paint)
            startAngle += sweep
        }

        // Legend
        if (legendHeight > 0f) {
            val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = (legendHeight / (data.slices.size.coerceAtMost(3) + 1)).coerceIn(10f, 20f)
                typeface = Typeface.DEFAULT
                color = if (isDark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()
            }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val dotR = legendPaint.textSize * 0.4f
            val lineHeight = legendPaint.textSize * 1.5f
            val legendY0 = chartHeight + (legendHeight - data.slices.take(3).size * lineHeight) / 2f + legendPaint.textSize

            data.slices.take(3).forEachIndexed { i, slice ->
                val y = legendY0 + i * lineHeight
                val dotX = 16f + dotR
                dotPaint.color = slice.colorArgb
                canvas.drawCircle(dotX, y - dotR / 2, dotR, dotPaint)
                legendPaint.color = if (isDark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()
                val label = if (slice.label.length > 12) slice.label.take(11) + "…" else slice.label
                canvas.drawText(label, dotX + dotR + 6f, y, legendPaint)
            }
        }
    }

    // ── Bar ───────────────────────────────────────────────────────────────────

    private fun renderBar(
        canvas: Canvas,
        data: ChartData.Cartesian,
        width: Int,
        height: Int,
        isDark: Boolean
    ) {
        val allPoints = data.series.flatMap { it.points }
        if (allPoints.isEmpty()) { renderPlaceholder(canvas, width, height, isDark); return }

        val padL = 12f; val padR = 12f; val padT = 12f; val padB = 12f
        val chartW = width - padL - padR
        val chartH = height - padT - padB

        val maxVal = allPoints.maxOf { it.value }.coerceAtLeast(1.0)
        val bucketCount = data.bucketTimestamps.size.coerceAtLeast(1)
        val barGroupW = chartW / bucketCount
        val seriesCount = data.series.size
        val barW = (barGroupW / (seriesCount + 0.5f)).coerceAtLeast(2f)
        val gap = (barGroupW - barW * seriesCount) / (seriesCount + 1)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0x33FFFFFF else 0x33000000
            strokeWidth = 1f
        }
        // 3 horizontal grid lines
        for (i in 1..3) {
            val y = padT + chartH * (1f - i / 4f)
            canvas.drawLine(padL, y, padL + chartW, y, gridPaint)
        }

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        data.series.forEachIndexed { seriesIdx, series ->
            val color = series.colorArgb ?: 0xFF6750A4.toInt()
            barPaint.color = color
            series.points.forEach { point ->
                val barH = (point.value / maxVal * chartH).toFloat()
                val groupX = padL + point.bucketIndex * barGroupW
                val barX = groupX + gap + seriesIdx * (barW + gap)
                val rect = RectF(barX, padT + chartH - barH, barX + barW, padT + chartH)
                canvas.drawRoundRect(rect, 2f, 2f, barPaint)
            }
        }
    }

    // ── Line ──────────────────────────────────────────────────────────────────

    private fun renderLine(
        canvas: Canvas,
        data: ChartData.Cartesian,
        width: Int,
        height: Int,
        isDark: Boolean
    ) {
        val allPoints = data.series.flatMap { it.points }
        if (allPoints.isEmpty()) { renderPlaceholder(canvas, width, height, isDark); return }

        val padL = 12f; val padR = 12f; val padT = 12f; val padB = 12f
        val chartW = width - padL - padR
        val chartH = height - padT - padB

        val maxVal = allPoints.maxOf { it.value }.coerceAtLeast(1.0)
        val bucketCount = (data.bucketTimestamps.size - 1).coerceAtLeast(1)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0x33FFFFFF else 0x33000000
            strokeWidth = 1f
        }
        for (i in 1..3) {
            val y = padT + chartH * (1f - i / 4f)
            canvas.drawLine(padL, y, padL + chartW, y, gridPaint)
        }

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun pointX(bucketIdx: Int) = padL + bucketIdx.toFloat() / bucketCount * chartW
        fun pointY(value: Double) = (padT + chartH - (value / maxVal * chartH)).toFloat()

        data.series.forEach { series ->
            val color = series.colorArgb ?: 0xFF6750A4.toInt()
            linePaint.color = color
            dotPaint.color = color

            if (series.points.size == 1) {
                val p = series.points[0]
                canvas.drawCircle(pointX(p.bucketIndex), pointY(p.value), 4f, dotPaint)
            } else {
                val path = Path()
                series.points.forEachIndexed { i, pt ->
                    val x = pointX(pt.bucketIndex)
                    val y = pointY(pt.value)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                canvas.drawPath(path, linePaint)
                series.points.forEach { pt ->
                    canvas.drawCircle(pointX(pt.bucketIndex), pointY(pt.value), 3f, dotPaint)
                }
            }
        }
    }

    // ── Placeholder ───────────────────────────────────────────────────────────

    private fun renderPlaceholder(canvas: Canvas, width: Int, height: Int, isDark: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isDark) 0x66CAC4D0.toInt() else 0x6649454F.toInt()
            textSize = (height * 0.1f).coerceIn(12f, 24f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        canvas.drawText("No chart data", width / 2f, height / 2f + paint.textSize / 3, paint)
    }
}
