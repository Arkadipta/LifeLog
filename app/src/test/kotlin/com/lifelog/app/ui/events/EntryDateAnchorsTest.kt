package com.lifelog.app.ui.events

import com.lifelog.app.domain.model.EventEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Locks down the flat list positions [entryDateAnchors] hands the
 * [DateNavigator] — the off-by-one-prone part where sticky headers, per-entry
 * cards, and any leading screen items must add up to exactly what
 * [entryCardItems] lays out. Time zone is pinned so day grouping is stable.
 */
class EntryDateAnchorsTest {

    private lateinit var originalTz: TimeZone

    @Before
    fun pinTimeZone() {
        originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(originalTz)
    }

    /** Local timestamp for a calendar day at a given hour (TZ is pinned to UTC). */
    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    private fun entry(id: Long, createdAt: Long) =
        EventEntry(id = id, eventTypeId = 1L, createdAt = createdAt, updatedAt = createdAt)

    // Reverse-chronological, exactly as the screens supply the list:
    // Jun 3 (one entry) then Jun 1 (two entries).
    private val jun3 = entry(1, at(2026, 6, 3, 10))
    private val jun1a = entry(2, at(2026, 6, 1, 12))
    private val jun1b = entry(3, at(2026, 6, 1, 9))
    private val entries = listOf(jun3, jun1a, jun1b)

    @Test
    fun `grouped anchors account for one sticky header per day plus each card`() {
        val anchors = entryDateAnchors(entries, groupByDate = true)

        // Jun 3 header at 0; its single card is index 1; Jun 1 header at 2.
        assertEquals(2, anchors.size)
        assertEquals(0, anchors[0].index)
        assertEquals(2, anchors[1].index)
        // Days are keyed to UTC start-of-day and stay in descending order.
        assertTrue(anchors[0].utcDateMillis > anchors[1].utcDateMillis)
    }

    @Test
    fun `leading items shift every grouped anchor by the same offset`() {
        val anchors = entryDateAnchors(entries, groupByDate = true, leadingItemCount = 1)

        // A chart carousel ahead of the cards pushes both headers down by one.
        assertEquals(1, anchors[0].index)
        assertEquals(3, anchors[1].index)
    }

    @Test
    fun `ungrouped anchors mark the first card of each contiguous date run`() {
        val anchors = entryDateAnchors(entries, groupByDate = false)

        // No sticky headers: one item per card, anchor at each date's first row.
        assertEquals(2, anchors.size)
        assertEquals(0, anchors[0].index)
        assertEquals(1, anchors[1].index)
    }

    @Test
    fun `no entries yield no anchors`() {
        assertTrue(entryDateAnchors(emptyList(), groupByDate = true).isEmpty())
    }
}
