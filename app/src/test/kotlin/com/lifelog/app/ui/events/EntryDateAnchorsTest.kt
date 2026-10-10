package com.lifelog.app.ui.events

import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.util.toDisplayDate
import com.lifelog.app.util.toUtcDateMillis
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Locks down what [entryListModel] hands the list and the [DateNavigator]: the
 * day groups the cards render under, and the flat list positions of those groups
 * — the off-by-one-prone part where sticky headers, per-entry cards, and any
 * leading screen items must add up to exactly what [entryCardItems] lays out.
 * Time zone is pinned so day grouping is stable.
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

    /** An absolute instant, named by its wall clock in [zone] (UTC by default,
     *  which is also the pinned device zone — so it reads as the local day). */
    private fun at(year: Int, month: Int, day: Int, hour: Int, zone: String = "UTC"): Long =
        Calendar.getInstance(TimeZone.getTimeZone(zone)).apply {
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
    fun `entries group into days in list order`() {
        val days = entryListModel(entries).days

        assertEquals(2, days.size)
        assertEquals(listOf(1L), days[0].entries.map { it.id })
        assertEquals(listOf(2L, 3L), days[1].entries.map { it.id })
        // Each group is labelled and keyed from its own day, and the label is the
        // same date the cards themselves would print.
        assertEquals(jun3.createdAt.toDisplayDate(), days[0].label)
        assertEquals(jun1a.createdAt.toDisplayDate(), days[1].label)
        assertNotEquals(days[0].label, days[1].label)
        assertTrue(days[0].utcDateMillis > days[1].utcDateMillis)
    }

    @Test
    fun `days break on the device's calendar date, not on UTC`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
        // Jun 3 18:00 PDT and Jun 4 10:00 PDT are two local days, but both fall
        // inside Jun 4 UTC — grouping by epoch day would fold them into one.
        val local = listOf(
            entry(1, at(2026, 6, 4, 10, zone = "America/Los_Angeles")),
            entry(2, at(2026, 6, 3, 18, zone = "America/Los_Angeles"))
        )

        val days = entryListModel(local).days

        assertEquals(2, days.size)
        assertEquals(listOf(1L), days[0].entries.map { it.id })
        assertEquals(listOf(2L), days[1].entries.map { it.id })
        assertNotEquals(days[0].utcDateMillis, days[1].utcDateMillis)
    }

    @Test
    fun `grouped anchors account for one sticky header per day plus each card`() {
        val anchors = entryListModel(entries).anchors

        // Jun 3 header at 0; its single card is index 1; Jun 1 header at 2.
        assertEquals(2, anchors.size)
        assertEquals(0, anchors[0].index)
        assertEquals(2, anchors[1].index)
        // Days are keyed to UTC start-of-day and stay in descending order.
        assertTrue(anchors[0].utcDateMillis > anchors[1].utcDateMillis)
    }

    @Test
    fun `anchors key the same days the groups do`() {
        val model = entryListModel(entries)

        assertEquals(model.days.map { it.utcDateMillis }, model.anchors.map { it.utcDateMillis })
    }

    @Test
    fun `leading items shift every grouped anchor by the same offset`() {
        val anchors = entryListModel(entries).anchors.offsetBy(leadingItemCount = 1)

        // A chart carousel ahead of the cards pushes both headers down by one.
        assertEquals(1, anchors[0].index)
        assertEquals(3, anchors[1].index)
        // Which day each anchor points at is untouched by where the cards start.
        assertEquals(
            listOf(jun3, jun1a).map { it.createdAt.toUtcDateMillis() },
            anchors.map { it.utcDateMillis }
        )
    }

    @Test
    fun `an ungrouped list keeps its rows but offers no days to jump to`() {
        // Sorted by a field value: dates interleave, so cards render flat with
        // their own captions and the date picker disables itself.
        val model = entryListModel(entries, groupByDate = false)

        assertEquals(entries, model.rows)
        assertTrue(model.days.isEmpty())
        assertTrue(model.anchors.isEmpty())
    }

    @Test
    fun `no entries yield no days and no anchors`() {
        val model = entryListModel(emptyList())

        assertTrue(model.days.isEmpty())
        assertTrue(model.anchors.isEmpty())
    }
}
