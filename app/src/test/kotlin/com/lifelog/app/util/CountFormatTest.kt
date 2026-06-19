package com.lifelog.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks down the entry-count display contract from the EventsScreen card:
 * pluralization (entry vs entries) and capping at [MAX_ENTRY_COUNT] with a "+".
 */
class CountFormatTest {

    @Test
    fun zero_isPluralEntries() {
        assertEquals("0 entries", formatEntryCount(0))
    }

    @Test
    fun one_isSingularEntry() {
        assertEquals("1 entry", formatEntryCount(1))
    }

    @Test
    fun typical_isPluralEntries() {
        assertEquals("25 entries", formatEntryCount(25))
    }

    @Test
    fun atCap_isShownVerbatim() {
        assertEquals("999 entries", formatEntryCount(MAX_ENTRY_COUNT))
    }

    @Test
    fun justOverCap_isCappedWithPlus() {
        assertEquals("999+ entries", formatEntryCount(MAX_ENTRY_COUNT + 1))
    }

    @Test
    fun farOverCap_isCappedWithPlus() {
        assertEquals("999+ entries", formatEntryCount(12_345))
    }

    @Test
    fun negative_clampsToZero() {
        assertEquals("0 entries", formatEntryCount(-3))
    }
}
