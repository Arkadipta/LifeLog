package com.lifelog.app.ui.events

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.ui.theme.LocalAmoledColors
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

    var showEntrySheet by remember { mutableStateOf(false) }
    var editingEntryId by remember { mutableStateOf<Long?>(null) }
    var deleteTarget by remember { mutableStateOf<EventEntry?>(null) }
    var searchActive by remember { mutableStateOf(false) }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(eventType?.name ?: "", fontWeight = FontWeight.Bold)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) viewModel.setSearchQuery("")
                    }) {
                        Icon(
                            if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                            if (searchActive) "Close search" else "Search entries"
                        )
                    }
                    IconButton(onClick = {
                        val name = eventType?.name?.replace(" ", "_") ?: "event"
                        exportLauncher.launch("${name}_export.csv")
                    }) {
                        Icon(Icons.Filled.Upload, "Export CSV")
                    }
                    IconButton(onClick = { onNavigateToEdit(eventId) }) {
                        Icon(Icons.Filled.Edit, "Edit Event")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = eventType?.let { Color(it.colorArgb).copy(alpha = 0.1f) }
                        ?: MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingEntryId = null
                    showEntrySheet = true
                },
                containerColor = eventType?.let { Color(it.colorArgb) }
                    ?: MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Filled.Add, "Add Entry", tint = Color.White)
            }
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
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    placeholder = { Text("Search entries…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Filled.Close, "Clear search")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    singleLine = true
                )
            }

            if (entries.isEmpty() && searchQuery.isBlank()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        eventType?.let {
                            Icon(
                                iconForName(it.iconName),
                                null,
                                modifier = Modifier.size(64.dp),
                                tint = Color(it.colorArgb).copy(alpha = 0.4f)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("No entries yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap + to log your first entry",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (entries.isEmpty()) {
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
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(entries, key = { it.id }) { entry ->
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
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showEntrySheet) {
        EntryFormSheet(
            eventTypeId = eventId,
            editingEntryId = editingEntryId,
            onDismiss = { showEntrySheet = false }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete entry?") },
            text = { Text("This entry logged at ${target.createdAt.toDisplayDateTime()} will be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteEntry(target.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun EntryCard(
    entry: EventEntry,
    fields: List<com.lifelog.app.domain.model.EventField>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showEventName: Boolean = false
) {
    val isAmoled = LocalAmoledColors.current.isAmoled
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        entry.createdAt.toDisplayDateTime(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        entry.createdAt.relativeTimeLabel(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Edit, "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Delete,
                            "Delete",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (entry.fieldValues.isNotEmpty() || entry.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(8.dp))
            }

            val orderedFields = fields.filter { entry.fieldValues.containsKey(it.id) }
            val preview = orderedFields.take(2)
            val rest = orderedFields.drop(2)

            preview.forEach { field ->
                entry.fieldValues[field.id]?.let { fv ->
                    FieldValueRow(fieldName = field.name, value = fv.displayString())
                }
            }

            if (entry.note.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Note: ${entry.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (rest.isNotEmpty()) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.padding(top = 4.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        if (expanded) "Show less" else "+${rest.size} more",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (expanded) {
                    rest.forEach { field ->
                        entry.fieldValues[field.id]?.let { fv ->
                            FieldValueRow(fieldName = field.name, value = fv.displayString())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldValueRow(fieldName: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$fieldName:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 80.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
