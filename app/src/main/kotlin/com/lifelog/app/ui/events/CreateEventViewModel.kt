package com.lifelog.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateEventUiState(
    val name: String = "",
    val description: String = "",
    val category: String = "",
    val colorArgb: Int = EventType.DEFAULT_COLOR,
    val iconName: String = "star",
    val fields: List<EventField> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val nameError: String? = null,
    /** True while the field-type-change confirmation dialog should be shown. */
    val showTypeChangeWarning: Boolean = false
)

/** Event metadata collected by [CreateEventScreen] in draft mode (CSV import). */
data class EventDraft(
    val name: String,
    val description: String,
    val category: String,
    val colorArgb: Int,
    val iconName: String
)

/**
 * Configures [CreateEventScreen] for the CSV-import "Configure Event" step: it
 * collects metadata and hands back an [EventDraft] via [onConfirm] instead of
 * persisting an event. The Fields section is hidden because fields are derived
 * from the CSV columns later in the wizard.
 */
data class EventDraftConfig(
    val initialName: String = "",
    val initialColorArgb: Int = EventType.DEFAULT_COLOR,
    val initialIconName: String = "star",
    val onConfirm: (EventDraft) -> Unit
)

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    private val repository: EventRepository,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    private val _state = MutableStateFlow(CreateEventUiState())
    val state: StateFlow<CreateEventUiState> = _state.asStateFlow()

    /** Declared types of the fields as originally loaded, keyed by field id. */
    private var originalFieldTypes: Map<Long, FieldType> = emptyMap()

    /** Number of entries the event had when loaded — gates the type-change warning. */
    private var entryCount: Int = 0

    /** Guards one-time seeding so returning to the draft step keeps user edits. */
    private var draftSeeded = false

    /** Guards one-time loading so a config change (e.g. rotation) doesn't clobber in-progress edits. */
    private var eventLoaded = false

    /** Pre-fill the draft form once with import-suggested metadata. */
    fun seedDraftOnce(config: EventDraftConfig) {
        if (draftSeeded) return
        draftSeeded = true
        _state.update {
            it.copy(
                name = config.initialName,
                colorArgb = config.initialColorArgb,
                iconName = config.initialIconName
            )
        }
    }

    /** Validate the name and hand the collected metadata back to the import wizard. */
    fun confirmDraft(onConfirm: (EventDraft) -> Unit) {
        val s = _state.value
        val name = s.name.trim()
        if (name.isBlank()) {
            _state.update { it.copy(nameError = "Name is required") }
            return
        }
        onConfirm(
            EventDraft(
                name = name,
                description = s.description.trim(),
                category = s.category.trim(),
                colorArgb = s.colorArgb,
                iconName = s.iconName
            )
        )
    }

    fun loadEvent(eventId: Long) {
        if (eventLoaded) return
        eventLoaded = true
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val event = repository.getEventType(eventId)
            if (event != null) {
                originalFieldTypes = event.fields.associate { it.id to it.type }
                entryCount = event.entryCount
                _state.update {
                    it.copy(
                        name = event.name,
                        description = event.description,
                        category = event.category,
                        colorArgb = event.colorArgb,
                        iconName = event.iconName,
                        fields = event.fields,
                        isLoading = false
                    )
                }
            } else {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setName(value: String) = _state.update { it.copy(name = value, nameError = null) }
    fun setDescription(value: String) = _state.update { it.copy(description = value) }
    fun setCategory(value: String) = _state.update { it.copy(category = value) }
    fun setColor(color: Int) = _state.update { it.copy(colorArgb = color) }
    fun setIcon(name: String) = _state.update { it.copy(iconName = name) }

    fun addField(field: EventField) = _state.update {
        it.copy(fields = it.fields + field)
    }

    fun updateField(index: Int, field: EventField) = _state.update {
        val updated = it.fields.toMutableList().also { list -> list[index] = field }
        it.copy(fields = updated)
    }

    fun removeField(index: Int) = _state.update {
        it.copy(fields = it.fields.filterIndexed { i, _ -> i != index })
    }

    fun moveFieldUp(index: Int) {
        if (index <= 0) return
        _state.update {
            val list = it.fields.toMutableList()
            val tmp = list[index]
            list[index] = list[index - 1]
            list[index - 1] = tmp
            it.copy(fields = list)
        }
    }

    fun moveFieldDown(index: Int) {
        _state.update {
            if (index >= it.fields.size - 1) return@update it
            val list = it.fields.toMutableList()
            val tmp = list[index]
            list[index] = list[index + 1]
            list[index + 1] = tmp
            it.copy(fields = list)
        }
    }

    fun save(existingId: Long = 0L) {
        val name = _state.value.name.trim()
        if (name.isBlank()) {
            _state.update { it.copy(nameError = "Name is required") }
            return
        }
        // Editing an existing event that already has entries, where a previously
        // existing field's type was changed: warn first. Existing values are not
        // converted and become legacy mismatches, so the user must opt in.
        if (existingId != 0L && entryCount > 0 && hasChangedFieldTypes()) {
            _state.update { it.copy(showTypeChangeWarning = true) }
            return
        }
        persist(existingId)
    }

    /** Proceed with the save after the user acknowledged the type-change warning. */
    fun confirmTypeChangeAndSave(existingId: Long) {
        _state.update { it.copy(showTypeChangeWarning = false) }
        persist(existingId)
    }

    fun dismissTypeChangeWarning() = _state.update { it.copy(showTypeChangeWarning = false) }

    /**
     * True when at least one field that existed before this edit (matched by id)
     * now has a different declared type. Brand-new fields (id == 0) are ignored.
     */
    private fun hasChangedFieldTypes(): Boolean =
        _state.value.fields.any { field ->
            field.id != 0L && originalFieldTypes[field.id].let { it != null && it != field.type }
        }

    private fun persist(existingId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val eventType = EventType(
                id = existingId,
                name = _state.value.name.trim(),
                description = _state.value.description.trim(),
                category = _state.value.category.trim(),
                colorArgb = _state.value.colorArgb,
                iconName = _state.value.iconName,
                fields = _state.value.fields
            )
            repository.saveEventType(eventType)
            // Event type name/category may be displayed in widget headers and
            // the QuickAddWidget label — refresh all widget types.
            widgetUpdater.refreshAll()
            _state.update { it.copy(isLoading = false, isSaved = true) }
        }
    }
}
