package com.lifelog.app.ui.events.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.AggregationStrategy
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartType
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.TimeRange
import java.util.UUID

private val PRESET_COLORS = listOf(
    0xFF6750A4, 0xFF409CFF, 0xFF4CAF50, 0xFFFF9800, 0xFFF44336,
    0xFF9C27B0, 0xFF00BCD4, 0xFFE91E63, 0xFF009688, 0xFF795548,
    0xFFFFEB3B, 0xFF607D8B
).map { it.toInt() }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var selectedColor by remember { mutableStateOf(editing?.colorArgb) }
    var selectedAggregation by remember {
        mutableStateOf(editing?.aggregation ?: AggregationStrategy.MEAN)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = if (editing == null) "Add Chart" else "Edit Chart",
                style = MaterialTheme.typography.titleLarge
            )

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
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            HorizontalDivider()

            // Time range
            Text(
                text = "Time range",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TimeRange.entries.forEachIndexed { i, range ->
                    SegmentedButton(
                        selected = selectedTimeRange == range,
                        onClick = { selectedTimeRange = range },
                        shape = SegmentedButtonDefaults.itemShape(i, TimeRange.entries.size),
                        label = { Text(range.displayName) }
                    )
                }
            }

            // Aggregation (line + bar only)
            if (selectedType != ChartType.PIE) {
                HorizontalDivider()
                Text(
                    text = "Aggregation",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                val aggEntries = AggregationStrategy.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    aggEntries.forEachIndexed { i, agg ->
                        SegmentedButton(
                            selected = selectedAggregation == agg,
                            onClick = { selectedAggregation = agg },
                            shape = SegmentedButtonDefaults.itemShape(i, aggEntries.size),
                            label = { Text(agg.displayName) }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Plot color
            Text(
                text = "Plot color",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // "Auto" (use event color)
                ColorDot(
                    color = MaterialTheme.colorScheme.primary,
                    selected = selectedColor == null,
                    label = "Auto",
                    onClick = { selectedColor = null }
                )
                PRESET_COLORS.forEach { argb ->
                    ColorDot(
                        color = Color(argb),
                        selected = selectedColor == argb,
                        onClick = { selectedColor = argb }
                    )
                }
            }

            HorizontalDivider()

            // Numeric field selection
            Text(
                text = if (selectedType == ChartType.PIE) "Numeric field" else "Numeric fields",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
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
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Group-by (pie only)
            if (selectedType == ChartType.PIE) {
                HorizontalDivider()
                Text(
                    text = "Group by",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
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

            Spacer(Modifier.height(4.dp))

            val isValid = selectedNumericIds.isNotEmpty() &&
                (selectedType != ChartType.PIE || selectedGroupId != null)

            Button(
                onClick = {
                    onSave(
                        ChartConfig(
                            id = editing?.id ?: UUID.randomUUID().toString(),
                            eventTypeId = eventTypeId,
                            title = title.trim(),
                            type = selectedType,
                            numericFieldIds = selectedNumericIds.toList(),
                            groupByFieldId = if (selectedType == ChartType.PIE) selectedGroupId else null,
                            timeRangeDays = selectedTimeRange.days,
                            colorArgb = selectedColor,
                            sortOrder = editing?.sortOrder ?: 0,
                            createdAt = editing?.createdAt ?: System.currentTimeMillis(),
                            aggregation = if (selectedType == ChartType.PIE) AggregationStrategy.SUM
                                          else selectedAggregation
                        )
                    )
                    onDismiss()
                },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (editing == null) "Add Chart" else "Save Changes")
            }
        }
    }
}

@Composable
private fun ColorDot(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(if (selected) 28.dp else 36.dp),
            shape = CircleShape,
            color = color
        ) {}
        if (selected) {
            val lum = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (lum > 0.4f) Color.Black else Color.White
            )
        }
    }
}
