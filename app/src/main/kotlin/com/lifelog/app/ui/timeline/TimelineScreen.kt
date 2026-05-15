package com.lifelog.app.ui.timeline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.ui.components.EmptyStatePlaceholder
import com.lifelog.app.ui.components.SwipeDeleteBackground
import com.lifelog.app.ui.components.TagFilterRow
import com.lifelog.app.ui.events.EntryCard
import com.lifelog.app.ui.events.EntryFormSheet
import com.lifelog.app.util.toDisplayDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val fieldsMap by viewModel.fieldsMap.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<EventEntry?>(null) }
    var editingEntryId by remember { mutableStateOf<Long?>(null) }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Timeline") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DockedSearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = viewModel::setSearchQuery,
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text("Search entries…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Rounded.Close, "Clear search")
                                }
                            }
                        }
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {}

            TagFilterRow(
                tags = availableTags,
                filterState = filterState,
                onFilterChange = viewModel::updateFilter,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            if (entries.isEmpty() && searchQuery.isBlank() && !filterState.hasActiveFilters) {
                EmptyStatePlaceholder(
                    icon = Icons.Rounded.Timeline,
                    title = "No entries yet",
                    subtitle = "Create events and add entries to see them here",
                    modifier = Modifier.fillMaxSize()
                )
            } else if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val message = when {
                        searchQuery.isNotBlank() && filterState.hasActiveFilters ->
                            "No entries match \"$searchQuery\" with selected tags"
                        searchQuery.isNotBlank() -> "No entries match \"$searchQuery\""
                        else -> "No entries match the selected tags"
                    }
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val grouped = remember(entries) {
                    entries.groupBy { it.createdAt.toDisplayDate() }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    grouped.forEach { (date, dayEntries) ->
                        stickyHeader(key = "header_$date") {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(
                                        modifier = Modifier.width(12.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        date,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    HorizontalDivider(
                                        modifier = Modifier.weight(1f),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                        items(dayEntries, key = { it.id }) { entry ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        deleteTarget = entry
                                    }
                                    false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = { SwipeDeleteBackground(dismissState) },
                                enableDismissFromEndToStart = true,
                                enableDismissFromStartToEnd = false,
                                modifier = Modifier.animateItem()
                            ) {
                                EntryCard(
                                    entry = entry,
                                    fields = fieldsMap[entry.eventTypeId] ?: emptyList(),
                                    showEventName = true,
                                    showDivider = false,
                                    onEdit = { editingEntryId = entry.id },
                                    onDelete = { deleteTarget = entry }
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    editingEntryId?.let { entryId ->
        EntryFormSheet(
            eventTypeId = 0L,
            editingEntryId = entryId,
            onDismiss = { editingEntryId = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete entry?") },
            text = { Text("This entry will be permanently deleted.") },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        viewModel.deleteEntry(target.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}
