package com.lifelog.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.EventFilterState
import com.lifelog.app.ui.theme.Spacing

private const val INLINE_TAG_LIMIT = 6

/**
 * Horizontally-scrollable row of Material 3 FilterChips for tag-based filtering.
 *
 * When the tag list exceeds [INLINE_TAG_LIMIT], a "All tags" chip opens a searchable
 * bottom sheet so users can find tags without endless horizontal scrolling.
 *
 * Only rendered when there is at least one tag to show.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagFilterRow(
    tags: List<String>,
    filterState: EventFilterState,
    onFilterChange: (EventFilterState) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return

    var showSheet by remember { mutableStateOf(false) }
    val useInline = tags.size <= INLINE_TAG_LIMIT

    AnimatedVisibility(
        visible = true,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (filterState.hasActiveFilters) {
                InputChip(
                    selected = false,
                    onClick = { onFilterChange(filterState.clearAll()) },
                    label = { Text("Clear") },
                    trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Clear filters", modifier = Modifier.size(16.dp)) }
                )
            }

            if (useInline) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = tag in filterState.selectedTags,
                        onClick = { onFilterChange(filterState.toggleTag(tag)) },
                        label = { Text(tag) }
                    )
                }
            } else {
                // Show only selected tags inline; rest via sheet
                filterState.selectedTags.forEach { tag ->
                    FilterChip(
                        selected = true,
                        onClick = { onFilterChange(filterState.toggleTag(tag)) },
                        label = { Text(tag) },
                        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Remove $tag", modifier = Modifier.size(16.dp)) }
                    )
                }

                val unselectedCount = tags.size - filterState.selectedTags.size
                FilterChip(
                    selected = false,
                    onClick = { showSheet = true },
                    label = {
                        Text(if (filterState.selectedTags.isEmpty()) "Filter by tag" else "+$unselectedCount more")
                    },
                    leadingIcon = { Icon(Icons.Rounded.FilterList, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }

    if (showSheet) {
        TagPickerSheet(
            tags = tags,
            filterState = filterState,
            onFilterChange = onFilterChange,
            onDismiss = { showSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPickerSheet(
    tags: List<String>,
    filterState: EventFilterState,
    onFilterChange: (EventFilterState) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(tags, searchQuery) {
        if (searchQuery.isBlank()) tags
        else tags.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sheetEdge)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filter by tag", style = MaterialTheme.typography.titleLarge)
                if (filterState.hasActiveFilters) {
                    TextButton(onClick = { onFilterChange(filterState.clearAll()) }) {
                        Text("Clear all")
                    }
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search tags…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(max = 360.dp)
            ) {
                items(filtered, key = { it }) { tag ->
                    val selected = tag in filterState.selectedTags
                    FilterChip(
                        selected = selected,
                        onClick = { onFilterChange(filterState.toggleTag(tag)) },
                        label = { Text(tag) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
