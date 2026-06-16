package com.lifelog.app.ui.timeline

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.ui.components.DeleteConfirmDialog
import com.lifelog.app.ui.components.EmptyStatePlaceholder
import com.lifelog.app.ui.components.TagFilterRow
import com.lifelog.app.ui.events.EntryFormSheet
import com.lifelog.app.ui.events.entryCardItems
import com.lifelog.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
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
                    .padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm)
            ) {}

            TagFilterRow(
                tags = availableTags,
                filterState = filterState,
                onFilterChange = viewModel::updateFilter,
                modifier = Modifier.padding(bottom = Spacing.xs)
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.xl)
                ) {
                    entryCardItems(
                        entries = entries,
                        fieldsFor = { entry -> fieldsMap[entry.eventTypeId].orEmpty() },
                        onEdit = { editingEntryId = it.id },
                        onDeleteRequest = { deleteTarget = it },
                        showEventName = true
                    )
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
        DeleteConfirmDialog(
            title = "Delete entry?",
            text = "This entry will be deleted.",
            onConfirm = {
                viewModel.deleteEntry(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}
