package com.lifelog.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the lifelog:// URI → [ShortcutDestination] mapping that backs the
 * launcher's static shortcuts (res/xml/shortcuts.xml). The two shipped URIs
 * must keep routing, and anything else — wrong scheme, unknown host, missing
 * parts — must map to null so the app just opens normally.
 */
class ShortcutDestinationTest {

    @Test
    fun `shipped shortcut URIs route`() {
        assertEquals(
            ShortcutDestination.TIMELINE,
            ShortcutDestination.fromUri("lifelog", "timeline")
        )
        assertEquals(
            ShortcutDestination.QUICK_ADD,
            ShortcutDestination.fromUri("lifelog", "quick_add")
        )
    }

    @Test
    fun `scheme and host match case-insensitively`() {
        assertEquals(
            ShortcutDestination.TIMELINE,
            ShortcutDestination.fromUri("LifeLog", "Timeline")
        )
    }

    @Test
    fun `unknown host opens the app normally`() {
        assertNull(ShortcutDestination.fromUri("lifelog", "settings"))
        assertNull(ShortcutDestination.fromUri("lifelog", ""))
        assertNull(ShortcutDestination.fromUri("lifelog", null))
    }

    @Test
    fun `foreign scheme never routes`() {
        assertNull(ShortcutDestination.fromUri("https", "timeline"))
        assertNull(ShortcutDestination.fromUri(null, "timeline"))
        assertNull(ShortcutDestination.fromUri(null, null))
    }
}
