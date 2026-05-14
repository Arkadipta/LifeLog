package com.lifelog.app.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.FieldValue
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
    private val repository: EventRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    val selectedTags: StateFlow<Set<String>> = _selectedTags.asStateFlow()

    /** All distinct MULTI_SELECT values across all entries — drives the tag chip row. */
    val availableTags: StateFlow<List<String>> = repository.observeAllEntries()
        .map { entries ->
            entries.flatMap { entry ->
                entry.fieldValues.values
                    .filterIsInstance<FieldValue.MultiSelect>()
                    .flatMap { it.values }
            }.distinct().sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries: StateFlow<List<EventEntry>> = combine(
        repository.observeAllEntries(),
        _searchQuery,
        _selectedTags
    ) { allEntries, query, tags ->
        allEntries.filter { e ->
            val matchesSearch = query.isBlank() ||
                e.eventTypeName.contains(query, ignoreCase = true) ||
                e.note.contains(query, ignoreCase = true)

            val matchesTags = tags.isEmpty() || run {
                val entryTags = e.fieldValues.values
                    .filterIsInstance<FieldValue.MultiSelect>()
                    .flatMap { it.values }
                    .toSet()
                tags.any { it in entryTags }
            }

            matchesSearch && matchesTags
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val fieldsMap: StateFlow<Map<Long, List<EventField>>> =
        repository.observeAllEventTypes()
            .map { types -> types.associate { it.id to it.fields } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun toggleTag(tag: String) {
        _selectedTags.update { current -> if (tag in current) current - tag else current + tag }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { repository.deleteEntry(id) }
    }
}
