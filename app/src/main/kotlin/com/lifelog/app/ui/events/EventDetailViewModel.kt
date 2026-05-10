package com.lifelog.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val repository: EventRepository
) : ViewModel() {

    private val eventIdFlow = MutableStateFlow<Long>(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val eventType: StateFlow<EventType?> = eventIdFlow
        .filter { it != 0L }
        .flatMapLatest { repository.observeEventType(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val entries: StateFlow<List<EventEntry>> = eventIdFlow
        .filter { it != 0L }
        .flatMapLatest { repository.observeEntriesForEventType(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadEvent(id: Long) {
        eventIdFlow.value = id
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { repository.deleteEntry(id) }
    }
}
