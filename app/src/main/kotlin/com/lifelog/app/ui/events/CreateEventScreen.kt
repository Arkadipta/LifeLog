package com.lifelog.app.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.ui.components.ColorDot
import com.lifelog.app.ui.components.IconTile
import com.lifelog.app.ui.components.LabelChip
import com.lifelog.app.ui.components.LifeLogCard
import com.lifelog.app.ui.components.SectionHeader
import com.lifelog.app.ui.components.SheetHeader
import com.lifelog.app.ui.theme.EventColors
import com.lifelog.app.ui.theme.Sizing
import com.lifelog.app.ui.theme.Spacing
import com.lifelog.app.util.eventIconCategories
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
                    Text(if (eventId == 0L) "New Event" else "Edit Event")
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
            contentPadding = PaddingValues(Spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
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

            item {
                IconPicker(
                    selected = state.iconName,
                    accent = Color(state.colorArgb),
                    onSelect = viewModel::setIcon
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Fields")
                    FilledTonalButton(onClick = { showAddFieldSheet = true }) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Spacing.xs))
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

            item { Spacer(Modifier.height(Spacing.fabClearance - Spacing.screenEdge)) }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("Color")
        // The full palette wraps onto a few rows so every swatch is visible at
        // once without horizontal scrolling.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            EventColors.forEach { color ->
                val argb = color.toArgb()
                ColorDot(
                    color = color,
                    selected = argb == selected,
                    onClick = { onSelect(argb) },
                    size = 44.dp
                )
            }
        }
    }
}

/**
 * The icon library is large, so rather than a long inline strip the picker shows
 * the current selection as a tappable row that opens a category-grouped sheet.
 * The preview tile uses the chosen [accent] so it reflects how the event will
 * actually look on cards.
 */
@Composable
private fun IconPicker(selected: String, accent: Color, onSelect: (String) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("Icon")
        Surface(
            onClick = { showSheet = true },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                IconTile(icon = iconForName(selected), tint = accent)
                Text(
                    "Choose icon",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.AutoMirrored.Rounded.NavigateNext,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showSheet) {
        IconPickerSheet(
            selected = selected,
            onSelect = {
                onSelect(it)
                showSheet = false
            },
            onDismiss = { showSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IconPickerSheet(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Bound the scrollable grid so it doesn't try to take infinite height inside
    // the sheet, while still filling most of the screen for easy browsing.
    val maxGridHeight = (LocalConfiguration.current.screenHeightDp * 0.62f).dp

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeader(title = "Choose Icon", onClose = onDismiss)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 56.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxGridHeight),
                contentPadding = PaddingValues(
                    start = Spacing.sheetEdge,
                    end = Spacing.sheetEdge,
                    bottom = Spacing.xxl
                ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                eventIconCategories.forEach { category ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            category.title,
                            modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs)
                        )
                    }
                    gridItems(category.icons, key = { it.first }) { (key, icon) ->
                        val isSelected = key == selected
                        Surface(
                            onClick = { onSelect(key) },
                            shape = MaterialTheme.shapes.medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = key,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
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
    LifeLogCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(field.name, style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LabelChip(field.type.displayName)
                    if (field.isRequired) {
                        LabelChip(
                            "Required",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    if (field.unit.isNotBlank()) {
                        LabelChip(
                            field.unit,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
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
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeader(
                title = if (existingField == null) "Add Field" else "Edit Field",
                onClose = onDismiss
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.sheetEdge)
                    .padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text("Field Name *") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader("Field Type")
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
                    Text("Required field", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = isRequired, onCheckedChange = { isRequired = it })
                }

                Button(
                    onClick = {
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
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Sizing.cta)
                ) {
                    Text(if (existingField == null) "Add Field" else "Save Field")
                }
            }
        }
    }
}
