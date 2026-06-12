package com.lifelog.app.ui.theme

import androidx.compose.ui.graphics.Color
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

// Accent colors for event categories
val EventColors = listOf(
    Color(0xFF6750A4), // Purple
    Color(0xFF0061A4), // Blue
    Color(0xFF006E1C), // Green
    Color(0xFFBA1A1A), // Red
    Color(0xFFB85300), // Orange
    Color(0xFF006874), // Teal
    Color(0xFF6B5F00), // Yellow
    Color(0xFF8B0086), // Pink
    Color(0xFF006A60), // Cyan
    Color(0xFF904D00), // Brown
)

/** Black or white — whichever stays readable on top of this color. */
fun Color.bestContentColor(): Color = if (luminance() > 0.5f) Color.Black else Color.White
