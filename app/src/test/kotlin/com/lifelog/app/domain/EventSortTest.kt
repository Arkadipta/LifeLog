package com.lifelog.app.domain

import com.lifelog.app.domain.model.EventSortOption
import com.lifelog.app.domain.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers [EventFilterUseCase.sortEventTypes] for every [EventSortOption]. */
class EventSortTest {

    private val useCase = EventFilterUseCase()

    private fun type(
        name: String,
        createdAt: Long = 0L,
        lastEntryAt: Long? = null
    ) = EventType(
        id = name.hashCode().toLong(),
        name = name,
        createdAt = createdAt,
        lastEntryAt = lastEntryAt
    )

    private fun List<EventType>.names() = map { it.name }

    @Test
    fun `name ascending is case-insensitive`() {
        val sorted = useCase.sortEventTypes(
            listOf(type("banana"), type("Apple"), type("cherry")),
            EventSortOption.NAME_ASC
        )
        assertEquals(listOf("Apple", "banana", "cherry"), sorted.names())
    }

    @Test
    fun `name descending is case-insensitive`() {
        val sorted = useCase.sortEventTypes(
            listOf(type("banana"), type("Apple"), type("cherry")),
            EventSortOption.NAME_DESC
        )
        assertEquals(listOf("cherry", "banana", "Apple"), sorted.names())
    }

    @Test
    fun `created newest first`() {
        val sorted = useCase.sortEventTypes(
            listOf(type("old", createdAt = 100), type("new", createdAt = 300), type("mid", createdAt = 200)),
            EventSortOption.CREATED_NEWEST
        )
        assertEquals(listOf("new", "mid", "old"), sorted.names())
    }

    @Test
    fun `created oldest first`() {
        val sorted = useCase.sortEventTypes(
            listOf(type("old", createdAt = 100), type("new", createdAt = 300), type("mid", createdAt = 200)),
            EventSortOption.CREATED_OLDEST
        )
        assertEquals(listOf("old", "mid", "new"), sorted.names())
    }

    @Test
    fun `recent activity orders by latest entry, newest first`() {
        val sorted = useCase.sortEventTypes(
            listOf(
                type("stale", lastEntryAt = 100),
                type("fresh", lastEntryAt = 300),
                type("middle", lastEntryAt = 200)
            ),
            EventSortOption.RECENT_ACTIVITY
        )
        assertEquals(listOf("fresh", "middle", "stale"), sorted.names())
    }

    @Test
    fun `recent activity places entryless events at the bottom, alphabetically`() {
        val sorted = useCase.sortEventTypes(
            listOf(
                type("zeta", lastEntryAt = null),
                type("active", lastEntryAt = 500),
                type("alpha", lastEntryAt = null)
            ),
            EventSortOption.RECENT_ACTIVITY
        )
        // Event with activity first; the two entryless events fall to the bottom
        // and tie-break alphabetically (alpha before zeta).
        assertEquals(listOf("active", "alpha", "zeta"), sorted.names())
    }

    @Test
    fun `sorting an empty list yields an empty list`() {
        assertEquals(emptyList<String>(), useCase.sortEventTypes(emptyList(), EventSortOption.NAME_ASC).names())
    }
}
