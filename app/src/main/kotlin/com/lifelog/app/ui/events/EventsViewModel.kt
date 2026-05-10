package com.lifelog.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.model.EventType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val repository: EventRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val eventTypes: StateFlow<List<EventType>> = combine(
        repository.observeAllEventTypes(),
        _searchQuery
    ) { types, query ->
        if (query.isBlank()) types
        else types.filter { et ->
            et.name.contains(query, ignoreCase = true) ||
            et.description.contains(query, ignoreCase = true) ||
            et.category.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun deleteEventType(id: Long) {
        viewModelScope.launch { repository.deleteEventType(id) }
    }
}
