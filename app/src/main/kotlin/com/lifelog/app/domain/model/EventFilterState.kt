package com.lifelog.app.domain.model

data class EventFilterState(
    val selectedTags: Set<String> = emptySet()
) {
    val hasActiveFilters: Boolean get() = selectedTags.isNotEmpty()

    fun toggleTag(tag: String): EventFilterState = copy(
        selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
    )

    fun clearAll(): EventFilterState = EventFilterState()
}
