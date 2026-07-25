package com.lifelog.app.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.data.repository.ReminderRepository
import com.lifelog.app.domain.model.DeliveryType
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.RecurrenceRule
import com.lifelog.app.domain.model.RecurrenceType
import com.lifelog.app.domain.model.Reminder
import com.lifelog.app.notifications.ReminderCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CreateReminderUiState(
    val title: String = "",
    val message: String = "",
    val eventTypeId: Long? = null,
    val eventTypeName: String? = null,
    val deliveryType: DeliveryType = DeliveryType.NOTIFICATION,
    val recurrenceRule: RecurrenceRule = RecurrenceRule(type = RecurrenceType.DAILY),
    val snoozeMinutes: Int = Reminder.DEFAULT_SNOOZE_MINUTES,
    // TIME_SINCE_LAST: optional entry datetime that seeds the initial trigger calculation
    val timeSinceLastEventDateTime: Long? = null,
    /** Carried through an edit untouched — the switch that owns it lives in the Reminders list. */
    val isActive: Boolean = true,
    val eventTypes: List<EventType> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val titleError: String? = null
)

@HiltViewModel
class CreateReminderViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val eventRepository: EventRepository,
    private val reminderCoordinator: ReminderCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(CreateReminderUiState())
    val state: StateFlow<CreateReminderUiState> = _state.asStateFlow()

    /** Guards one-time loading so a config change (e.g. rotation) doesn't clobber in-progress edits. */
    private var reminderLoaded = false

    init {
        viewModelScope.launch {
            eventRepository.observeAllEventTypes().collect { types ->
                _state.update { it.copy(eventTypes = types) }
            }
        }
    }

    fun loadReminder(id: Long) {
        if (reminderLoaded) return
        reminderLoaded = true
        viewModelScope.launch {
            val reminder = reminderRepository.getById(id) ?: return@launch
            _state.update {
                it.copy(
                    title = reminder.title,
                    message = reminder.message,
                    eventTypeId = reminder.eventTypeId,
                    eventTypeName = reminder.eventTypeName,
                    deliveryType = reminder.deliveryType,
                    recurrenceRule = reminder.recurrenceRule,
                    snoozeMinutes = reminder.snoozeMinutes,
                    isActive = reminder.isActive
                )
            }
        }
    }

    // ── Field updaters ────────────────────────────────────────────────────────

    fun setTitle(v: String) = _state.update { it.copy(title = v, titleError = null) }
    fun setMessage(v: String) = _state.update { it.copy(message = v) }
    fun setEventType(id: Long?, name: String?) = _state.update { it.copy(eventTypeId = id, eventTypeName = name) }
    fun setDeliveryType(v: DeliveryType) = _state.update { it.copy(deliveryType = v) }

    fun setRecurrenceType(type: RecurrenceType) = _state.update {
        it.copy(recurrenceRule = it.recurrenceRule.copy(type = type))
    }

    fun setRecurrenceRule(rule: RecurrenceRule) = _state.update { it.copy(recurrenceRule = rule) }

    fun setSnoozeMinutes(minutes: Int) = _state.update {
        it.copy(snoozeMinutes = minutes.coerceIn(Reminder.MIN_SNOOZE_MINUTES, Reminder.MAX_SNOOZE_MINUTES))
    }

    fun setTimeOfDay(minutes: Int) = _state.update {
        it.copy(recurrenceRule = it.recurrenceRule.copy(timeOfDayMinutes = minutes))
    }

    fun setIntervalMinutes(totalMinutes: Int) = _state.update {
        it.copy(recurrenceRule = it.recurrenceRule.copy(intervalMinutes = totalMinutes.coerceAtLeast(1)))
    }

    fun setTimeSinceLastTotalMinutes(totalMinutes: Int) = _state.update {
        it.copy(recurrenceRule = it.recurrenceRule.copy(timeSinceLastMinutes = totalMinutes.coerceAtLeast(1)))
    }

    fun setTimeSinceLastEventDateTime(epochMs: Long?) = _state.update {
        it.copy(timeSinceLastEventDateTime = epochMs)
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    fun save(existingId: Long = 0L) {
        val title = _state.value.title.trim()
        if (title.isBlank()) {
            _state.update { it.copy(titleError = "Title is required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val current = _state.value

            // The coordinator computes the initial trigger, persists it, and reconciles the OS
            // alarm — including cancelling a leftover one when the new rule has nothing to fire
            // (an already-elapsed TIME_SINCE_LAST) or the reminder is switched off.
            reminderCoordinator.save(
                reminder = Reminder(
                    id = existingId,
                    eventTypeId = current.eventTypeId,
                    eventTypeName = current.eventTypeName,
                    title = title,
                    message = current.message.trim(),
                    deliveryType = current.deliveryType,
                    recurrenceRule = current.recurrenceRule,
                    snoozeMinutes = current.snoozeMinutes,
                    isActive = current.isActive
                ),
                eventDateTime = current.timeSinceLastEventDateTime
            )

            _state.update { it.copy(isLoading = false, isSaved = true) }
        }
    }
}
