package com.lifelog.app.ui.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.DeliveryType
import com.lifelog.app.domain.model.RecurrenceType
import com.lifelog.app.ui.components.LifeLogTimePickerDialog
import com.lifelog.app.util.minutesFromMidnightToLabel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateReminderScreen(
    reminderId: Long = 0L,
    onNavigateBack: () -> Unit,
    viewModel: CreateReminderViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(reminderId) {
        if (reminderId != 0L) viewModel.loadReminder(reminderId)
    }
    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }

    var showTimePicker by remember { mutableStateOf(false) }
    var showEventPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (reminderId == 0L) "New Reminder" else "Edit Reminder",
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
                        onClick = { viewModel.save(reminderId) },
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

            // ── Title ───────────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("Title *") },
                    isError = state.titleError != null,
                    supportingText = state.titleError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            // ── Message ──────────────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = state.message,
                    onValueChange = viewModel::setMessage,
                    label = { Text("Message (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }

            // ── Linked event ─────────────────────────────────────────────────
            item {
                OutlinedCard(
                    onClick = { showEventPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text("Linked Event") },
                        supportingContent = { Text(state.eventTypeName ?: "All Events (Global)") }
                    )
                }
            }

            // ── Delivery type ─────────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delivery", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        DeliveryType.entries.forEachIndexed { idx, dt ->
                            SegmentedButton(
                                selected = state.deliveryType == dt,
                                onClick = { viewModel.setDeliveryType(dt) },
                                shape = SegmentedButtonDefaults.itemShape(index = idx, count = DeliveryType.entries.size),
                                icon = {
                                    Icon(
                                        if (dt == DeliveryType.ALARM) Icons.Rounded.Alarm
                                        else Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            ) { Text(dt.displayName) }
                        }
                    }
                    if (state.deliveryType == DeliveryType.ALARM) {
                        Text(
                            "Alarm shows a full-screen alert and rings even in silent mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Recurrence type ───────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Repeat", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(
                            RecurrenceType.NONE,
                            RecurrenceType.WEEKLY,
                            RecurrenceType.MONTHLY,
                            RecurrenceType.INTERVAL,
                            RecurrenceType.TIME_SINCE_LAST
                        )) { type ->
                            FilterChip(
                                selected = state.recurrenceRule.type == type,
                                onClick = { viewModel.setRecurrenceType(type) },
                                label = { Text(type.displayName) }
                            )
                        }
                    }
                }
            }

            // ── Interval input (INTERVAL type) ────────────────────────────────
            if (state.recurrenceRule.type == RecurrenceType.INTERVAL) {
                item {
                    val hours = state.recurrenceRule.intervalMinutes / 60
                    val mins  = state.recurrenceRule.intervalMinutes % 60
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = hours.toString(),
                            onValueChange = { v ->
                                val h = v.toIntOrNull() ?: 0
                                viewModel.setIntervalMinutes(h * 60 + mins)
                            },
                            label = { Text("Hours") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = mins.toString(),
                            onValueChange = { v ->
                                val m = (v.toIntOrNull() ?: 0).coerceIn(0, 59)
                                viewModel.setIntervalMinutes(hours * 60 + m)
                            },
                            label = { Text("Minutes") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }
            }

            // ── TIME_SINCE_LAST duration input ───────────────────────────────
            if (state.recurrenceRule.type == RecurrenceType.TIME_SINCE_LAST) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val hours = state.recurrenceRule.timeSinceLastMinutes / 60
                        val mins  = state.recurrenceRule.timeSinceLastMinutes % 60
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = hours.toString(),
                                onValueChange = { v ->
                                    val h = v.toIntOrNull() ?: 0
                                    viewModel.setTimeSinceLastTotalMinutes(h * 60 + mins)
                                },
                                label = { Text("Hours after last entry") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                            OutlinedTextField(
                                value = mins.toString(),
                                onValueChange = { v ->
                                    val m = (v.toIntOrNull() ?: 0).coerceIn(0, 59)
                                    viewModel.setTimeSinceLastTotalMinutes(hours * 60 + m)
                                },
                                label = { Text("Min") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                        Text(
                            "Fires automatically N hours after the last linked entry. Resets on each new entry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Time picker (non-INTERVAL, non-TIME_SINCE_LAST) ───────────────
            val showTimePicker2 = state.recurrenceRule.type != RecurrenceType.INTERVAL &&
                state.recurrenceRule.type != RecurrenceType.TIME_SINCE_LAST
            if (showTimePicker2) {
                item {
                    OutlinedCard(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text("Time") },
                            supportingContent = {
                                Text(minutesFromMidnightToLabel(state.recurrenceRule.timeOfDayMinutes))
                            }
                        )
                    }
                }
            }

            // ── RecurrenceRuleEditor (WEEKLY / MONTHLY / YEARLY) ─────────────
            val needsEditor = state.recurrenceRule.type in listOf(
                RecurrenceType.WEEKLY, RecurrenceType.MONTHLY, RecurrenceType.YEARLY
            )
            if (needsEditor) {
                item {
                    RecurrenceRuleEditor(
                        rule = state.recurrenceRule,
                        onRuleChange = viewModel::setRecurrenceRule,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    // ── Time picker dialog ────────────────────────────────────────────────────
    if (showTimePicker) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, state.recurrenceRule.timeOfDayMinutes / 60)
            set(Calendar.MINUTE, state.recurrenceRule.timeOfDayMinutes % 60)
        }
        val timeState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        LifeLogTimePickerDialog(
            state = timeState,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                viewModel.setTimeOfDay(timeState.hour * 60 + timeState.minute)
                showTimePicker = false
            }
        )
    }

    // ── Event picker dialog ────────────────────────────────────────────────────
    if (showEventPicker) {
        AlertDialog(
            onDismissRequest = { showEventPicker = false },
            title = { Text("Link to Event") },
            text = {
                Column {
                    TextButton(
                        onClick = { viewModel.setEventType(null, null); showEventPicker = false },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("All Events (Global)") }
                    state.eventTypes.forEach { et ->
                        TextButton(
                            onClick = { viewModel.setEventType(et.id, et.name); showEventPicker = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(et.name) }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
