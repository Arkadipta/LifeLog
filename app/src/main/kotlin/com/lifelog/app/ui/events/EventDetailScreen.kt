package com.lifelog.app.ui.events

import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.ui.theme.LocalAmoledColors
import com.lifelog.app.util.iconForName
import com.lifelog.app.util.relativeTimeLabel
import com.lifelog.app.util.toDisplayDateTime
import kotlinx.coroutines.delay

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

    val context = LocalContext.current
    val animationsEnabled = remember {
        AndroidSettings.Global.getFloat(
            context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
    }
    var fabVisible by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            delay(200)
            fabVisible = true
        }
    }

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

    // Compute FAB content color from luminance to ensure contrast on all 10 event colors
    val fabColor = eventType?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary
    val fabContentColor = remember(fabColor) {
        val lum = 0.2126f * fabColor.red + 0.7152f * fabColor.green + 0.0722f * fabColor.blue
        if (lum > 0.4f) Color.Black else Color.White
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchActive = !searchActive
                        if (!searchActive) viewModel.setSearchQuery("")
                    }) {
                        Icon(
                            if (searchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                            if (searchActive) "Close search" else "Search entries"
                        )
                    }
                    IconButton(onClick = {
                        val name = eventType?.name?.replace(" ", "_") ?: "event"
                        exportLauncher.launch("${name}_export.csv")
                    }) {
                        Icon(Icons.Rounded.Upload, "Export CSV")
                    }
                    IconButton(onClick = { onNavigateToEdit(eventId) }) {
                        Icon(Icons.Rounded.Edit, "Edit Event")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = eventType?.let { Color(it.colorArgb).copy(alpha = 0.1f) }
                        ?: MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    initialScale = 0.85f
                ) + fadeIn(tween(150)),
                exit = scaleOut(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    targetScale = 0.85f
                ) + fadeOut(tween(100))
            ) {
                FloatingActionButton(
                    onClick = {
                        editingEntryId = null
                        showEntrySheet = true
                    },
                    containerColor = fabColor,
                    contentColor = fabContentColor
                ) {
                    Icon(Icons.Rounded.Add, "Add Entry")
                }
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
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Rounded.Close, "Clear search")
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
                    Spacer(Modifier.height(16.dp))
                    Text("No entries yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap + to log your first entry",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(0.62f))
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
                            backgroundContent = {
                                val color by animateColorAsState(
                                    targetValue = when (dismissState.targetValue) {
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                        else -> Color.Transparent
                                    },
                                    label = "swipe_delete_bg"
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(color, MaterialTheme.shapes.medium)
                                        .padding(end = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            },
                            enableDismissFromEndToStart = true,
                            enableDismissFromStartToEnd = false,
                            modifier = Modifier.animateItem()
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
            icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete entry?") },
            text = { Text("This entry logged at ${target.createdAt.toDisplayDateTime()} will be deleted.") },
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

@Composable
fun EntryCard(
    entry: EventEntry,
    fields: List<com.lifelog.app.domain.model.EventField>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showEventName: Boolean = false,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isAmoled = LocalAmoledColors.current.isAmoled
    var expanded by remember { mutableStateOf(false) }

    val orderedFields = fields.filter { entry.fieldValues.containsKey(it.id) }
    val preview = orderedFields.take(2)
    val rest = orderedFields.drop(2)
    val hasHiddenContent = rest.isNotEmpty()

    Card(
        onClick = { if (hasHiddenContent) expanded = !expanded },
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasHiddenContent) {
                        AnimatedContent(
                            targetState = expanded,
                            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
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

            if (entry.fieldValues.isNotEmpty() || entry.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                if (showDivider) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(8.dp))
                }
            }

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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "$fieldName:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.widthIn(min = 80.dp)
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
