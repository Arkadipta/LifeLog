package com.lifelog.app.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.ui.theme.EventColors
import com.lifelog.app.util.eventIconMap
import com.lifelog.app.util.iconForName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEventScreen(
    eventId: Long = 0L,
    onNavigateBack: () -> Unit,
    viewModel: CreateEventViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(eventId) {
        if (eventId != 0L) viewModel.loadEvent(eventId)
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }

    var showAddFieldSheet by remember { mutableStateOf(false) }
    var editingFieldIndex by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (eventId == 0L) "New Event" else "Edit Event",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.save(eventId) },
                        enabled = !state.isLoading
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Icon(Icons.Rounded.Check, "Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    label = { Text("Event Name *") },
                    isError = state.nameError != null,
                    supportingText = state.nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                OutlinedTextField(
                    value = state.description,
                    onValueChange = viewModel::setDescription,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }

            item {
                OutlinedTextField(
                    value = state.category,
                    onValueChange = viewModel::setCategory,
                    label = { Text("Tag / Category") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null) }
                )
            }

            item { ColorPicker(selected = state.colorArgb, onSelect = viewModel::setColor) }

            item { IconPicker(selected = state.iconName, onSelect = viewModel::setIcon) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Fields", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FilledTonalButton(onClick = { showAddFieldSheet = true }) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add Field")
                    }
                }
            }

            itemsIndexed(state.fields) { index, field ->
                FieldConfigCard(
                    field = field,
                    index = index,
                    totalCount = state.fields.size,
                    onEdit = { editingFieldIndex = index },
                    onDelete = { viewModel.removeField(index) },
                    onMoveUp = { viewModel.moveFieldUp(index) },
                    onMoveDown = { viewModel.moveFieldDown(index) }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showAddFieldSheet) {
        AddFieldSheet(
            onDismiss = { showAddFieldSheet = false },
            onAdd = { field ->
                viewModel.addField(field)
                showAddFieldSheet = false
            }
        )
    }

    editingFieldIndex?.let { idx ->
        if (idx < state.fields.size) {
            AddFieldSheet(
                existingField = state.fields[idx],
                onDismiss = { editingFieldIndex = null },
                onAdd = { field ->
                    viewModel.updateField(idx, field)
                    editingFieldIndex = null
                }
            )
        }
    }
}

@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Color", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(EventColors) { color ->
                val argb = color.toArgb()
                val isSelected = argb == selected
                Box(
                    modifier = Modifier
                        .size(40.dp)  // 40dp meets minimum 44dp guideline with spacedBy(10dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier
                        )
                        .semantics {
                            role = Role.RadioButton
                            stateDescription = if (isSelected) "Selected" else "Not selected"
                        }
                        .clickable { onSelect(argb) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        // Checkmark gives clear selected state on all colors
                        val lum = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (lum > 0.4f) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IconPicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Icon", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(eventIconMap.keys.toList()) { iconName ->
                val isSelected = iconName == selected
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable { onSelect(iconName) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = iconForName(iconName),
                            contentDescription = iconName,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldConfigCard(
    field: EventField,
    index: Int,
    totalCount: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(field.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Surface badges replace AssistChip (which semantically implies an action)
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            field.type.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    if (field.isRequired) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                "Required",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (field.unit.isNotBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                field.unit,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Column {
                IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.KeyboardArrowUp, "Move up", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onMoveDown, enabled = index < totalCount - 1, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.KeyboardArrowDown, "Move down", modifier = Modifier.size(18.dp))
                }
            }

            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, "Edit") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFieldSheet(
    existingField: EventField? = null,
    onDismiss: () -> Unit,
    onAdd: (EventField) -> Unit
) {
    var name by remember { mutableStateOf(existingField?.name ?: "") }
    var fieldType by remember { mutableStateOf(existingField?.type ?: FieldType.TEXT) }
    var unit by remember { mutableStateOf(existingField?.unit ?: "") }
    var isRequired by remember { mutableStateOf(existingField?.isRequired ?: false) }
    var options by remember { mutableStateOf(existingField?.options?.joinToString("\n") ?: "") }
    var nameError by remember { mutableStateOf<String?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                if (existingField == null) "Add Field" else "Edit Field",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null },
                label = { Text("Field Name *") },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Field Type", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(FieldType.entries) { type ->
                        FilterChip(
                            selected = fieldType == type,
                            onClick = { fieldType = type },
                            label = { Text(type.displayName, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            if (fieldType == FieldType.NUMERIC) {
                OutlinedTextField(
                    value = unit,
                    onValueChange = { unit = it },
                    label = { Text("Unit (e.g. kg, ml)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (fieldType == FieldType.CHOICE || fieldType == FieldType.MULTI_SELECT) {
                OutlinedTextField(
                    value = options,
                    onValueChange = { options = it },
                    label = { Text("Options (one per line)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Required field")
                Switch(checked = isRequired, onCheckedChange = { isRequired = it })
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (name.isBlank()) {
                        nameError = "Name is required"
                        return@Button
                    }
                    onAdd(
                        EventField(
                            id = existingField?.id ?: 0L,
                            name = name.trim(),
                            type = fieldType,
                            unit = unit.trim(),
                            isRequired = isRequired,
                            options = if (fieldType == FieldType.CHOICE || fieldType == FieldType.MULTI_SELECT) {
                                options.lines().map { it.trim() }.filter { it.isNotBlank() }
                            } else emptyList()
                        )
                    )
                }) { Text("Save") }
            }
        }
    }
}
