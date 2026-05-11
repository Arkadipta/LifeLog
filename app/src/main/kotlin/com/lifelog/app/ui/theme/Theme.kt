package com.lifelog.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    onPrimary = Purple20,
    primaryContainer = Purple30,
    onPrimaryContainer = Purple90,
    secondary = PurpleGrey80,
    onSecondary = PurpleGrey20,
    secondaryContainer = PurpleGrey30,
    onSecondaryContainer = PurpleGrey90,
    tertiary = Pink80,
    onTertiary = Pink20,
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Pink90,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
)

internal val LightColorScheme = lightColorScheme(
    primary = Purple40,
    onPrimary = Color.White,
    primaryContainer = Purple90,
    onPrimaryContainer = Purple10,
    secondary = PurpleGrey40,
    onSecondary = Color.White,
    secondaryContainer = PurpleGrey90,
    onSecondaryContainer = PurpleGrey20,
    tertiary = Pink40,
    onTertiary = Color.White,
    tertiaryContainer = Pink90,
    onTertiaryContainer = Pink20,
)

val LifeLogShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

data class AmoledColors(val isAmoled: Boolean)

val LocalAmoledColors = staticCompositionLocalOf { AmoledColors(false) }

@Composable
fun LifeLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledBlack: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val baseColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = if (darkTheme && amoledBlack) {
        baseColorScheme.copy(
            background = AmoledBlack,
            surface = AmoledBlack,
            surfaceVariant = AmoledSurfaceVariant,
            surfaceContainer = AmoledSurfaceContainer,
            surfaceContainerHigh = AmoledSurfaceContainerHigh,
            surfaceContainerHighest = AmoledSurfaceContainerHighest,
            surfaceBright = AmoledSurfaceContainerHighest,
            surfaceDim = AmoledBlack,
        )
    } else {
        baseColorScheme
    }

    CompositionLocalProvider(LocalAmoledColors provides AmoledColors(darkTheme && amoledBlack)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LifeLogTypography,
            shapes = LifeLogShapes,
            content = content
        )
    }
}
