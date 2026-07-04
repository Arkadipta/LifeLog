package com.lifelog.app.ui.events

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.csv.CsvManager
import com.lifelog.app.data.repository.ChartRepository
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.ChartDataProcessor
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.EntryQueryEngine
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.StoredChartConfig
import com.lifelog.app.domain.query.EntryQuery
import com.lifelog.app.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val repository: EventRepository,
    private val chartRepository: ChartRepository,
    private val csvManager: CsvManager,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    private val eventIdFlow = MutableStateFlow<Long>(0)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _entryQuery = MutableStateFlow(EntryQuery.Empty)
    val entryQuery: StateFlow<EntryQuery> = _entryQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val eventType: StateFlow<EventType?> = eventIdFlow
        .filter { it != 0L }
        .flatMapLatest { repository.observeEventType(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _allEntries: Flow<List<EventEntry>> = eventIdFlow
        .filter { it != 0L }
        .flatMapLatest { repository.observeEntriesForEventType(it) }

    val entries: StateFlow<List<EventEntry>> = combine(
        _allEntries, _searchQuery, _entryQuery
    ) { all, q, query ->
        val searched = if (q.isBlank()) all
        else all.filter { entry ->
            entry.note.contains(q, ignoreCase = true) ||
            entry.fieldValues.values.any { it.displayString().contains(q, ignoreCase = true) }
        }
        EntryQueryEngine.apply(searched, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val charts: StateFlow<List<StoredChartConfig>> = eventIdFlow
        .filter { it != 0L }
        .flatMapLatest { chartRepository.observeCharts(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val chartDataMap: StateFlow<Map<String, ChartData>> = combine(
        charts, eventType, _allEntries
    ) { stored, type, entries ->
        val fields = type?.fields ?: emptyList()
        stored.filterIsInstance<StoredChartConfig.Readable>().associate {
            it.config.id to ChartDataProcessor.process(it.config, entries, fields)
        }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun loadEvent(id: Long) {
        eventIdFlow.value = id
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun setEntryQuery(query: EntryQuery) { _entryQuery.value = query }

    fun clearEntryQuery() { _entryQuery.value = EntryQuery.Empty }

    fun deleteEntry(id: Long) {
        viewModelScope.launch {
            repository.deleteEntry(id)
            widgetUpdater.refreshTimeline()
        }
    }

    fun deleteEventType(id: Long) {
        viewModelScope.launch {
            repository.deleteEventType(id)
            // Unbind any QuickAddWidget for this event (it re-renders as part of
            // the clear), then refresh timelines that showed its entries.
            widgetUpdater.clearQuickAddForEvent(id)
            widgetUpdater.refreshTimeline()
        }
    }

    fun saveChart(config: ChartConfig) {
        viewModelScope.launch { chartRepository.saveChart(config) }
    }

    fun deleteChart(id: String) {
        viewModelScope.launch { chartRepository.deleteChart(id) }
    }

    fun exportCsv(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val et = eventType.value ?: return@launch
                val allEntries = repository.getAllEntriesForEventType(et.id)
                csvManager.exportToCsv(uri, et, allEntries)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }
}
