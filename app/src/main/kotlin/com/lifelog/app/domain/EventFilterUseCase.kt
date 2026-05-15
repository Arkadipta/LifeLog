package com.lifelog.app.domain

import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventFilterState
import com.lifelog.app.domain.model.EventType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventFilterUseCase @Inject constructor() {

    fun filterEntries(
        entries: List<EventEntry>,
        query: String,
        filterState: EventFilterState
    ): List<EventEntry> = entries
        .let { list ->
            if (filterState.selectedTags.isEmpty()) list
            else list.filter { it.eventTypeCategory in filterState.selectedTags }
        }
        .let { list ->
            if (query.isBlank()) list
            else list.filter { e ->
                e.eventTypeName.contains(query, ignoreCase = true) ||
                e.note.contains(query, ignoreCase = true)
            }
        }

    fun filterEventTypes(
        types: List<EventType>,
        query: String,
        filterState: EventFilterState
    ): List<EventType> = types
        .let { list ->
            if (filterState.selectedTags.isEmpty()) list
            else list.filter { it.category in filterState.selectedTags }
        }
        .let { list ->
            if (query.isBlank()) list
            else list.filter { et ->
                et.name.contains(query, ignoreCase = true) ||
                et.description.contains(query, ignoreCase = true) ||
                et.category.contains(query, ignoreCase = true)
            }
        }

    fun extractTags(eventTypes: List<EventType>): List<String> =
        eventTypes.mapNotNull { it.category.takeIf { c -> c.isNotBlank() } }
            .distinct()
            .sorted()
}
