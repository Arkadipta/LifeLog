package com.lifelog.app.ui.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.ui.events.components.FieldInput
import com.lifelog.app.util.toDisplayDateTime
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryFormSheet(
    eventTypeId: Long,
    editingEntryId: Long?,
    onDismiss: () -> Unit,
    viewModel: EntryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(eventTypeId, editingEntryId) {
        if (editingEntryId != null && editingEntryId != 0L) {
            viewModel.loadEntry(editingEntryId)
        } else {
            viewModel.loadEventType(eventTypeId)
        }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDismiss()
    }

    var showDateTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Header — no divider; ModalBottomSheet drag handle provides visual separation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (editingEntryId != null) "Edit Entry" else "New Entry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Close")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Timestamp row
                OutlinedCard(
                    onClick = { showDateTimePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Rounded.AccessTime,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text("Timestamp", style = MaterialTheme.typography.labelMedium)
                            Text(
                                state.createdAt.toDisplayDateTime(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Dynamic fields
                state.eventType?.fields?.forEach { field ->
                    FieldInput(
                        field = field,
                        value = state.fieldValues[field.id],
                        onValueChange = { value -> viewModel.setFieldValue(field.id, value) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Optional note
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )

                // Save button
                Button(
                    onClick = { viewModel.save(eventTypeId) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isLoading
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save Entry", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showDateTimePicker) {
        DateTimePickerDialog(
            initialTime = state.createdAt,
            onDismiss = { showDateTimePicker = false },
            onConfirm = { time ->
                viewModel.setCreatedAt(time)
                showDateTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerDialog(
    initialTime: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialTime }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialTime)
    val timePickerState = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE)
    )
    var showTime by remember { mutableStateOf(false) }

    if (!showTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { showTime = true }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        ) {
            DatePicker(state = datePickerState)
        }
    } else {
        // Use a properly-sized Dialog so TimePicker isn't clipped by AlertDialog constraints
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Select Time",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .align(Alignment.Start)
                            .padding(bottom = 20.dp)
                    )
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Back") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val selectedDate = datePickerState.selectedDateMillis ?: initialTime
                            val resultCal = Calendar.getInstance().apply {
                                timeInMillis = selectedDate
                                set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                set(Calendar.MINUTE, timePickerState.minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            onConfirm(resultCal.timeInMillis)
                        }) { Text("Set") }
                    }
                }
            }
        }
    }
}
