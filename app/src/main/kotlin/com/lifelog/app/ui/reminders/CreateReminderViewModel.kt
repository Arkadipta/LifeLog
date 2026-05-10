package com.lifelog.app.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.data.repository.ReminderRepository
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.Reminder
import com.lifelog.app.domain.model.RepeatType
import com.lifelog.app.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

data class CreateReminderUiState(
    val title: String = "",
    val message: String = "",
    val eventTypeId: Long? = null,
    val eventTypeName: String? = null,
    val repeatType: RepeatType = RepeatType.DAILY,
    val repeatIntervalHours: Int = 1,
    val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5),
    val timeOfDayMinutes: Int = 8 * 60,
    val eventTypes: List<EventType> = emptyList(),
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val titleError: String? = null
)

@HiltViewModel
class CreateReminderViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val eventRepository: EventRepository,
    private val scheduler: ReminderScheduler
) : ViewModel() {

    private val _state = MutableStateFlow(CreateReminderUiState())
    val state: StateFlow<CreateReminderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            eventRepository.observeAllEventTypes().collect { types ->
                _state.update { it.copy(eventTypes = types) }
            }
        }
    }

    fun loadReminder(id: Long) {
        viewModelScope.launch {
            val reminder = reminderRepository.getById(id) ?: return@launch
            _state.update {
                it.copy(
                    title = reminder.title,
                    message = reminder.message,
                    eventTypeId = reminder.eventTypeId,
                    eventTypeName = reminder.eventTypeName,
                    repeatType = reminder.repeatType,
                    repeatIntervalHours = reminder.repeatIntervalMinutes / 60,
                    daysOfWeek = reminder.daysOfWeek,
                    timeOfDayMinutes = reminder.timeOfDayMinutes
                )
            }
        }
    }

    fun setTitle(v: String) = _state.update { it.copy(title = v, titleError = null) }
    fun setMessage(v: String) = _state.update { it.copy(message = v) }
    fun setEventType(id: Long?, name: String?) = _state.update { it.copy(eventTypeId = id, eventTypeName = name) }
    fun setRepeatType(v: RepeatType) = _state.update { it.copy(repeatType = v) }
    fun setIntervalHours(v: Int) = _state.update { it.copy(repeatIntervalHours = v.coerceAtLeast(1)) }
    fun setTimeOfDay(minutes: Int) = _state.update { it.copy(timeOfDayMinutes = minutes) }
    fun toggleDayOfWeek(day: Int) = _state.update {
        val days = if (it.daysOfWeek.contains(day)) it.daysOfWeek - day else it.daysOfWeek + day
        it.copy(daysOfWeek = days.sorted())
    }

    fun save(existingId: Long = 0L) {
        val title = _state.value.title.trim()
        if (title.isBlank()) {
            _state.update { it.copy(titleError = "Title is required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val current = _state.value
            val nextTrigger = computeNextTrigger(current)
            val reminder = Reminder(
                id = existingId,
                eventTypeId = current.eventTypeId,
                eventTypeName = current.eventTypeName,
                title = title,
                message = current.message.trim(),
                repeatType = current.repeatType,
                repeatIntervalMinutes = current.repeatIntervalHours * 60,
                daysOfWeek = current.daysOfWeek,
                timeOfDayMinutes = current.timeOfDayMinutes,
                nextTriggerAt = nextTrigger,
                isActive = true
            )
            val id = reminderRepository.save(reminder)
            scheduler.schedule(reminder.copy(id = id))
            _state.update { it.copy(isLoading = false, isSaved = true) }
        }
    }

    private fun computeNextTrigger(state: CreateReminderUiState): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, state.timeOfDayMinutes / 60)
            set(Calendar.MINUTE, state.timeOfDayMinutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return when (state.repeatType) {
            RepeatType.INTERVAL -> System.currentTimeMillis() + state.repeatIntervalHours * 3_600_000L
            else -> target.timeInMillis
        }
    }
}
