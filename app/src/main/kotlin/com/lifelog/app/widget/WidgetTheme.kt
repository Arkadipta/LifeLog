package com.lifelog.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider as dayNightColor
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import com.lifelog.app.ui.theme.bestContentColor
import com.lifelog.app.ui.theme.onAccentTile

/**
 * Shared visual language for LifeLog's home-screen widgets (Timeline + Quick Add),
 * so both render from the same design tokens and read as one system. Event color
 * is the identity language: a low tint marks a surface as "belonging to" an event
 * while keeping standard onSurface text legible, and a solid accent chip is the
 * bold "which event" anchor on top of it. Every accent that becomes content is
 * routed through [onAccentTile] / [bestContentColor] so bright pastel accents stay
 * readable in light, dark, and AMOLED.
 *
 * These were first introduced in the Timeline widget redesign; they live here so a
 * change to the design system updates every widget at once instead of drifting.
 */

/** The accent drawn as content (icon/text) over a surface, corrected per theme. */
internal fun accentContent(accent: Color): ColorProvider =
    dayNightColor(day = accent.onAccentTile(false), night = accent.onAccentTile(true))

/** The faint accent tint behind an icon tile — a translucent wash over the surface. */
internal fun accentTile(accent: Color): ColorProvider =
    ColorProvider(accent.copy(alpha = 0.16f))

/**
 * How strongly a surface is tinted with its event's color. Kept low so the surface
 * still reads as "mostly surface" and standard onSurface / onSurfaceVariant text
 * stays well above contrast minimums over it, in both light and dark.
 */
internal const val ENTRY_TINT_ALPHA = 0.12f

/** A surface tinted with an event's color — the per-event/entry identity wash. */
internal fun entryCardTint(accent: Color): ColorProvider =
    ColorProvider(accent.copy(alpha = ENTRY_TINT_ALPHA))

/**
 * A rounded tile holding an event icon. [filled] = true paints a solid accent
 * chip with a black/white icon (the bold "which event" anchor — it stays distinct
 * on top of an event-tinted surface); [filled] = false is the faint tile used when
 * the icon sits on the plain widget surface. [cornerRadius] lets a larger chip
 * round more generously without changing the default used across list rows.
 */
@Composable
internal fun WidgetIconTile(
    iconName: String,
    accent: Color,
    tileSize: Dp,
    iconSize: Dp,
    filled: Boolean = false,
    cornerRadius: Dp = 10.dp
) {
    val context = LocalContext.current
    val px = (iconSize.value * context.resources.displayMetrics.density).toInt()
    val tileBg = if (filled) ColorProvider(accent) else accentTile(accent)
    val iconTint = if (filled) ColorProvider(accent.bestContentColor()) else accentContent(accent)
    Box(
        modifier = GlanceModifier
            .size(tileSize)
            .background(tileBg)
            .cornerRadius(cornerRadius),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(widgetIconMask(iconName, px)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(iconTint),
            modifier = GlanceModifier.size(iconSize)
        )
    }
}
