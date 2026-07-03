package com.lifelog.app.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.ReminderRepository
import com.lifelog.app.domain.model.Reminder
import com.lifelog.app.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RemindersViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val scheduler: ReminderScheduler
) : ViewModel() {

    val reminders: StateFlow<List<Reminder>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleActive(reminder: Reminder) {
        viewModelScope.launch {
            if (reminder.isActive) {
                repository.setActive(reminder.id, false)
                scheduler.cancel(reminder.id)
            } else {
                // Persist the recomputed trigger and active flag before arming: the receiver
                // re-reads the row when the alarm fires and stays silent if it looks inactive.
                val armed = reminder.reactivated()
                repository.updateNextTrigger(armed.id, armed.nextTriggerAt)
                repository.setActive(armed.id, true)
                scheduler.schedule(armed)
            }
        }
    }

    fun delete(reminder: Reminder) {
        viewModelScope.launch {
            scheduler.cancel(reminder.id)
            repository.delete(reminder.id)
        }
    }
}
