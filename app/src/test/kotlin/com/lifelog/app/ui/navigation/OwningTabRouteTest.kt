package com.lifelog.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwningTabRouteTest {

    @Test fun `a tab owns itself`() {
        assertEquals("events", owningTabRoute(Screen.Events.route))
        assertEquals("timeline", owningTabRoute(Screen.Timeline.route))
        assertEquals("reminders", owningTabRoute(Screen.Reminders.route))
    }

    @Test fun `a tab owns every screen beneath it`() {
        assertEquals("events", owningTabRoute(Screen.EventDetail.route))
        assertEquals("events", owningTabRoute(Screen.EditEvent.route))
        assertEquals("events", owningTabRoute(Screen.CreateEvent.route))
        assertEquals("events", owningTabRoute(Screen.ImportCsv.route))
        assertEquals("reminders", owningTabRoute(Screen.CreateReminder.route))
        assertEquals("reminders", owningTabRoute(Screen.EditReminder.route))
    }

    @Test fun `a screen outside every tab lights none`() {
        assertNull(owningTabRoute(Screen.Settings.route))
        assertNull(owningTabRoute(null))
    }

    @Test fun `a route that merely starts with a tab's name is not beneath it`() {
        assertNull(owningTabRoute("eventsarchive"))
    }
}
