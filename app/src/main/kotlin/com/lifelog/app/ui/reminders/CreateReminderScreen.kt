package com.lifelog.app.ui.reminders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.RepeatType
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
                    TextButton(
                        onClick = { viewModel.save(reminderId) },
                        enabled = !state.isLoading
                    ) { Text("Save", fontWeight = FontWeight.SemiBold) }
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

            item {
                OutlinedTextField(
                    value = state.message,
                    onValueChange = viewModel::setMessage,
                    label = { Text("Message (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }

            item {
                OutlinedCard(
                    onClick = { showEventPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text("Linked Event") },
                        supportingContent = {
                            Text(state.eventTypeName ?: "All Events (Global)")
                        }
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Repeat", style = MaterialTheme.typography.labelLarge)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(RepeatType.entries) { type ->
                            FilterChip(
                                selected = state.repeatType == type,
                                onClick = { viewModel.setRepeatType(type) },
                                label = { Text(type.displayName) }
                            )
                        }
                    }
                }
            }

            if (state.repeatType == RepeatType.INTERVAL) {
                item {
                    OutlinedTextField(
                        value = state.repeatIntervalHours.toString(),
                        onValueChange = { v ->
                            v.toIntOrNull()?.let { viewModel.setIntervalHours(it) }
                        },
                        label = { Text("Interval (hours)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            if (state.repeatType != RepeatType.INTERVAL) {
                item {
                    OutlinedCard(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text("Time") },
                            supportingContent = {
                                Text(minutesFromMidnightToLabel(state.timeOfDayMinutes))
                            }
                        )
                    }
                }
            }

            if (state.repeatType == RepeatType.WEEKLY) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Days of Week", style = MaterialTheme.typography.labelLarge)
                        val days = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(days.indices.toList()) { idx ->
                                FilterChip(
                                    selected = state.daysOfWeek.contains(idx),
                                    onClick = { viewModel.toggleDayOfWeek(idx) },
                                    label = { Text(days[idx]) }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, state.timeOfDayMinutes / 60)
            set(Calendar.MINUTE, state.timeOfDayMinutes % 60)
        }
        val timeState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Select Time") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setTimeOfDay(timeState.hour * 60 + timeState.minute)
                    showTimePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            }
        )
    }

    if (showEventPicker) {
        AlertDialog(
            onDismissRequest = { showEventPicker = false },
            title = { Text("Link to Event") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.setEventType(null, null)
                            showEventPicker = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("All Events (Global)") }
                    state.eventTypes.forEach { et ->
                        TextButton(
                            onClick = {
                                viewModel.setEventType(et.id, et.name)
                                showEventPicker = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(et.name) }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
