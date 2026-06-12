package com.lifelog.app.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.DeliveryType
import com.lifelog.app.domain.model.RecurrenceType
import com.lifelog.app.ui.components.DialogOption
import com.lifelog.app.ui.components.IconTile
import com.lifelog.app.ui.components.LifeLogCard
import com.lifelog.app.ui.components.LifeLogTimePickerDialog
import com.lifelog.app.ui.components.SectionHeader
import com.lifelog.app.ui.components.SingleChoiceDialog
import com.lifelog.app.ui.theme.Sizing
import com.lifelog.app.ui.theme.Spacing
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
                    Text(if (reminderId == 0L) "New Reminder" else "Edit Reminder")
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
            contentPadding = PaddingValues(Spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
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
                PickerCard(
                    icon = Icons.Rounded.Link,
                    title = "Linked Event",
                    value = state.eventTypeName ?: "All Events (Global)",
                    onClick = { showEventPicker = true }
                )
            }

            // ── Delivery type ─────────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader("Delivery")
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
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader("Repeat")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
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
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        val hours = state.recurrenceRule.timeSinceLastMinutes / 60
                        val mins  = state.recurrenceRule.timeSinceLastMinutes % 60
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
            val needsTimeOfDay = state.recurrenceRule.type != RecurrenceType.INTERVAL &&
                state.recurrenceRule.type != RecurrenceType.TIME_SINCE_LAST
            if (needsTimeOfDay) {
                item {
                    PickerCard(
                        icon = Icons.Rounded.Schedule,
                        title = "Time",
                        value = minutesFromMidnightToLabel(state.recurrenceRule.timeOfDayMinutes),
                        onClick = { showTimePicker = true }
                    )
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

            item { Spacer(Modifier.height(Spacing.fabClearance - Spacing.screenEdge)) }
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
        val options = listOf(DialogOption("All Events (Global)")) +
            state.eventTypes.map { DialogOption(it.name) }
        val selectedIndex = if (state.eventTypeName == null) 0
            else state.eventTypes.indexOfFirst { it.name == state.eventTypeName } + 1
        SingleChoiceDialog(
            title = "Link to Event",
            options = options,
            selectedIndex = selectedIndex,
            onDismiss = { showEventPicker = false },
            onSelect = { idx ->
                if (idx == 0) viewModel.setEventType(null, null)
                else state.eventTypes.getOrNull(idx - 1)?.let { viewModel.setEventType(it.id, it.name) }
                showEventPicker = false
            }
        )
    }
}

/** Tappable setting card with a leading icon tile, label, and current value. */
@Composable
private fun PickerCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LifeLogCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            IconTile(
                icon = icon,
                tint = MaterialTheme.colorScheme.primary,
                size = Sizing.iconTileSmall
            )
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
