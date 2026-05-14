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

/**
 * Renders [ChartData] to an Android [Bitmap] using the hardware Canvas API.
 * All sizes are expressed in dp-equivalent units then scaled by [density] to
 * produce physical pixels — so charts look consistent across screen densities.
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
        val canvas = Canvas(bitmap)

        // Background
        val bgColor = if (isDark) 0xFF1C1B1F.toInt() else 0xFFFFFBFE.toInt()
        canvas.drawColor(bgColor)

        when (data) {
            is ChartData.Pie -> renderPie(canvas, data, widthPx, heightPx, isDark, density)
            is ChartData.Cartesian -> when (data.type) {
                ChartType.BAR -> renderBar(canvas, data, widthPx, heightPx, isDark, density)
                else -> renderLine(canvas, data, widthPx, heightPx, isDark, density)
            }
            else -> renderPlaceholder(canvas, widthPx, heightPx, isDark, density)
        }

        return bitmap
    }

    // ── Pie ───────────────────────────────────────────────────────────────────

    private fun renderPie(
        canvas: Canvas,
        data: ChartData.Pie,
        width: Int,
        height: Int,
        isDark: Boolean,
        density: Float
    ) {
        val total = data.slices.sumOf { it.value }.toFloat()
        if (total == 0f) { renderPlaceholder(canvas, width, height, isDark, density); return }

        val legendItemH = 18f * density
        val legendPadding = 8f * density
        val legendHeight = if (data.slices.size > 1)
            (data.slices.size.coerceAtMost(4) * legendItemH + legendPadding * 2f)
                .coerceAtMost(height * 0.3f)
        else 0f

        val chartAreaH = height - legendHeight
        val diameter = min(width.toFloat(), chartAreaH) * 0.80f
        val left = (width - diameter) / 2f
        val top = (chartAreaH - diameter) / 2f
        val oval = RectF(left, top, left + diameter, top + diameter)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var startAngle = -90f
        data.slices.forEach { slice ->
            val sweep = (slice.value / total * 360f).toFloat()
            paint.color = slice.colorArgb
            canvas.drawArc(oval, startAngle, sweep, true, paint)
            startAngle += sweep
        }

        // Thin separator stroke between slices for clarity
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        paint.color = bgColorFor(isDark)
        startAngle = -90f
        data.slices.forEach { slice ->
            val sweep = (slice.value / total * 360f).toFloat()
            canvas.drawArc(oval, startAngle, sweep, true, paint)
            startAngle += sweep
        }
        paint.style = Paint.Style.FILL

        // Legend
        if (legendHeight > 0f) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 11f * density
                typeface = Typeface.DEFAULT
                color = onSurfaceVariantFor(isDark)
            }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val dotR = 4f * density
            val lineH = legendItemH
            val startY = chartAreaH + legendPadding + textPaint.textSize

            data.slices.take(4).forEachIndexed { i, slice ->
                val y = startY + i * lineH
                val dotX = 14f * density
                dotPaint.color = slice.colorArgb
                canvas.drawCircle(dotX, y - textPaint.textSize / 3, dotR, dotPaint)
                textPaint.color = onSurfaceVariantFor(isDark)
                val label = slice.label.take(16).let { if (slice.label.length > 16) "$it…" else it }
                canvas.drawText(label, dotX + dotR + 6f * density, y, textPaint)
            }
        }
    }

    // ── Bar ───────────────────────────────────────────────────────────────────

    private fun renderBar(
        canvas: Canvas,
        data: ChartData.Cartesian,
        width: Int,
        height: Int,
        isDark: Boolean,
        density: Float
    ) {
        val allPoints = data.series.flatMap { it.points }
        if (allPoints.isEmpty()) { renderPlaceholder(canvas, width, height, isDark, density); return }

        val padL = 6f * density
        val padR = 8f * density
        val padT = 8f * density
        val padB = 18f * density   // room for x-axis labels
        val chartW = width - padL - padR
        val chartH = height - padT - padB

        val maxVal = allPoints.maxOf { it.value }.coerceAtLeast(1.0)
        val bucketCount = data.bucketTimestamps.size.coerceAtLeast(1)
        val barGroupW = chartW / bucketCount
        val seriesCount = data.series.size
        val barW = (barGroupW / (seriesCount + 0.5f)).coerceAtLeast(2f * density)
        val gap = ((barGroupW - barW * seriesCount) / (seriesCount + 1)).coerceAtLeast(1f)

        // Grid lines
        val gridPaint = Paint().apply {
            color = gridColorFor(isDark)
            strokeWidth = 0.75f * density
            isAntiAlias = true
        }
        for (i in 1..3) {
            val y = padT + chartH * (1f - i / 4f)
            canvas.drawLine(padL, y, padL + chartW, y, gridPaint)
        }

        // Axis lines
        val axisPaint = Paint().apply {
            color = axisColorFor(isDark)
            strokeWidth = 1f * density
            isAntiAlias = true
        }
        canvas.drawLine(padL, padT, padL, padT + chartH, axisPaint)           // Y-axis
        canvas.drawLine(padL, padT + chartH, padL + chartW, padT + chartH, axisPaint) // X-axis

        // Bars
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        data.series.forEachIndexed { seriesIdx, series ->
            barPaint.color = series.colorArgb ?: primaryColorFor(isDark)
            series.points.forEach { point ->
                val barH = (point.value / maxVal * chartH).toFloat()
                val groupX = padL + point.bucketIndex * barGroupW
                val barX = groupX + gap + seriesIdx * (barW + gap)
                val rect = RectF(barX, padT + chartH - barH, barX + barW, padT + chartH)
                canvas.drawRoundRect(rect, 2f * density, 2f * density, barPaint)
            }
        }
    }

    // ── Line ──────────────────────────────────────────────────────────────────

    private fun renderLine(
        canvas: Canvas,
        data: ChartData.Cartesian,
        width: Int,
        height: Int,
        isDark: Boolean,
        density: Float
    ) {
        val allPoints = data.series.flatMap { it.points }
        if (allPoints.isEmpty()) { renderPlaceholder(canvas, width, height, isDark, density); return }

        val padL = 6f * density
        val padR = 8f * density
        val padT = 8f * density
        val padB = 18f * density
        val chartW = width - padL - padR
        val chartH = height - padT - padB

        val maxVal = allPoints.maxOf { it.value }.coerceAtLeast(1.0)
        val bucketCount = (data.bucketTimestamps.size - 1).coerceAtLeast(1)

        // Grid lines
        val gridPaint = Paint().apply {
            color = gridColorFor(isDark)
            strokeWidth = 0.75f * density
            isAntiAlias = true
        }
        for (i in 1..3) {
            val y = padT + chartH * (1f - i / 4f)
            canvas.drawLine(padL, y, padL + chartW, y, gridPaint)
        }

        // Axis lines
        val axisPaint = Paint().apply {
            color = axisColorFor(isDark)
            strokeWidth = 1f * density
            isAntiAlias = true
        }
        canvas.drawLine(padL, padT, padL, padT + chartH, axisPaint)
        canvas.drawLine(padL, padT + chartH, padL + chartW, padT + chartH, axisPaint)

        fun ptX(bucketIdx: Int) = padL + bucketIdx.toFloat() / bucketCount * chartW
        fun ptY(value: Double) = (padT + chartH - (value / maxVal * chartH)).toFloat()

        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        data.series.forEach { series ->
            val color = series.colorArgb ?: primaryColorFor(isDark)
            linePaint.color = color
            dotPaint.color = color

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
            color = onSurfaceVariantFor(isDark)
            textSize = 12f * density
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        canvas.drawText("No chart data", width / 2f, height / 2f + paint.textSize / 3, paint)
    }

    // ── Color helpers ─────────────────────────────────────────────────────────

    private fun bgColorFor(isDark: Boolean) =
        if (isDark) 0xFF1C1B1F.toInt() else 0xFFFFFBFE.toInt()

    private fun gridColorFor(isDark: Boolean) =
        if (isDark) 0x40CAC4D0.toInt() else 0x3049454F.toInt()

    private fun axisColorFor(isDark: Boolean) =
        if (isDark) 0x99CAC4D0.toInt() else 0x9949454F.toInt()

    private fun onSurfaceVariantFor(isDark: Boolean) =
        if (isDark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()

    private fun primaryColorFor(isDark: Boolean) =
        if (isDark) 0xFFD0BCFF.toInt() else 0xFF6750A4.toInt()
}
