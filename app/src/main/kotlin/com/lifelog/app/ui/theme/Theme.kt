package com.lifelog.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifelog.app.ui.LocalIs24HourFormat
import com.lifelog.app.ui.rememberIs24HourFormat

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

// Expressive shape scale: generous radii, cards on `large`, sheets/dialogs on `extraLarge`.
val LifeLogShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * AMOLED variant: the window goes pure black while container roles stay on
 * subtly elevated dark surfaces, so cards separate through color and depth
 * instead of borders. Dynamic schemes keep their wallpaper-tinted containers;
 * the static scheme swaps in brand-tinted ones.
 */
private fun ColorScheme.toAmoled(retainContainers: Boolean): ColorScheme = copy(
    background = AmoledBlack,
    surface = AmoledBlack,
    surfaceDim = AmoledBlack,
    surfaceContainerLowest = if (retainContainers) surfaceContainerLowest else AmoledSurfaceContainerLowest,
    surfaceContainerLow = if (retainContainers) surfaceContainerLow else AmoledSurfaceContainerLow,
    surfaceContainer = if (retainContainers) surfaceContainer else AmoledSurfaceContainer,
    surfaceContainerHigh = if (retainContainers) surfaceContainerHigh else AmoledSurfaceContainerHigh,
    surfaceContainerHighest = if (retainContainers) surfaceContainerHighest else AmoledSurfaceContainerHighest,
)

@Composable
fun LifeLogTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    amoledBlack: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseColorScheme = when {
        useDynamic -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val colorScheme = if (darkTheme && amoledBlack) {
        baseColorScheme.toAmoled(retainContainers = useDynamic)
    } else {
        baseColorScheme
    }

    // Every Compose surface in the app is wrapped in this theme, so providing the hour
    // format here is what makes one observer serve the whole UI — see LocalIs24HourFormat.
    CompositionLocalProvider(LocalIs24HourFormat provides rememberIs24HourFormat()) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LifeLogTypography,
            shapes = LifeLogShapes,
            content = content
        )
    }
}
