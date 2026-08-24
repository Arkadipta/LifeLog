package com.lifelog.app.ui.events

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.EntryRow
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.query.SortField
import com.lifelog.app.ui.components.DeleteConfirmDialog
import com.lifelog.app.ui.components.LifeLogFab
import com.lifelog.app.ui.events.components.ChartCarousel
import com.lifelog.app.ui.events.components.ChartConfigSheet
import com.lifelog.app.ui.theme.Motion
import com.lifelog.app.ui.theme.Spacing
import com.lifelog.app.ui.theme.bestContentColor
import com.lifelog.app.ui.theme.rememberAccentOnSurface
import com.lifelog.app.util.iconForName
import com.lifelog.app.util.toDisplayDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: EventDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(eventId) { viewModel.loadEvent(eventId) }

    val eventType by viewModel.eventType.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val charts by viewModel.charts.collectAsStateWithLifecycle()
    val chartDataMap by viewModel.chartDataMap.collectAsStateWithLifecycle()

    var showEntrySheet by remember { mutableStateOf(false) }
    var editingEntryId by remember { mutableStateOf<Long?>(null) }
    var deleteTarget by remember { mutableStateOf<EntryRow?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var showChartConfigSheet by remember { mutableStateOf(false) }
    var editingChart by remember { mutableStateOf<ChartConfig?>(null) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteEventDialog by remember { mutableStateOf(false) }
    var showFilterSortSheet by remember { mutableStateOf(false) }

    val entryQuery by viewModel.entryQuery.collectAsStateWithLifecycle()

    // Sticky day headers only make sense while the list is chronological;
    // a field-value sort interleaves dates, so cards carry their own date.
    val groupByDate = remember(entryQuery) {
        val sortField = entryQuery.sort?.field
        sortField == null || sortField is SortField.Timestamp
    }

    val snackbarHostState = remember { SnackbarHostState() }

    var exportResult by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            viewModel.exportCsv(it) { success ->
                exportResult = if (success) "Exported successfully" else "Export failed"
            }
        }
    }

    LaunchedEffect(exportResult) {
        exportResult?.let {
            snackbarHostState.showSnackbar(it)
            exportResult = null
        }
    }

    val fabColor = eventType?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary
    val fabContentColor = remember(fabColor) { fabColor.bestContentColor() }

    // Any field that can back a chart: numeric (line/bar/pie/heatmap) or yes/no
    // (heatmap). Boolean-only events — e.g. habit trackers — can still add a heatmap.
    val hasChartableFields = remember(eventType) {
        eventType?.fields?.any {
            it.type == FieldType.NUMERIC || it.type == FieldType.BOOLEAN
        } == true
    }
    // Show the carousel whenever any chart config exists — even after a field was
    // retyped so no chartable fields remain — so stale charts can be discovered and
    // edited/deleted rather than silently disappearing. Adding new charts still
    // requires a chartable field (gated separately by hasChartableFields).
    val showChartCarousel = charts.isNotEmpty()

    val listState = rememberLazyListState()
    // The chart carousel is the only item the list emits before the entry
    // cards, so it is the lone offset the date navigator needs.
    val leadingItemCount = if (showChartCarousel) 1 else 0
    val dateNavigator = rememberDateNavigator(
        entries = entries,
        listState = listState,
        groupByDate = groupByDate,
        leadingItemCount = leadingItemCount
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            eventType?.name ?: "",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (eventType?.category?.isNotBlank() == true) {
                            Text(
                                eventType!!.category,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSortSheet = true }) {
                        BadgedBox(
                            badge = {
                                if (entryQuery.isActive) Badge()
                            }
                        ) {
                            Icon(Icons.Rounded.FilterList, "Filter and sort entries")
                        }
                    }
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) viewModel.setSearchQuery("")
                    }) {
                        Icon(
                            if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            if (searchActive) "Close search" else "Search entries"
                        )
                    }
                    Box {
                        IconButton(onClick = { overflowMenuExpanded = true }) {
                            Icon(Icons.Rounded.MoreVert, "More options")
                        }
                        DropdownMenu(
                            expanded = overflowMenuExpanded,
                            onDismissRequest = { overflowMenuExpanded = false }
                        ) {
                            if (dateNavigator.canPickDate) {
                                DropdownMenuItem(
                                    text = { Text("Jump to date") },
                                    leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null) },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        dateNavigator.openPicker()
                                    }
                                )
                            }
                            if (entries.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("Jump to top") },
                                    leadingIcon = { Icon(Icons.Rounded.VerticalAlignTop, null) },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        dateNavigator.jumpToTop()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    onNavigateToEdit(eventId)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = {
                                    overflowMenuExpanded = false
                                    showDeleteEventDialog = true
                                }
                            )
                            if (hasChartableFields) {
                                DropdownMenuItem(
                                    text = { Text("Add Chart") },
                                    leadingIcon = { Icon(Icons.Rounded.AddChart, null) },
                                    onClick = {
                                        overflowMenuExpanded = false
                                        editingChart = null
                                        showChartConfigSheet = true
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Export to CSV") },
                                leadingIcon = { Icon(Icons.Rounded.Upload, null) },
                                onClick = {
                                    overflowMenuExpanded = false
                                    val name = eventType?.name?.replace(" ", "_") ?: "event"
                                    exportLauncher.launch("${name}_export.csv")
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            LifeLogFab(
                onClick = {
                    editingEntryId = null
                    showEntrySheet = true
                },
                icon = Icons.Rounded.Add,
                containerColor = fabColor,
                contentColor = fabContentColor,
                contentDescription = "Add Entry"
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AnimatedVisibility(
                visible = searchActive,
                enter = expandVertically(animationSpec = Motion.spatial()),
                exit = shrinkVertically(animationSpec = Motion.snappy())
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
            }

            if (entries.isEmpty() && searchQuery.isBlank() && !showChartCarousel) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.weight(0.38f))
                    eventType?.let {
                        Icon(
                            iconForName(it.iconName),
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = rememberAccentOnSurface(Color(it.colorArgb)).copy(alpha = 0.5f)
                        )
                    }
                    Spacer(Modifier.height(Spacing.lg))
                    Text("No entries yet", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "Tap + to log your first entry",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(0.62f))
                }
            } else if (entries.isEmpty() && searchQuery.isNotBlank()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No entries match \"$searchQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.fabClearance)
                ) {
                    // Chart carousel — visible whenever a chart config exists. The
                    // "add chart" tile only appears when a numeric field is present.
                    if (showChartCarousel) {
                        item(key = "chart_carousel") {
                            ChartCarousel(
                                charts = charts,
                                chartDataMap = chartDataMap,
                                eventType = eventType,
                                onAddChart = {
                                    editingChart = null
                                    showChartConfigSheet = true
                                },
                                onEditChart = { chart ->
                                    editingChart = chart
                                    showChartConfigSheet = true
                                },
                                onDeleteChart = viewModel::deleteChart,
                                modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.md),
                                showAddCard = hasChartableFields
                            )
                        }
                    }

                    // Empty entries state when carousel is shown
                    if (entries.isEmpty() && searchQuery.isBlank()) {
                        item(key = "entries_empty") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                eventType?.let {
                                    Icon(
                                        iconForName(it.iconName),
                                        null,
                                        modifier = Modifier.size(48.dp),
                                        tint = rememberAccentOnSurface(Color(it.colorArgb)).copy(alpha = 0.5f)
                                    )
                                }
                                Spacer(Modifier.height(Spacing.md))
                                Text("No entries yet", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Tap + to log your first entry",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    entryCardItems(
                        entries = entries,
                        fieldsFor = { eventType?.fields ?: emptyList() },
                        onEdit = {
                            editingEntryId = it.id
                            showEntrySheet = true
                        },
                        onDeleteRequest = { deleteTarget = it },
                        groupByDate = groupByDate
                    )
                }
            }
        }
    }

    JumpToDateDialog(dateNavigator)

    if (showFilterSortSheet) {
        FilterSortSheet(
            fields = eventType?.fields ?: emptyList(),
            currentQuery = entryQuery,
            onApply = viewModel::setEntryQuery,
            onDismiss = { showFilterSortSheet = false }
        )
    }

    if (showEntrySheet) {
        EntryFormSheet(
            mode = editingEntryId?.let { EntryFormMode.Edit(it) } ?: EntryFormMode.New(eventId),
            onDismiss = { showEntrySheet = false }
        )
    }

    if (showChartConfigSheet) {
        ChartConfigSheet(
            eventTypeId = eventId,
            fields = eventType?.fields ?: emptyList(),
            editing = editingChart,
            onSave = viewModel::saveChart,
            onDismiss = { showChartConfigSheet = false }
        )
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            title = "Delete entry?",
            text = "This entry logged at ${target.createdAt.toDisplayDateTime()} will be deleted.",
            onConfirm = {
                viewModel.deleteEntry(target.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    if (showDeleteEventDialog) {
        eventType?.let { target ->
            val linkedReminders by viewModel.linkedActiveReminderCount.collectAsStateWithLifecycle()
            DeleteConfirmDialog(
                title = "Delete \"${target.name}\"?",
                text = buildString {
                    append("This will permanently delete this event and all its entries.")
                    when {
                        linkedReminders == 1 -> append(" Its linked reminder will be turned off.")
                        linkedReminders > 1 -> append(" Its $linkedReminders linked reminders will be turned off.")
                    }
                },
                onConfirm = {
                    viewModel.deleteEventType(target.id)
                    showDeleteEventDialog = false
                    onNavigateBack()
                },
                onDismiss = { showDeleteEventDialog = false }
            )
        }
    }
}
