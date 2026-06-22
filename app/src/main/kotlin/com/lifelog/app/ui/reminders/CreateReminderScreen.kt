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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.lifelog.app.ui.components.IconTile
import com.lifelog.app.ui.components.LifeLogCard
import com.lifelog.app.ui.components.LifeLogTimePickerDialog
import com.lifelog.app.ui.components.SectionHeader
import com.lifelog.app.ui.theme.Sizing
import com.lifelog.app.ui.theme.Spacing
import com.lifelog.app.util.SNOOZE_PRESETS_MINUTES
import com.lifelog.app.util.SnoozeUnit
import com.lifelog.app.util.decomposeSnooze
import com.lifelog.app.util.minutesFromMidnightToLabel
import com.lifelog.app.util.snoozeLongLabel
import com.lifelog.app.util.snoozeShortLabel
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
    var showSnoozeSheet by remember { mutableStateOf(false) }

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

            // ── Snooze duration ───────────────────────────────────────────────
            item {
                PickerCard(
                    icon = Icons.Rounded.Snooze,
                    title = "Snooze duration",
                    value = snoozeLongLabel(state.snoozeMinutes),
                    onClick = { showSnoozeSheet = true }
                )
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

    // ── Event picker sheet ─────────────────────────────────────────────────────
    if (showEventPicker) {
        EventPickerSheet(
            eventTypes = state.eventTypes,
            selectedEventTypeId = state.eventTypeId,
            onSelect = { eventType -> viewModel.setEventType(eventType?.id, eventType?.name) },
            onDismiss = { showEventPicker = false }
        )
    }

    // ── Snooze duration sheet ──────────────────────────────────────────────────
    if (showSnoozeSheet) {
        SnoozeDurationSheet(
            currentMinutes = state.snoozeMinutes,
            onSelect = viewModel::setSnoozeMinutes,
            onDismiss = { showSnoozeSheet = false }
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

/**
 * Bottom sheet for choosing a reminder's snooze duration: quick-pick preset chips plus a custom
 * value/unit input spanning 1 minute … 1 week. Chips and the custom controls share one source of
 * truth so they always agree; the ViewModel clamps the final value to the allowed range.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnoozeDurationSheet(
    currentMinutes: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val seed = remember { decomposeSnooze(currentMinutes) }
    var valueText by remember { mutableStateOf(seed.first.toString()) }
    var unit by remember { mutableStateOf(seed.second) }

    // Minutes currently described by the custom controls (0 when the field is blank/invalid).
    val customMinutes = (valueText.toIntOrNull() ?: 0) * unit.minutes

    fun commit(text: String, newUnit: SnoozeUnit) {
        valueText = text
        unit = newUnit
        val v = text.toIntOrNull()
        if (v != null && v >= 1) onSelect(v * newUnit.minutes)   // ViewModel clamps to 1 min … 1 week
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sheetEdge)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("Snooze duration", style = MaterialTheme.typography.titleLarge)
                Text(
                    "How long the Snooze action defers this reminder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Horizontally scrollable — same pattern as the "Repeat" chips above, so the row never
            // wraps regardless of screen width. The custom control below covers any other value.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(SNOOZE_PRESETS_MINUTES) { preset ->
                    FilterChip(
                        selected = customMinutes == preset,
                        onClick = {
                            val (presetValue, presetUnit) = decomposeSnooze(preset)
                            commit(presetValue.toString(), presetUnit)
                        },
                        label = { Text(snoozeShortLabel(preset)) }
                    )
                }
            }

            HorizontalDivider()

            SectionHeader("Custom")
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { commit(it.filter(Char::isDigit).take(5), unit) },
                    label = { Text("Every") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(110.dp)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                    SnoozeUnit.entries.forEachIndexed { idx, u ->
                        SegmentedButton(
                            selected = unit == u,
                            onClick = { commit(valueText, u) },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = SnoozeUnit.entries.size)
                        ) { Text(u.displayName, maxLines = 1, softWrap = false) }
                    }
                }
            }

            Text(
                "Ranges from 1 minute to 1 week.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
