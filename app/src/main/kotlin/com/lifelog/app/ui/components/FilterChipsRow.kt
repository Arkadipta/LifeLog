package com.lifelog.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Horizontally scrollable row of filter chips. Renders nothing when [chips] is empty.
 * Each chip toggles independently; selected state is driven by [selectedChips].
 * Designed to be composable with search bars and extensible for future filter types.
 */
@Composable
fun FilterChipsRow(
    chips: List<String>,
    selectedChips: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    startPadding: PaddingValues = PaddingValues(horizontal = 16.dp)
) {
    if (chips.isEmpty()) return

    LazyRow(
        modifier = modifier,
        contentPadding = startPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(chips) { chip ->
            FilterChip(
                selected = chip in selectedChips,
                onClick = { onToggle(chip) },
                label = { Text(chip) },
                leadingIcon = if (chip in selectedChips) {
                    { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}
