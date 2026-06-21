package com.lifelog.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import com.lifelog.app.util.iconForName
import java.util.concurrent.ConcurrentHashMap

/**
 * Glance can only draw bitmaps or drawable resources — never a Compose
 * [ImageVector] — so event icons are rasterized here into a solid-black
 * alpha-mask bitmap. The widget draws each mask through `ColorFilter.tint`,
 * which recolors using only the alpha channel, so one cached bitmap adapts to
 * any event accent and to light/dark without re-rendering.
 *
 * The vector tree is walked by hand (group transforms, then path fills) rather
 * than through a `VectorPainter`, because a painter needs a live Compose
 * composition that does not exist inside `provideGlance`.
 *
 * Masks are cached by "name@px" — the widget reuses the same handful of icons
 * across many entries, so this keeps each render to a single rasterization.
 */
private val maskCache = ConcurrentHashMap<String, Bitmap>()

/** A tintable alpha-mask bitmap for [iconName], rendered at [sizePx] square. */
fun widgetIconMask(iconName: String, sizePx: Int): Bitmap {
    val safeSize = sizePx.coerceAtLeast(1)
    return maskCache.getOrPut("$iconName@$safeSize") {
        rasterize(iconForName(iconName), safeSize)
    }
}

private fun rasterize(image: ImageVector, sizePx: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    // Vector geometry is in viewport units (24×24 for the Material set); scale the
    // whole canvas so the icon fills the requested pixel size.
    canvas.scale(sizePx / image.viewportWidth, sizePx / image.viewportHeight)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.BLACK
    }
    drawGroup(image.root, canvas, paint)
    return bitmap
}

private fun drawGroup(group: VectorGroup, canvas: Canvas, paint: Paint) {
    canvas.save()
    // Mirror the transform Compose composes for a VectorGroup: pivot-centered
    // rotate then scale, around the group's translation. The Material icons use
    // the identity here, but handling it keeps any transformed glyph correct.
    canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
    canvas.rotate(group.rotation)
    canvas.scale(group.scaleX, group.scaleY)
    canvas.translate(-group.pivotX, -group.pivotY)
    group.forEach { node ->
        when (node) {
            is VectorPath -> {
                val path = PathParser().addPathNodes(node.pathData).toPath().asAndroidPath()
                canvas.drawPath(path, paint)
            }
            is VectorGroup -> drawGroup(node, canvas, paint)
        }
    }
    canvas.restore()
}
