package com.lifelog.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.EventFilterUseCase
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.EventFilterState
import com.lifelog.app.ui.events.EntryListModel
import com.lifelog.app.ui.events.entryListModel
import com.lifelog.app.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** How long typing settles before the timeline re-filters. Long enough to skip
 *  the intermediate words of a real search, short enough to feel immediate. */
private const val SEARCH_DEBOUNCE_MS = 200L

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

    // The two event-type flows below map on Dispatchers.Default for the same
    // reason as `entries`: observeAllEventTypes joins four whole-table reads and
    // groups them in memory, and that work runs wherever it is collected.
    val availableTags: StateFlow<List<String>> =
        repository.observeAllEventTypes()
            .map { filterUseCase.extractTags(it) }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Typing is not a filter request per keystroke — each one would otherwise
     * re-scan the whole table. Clearing the box skips the wait so the full list
     * snaps back instantly; the visible text comes from [searchQuery], which is
     * never debounced, so the field itself stays immediate either way.
     */
    @OptIn(FlowPreview::class)
    private val debouncedQuery: Flow<String> =
        _searchQuery.debounce { query -> if (query.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }

    /**
     * The filtered timeline, laid out. Filtering reads only plain columns, so
     * this scans every entry — search stays honest about old entries — while
     * decoding none of them; a row's values are parsed only when its card is
     * composed. [entryListModel] then groups the survivors into days once, here,
     * instead of on the main thread in the screen's list lambda.
     *
     * [flowOn] covers the whole upstream (the two table reads, the row mapping,
     * the filter pass, and the grouping) so none of it lands on the main thread,
     * which is where a `stateIn(viewModelScope, …)` collector would otherwise
     * run it.
     */
    val entryList: StateFlow<EntryListModel> = combine(
        repository.observeAllEntryRows(),
        debouncedQuery,
        _filterState
    ) { allEntries, query, state ->
        entryListModel(filterUseCase.filterEntries(allEntries, query, state))
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryListModel())

    val fieldsMap: StateFlow<Map<Long, List<EventField>>> =
        repository.observeAllEventTypes()
            .map { types -> types.associate { it.id to it.fields } }
            .flowOn(Dispatchers.Default)
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
