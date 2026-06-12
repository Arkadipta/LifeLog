package com.lifelog.app.ui.events.components

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.AggregationStrategy
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartType
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.TimeRange
import com.lifelog.app.ui.components.ColorDot
import com.lifelog.app.ui.components.SectionHeader
import com.lifelog.app.ui.components.SheetHeader
import com.lifelog.app.ui.theme.Sizing
import com.lifelog.app.ui.theme.Spacing
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartConfigSheet(
    eventTypeId: Long,
    fields: List<EventField>,
    editing: ChartConfig?,
    onSave: (ChartConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val numericFields = fields.filter { it.type == FieldType.NUMERIC }
    val categoricalFields = fields.filter {
        it.type == FieldType.CHOICE || it.type == FieldType.MULTI_SELECT
    }

    var selectedType by remember { mutableStateOf(editing?.type ?: ChartType.LINE) }
    var title by remember { mutableStateOf(editing?.title ?: "") }
    var selectedNumericIds by remember {
        mutableStateOf(editing?.numericFieldIds?.toSet() ?: emptySet<Long>())
    }
    var selectedGroupId by remember { mutableStateOf(editing?.groupByFieldId) }
    var selectedTimeRange by remember {
        mutableStateOf(TimeRange.fromDays(editing?.timeRangeDays))
    }
    // Per-field color map: key present = explicit color, absent = Auto
    var selectedFieldColors by remember {
        mutableStateOf<Map<Long, Int?>>(
            editing?.fieldColors?.mapValues { (_, v) -> v } ?: emptyMap()
        )
    }
    var selectedAggregation by remember {
        mutableStateOf(editing?.aggregation ?: AggregationStrategy.MEAN)
    }

    // fieldId whose color picker dialog is open; null = closed
    var colorPickerFieldId by remember { mutableStateOf<Long?>(null) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeader(
                title = if (editing == null) "Add Chart" else "Edit Chart",
                onClose = onDismiss
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.sheetEdge)
                    .padding(top = Spacing.sm, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Chart type
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ChartType.entries.forEachIndexed { i, type ->
                        SegmentedButton(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                if (type == ChartType.PIE && selectedNumericIds.size > 1) {
                                    selectedNumericIds = setOf(selectedNumericIds.first())
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, ChartType.entries.size),
                            label = { Text(type.displayName) }
                        )
                    }
                }

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Chart title (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Time range
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader("Time range")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        TimeRange.entries.forEach { range ->
                            FilterChip(
                                selected = selectedTimeRange == range,
                                onClick = { selectedTimeRange = range },
                                label = { Text(range.displayName, maxLines = 1) }
                            )
                        }
                    }
                }

                // Aggregation (line + bar only)
                if (selectedType != ChartType.PIE) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SectionHeader("Aggregation")
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            AggregationStrategy.entries.forEach { agg ->
                                FilterChip(
                                    selected = selectedAggregation == agg,
                                    onClick = { selectedAggregation = agg },
                                    label = { Text(agg.displayName, maxLines = 1) }
                                )
                            }
                        }
                    }
                }

                // Numeric field selection
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SectionHeader(
                        if (selectedType == ChartType.PIE) "Numeric field" else "Numeric fields"
                    )
                    if (numericFields.isEmpty()) {
                        Text(
                            "No numeric fields in this event.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        numericFields.forEach { field ->
                            val isSelected = field.id in selectedNumericIds
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (selectedType == ChartType.PIE) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedNumericIds = setOf(field.id) }
                                    )
                                } else {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            selectedNumericIds = if (checked) selectedNumericIds + field.id
                                            else selectedNumericIds - field.id
                                        }
                                    )
                                }
                                val label = if (field.unit.isNotBlank()) "${field.name} (${field.unit})"
                                            else field.name
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                // Color swatch — shown for selected line/bar fields; fixed size, no layout shift
                                if (selectedType != ChartType.PIE && isSelected) {
                                    ColorSwatchButton(
                                        color = selectedFieldColors[field.id]?.let { Color(it) },
                                        onClick = { colorPickerFieldId = field.id }
                                    )
                                }
                            }
                        }
                    }
                }

                // Group-by (pie only)
                if (selectedType == ChartType.PIE) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        SectionHeader("Group by")
                        if (categoricalFields.isEmpty()) {
                            Text(
                                "No choice or multi-select fields found. Add one to use pie charts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            categoricalFields.forEach { field ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    RadioButton(
                                        selected = selectedGroupId == field.id,
                                        onClick = { selectedGroupId = field.id }
                                    )
                                    Text(
                                        text = "${field.name} (${field.type.displayName})",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                val isValid = selectedNumericIds.isNotEmpty() &&
                    (selectedType != ChartType.PIE || selectedGroupId != null)

                Button(
                    onClick = {
                        val fieldColors = selectedFieldColors
                            .filterValues { it != null }
                            .mapValues { (_, v) -> v!! }
                        onSave(
                            ChartConfig(
                                id = editing?.id ?: UUID.randomUUID().toString(),
                                eventTypeId = eventTypeId,
                                title = title.trim(),
                                type = selectedType,
                                numericFieldIds = selectedNumericIds.toList(),
                                groupByFieldId = if (selectedType == ChartType.PIE) selectedGroupId else null,
                                timeRangeDays = selectedTimeRange.days,
                                colorArgb = null,
                                fieldColors = fieldColors,
                                sortOrder = editing?.sortOrder ?: 0,
                                createdAt = editing?.createdAt ?: System.currentTimeMillis(),
                                aggregation = if (selectedType == ChartType.PIE) AggregationStrategy.SUM
                                              else selectedAggregation
                            )
                        )
                        onDismiss()
                    },
                    enabled = isValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Sizing.cta)
                ) {
                    Text(if (editing == null) "Add Chart" else "Save Changes")
                }
            }
        }
    }

    // Color picker — rendered as a sibling to the bottom sheet so it floats above it
    colorPickerFieldId?.let { fieldId ->
        SeriesColorPickerSheet(
            currentColor = selectedFieldColors[fieldId],
            onColorSelected = { color ->
                selectedFieldColors = if (color == null)
                    selectedFieldColors - fieldId
                else
                    selectedFieldColors + (fieldId to color)
                colorPickerFieldId = null
            },
            onDismiss = { colorPickerFieldId = null }
        )
    }
}

/**
 * A fixed-size tappable button showing a 22 dp color swatch.
 * Null color = Auto; renders as a neutral ring with an "A".
 */
@Composable
private fun ColorSwatchButton(
    color: Color?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swatchColor = color ?: MaterialTheme.colorScheme.surfaceContainerHighest
    val ringColor = if (color == null)
        MaterialTheme.colorScheme.outline
    else
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    IconButton(onClick = onClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(1.5.dp, ringColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(22.dp),
                shape = CircleShape,
                color = swatchColor
            ) {}
            // "A" overlay when Auto so the neutral circle is not ambiguous
            if (color == null) {
                Text(
                    text = "A",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Sheet with an "Automatic" option and a grid of preset colors.
 * Selecting any option immediately confirms and closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesColorPickerSheet(
    currentColor: Int?,
    onColorSelected: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Theme-aware palette plus a few fixed accents
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,

        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.errorContainer,

        Color(0xFF409CFF),
        Color(0xFF4CAF50),
        Color(0xFFFF9800),
        Color(0xFFE91E63)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeader(title = "Series color")

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sheetEdge)
                    .padding(top = Spacing.sm, bottom = Spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl)
            ) {
                // Auto option
                Surface(
                    onClick = { onColorSelected(null) },
                    shape = MaterialTheme.shapes.large,
                    color = if (currentColor == null)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.lg, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "A",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Automatic",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Inherit event color",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (currentColor == null) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    SectionHeader("Preset colors")
                    palette.chunked(4).forEach { rowColors ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(
                                Spacing.lg,
                                Alignment.CenterHorizontally
                            )
                        ) {
                            rowColors.forEach { color ->
                                ColorDot(
                                    color = color,
                                    selected = currentColor == color.toArgb(),
                                    onClick = { onColorSelected(color.toArgb()) },
                                    size = 56.dp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
