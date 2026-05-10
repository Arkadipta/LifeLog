package com.lifelog.app.ui.events

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.csv.CsvManager
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EventDetailViewModel @Inject constructor(
    private val repository: EventRepository,
    private val csvManager: CsvManager
) : ViewModel() {

    private val eventIdFlow = MutableStateFlow<Long>(0)
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val eventType: StateFlow<EventType?> = eventIdFlow
        .filter { it != 0L }
        .flatMapLatest { repository.observeEventType(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _allEntries: Flow<List<EventEntry>> = eventIdFlow
        .filter { it != 0L }
        .flatMapLatest { repository.observeEntriesForEventType(it) }

    val entries: StateFlow<List<EventEntry>> = combine(_allEntries, _searchQuery) { all, q ->
        if (q.isBlank()) all
        else all.filter { entry ->
            entry.note.contains(q, ignoreCase = true) ||
            entry.fieldValues.values.any { it.displayString().contains(q, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun loadEvent(id: Long) {
        eventIdFlow.value = id
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { repository.deleteEntry(id) }
    }

    fun exportCsv(uri: Uri, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val et = eventType.value ?: return@launch
                // Use all entries (not filtered) for export
                val allEntries = repository.getAllEntriesForEventType(et.id)
                csvManager.exportToCsv(uri, et, allEntries)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }
}
