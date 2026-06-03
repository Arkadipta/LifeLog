package com.lifelog.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.data.repository.ReminderRepository
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.notifications.ReminderScheduler
import com.lifelog.app.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EntryFormState(
    val eventType: EventType? = null,
    val fieldValues: Map<Long, FieldValue> = emptyMap(),
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isLoading: Boolean = false,
    val existingEntryId: Long = 0L
)

@HiltViewModel
class EntryViewModel @Inject constructor(
    private val repository: EventRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    private val _state = MutableStateFlow(EntryFormState())
    val state: StateFlow<EntryFormState> = _state.asStateFlow()

    private val _dismiss = Channel<Unit>(Channel.BUFFERED)
    val dismiss: Flow<Unit> = _dismiss.receiveAsFlow()

    fun loadEventType(eventTypeId: Long) {
        _state.value = EntryFormState()
        viewModelScope.launch {
            val eventType = repository.getEventType(eventTypeId)
            _state.update { it.copy(eventType = eventType) }
        }
    }

    fun loadEntry(entryId: Long) {
        _state.value = EntryFormState(isLoading = true)
        viewModelScope.launch {
            val entry = repository.getEntry(entryId)
            if (entry != null) {
                val eventType = repository.getEventType(entry.eventTypeId)
                _state.update {
                    it.copy(
                        eventType = eventType,
                        fieldValues = entry.fieldValues,
                        note = entry.note,
                        createdAt = entry.createdAt,
                        existingEntryId = entry.id,
                        isLoading = false
                    )
                }
            } else {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setFieldValue(fieldId: Long, value: FieldValue?) {
        _state.update {
            val updated = it.fieldValues.toMutableMap()
            if (value == null) updated.remove(fieldId) else updated[fieldId] = value
            it.copy(fieldValues = updated)
        }
    }

    fun setNote(note: String) = _state.update { it.copy(note = note) }
    fun setCreatedAt(time: Long) = _state.update { it.copy(createdAt = time) }

    fun save(eventTypeId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val current = _state.value
            val entry = EventEntry(
                id = current.existingEntryId,
                eventTypeId = eventTypeId,
                fieldValues = current.fieldValues,
                note = current.note,
                createdAt = current.createdAt
            )
            repository.saveEntry(entry)

            // Reschedule any TIME_SINCE_LAST reminders linked to this event type
            reminderRepository.rescheduleTimeSinceLast(
                eventTypeId = eventTypeId,
                entryAt = current.createdAt,
                schedule = { reminder -> reminderScheduler.schedule(reminder) }
            )

            // Entry data changed — refresh Timeline widgets so they show the new entry.
            // Only timeline needs refreshing; QuickAddWidget shows static event metadata.
            widgetUpdater.refreshTimeline()

            _state.update { it.copy(isLoading = false) }
            _dismiss.trySend(Unit)
        }
    }
}
