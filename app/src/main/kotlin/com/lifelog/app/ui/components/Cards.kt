package com.lifelog.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifelog.app.ui.theme.accentTileColors
import com.lifelog.app.ui.theme.Sizing

/**
 * The one card used everywhere in LifeLog: `large` corners, borderless,
 * sitting on a surface-container step so it reads against any background —
 * including pure black in AMOLED mode.
 *
 * On screen backgrounds use the default [containerColor]; inside bottom
 * sheets step up to `surfaceContainerHigh`.
 */
@Composable
fun LifeLogCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, content = content)
    }
}

/**
 * Rounded icon tile that leads list cards: a faint tinted container with the
 * icon drawn in the matching luminance-corrected on-color. Sources its colors
 * from [accentTileColors], the same place the entry time tile does, so every
 * tile across the app reads with identical tint and contrast in both themes.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = Sizing.iconTile,
    contentDescription: String? = null
) {
    val tile = accentTileColors(tint)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = tile.container,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tile.content,
                modifier = Modifier.size(size * 0.54f)
            )
        }
    }
}

/** Small tonal badge for metadata (category, field type, units). */
@Composable
fun LabelChip(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Surface(shape = MaterialTheme.shapes.small, color = containerColor, modifier = modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
