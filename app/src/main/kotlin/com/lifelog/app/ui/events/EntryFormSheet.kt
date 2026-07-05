package com.lifelog.app.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.ui.components.IconTile
import com.lifelog.app.ui.components.LifeLogTimePickerDialog
import com.lifelog.app.ui.components.SectionHeader
import com.lifelog.app.ui.events.components.FieldInput
import com.lifelog.app.ui.theme.Sizing
import com.lifelog.app.ui.theme.Spacing
import com.lifelog.app.ui.theme.rememberAccentOnSurface
import com.lifelog.app.util.iconForName
import com.lifelog.app.util.toDisplayDate
import com.lifelog.app.util.toDisplayTime
import com.lifelog.app.util.toUtcDateMillis
import com.lifelog.app.util.withUtcDate
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryFormSheet(
    mode: EntryFormMode,
    onDismiss: () -> Unit,
    onViewHistory: ((eventTypeId: Long) -> Unit)? = null,
    onEventMissing: () -> Unit = onDismiss,
    viewModel: EntryViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(mode) {
        when (mode) {
            is EntryFormMode.Edit -> viewModel.loadEntry(mode.entryId)
            is EntryFormMode.New -> viewModel.loadEventType(mode.eventTypeId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.dismiss.collect { onDismiss() }
    }

    // The event type (or the entry being edited) is gone — e.g. a stale widget or
    // notification pointing at a deleted event. Close instead of offering a form
    // whose save could never succeed.
    LaunchedEffect(state.eventTypeMissing) {
        if (state.eventTypeMissing) onEventMissing()
    }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // The event itself is the header — its icon and name identify what
            // is being logged, so no redundant "New Entry" label is needed. The
            // history shortcut and close action trail it on the same row,
            // reclaiming the vertical space a separate title would take.
            EntrySheetHeader(
                eventType = state.eventType,
                onViewHistory = onViewHistory,
                onClose = onDismiss
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.sheetEdge)
                    .padding(top = Spacing.sm, bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                // Timestamp row — date and time are independently tappable
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionHeader("Timestamp")
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        TimestampChip(
                            icon = Icons.Rounded.CalendarMonth,
                            label = state.createdAt.toDisplayDate(),
                            onClick = { showDatePicker = true },
                            modifier = Modifier.weight(1f)
                        )
                        TimestampChip(
                            icon = Icons.Rounded.AccessTime,
                            label = state.createdAt.toDisplayTime(),
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Dynamic fields
                state.eventType?.fields?.forEach { field ->
                    FieldInput(
                        field = field,
                        value = state.fieldValues[field.id],
                        onValueChange = { value -> viewModel.setFieldValue(field.id, value) },
                        onAddOption = { option -> viewModel.addFieldOption(field.id, option) },
                        modifier = Modifier.fillMaxWidth(),
                        isError = state.fieldErrors.contains(field.id)
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

                state.errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Save button — disabled until the event type resolves so an entry
                // can never be saved without a valid type to attach it to.
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Sizing.cta),
                    enabled = !state.isLoading && state.eventType != null
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save Entry")
                    }
                }

                Spacer(Modifier.height(Spacing.sm))
            }
        }
    }

    if (showDatePicker) {
        // The M3 date picker speaks UTC start-of-day in both directions, so the
        // entry's local timestamp is bridged through toUtcDateMillis/withUtcDate;
        // merging the selection with a local Calendar would land the entry on
        // the previous day in zones west of UTC.
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.createdAt.toUtcDateMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { day ->
                        viewModel.setCreatedAt(state.createdAt.withUtcDate(day))
                    }
                    showDatePicker = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = state.createdAt }
        val timePickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE)
        )
        LifeLogTimePickerDialog(
            state = timePickerState,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                val resultCal = Calendar.getInstance().apply {
                    timeInMillis = state.createdAt
                    set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                    set(Calendar.MINUTE, timePickerState.minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.setCreatedAt(resultCal.timeInMillis)
                showTimePicker = false
            }
        )
    }
}

/**
 * The entry sheet's header. The event's icon tile and name (in its accent color)
 * stand in for a generic title, so the user sees what they are logging without a
 * redundant label; the optional history shortcut and the close action trail on
 * the same row. Before the event resolves, only the close affordance shows.
 */
@Composable
private fun EntrySheetHeader(
    eventType: EventType?,
    onViewHistory: ((eventTypeId: Long) -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Spacing.sheetEdge, end = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (eventType != null) {
            val accent = Color(eventType.colorArgb)
            IconTile(
                icon = iconForName(eventType.iconName),
                tint = accent,
                size = Sizing.iconTileSmall
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = eventType.name,
                style = MaterialTheme.typography.titleLarge,
                color = rememberAccentOnSurface(accent),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (onViewHistory != null) {
                IconButton(onClick = { onViewHistory(eventType.id) }) {
                    Icon(Icons.Rounded.History, contentDescription = "View entry history")
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Close")
        }
    }
}

/** Tonal tappable date/time selector used in the entry form. */
@Composable
private fun TimestampChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally)
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
        }
    }
}
