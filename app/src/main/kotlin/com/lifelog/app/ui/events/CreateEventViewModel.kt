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
    val nameError: String? = null
)

@HiltViewModel
class CreateEventViewModel @Inject constructor(
    private val repository: EventRepository,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    private val _state = MutableStateFlow(CreateEventUiState())
    val state: StateFlow<CreateEventUiState> = _state.asStateFlow()

    fun loadEvent(eventId: Long) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val event = repository.getEventType(eventId)
            if (event != null) {
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
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val eventType = EventType(
                id = existingId,
                name = name,
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
