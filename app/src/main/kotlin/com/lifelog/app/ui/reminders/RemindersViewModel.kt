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
            val newActive = !reminder.isActive
            repository.setActive(reminder.id, newActive)
            if (newActive) {
                scheduler.schedule(reminder)
            } else {
                scheduler.cancel(reminder.id)
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
