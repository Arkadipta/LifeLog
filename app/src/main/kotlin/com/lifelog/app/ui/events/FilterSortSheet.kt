package com.lifelog.app.ui.events

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.query.*
import com.lifelog.app.ui.components.LifeLogCard
import com.lifelog.app.ui.components.SectionHeader
import com.lifelog.app.ui.components.SheetHeader
import com.lifelog.app.ui.theme.LifeLogTheme
import com.lifelog.app.ui.theme.Sizing
import com.lifelog.app.ui.theme.Spacing

/**
 * Modal bottom sheet for configuring filter conditions and sort specification on entries.
 *
 * Changes are staged locally and only committed to the ViewModel when the user taps Apply.
 * Tapping Clear All resets the committed query to Empty and closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSortSheet(
    fields: List<EventField>,
    currentQuery: EntryQuery,
    onApply: (EntryQuery) -> Unit,
    onDismiss: () -> Unit
) {
    // Local draft — not committed until user taps Apply
    var draftFilters by remember(currentQuery) { mutableStateOf(currentQuery.filters.toMutableList() as List<FilterCondition>) }
    var draftLogicalOp by remember(currentQuery) { mutableStateOf(currentQuery.logicalOperator) }
    var draftSort by remember(currentQuery) { mutableStateOf(currentQuery.sort) }

    var showAddFilterDialog by remember { mutableStateOf(false) }
    var editingFilterIndex by remember { mutableStateOf<Int?>(null) }

    val filterableFields = remember(fields) { fields.filter { it.type != FieldType.TEXT } }
    val sortableFields = remember(fields) { fields.filter { it.type == FieldType.NUMERIC } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            SheetHeader(title = "Filter & Sort", onClose = onDismiss)

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.sheetEdge)
            ) {
                // ── Filters section ──────────────────────────────────────────────
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Filters",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (draftFilters.isNotEmpty()) {
                        // AND / OR toggle
                        LogicalOperatorToggle(
                            selected = draftLogicalOp,
                            onSelect = { draftLogicalOp = it }
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (filterableFields.isNotEmpty()) {
                        FilledTonalIconButton(onClick = { showAddFilterDialog = true }) {
                            Icon(Icons.Rounded.Add, "Add filter", modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (draftFilters.isEmpty()) {
                    Text(
                        "No filters applied",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    draftFilters.forEachIndexed { index, condition ->
                        FilterConditionChip(
                            condition = condition,
                            onEdit = { editingFilterIndex = index },
                            onRemove = { draftFilters = draftFilters.toMutableList().also { it.removeAt(index) } }
                        )
                        if (index < draftFilters.lastIndex) {
                            Text(
                                draftLogicalOp.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
                            )
                        }
                    }
                }

                // ── Sort section ─────────────────────────────────────────────────
                Spacer(Modifier.height(Spacing.xl))
                Text(
                    "Sort",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(Spacing.md))
                SortSection(
                    sortableFields = sortableFields,
                    current = draftSort,
                    onChange = { draftSort = it }
                )

                Spacer(Modifier.height(Spacing.sm))
            }

            // ── Action buttons ────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sheetEdge, vertical = Spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                FilledTonalButton(
                    onClick = {
                        onApply(EntryQuery.Empty)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(Sizing.cta)
                ) { Text("Clear All") }

                Button(
                    onClick = {
                        onApply(EntryQuery(draftFilters, draftLogicalOp, draftSort))
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(Sizing.cta)
                ) { Text("Apply") }
            }
        }
    }

    // ── Add / Edit filter dialog ─────────────────────────────────────────────
    if (showAddFilterDialog) {
        FilterBuilderDialog(
            fields = filterableFields,
            initial = null,
            onConfirm = { newCondition ->
                draftFilters = draftFilters + newCondition
                showAddFilterDialog = false
            },
            onDismiss = { showAddFilterDialog = false }
        )
    }

    editingFilterIndex?.let { idx ->
        FilterBuilderDialog(
            fields = filterableFields,
            initial = draftFilters.getOrNull(idx),
            onConfirm = { updated ->
                draftFilters = draftFilters.toMutableList().also { it[idx] = updated }
                editingFilterIndex = null
            },
            onDismiss = { editingFilterIndex = null }
        )
    }
}

// ── LogicalOperatorToggle ─────────────────────────────────────────────────────

@Composable
private fun LogicalOperatorToggle(
    selected: LogicalOperator,
    onSelect: (LogicalOperator) -> Unit
) {
    Row(
        modifier = Modifier
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LogicalOperator.entries.forEach { op ->
            FilterChip(
                selected = selected == op,
                onClick = { onSelect(op) },
                label = { Text(op.label, style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}

// ── FilterConditionChip ──────────────────────────────────────────────────────

@Composable
private fun FilterConditionChip(
    condition: FilterCondition,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val label = when (condition) {
        is FilterCondition.NumericFilter ->
            "${condition.fieldName} ${condition.operator.symbol} ${condition.value.toDisplayValue()}"
        is FilterCondition.BooleanFilter ->
            "${condition.fieldName} ${condition.operator.label} ${if (condition.value) "Yes" else "No"}"
        is FilterCondition.ChoiceFilter ->
            "${condition.fieldName} ${condition.operator.label} ${condition.value}"
        is FilterCondition.MultiSelectFilter ->
            "${condition.fieldName} ${condition.operator.label} ${condition.value}"
    }

    LifeLogCard(
        onClick = onEdit,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Rounded.Delete,
                    "Remove filter",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    Spacer(Modifier.height(Spacing.xs))
}

private fun Double.toDisplayValue(): String =
    if (this == toLong().toDouble()) toLong().toString() else toString()

// ── SortSection ──────────────────────────────────────────────────────────────

@Composable
private fun SortSection(
    sortableFields: List<EventField>,
    current: SortSpecification?,
    onChange: (SortSpecification?) -> Unit
) {
    val sortFieldOptions: List<SortField> = remember(sortableFields) {
        listOf(SortField.Timestamp) + sortableFields.map { SortField.NumericField(it.id, it.name) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // ── Sort field — horizontal scrollable row ────────────────────────────
        SectionHeader("Sort by")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // "Clear" chip — only shown when a field is selected
            if (current != null) {
                InputChip(
                    selected = false,
                    onClick = { onChange(null) },
                    label = { Text("Clear") },
                    trailingIcon = {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear sort",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
            sortFieldOptions.forEach { field ->
                FilterChip(
                    selected = current?.field?.let { fieldEquals(it, field) } == true,
                    onClick = {
                        val dir = current?.direction ?: SortDirection.DESCENDING
                        onChange(SortSpecification(field, dir))
                    },
                    label = { Text(field.displayName) }
                )
            }
        }

        // ── Direction — always visible; disabled when no field is selected ───
        SectionHeader("Direction")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortDirection.entries.forEach { dir ->
                FilterChip(
                    selected = current?.direction == dir,
                    onClick = {
                        val field = current?.field ?: return@FilterChip
                        onChange(SortSpecification(field, dir))
                    },
                    enabled = current != null,
                    label = { Text(dir.label) }
                )
            }
        }
    }
}

private fun fieldEquals(a: SortField, b: SortField): Boolean = when {
    a is SortField.Timestamp && b is SortField.Timestamp -> true
    a is SortField.NumericField && b is SortField.NumericField -> a.fieldId == b.fieldId
    else -> false
}

// ── FilterBuilderDialog ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBuilderDialog(
    fields: List<EventField>,
    initial: FilterCondition?,
    onConfirm: (FilterCondition) -> Unit,
    onDismiss: () -> Unit
) {
    // Step 1: field selection; Step 2: operator + value
    var selectedField by remember {
        mutableStateOf(
            when (initial) {
                is FilterCondition.NumericFilter -> fields.find { it.id == initial.fieldId }
                is FilterCondition.BooleanFilter -> fields.find { it.id == initial.fieldId }
                is FilterCondition.ChoiceFilter -> fields.find { it.id == initial.fieldId }
                is FilterCondition.MultiSelectFilter -> fields.find { it.id == initial.fieldId }
                null -> null
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Filter" else "Edit Filter") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Field picker — horizontal scrollable row
                SectionHeader("Field")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fields.forEach { field ->
                        FilterChip(
                            selected = selectedField?.id == field.id,
                            onClick = { selectedField = field },
                            label = { Text(field.name) }
                        )
                    }
                }

                // Operator + value editor
                selectedField?.let { field ->
                    Spacer(Modifier.height(Spacing.xs))
                    FilterValueEditor(
                        field = field,
                        initial = initial,
                        onConditionReady = { condition ->
                            onConfirm(condition)
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── FilterValueEditor — per field-type editor embedded in dialog ──────────────

@Composable
private fun FilterValueEditor(
    field: EventField,
    initial: FilterCondition?,
    onConditionReady: (FilterCondition) -> Unit
) {
    when (field.type) {
        FieldType.NUMERIC -> NumericFilterEditor(field, initial as? FilterCondition.NumericFilter, onConditionReady)
        FieldType.BOOLEAN -> BooleanFilterEditor(field, initial as? FilterCondition.BooleanFilter, onConditionReady)
        FieldType.CHOICE -> ChoiceFilterEditor(field, initial as? FilterCondition.ChoiceFilter, onConditionReady)
        FieldType.MULTI_SELECT -> MultiSelectFilterEditor(field, initial as? FilterCondition.MultiSelectFilter, onConditionReady)
        FieldType.TEXT -> {} // Text filtering not supported
    }
}

@Composable
private fun NumericFilterEditor(
    field: EventField,
    initial: FilterCondition.NumericFilter?,
    onConfirm: (FilterCondition) -> Unit
) {
    var operator by remember { mutableStateOf(initial?.operator ?: NumericOperator.GREATER_THAN) }
    var valueText by remember { mutableStateOf(initial?.value?.let { it.toDisplayValue() } ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Operator")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NumericOperator.entries.forEach { op ->
                FilterChip(
                    selected = operator == op,
                    onClick = { operator = op },
                    label = { Text("${op.symbol} ${op.label}") }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = valueText,
            onValueChange = { valueText = it },
            label = { Text("Value${if (field.unit.isNotBlank()) " (${field.unit})" else ""}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // toDoubleOrNull accepts "NaN" and "Infinity", which a Decimal keyboard cannot
        // produce but a paste or a hardware keyboard can. The engine refuses to judge a
        // non-finite value, so such a filter would apply cleanly and then match nothing;
        // leaving Apply disabled says so up front.
        val parsed = valueText.toDoubleOrNull()?.takeIf { it.isFinite() }
        Button(
            onClick = {
                if (parsed != null) {
                    onConfirm(FilterCondition.NumericFilter(field.id, field.name, operator, parsed))
                }
            },
            enabled = parsed != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Apply") }
    }
}

@Composable
private fun BooleanFilterEditor(
    field: EventField,
    initial: FilterCondition.BooleanFilter?,
    onConfirm: (FilterCondition) -> Unit
) {
    var operator by remember { mutableStateOf(initial?.operator ?: BooleanOperator.EQUALS) }
    var value by remember { mutableStateOf(initial?.value ?: true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Operator")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BooleanOperator.entries.forEach { op ->
                FilterChip(selected = operator == op, onClick = { operator = op }, label = { Text(op.label) })
            }
        }
        SectionHeader("Value")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = value, onClick = { value = true }, label = { Text("Yes") })
            FilterChip(selected = !value, onClick = { value = false }, label = { Text("No") })
        }
        Button(
            onClick = { onConfirm(FilterCondition.BooleanFilter(field.id, field.name, operator, value)) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Apply") }
    }
}

@Composable
private fun ChoiceFilterEditor(
    field: EventField,
    initial: FilterCondition.ChoiceFilter?,
    onConfirm: (FilterCondition) -> Unit
) {
    var operator by remember { mutableStateOf(initial?.operator ?: ChoiceOperator.EQUALS) }
    var selectedValue by remember { mutableStateOf(initial?.value ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Operator")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ChoiceOperator.entries.forEach { op ->
                FilterChip(selected = operator == op, onClick = { operator = op }, label = { Text(op.label) })
            }
        }
        SectionHeader("Value")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            field.options.forEach { option ->
                FilterChip(
                    selected = selectedValue == option,
                    onClick = { selectedValue = option },
                    label = { Text(option) }
                )
            }
        }
        Button(
            onClick = {
                if (selectedValue.isNotBlank()) {
                    onConfirm(FilterCondition.ChoiceFilter(field.id, field.name, operator, selectedValue))
                }
            },
            enabled = selectedValue.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Apply") }
    }
}

@Composable
private fun MultiSelectFilterEditor(
    field: EventField,
    initial: FilterCondition.MultiSelectFilter?,
    onConfirm: (FilterCondition) -> Unit
) {
    var operator by remember { mutableStateOf(initial?.operator ?: MultiSelectOperator.CONTAINS) }
    var selectedValue by remember { mutableStateOf(initial?.value ?: "") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("Operator")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MultiSelectOperator.entries.forEach { op ->
                FilterChip(selected = operator == op, onClick = { operator = op }, label = { Text(op.label) })
            }
        }
        SectionHeader("Tag")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            field.options.forEach { option ->
                FilterChip(
                    selected = selectedValue == option,
                    onClick = { selectedValue = option },
                    label = { Text(option) }
                )
            }
        }
        Button(
            onClick = {
                if (selectedValue.isNotBlank()) {
                    onConfirm(FilterCondition.MultiSelectFilter(field.id, field.name, operator, selectedValue))
                }
            },
            enabled = selectedValue.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Apply") }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun FilterSortSheetPreview() {
    LifeLogTheme {
        FilterSortSheet(
            fields = listOf(
                EventField(1, 1, "Systolic", FieldType.NUMERIC, unit = "mmHg"),
                EventField(2, 1, "Diastolic", FieldType.NUMERIC, unit = "mmHg"),
                EventField(3, 1, "Smoked", FieldType.BOOLEAN),
                EventField(4, 1, "Category", FieldType.CHOICE, options = listOf("Morning", "Evening")),
                EventField(5, 1, "Tags", FieldType.MULTI_SELECT, options = listOf("High Protein", "Vegetarian"))
            ),
            currentQuery = EntryQuery(
                filters = listOf(
                    FilterCondition.NumericFilter(1, "Systolic", NumericOperator.GREATER_THAN, 130.0)
                ),
                sort = SortSpecification(SortField.NumericField(1, "Systolic"), SortDirection.DESCENDING)
            ),
            onApply = {},
            onDismiss = {}
        )
    }
}
