package com.lifelog.app.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.ReminderRepository
import com.lifelog.app.domain.RecurrenceCalculator
import com.lifelog.app.domain.model.RecurrenceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlarmDismissViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val _nextTriggerAt = MutableStateFlow<Long?>(null)
    val nextTriggerAt: StateFlow<Long?> = _nextTriggerAt.asStateFlow()

    fun loadNextTrigger(reminderId: Long) {
        viewModelScope.launch {
            val reminder = reminderRepository.getById(reminderId) ?: return@launch
            // TIME_SINCE_LAST reminders reset on entry, not on a fixed schedule
            if (reminder.recurrenceRule.type == RecurrenceType.TIME_SINCE_LAST) return@launch
            _nextTriggerAt.value = RecurrenceCalculator.computeNextTrigger(
                rule = reminder.recurrenceRule,
                after = System.currentTimeMillis()
            )
        }
    }
}
