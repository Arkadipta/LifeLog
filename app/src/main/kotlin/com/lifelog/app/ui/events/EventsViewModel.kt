package com.lifelog.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.EventFilterUseCase
import com.lifelog.app.domain.model.EventFilterState
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.ui.undo.UndoDeleteManager
import com.lifelog.app.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class EventsViewModel @Inject constructor(
    private val repository: EventRepository,
    private val filterUseCase: EventFilterUseCase,
    private val widgetUpdater: WidgetUpdater,
    private val undoManager: UndoDeleteManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterState = MutableStateFlow(EventFilterState())
    val filterState: StateFlow<EventFilterState> = _filterState.asStateFlow()

    val availableTags: StateFlow<List<String>> =
        repository.observeAllEventTypes()
            .map { filterUseCase.extractTags(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val eventTypes: StateFlow<List<EventType>> = combine(
        repository.observeAllEventTypes(),
        _searchQuery,
        _filterState
    ) { types, query, state ->
        filterUseCase.filterEventTypes(types, query, state)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun updateFilter(state: EventFilterState) { _filterState.value = state }

    fun deleteEventType(id: Long) {
        undoManager.delete(
            message = "Event deleted",
            delete = {
                val snapshot = repository.deleteEventTypeReturningSnapshot(id)
                // Deleting an event type removes its entries from the timeline and may
                // leave a QuickAddWidget pointing at a now-deleted event — refresh all.
                widgetUpdater.refreshAll()
                snapshot
            },
            restore = { snapshot ->
                snapshot?.let {
                    repository.restoreEventType(it)
                    widgetUpdater.refreshAll()
                }
            }
        )
    }
}
