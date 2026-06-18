package com.lifelog.app.ui.events

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
import com.lifelog.app.ui.theme.bestContentColor
import com.lifelog.app.ui.theme.EventColors
import com.lifelog.app.ui.theme.Sizing
import com.lifelog.app.ui.theme.Spacing
import com.lifelog.app.util.eventIconCategories
import com.lifelog.app.util.iconForName
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

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

/** A settings-style row that opens a picker sheet: leading preview, label, chevron. */
@Composable
private fun PickerRow(
    label: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            leading()
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Rounded.NavigateNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Solid color tile used as the color picker's [PickerRow] leading preview. */
@Composable
private fun ColorSwatch(color: Color, size: Dp = Sizing.iconTile) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.size(size)
    ) {}
}

@Composable
private fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        SectionHeader("Color")
        PickerRow(label = "Choose color", onClick = { showSheet = true }) {
            ColorSwatch(Color(selected))
        }
    }

    if (showSheet) {
        ColorPickerSheet(
            selected = selected,
            onSelect = {
                onSelect(it)
                showSheet = false
            },
            onDismiss = { showSheet = false }
        )
    }
}

/**
 * Color picker sheet: a small set of curated presets for quick taps, plus a hue
 * wheel + brightness slider so any custom color can be chosen (dark and
 * low-contrast included — that's allowed by design). HSV is the source of truth
 * while editing; [presetArgb] preserves the exact stored value when a preset is
 * tapped and is cleared once the wheel/slider is touched.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerSheet(
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialHsv = remember(selected) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(selected, it) }
    }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var value by remember { mutableStateOf(initialHsv[2]) }
    var presetArgb by remember {
        mutableStateOf(if (EventColors.any { it.toArgb() == selected }) selected else null)
    }

    val current = Color.hsv(hue, saturation, value)
    val resultArgb = presetArgb ?: current.toArgb()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeader(title = "Choose Color", onClose = onDismiss)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.sheetEdge)
                    .padding(bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                ColorPreview(current)

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader("Palette")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        EventColors.forEach { color ->
                            val argb = color.toArgb()
                            ColorDot(
                                color = color,
                                selected = argb == presetArgb,
                                onClick = {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(argb, hsv)
                                    hue = hsv[0]; saturation = hsv[1]; value = hsv[2]
                                    presetArgb = argb
                                },
                                size = 44.dp
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    SectionHeader("Custom")
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ColorWheel(
                            hue = hue,
                            saturation = saturation,
                            value = value,
                            onChange = { h, s -> hue = h; saturation = s; presetArgb = null }
                        )
                    }
                    ValueSlider(
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        onChange = { value = it; presetArgb = null },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = { onSelect(resultArgb) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Sizing.cta)
                ) {
                    Text("Select color")
                }
            }
        }
    }
}

/** Live preview: the working color with its hex in the contrast-adaptive on-color. */
@Composable
private fun ColorPreview(color: Color) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "#%06X".format(0xFFFFFF and color.toArgb()),
                style = MaterialTheme.typography.titleMedium,
                color = color.bestContentColor()
            )
        }
    }
}

/**
 * Hue-saturation wheel: angle = hue, radius from center = saturation. Drawn at
 * the current [value] (darkened toward the rim of brightness) with a draggable
 * thumb. The pointer math and the sweep gradient share the same angle
 * convention so the thumb always sits on the color it selects.
 */
@Composable
private fun ColorWheel(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (hue: Float, saturation: Float) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 224.dp
) {
    val hueColors = remember {
        listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
    }
    Canvas(
        modifier = modifier
            .size(diameter)
            .pointerInput(Unit) {
                detectTapGestures { handleWheelInput(it, size, onChange) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> handleWheelInput(change.position, size, onChange) }
            }
    ) {
        val radius = this.size.minDimension / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        drawCircle(Brush.sweepGradient(hueColors, center), radius, center)
        drawCircle(
            Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius),
            radius,
            center
        )
        if (value < 1f) drawCircle(Color.Black.copy(alpha = 1f - value), radius, center)

        val angle = Math.toRadians(hue.toDouble())
        val thumbDist = saturation * radius
        val thumb = Offset(
            center.x + (cos(angle) * thumbDist).toFloat(),
            center.y + (sin(angle) * thumbDist).toFloat()
        )
        drawCircle(Color.White, 9.dp.toPx(), thumb)
        drawCircle(Color.Black.copy(alpha = 0.4f), 9.dp.toPx(), thumb, style = Stroke(2.dp.toPx()))
    }
}

private fun handleWheelInput(pos: Offset, size: IntSize, onChange: (Float, Float) -> Unit) {
    val radius = min(size.width, size.height) / 2f
    val dx = pos.x - size.width / 2f
    val dy = pos.y - size.height / 2f
    val saturation = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
    var degrees = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
    if (degrees < 0f) degrees += 360f
    onChange(degrees, saturation)
}

/** Brightness (value) slider: black → the full-brightness hue, with a draggable thumb. */
@Composable
private fun ValueSlider(
    hue: Float,
    saturation: Float,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val fullColor = Color.hsv(hue, saturation, 1f)
    Canvas(
        modifier = modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> onChange((change.position.x / size.width).coerceIn(0f, 1f)) }
            }
    ) {
        val trackHeight = this.size.height
        val trackWidth = this.size.width
        val r = trackHeight / 2f
        drawRoundRect(
            brush = Brush.horizontalGradient(listOf(Color.Black, fullColor)),
            cornerRadius = CornerRadius(r, r)
        )
        val tx = (value * trackWidth).coerceIn(r, trackWidth - r)
        drawCircle(Color.White, r * 0.7f, Offset(tx, trackHeight / 2f))
        drawCircle(Color.Black.copy(alpha = 0.35f), r * 0.7f, Offset(tx, trackHeight / 2f), style = Stroke(2.dp.toPx()))
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
        PickerRow(label = "Choose icon", onClick = { showSheet = true }) {
            IconTile(icon = iconForName(selected), tint = accent)
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
