package com.lifelog.app.ui.events.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.domain.model.LegacyValueMismatch
import com.lifelog.app.domain.model.legacyMismatchOf

@Composable
fun FieldInput(
    field: EventField,
    value: FieldValue?,
    onValueChange: (FieldValue?) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
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

        // A value stored under a different type (the field's type was changed
        // after this entry was saved) is shown read-only instead of as an empty
        // input, so it can't be silently overwritten. The user must explicitly
        // clear it to enter a new value of the current type.
        val mismatch = legacyMismatchOf(field.type, value)
        if (mismatch != null) {
            LegacyValueCard(mismatch = mismatch, onClear = { onValueChange(null) })
        } else {
            when (field.type) {
                FieldType.NUMERIC -> NumericInput(field, value as? FieldValue.Numeric, onValueChange, isError)
                FieldType.TEXT -> TextInput(field, value as? FieldValue.Text, onValueChange, isError)
                FieldType.BOOLEAN -> BooleanInput(value as? FieldValue.Bool, onValueChange)
                FieldType.CHOICE -> ChoiceInput(field, value as? FieldValue.Choice, onValueChange)
                FieldType.MULTI_SELECT -> MultiSelectInput(field, value as? FieldValue.MultiSelect, onValueChange)
            }
        }

        if (isError) {
            Text(
                "Required",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * Read-only presentation of a legacy value whose stored type no longer matches
 * the field's current type. Shows the human-readable value plus both the type it
 * was stored as and the field's current type, and offers an explicit "Clear
 * value" action — the only way to discard it and unlock the normal editor. The
 * original value is never converted or overwritten implicitly.
 */
@Composable
private fun LegacyValueCard(
    mismatch: LegacyValueMismatch,
    onClear: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    "Legacy value detected",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            LegacyDetailRow("Value", mismatch.displayValue)
            LegacyDetailRow("Stored as", mismatch.storedType.displayName)
            LegacyDetailRow("Current field type", mismatch.declaredType.displayName)
            Text(
                "This value was saved before the field type changed, so it can't be " +
                    "edited as ${mismatch.declaredType.displayName}. It is kept as-is. " +
                    "Clear it to enter a new ${mismatch.declaredType.displayName} value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onClear,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Clear value")
            }
        }
    }
}

@Composable
private fun LegacyDetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "$label:",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NumericInput(
    field: EventField,
    value: FieldValue.Numeric?,
    onValueChange: (FieldValue?) -> Unit,
    isError: Boolean = false
) {
    // The editable text is the source of truth while the user types. It is kept
    // decoupled from the parsed model value so that in-progress, not-yet-parseable
    // states ("-", ".", "-.", "12.") survive recomposition instead of being
    // snapped back to the canonical formatting of the parsed Double — which is what
    // broke negative input (typing "-0" became "0", dropping the sign).
    var textState by remember { mutableStateOf(TextFieldValue(formatNumericForEditing(value))) }

    // Pull external value changes (form reset, value populated from elsewhere) into
    // the field, but only when the incoming value is a genuinely different number
    // than what is already typed. Guarding on the parsed value — rather than the raw
    // text — is what preserves in-progress edits: every keystroke that parses round-
    // trips through the model, and without this guard the canonical re-formatting
    // would overwrite the user's text ("-0" -> "0", "1." -> "1") on the next frame.
    LaunchedEffect(value) {
        if (value?.value != textState.text.toDoubleOrNull()) {
            val formatted = formatNumericForEditing(value)
            textState = TextFieldValue(formatted, TextRange(formatted.length))
        }
    }

    OutlinedTextField(
        value = textState,
        onValueChange = { raw ->
            val sanitized = sanitizeNumericInput(raw)
            textState = sanitized
            onValueChange(sanitized.text.toDoubleOrNull()?.let { FieldValue.Numeric(it) })
        },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = if (field.unit.isNotBlank()) ({ Text(field.unit) }) else null,
        singleLine = true,
        isError = isError
    )
}

/** Formats a stored numeric value for display in the editor, dropping a trailing
 *  ".0" for whole numbers (so 5.0 shows as "5", not "5.0"). Null becomes empty. */
private fun formatNumericForEditing(value: FieldValue.Numeric?): String {
    val d = value?.value ?: return ""
    return if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
}

/**
 * Filters [raw] down to characters that can form a (possibly in-progress) decimal
 * number — an optional leading minus sign, the digits, and a single decimal point —
 * while keeping the caret anchored to the same logical position. A comma is accepted
 * as a decimal separator and normalized to a point. Returning a [TextFieldValue]
 * (rather than a plain String) lets the field drive the caret explicitly, so it
 * doesn't jump to the end whenever a keystroke is sanitized.
 */
private fun sanitizeNumericInput(raw: TextFieldValue): TextFieldValue {
    val builder = StringBuilder()
    val caret = raw.selection.start
    var newCaret = 0
    raw.text.forEachIndexed { index, c ->
        val normalized = if (c == ',') '.' else c
        val keep = when {
            normalized.isDigit() -> true
            normalized == '-' -> builder.isEmpty()       // only as a leading sign
            normalized == '.' -> !builder.contains('.')  // at most one point
            else -> false
        }
        if (keep) {
            builder.append(normalized)
            if (index < caret) newCaret++
        }
    }
    val text = builder.toString()
    return TextFieldValue(text, TextRange(newCaret.coerceIn(0, text.length)))
}

@Composable
private fun TextInput(
    field: EventField,
    value: FieldValue.Text?,
    onValueChange: (FieldValue?) -> Unit,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value?.value ?: "",
        onValueChange = { onValueChange(if (it.isEmpty()) null else FieldValue.Text(it)) },
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
        maxLines = 5,
        isError = isError
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
                    label = { Text("Add") },
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
                    label = { Text("Add") },
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
