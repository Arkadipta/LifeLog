package com.lifelog.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

// Material 3 seed color palette
val Purple10 = Color(0xFF21005D)
val Purple20 = Color(0xFF381E72)
val Purple30 = Color(0xFF4F378B)
val Purple40 = Color(0xFF6750A4)
val Purple80 = Color(0xFFD0BCFF)
val Purple90 = Color(0xFFEADDFF)

val PurpleGrey20 = Color(0xFF332D41)
val PurpleGrey30 = Color(0xFF4A4458)
val PurpleGrey40 = Color(0xFF625B71)
val PurpleGrey80 = Color(0xFFCCC2DC)
val PurpleGrey90 = Color(0xFFE8DEF8)

val Pink20 = Color(0xFF492532)
val Pink40 = Color(0xFF7D5260)
val Pink80 = Color(0xFFEFB8C8)
val Pink90 = Color(0xFFFFD8E4)

// Darker surfaces for regular dark mode
val DarkBackground = Color(0xFF0F0D14)
val DarkSurface = Color(0xFF0F0D14)
val DarkSurfaceContainer = Color(0xFF191720)
val DarkSurfaceContainerLow = Color(0xFF141220)
val DarkSurfaceContainerHigh = Color(0xFF1F1D28)
val DarkSurfaceContainerHighest = Color(0xFF282532)

// AMOLED: pure black background with subtly raised, brand-tinted containers.
// Depth comes from these surface steps — never from borders.
val AmoledBlack = Color(0xFF000000)
val AmoledSurfaceContainerLowest = Color(0xFF0C0A11)
val AmoledSurfaceContainerLow = Color(0xFF14121A)
val AmoledSurfaceContainer = Color(0xFF1A1721)
val AmoledSurfaceContainerHigh = Color(0xFF211E29)
val AmoledSurfaceContainerHighest = Color(0xFF2A2733)

/**
 * Quick-pick accent swatches for event categories: a compact, balanced set of
 * bright, pastel-inspired hues laid out as a spectrum (blues → greens → warms →
 * pinks → violets → neutral). These are convenience presets only — the color
 * picker also offers a full color wheel, so an event's accent can be any color.
 *
 * Whatever color is chosen (preset or custom, light or dark), the rendering
 * helpers below adapt content for it: [bestContentColor] picks black/white for
 * solid fills, and [onAccentTile] / [rememberAccentOnSurface] produce a legible
 * on-color for tiles and accent text across light / dark / AMOLED.
 *
 * The first entry doubles as [com.lifelog.app.domain.model.EventType.DEFAULT_COLOR]
 * — keep them in sync.
 */
val EventColors = listOf(
    Color(0xFF7FB0F2), // Soft Blue
    Color(0xFF4FC9C4), // Teal
    Color(0xFF5FC98A), // Emerald
    Color(0xFFA8D965), // Lime
    Color(0xFFE9C23A), // Yellow
    Color(0xFFFB9E5E), // Orange
    Color(0xFFF89685), // Coral
    Color(0xFFF593B9), // Pink
    Color(0xFFB795F0), // Purple
    Color(0xFFC3AEF5), // Lavender
    Color(0xFF9FA3F2), // Indigo
    Color(0xFF9EAAC0), // Slate
)

/**
 * Black or white — whichever stays more readable on top of this color. The
 * threshold is the relative-luminance crossover (~0.179) where black and white
 * give equal WCAG contrast; above it black wins. The bright event accents all
 * land above this point (so they take black content), while the deep legacy
 * colors fall below it and take white.
 */
fun Color.bestContentColor(): Color = if (luminance() > 0.179f) Color.Black else Color.White

/**
 * A readable on-color for text or icons drawn over this accent — either a faint
 * tint of it (tonal tiles like the entry TimeTile / IconTile) or the card
 * surface directly. The bright pastel accents are light, so on a light surface
 * they need real darkening for contrast, while on a dark surface only a gentle
 * white-lift is needed — lifting further would wash distinct hues toward a
 * uniform white. These two factors recreate the M3 container/on-container
 * relationship for a custom accent and clear 4.5:1 in light, dark, and AMOLED.
 */
fun Color.onAccentTile(onDarkSurface: Boolean): Color =
    if (onDarkSurface) lerp(this, Color.White, 0.22f) else lerp(this, Color.Black, 0.50f)

/**
 * The luminance-corrected accent for drawing text or icons on the current
 * surface (or a faint tint of the accent). Keyed to the active surface's
 * luminance so it adapts across light / dark / AMOLED, this is the same
 * correction [accentTileColors] applies to tile content — use it anywhere a raw
 * event accent would otherwise sit as content directly on a surface.
 */
@Composable
fun rememberAccentOnSurface(accent: Color): Color {
    val onDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return remember(accent, onDarkSurface) { accent.onAccentTile(onDarkSurface) }
}

/**
 * The container and content colors of a tonal accent tile: the faint tinted
 * background and the luminance-corrected on-color drawn over it.
 */
@Immutable
data class AccentTileColors(val container: Color, val content: Color)

/** Faint tint strength every accent tile shares for its container. */
private const val ACCENT_TILE_CONTAINER_ALPHA = 0.14f

/**
 * The single source of truth for accent-tile colors. The entry time tile and
 * the leading icon tile on list cards both read their container and on-color
 * here — the same [onAccentTile] correction keyed to the current surface's
 * luminance — so every tile renders with identical tint and contrast in both
 * themes and the two can never drift apart.
 */
@Composable
fun accentTileColors(accent: Color): AccentTileColors =
    AccentTileColors(
        container = accent.copy(alpha = ACCENT_TILE_CONTAINER_ALPHA),
        content = rememberAccentOnSurface(accent)
    )
