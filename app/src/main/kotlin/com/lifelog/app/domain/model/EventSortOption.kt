package com.lifelog.app.domain.model

/**
 * How the Events list on [com.lifelog.app.ui.events.EventsScreen] is ordered.
 *
 * Persisted by name in user preferences (see UserPreferencesRepository), so the
 * chosen order survives app restarts. [RECENT_ACTIVITY] keys off each event's
 * most recent entry timestamp ([EventType.lastEntryAt]); events with no entries
 * sort to the bottom.
 */
enum class EventSortOption(val label: String) {
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    CREATED_NEWEST("Newest created"),
    CREATED_OLDEST("Oldest created"),
    RECENT_ACTIVITY("Recent activity");

    companion object {
        /** Default matches the historical `ORDER BY name ASC` list order. */
        val DEFAULT = NAME_ASC
    }
}
