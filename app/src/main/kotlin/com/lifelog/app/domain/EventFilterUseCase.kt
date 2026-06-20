package com.lifelog.app.domain

import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventFilterState
import com.lifelog.app.domain.model.EventSortOption
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

    /**
     * Orders event types for display. Name comparisons are case-insensitive.
     * For [EventSortOption.RECENT_ACTIVITY], types with no entries (null
     * [EventType.lastEntryAt]) sort to the bottom, then alphabetically among
     * themselves for a stable, predictable order.
     */
    fun sortEventTypes(
        types: List<EventType>,
        sortOption: EventSortOption
    ): List<EventType> = when (sortOption) {
        EventSortOption.NAME_ASC -> types.sortedBy { it.name.lowercase() }
        EventSortOption.NAME_DESC -> types.sortedByDescending { it.name.lowercase() }
        EventSortOption.CREATED_NEWEST -> types.sortedByDescending { it.createdAt }
        EventSortOption.CREATED_OLDEST -> types.sortedBy { it.createdAt }
        EventSortOption.RECENT_ACTIVITY -> types.sortedWith(
            compareByDescending<EventType> { it.lastEntryAt ?: Long.MIN_VALUE }
                .thenBy { it.name.lowercase() }
        )
    }

    fun extractTags(eventTypes: List<EventType>): List<String> =
        eventTypes.mapNotNull { it.category.takeIf { c -> c.isNotBlank() } }
            .distinct()
            .sorted()
}
