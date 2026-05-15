package com.lifelog.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.EventFilterUseCase
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.EventFilterState
import com.lifelog.app.widget.WidgetUpdater
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
class TimelineViewModel @Inject constructor(
    private val repository: EventRepository,
    private val filterUseCase: EventFilterUseCase,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterState = MutableStateFlow(EventFilterState())
    val filterState: StateFlow<EventFilterState> = _filterState.asStateFlow()

    val availableTags: StateFlow<List<String>> =
        repository.observeAllEventTypes()
            .map { filterUseCase.extractTags(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries: StateFlow<List<EventEntry>> = combine(
        repository.observeAllEntries(),
        _searchQuery,
        _filterState
    ) { allEntries, query, state ->
        filterUseCase.filterEntries(allEntries, query, state)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fieldsMap: StateFlow<Map<Long, List<EventField>>> =
        repository.observeAllEventTypes()
            .map { types -> types.associate { it.id to it.fields } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun updateFilter(state: EventFilterState) { _filterState.value = state }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteEntry(id)
            widgetUpdater.refreshTimeline()
        }
    }
}
