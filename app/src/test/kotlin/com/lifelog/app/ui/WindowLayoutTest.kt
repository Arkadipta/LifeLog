package com.lifelog.app.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowLayoutTest {

    @Test fun `a phone held upright keeps the bottom bar and one pane`() {
        assertEquals(WindowLayout.COMPACT, WindowLayout.of(412.dp, 915.dp))
        assertEquals(WindowLayout.COMPACT, WindowLayout.of(599.dp, 1200.dp))
    }

    @Test fun `the rail starts at 600 wide`() {
        assertEquals(WindowLayout.MEDIUM, WindowLayout.of(600.dp, 960.dp))
        assertEquals(WindowLayout.MEDIUM, WindowLayout.of(839.dp, 1200.dp))
    }

    @Test fun `two panes start at 840 wide`() {
        assertEquals(WindowLayout.EXPANDED, WindowLayout.of(840.dp, 600.dp))
        assertEquals(WindowLayout.EXPANDED, WindowLayout.of(1280.dp, 800.dp))
    }

    @Test fun `a phone on its side gets the rail but is too short for two panes`() {
        // 915 wide clears the two-pane width; 412 tall does not leave a list pane any room.
        assertEquals(WindowLayout.MEDIUM, WindowLayout.of(915.dp, 412.dp))
        assertEquals(WindowLayout.MEDIUM, WindowLayout.of(1280.dp, 479.dp))
        assertEquals(WindowLayout.EXPANDED, WindowLayout.of(1280.dp, 480.dp))
    }

    @Test fun `a short window narrower than the rail breakpoint is still compact`() {
        assertEquals(WindowLayout.COMPACT, WindowLayout.of(500.dp, 300.dp))
    }

    @Test fun `only the expanded layout shows the list beside the detail`() {
        assertEquals(
            listOf(WindowLayout.EXPANDED),
            WindowLayout.entries.filter { it.showsListBesideDetail }
        )
        assertEquals(
            listOf(WindowLayout.MEDIUM, WindowLayout.EXPANDED),
            WindowLayout.entries.filter { it.usesNavigationRail }
        )
    }
}
