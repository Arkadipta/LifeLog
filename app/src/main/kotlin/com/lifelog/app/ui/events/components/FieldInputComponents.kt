package com.lifelog.app.ui.events.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue

@Composable
fun FieldInput(
    field: EventField,
    value: FieldValue?,
    onValueChange: (FieldValue?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                field.name,
                style = MaterialTheme.typography.labelLarge
            )
            if (field.isRequired) {
                Text("*", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
        }

        when (field.type) {
            FieldType.NUMERIC -> NumericInput(field, value as? FieldValue.Numeric, onValueChange)
            FieldType.TEXT -> TextInput(field, value as? FieldValue.Text, onValueChange)
            FieldType.BOOLEAN -> BooleanInput(value as? FieldValue.Bool, onValueChange)
            FieldType.CHOICE -> ChoiceInput(field, value as? FieldValue.Choice, onValueChange)
            FieldType.MULTI_SELECT -> MultiSelectInput(field, value as? FieldValue.MultiSelect, onValueChange)
        }
    }
}

@Composable
private fun NumericInput(
    field: EventField,
    value: FieldValue.Numeric?,
    onValueChange: (FieldValue?) -> Unit
) {
    var text by remember(value) {
        mutableStateOf(value?.let {
            if (it.value == it.value.toLong().toDouble()) it.value.toLong().toString()
            else it.value.toString()
        } ?: "")
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            val d = raw.toDoubleOrNull()
            onValueChange(if (d != null) FieldValue.Numeric(d) else null)
        },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = if (field.unit.isNotBlank()) ({ Text(field.unit) }) else null,
        singleLine = true
    )
}

@Composable
private fun TextInput(
    field: EventField,
    value: FieldValue.Text?,
    onValueChange: (FieldValue?) -> Unit
) {
    OutlinedTextField(
        value = value?.value ?: "",
        onValueChange = { onValueChange(if (it.isEmpty()) null else FieldValue.Text(it)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 5
    )
}

@Composable
private fun BooleanInput(
    value: FieldValue.Bool?,
    onValueChange: (FieldValue?) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = value?.value == true,
            onClick = { onValueChange(FieldValue.Bool(true)) },
            label = { Text("Yes") }
        )
        FilterChip(
            selected = value?.value == false,
            onClick = { onValueChange(FieldValue.Bool(false)) },
            label = { Text("No") }
        )
        if (value != null) {
            TextButton(onClick = { onValueChange(null) }) { Text("Clear") }
        }
    }
}

@Composable
private fun ChoiceInput(
    field: EventField,
    value: FieldValue.Choice?,
    onValueChange: (FieldValue?) -> Unit
) {
    var allOptions by remember { mutableStateOf(field.options) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(allOptions) { option ->
                FilterChip(
                    selected = value?.value == option,
                    onClick = {
                        onValueChange(
                            if (value?.value == option) null else FieldValue.Choice(option)
                        )
                    },
                    label = { Text(option) }
                )
            }
            item {
                AssistChip(
                    onClick = { showAddDialog = true },
                    label = { Text("+ Add") },
                    leadingIcon = { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddOptionDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { newOption ->
                if (newOption.isNotBlank() && !allOptions.contains(newOption)) {
                    allOptions = allOptions + newOption
                }
                onValueChange(FieldValue.Choice(newOption))
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun MultiSelectInput(
    field: EventField,
    value: FieldValue.MultiSelect?,
    onValueChange: (FieldValue?) -> Unit
) {
    var allOptions by remember { mutableStateOf(field.options) }
    val selected = value?.values ?: emptyList()
    var showAddDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(allOptions) { option ->
                FilterChip(
                    selected = selected.contains(option),
                    onClick = {
                        val updated = if (selected.contains(option)) {
                            selected - option
                        } else {
                            selected + option
                        }
                        onValueChange(if (updated.isEmpty()) null else FieldValue.MultiSelect(updated))
                    },
                    label = { Text(option) }
                )
            }
            item {
                AssistChip(
                    onClick = { showAddDialog = true },
                    label = { Text("+ Add") },
                    leadingIcon = { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
    }

    if (showAddDialog) {
        AddOptionDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { newOption ->
                if (newOption.isNotBlank() && !allOptions.contains(newOption)) {
                    allOptions = allOptions + newOption
                }
                val updated = if (selected.contains(newOption)) selected else selected + newOption
                onValueChange(FieldValue.MultiSelect(updated))
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun AddOptionDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Option") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Option") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(text.trim()) }, enabled = text.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
