package com.lifelog.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.notifications.ReminderCoordinator
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
    val existingEntryId: Long = 0L,
    /** The event type (or the entry being edited) no longer exists — the form cannot save. */
    val eventTypeMissing: Boolean = false,
    val errorMessage: String? = null,
    /** Ids of required fields ([EventField.isRequired]) left empty by the last save attempt. */
    val fieldErrors: Set<Long> = emptySet()
)

/**
 * The entry this form would save, or null when it must not save: the event type
 * is resolved from the loaded state — never from a caller-supplied id — so an
 * edited entry always keeps the type it was loaded with, and a form whose event
 * type failed to load (deleted event, stale widget) cannot insert a dead FK.
 */
fun EntryFormState.toEventEntry(): EventEntry? {
    val typeId = eventType?.id ?: return null
    return EventEntry(
        id = existingEntryId,
        eventTypeId = typeId,
        fieldValues = fieldValues,
        note = note,
        createdAt = createdAt
    )
}

/**
 * Required fields still missing a value. A field only ever appears in
 * [EntryFormState.fieldValues] once it holds a value (every [FieldInput] variant
 * calls `onValueChange(null)` to represent "empty"), so an untouched or cleared
 * required field is simply absent from the map — no per-type emptiness check needed.
 */
fun EntryFormState.missingRequiredFieldIds(): Set<Long> =
    eventType?.fields.orEmpty()
        .asSequence()
        .filter { it.isRequired && fieldValues[it.id] == null }
        .map { it.id }
        .toSet()

/**
 * The field's value after a just-added option is picked: CHOICE replaces the
 * selection, MULTI_SELECT appends (already-selected stays as-is). Types without
 * options keep their value unchanged.
 */
fun FieldValue?.withOptionSelected(type: FieldType, option: String): FieldValue? = when (type) {
    FieldType.CHOICE -> FieldValue.Choice(option)
    FieldType.MULTI_SELECT -> {
        val current = (this as? FieldValue.MultiSelect)?.values.orEmpty()
        if (option in current) this else FieldValue.MultiSelect(current + option)
    }
    else -> this
}

@HiltViewModel
class EntryViewModel @Inject constructor(
    private val repository: EventRepository,
    private val reminderCoordinator: ReminderCoordinator,
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
            _state.update { it.copy(eventType = eventType, eventTypeMissing = eventType == null) }
        }
    }

    fun loadEntry(entryId: Long) {
        _state.value = EntryFormState(isLoading = true)
        viewModelScope.launch {
            val entry = repository.getEntry(entryId)
            val eventType = entry?.let { repository.getEventType(it.eventTypeId) }
            if (entry != null && eventType != null) {
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
                _state.update { it.copy(isLoading = false, eventTypeMissing = true) }
            }
        }
    }

    fun setFieldValue(fieldId: Long, value: FieldValue?) {
        _state.update {
            val updated = it.fieldValues.toMutableMap()
            if (value == null) updated.remove(fieldId) else updated[fieldId] = value
            it.copy(
                fieldValues = updated,
                // A value just came in — clear its flagged-required error, if any.
                fieldErrors = if (value != null) it.fieldErrors - fieldId else it.fieldErrors
            )
        }
    }

    fun setNote(note: String) = _state.update { it.copy(note = note) }
    fun setCreatedAt(time: Long) = _state.update { it.copy(createdAt = time) }

    /**
     * Persists a user-added option onto its Choice/MultiSelect field definition,
     * then selects it. Persist-first is the point: the entry's value must never
     * reference an option the field doesn't durably have (the add used to live
     * in remember-local UI state, so the option vanished when the form closed
     * and any saved value pointing at it became invisible on the chips row).
     */
    fun addFieldOption(fieldId: Long, option: String) {
        val trimmed = option.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val persisted = runCatching { repository.addFieldOption(fieldId, trimmed) }.getOrNull()
            if (persisted == null) {
                _state.update { it.copy(errorMessage = "Couldn't add the option") }
                return@launch
            }
            _state.update { st ->
                st.copy(
                    eventType = st.eventType?.let { type ->
                        type.copy(fields = type.fields.map { if (it.id == fieldId) persisted else it })
                    }
                )
            }
            setFieldValue(
                fieldId,
                _state.value.fieldValues[fieldId].withOptionSelected(persisted.type, trimmed)
            )
        }
    }

    fun save() {
        viewModelScope.launch {
            val missing = _state.value.missingRequiredFieldIds()
            if (missing.isNotEmpty()) {
                _state.update {
                    it.copy(fieldErrors = missing, errorMessage = "Fill in the required fields")
                }
                return@launch
            }

            _state.update { it.copy(isLoading = true, errorMessage = null, fieldErrors = emptySet()) }
            val current = _state.value
            val entry = current.toEventEntry()
            if (entry == null) {
                // No loaded event type — saving would insert a dead foreign key.
                _state.update {
                    it.copy(isLoading = false, errorMessage = "This event no longer exists")
                }
                return@launch
            }
            val saved = runCatching { repository.saveEntry(entry) }
            if (saved.isFailure) {
                _state.update {
                    it.copy(isLoading = false, errorMessage = "Couldn't save the entry")
                }
                return@launch
            }

            // Restart any TIME_SINCE_LAST reminders watching this event type
            reminderCoordinator.onEntryLogged(
                eventTypeId = entry.eventTypeId,
                entryAt = current.createdAt
            )

            // Entry data changed — refresh Timeline widgets so they show the new entry.
            // Only timeline needs refreshing; QuickAddWidget shows static event metadata.
            widgetUpdater.refreshTimeline()

            _state.update { it.copy(isLoading = false) }
            _dismiss.trySend(Unit)
        }
    }
}
