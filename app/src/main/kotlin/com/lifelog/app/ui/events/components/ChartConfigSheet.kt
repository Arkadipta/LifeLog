package com.lifelog.app.ui.events.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartType
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldType
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
                text = if (editing == null) "Add Chart" else "Edit Chart",
                style = MaterialTheme.typography.titleLarge
            )

            // Chart type selector
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

            // Title input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Chart title (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

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

            // Group-by selection (pie only)
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
                            sortOrder = editing?.sortOrder ?: 0,
                            createdAt = editing?.createdAt ?: System.currentTimeMillis()
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
