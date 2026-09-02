package com.lifelog.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.data.repository.UserPreferencesRepository
import com.lifelog.app.domain.EventFilterUseCase
import com.lifelog.app.domain.model.EventFilterState
import com.lifelog.app.domain.model.EventSortOption
import com.lifelog.app.domain.model.EventType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val repository: EventRepository,
    private val filterUseCase: EventFilterUseCase,
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterState = MutableStateFlow(EventFilterState())
    val filterState: StateFlow<EventFilterState> = _filterState.asStateFlow()

    val sortOption: StateFlow<EventSortOption> = prefsRepo.userPreferences
        .map { it.eventSortOption }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventSortOption.DEFAULT)

    val availableTags: StateFlow<List<String>> =
        repository.observeAllEventTypes()
            .map { filterUseCase.extractTags(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val eventTypes: StateFlow<List<EventType>> = combine(
        repository.observeAllEventTypes(),
        _searchQuery,
        _filterState,
        sortOption
    ) { types, query, state, sort ->
        val filtered = filterUseCase.filterEventTypes(types, query, state)
        filterUseCase.sortEventTypes(filtered, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun updateFilter(state: EventFilterState) { _filterState.value = state }

    fun setSortOption(option: EventSortOption) {
        viewModelScope.launch { prefsRepo.setEventSortOption(option) }
    }
}
