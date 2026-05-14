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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val repository: EventRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategories = MutableStateFlow<Set<String>>(emptySet())
    val selectedCategories: StateFlow<Set<String>> = _selectedCategories.asStateFlow()

    /** All distinct non-blank categories across all event types — drives the chip row. */
    val availableCategories: StateFlow<List<String>> = repository.observeAllEventTypes()
        .map { types ->
            types.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val eventTypes: StateFlow<List<EventType>> = combine(
        repository.observeAllEventTypes(),
        _searchQuery,
        _selectedCategories
    ) { types, query, categories ->
        types.filter { et ->
            val matchesSearch = query.isBlank() ||
                et.name.contains(query, ignoreCase = true) ||
                et.description.contains(query, ignoreCase = true) ||
                et.category.contains(query, ignoreCase = true)

            val matchesCategory = categories.isEmpty() || et.category in categories

            matchesSearch && matchesCategory
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun toggleCategory(cat: String) {
        _selectedCategories.update { current -> if (cat in current) current - cat else current + cat }
    }

    fun deleteEventType(id: Long) {
        viewModelScope.launch { repository.deleteEventType(id) }
    }
}
