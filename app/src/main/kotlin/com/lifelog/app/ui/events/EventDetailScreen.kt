package com.lifelog.app.ui.events

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddChart
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.ui.components.DeleteConfirmDialog
import com.lifelog.app.ui.components.LifeLogCard
import com.lifelog.app.ui.components.LifeLogFab
import com.lifelog.app.ui.components.SwipeDeleteBackground
import com.lifelog.app.ui.events.components.ChartCarousel
import com.lifelog.app.ui.events.components.ChartConfigSheet
import com.lifelog.app.ui.theme.Motion
import com.lifelog.app.ui.theme.Spacing
import com.lifelog.app.ui.theme.bestContentColor
import com.lifelog.app.util.iconForName
import com.lifelog.app.util.relativeTimeLabel
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
    var deleteTarget by remember { mutableStateOf<EventEntry?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var showChartConfigSheet by remember { mutableStateOf(false) }
    var editingChart by remember { mutableStateOf<ChartConfig?>(null) }
    var overflowMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteEventDialog by remember { mutableStateOf(false) }
    var showFilterSortSheet by remember { mutableStateOf(false) }

    val entryQuery by viewModel.entryQuery.collectAsStateWithLifecycle()

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

    val hasNumericFields = remember(eventType) {
        eventType?.fields?.any { it.type == FieldType.NUMERIC } == true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(eventType?.name ?: "")
                        if (eventType?.category?.isNotBlank() == true) {
                            Text(
                                eventType!!.category,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                            if (hasNumericFields) {
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

            if (entries.isEmpty() && searchQuery.isBlank() && !hasNumericFields) {
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
                            tint = Color(it.colorArgb).copy(alpha = 0.4f)
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.fabClearance)
                ) {
                    // Chart carousel — only when at least one chart has been created
                    if (hasNumericFields && charts.isNotEmpty()) {
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
                                modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.md)
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
                                        tint = Color(it.colorArgb).copy(alpha = 0.4f)
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

                    items(entries, key = { it.id }) { entry ->
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
                            modifier = Modifier
                                .animateItem()
                                .padding(horizontal = Spacing.screenEdge, vertical = Spacing.cardGap / 2)
                        ) {
                            EntryCard(
                                entry = entry,
                                fields = eventType?.fields ?: emptyList(),
                                onEdit = {
                                    editingEntryId = entry.id
                                    showEntrySheet = true
                                },
                                onDelete = { deleteTarget = entry }
                            )
                        }
                    }
                }
            }
        }
    }

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
            eventTypeId = eventId,
            editingEntryId = editingEntryId,
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
            DeleteConfirmDialog(
                title = "Delete \"${target.name}\"?",
                text = "This will permanently delete this event and all its entries.",
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

/**
 * One logged entry. Shows up to two field values; taps expand the rest with
 * a spring. Used on the event detail screen and (with [showEventName]) the
 * timeline.
 */
@Composable
fun EntryCard(
    entry: EventEntry,
    fields: List<com.lifelog.app.domain.model.EventField>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showEventName: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val orderedFields = fields.filter { entry.fieldValues.containsKey(it.id) }
    val preview = orderedFields.take(2)
    val rest = orderedFields.drop(2)
    val hasHiddenContent = rest.isNotEmpty()

    LifeLogCard(
        onClick = if (hasHiddenContent) ({ expanded = !expanded }) else null,
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = Motion.spatial())
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (showEventName) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                iconForName(entry.eventTypeIcon),
                                null,
                                tint = Color(entry.eventTypeColor),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                entry.eventTypeName,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(entry.eventTypeColor),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(Spacing.xs))
                    }
                    Text(
                        entry.createdAt.toDisplayDateTime(),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        entry.createdAt.relativeTimeLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasHiddenContent) {
                        AnimatedContent(
                            targetState = expanded,
                            transitionSpec = {
                                fadeIn(tween(Motion.SHORT)) togetherWith fadeOut(tween(Motion.SHORT))
                            },
                            label = "expand_icon"
                        ) { isExpanded ->
                            Icon(
                                imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowUp
                                              else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Rounded.Edit, "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Delete,
                            "Delete",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (preview.isNotEmpty() || entry.note.isNotBlank()) {
                Spacer(Modifier.height(Spacing.sm))
            }

            preview.forEach { field ->
                entry.fieldValues[field.id]?.let { fv ->
                    FieldValueRow(fieldName = field.name, value = fv.displayString())
                }
            }

            if (entry.note.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Note: ${entry.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hasHiddenContent && expanded) {
                rest.forEach { field ->
                    entry.fieldValues[field.id]?.let { fv ->
                        FieldValueRow(fieldName = field.name, value = fv.displayString())
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldValueRow(fieldName: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Text(
            fieldName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}
