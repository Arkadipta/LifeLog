package com.lifelog.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins how [AlarmService] keeps track of overlapping alarms. Two reminders can come due while
 * neither has been answered, and every rule below exists because the single-alarm version of this
 * service broke one of them: the second alarm displaced the first's notification, and one Dismiss
 * silenced them both.
 *
 * [RingingAlarms.front] is the alarm holding the service's foreground notification, so who is at
 * the front — and when that changes — is the whole contract.
 */
class RingingAlarmsTest {

    private fun alarm(reminderId: Long, title: String = "Alarm $reminderId") = RingingAlarm(
        reminderId = reminderId,
        title = title,
        message = "",
        notificationId = reminderId.toInt(),
        eventTypeId = null
    )

    @Test
    fun `nothing rings at rest`() {
        val ringing = RingingAlarms()

        assertNull(ringing.front)
        assertEquals(emptyList<RingingAlarm>(), ringing.all)
    }

    @Test
    fun `a second alarm rings alongside the first, which keeps the front`() {
        val ringing = RingingAlarms()

        ringing.add(alarm(1))
        ringing.add(alarm(2))

        // Both ring; the newcomer does not take the foreground notification off the first,
        // because re-designating it is what cancelled the first alarm's notification.
        assertEquals(listOf(alarm(1), alarm(2)), ringing.all)
        assertEquals(alarm(1), ringing.front)
    }

    @Test
    fun `answering an alarm behind the front leaves the front ringing`() {
        val ringing = RingingAlarms()
        ringing.add(alarm(1))
        ringing.add(alarm(2))

        assertEquals(alarm(2), ringing.remove(2))

        assertEquals(alarm(1), ringing.front)
        assertEquals(listOf(alarm(1)), ringing.all)
    }

    @Test
    fun `answering the front hands it to the next oldest`() {
        val ringing = RingingAlarms()
        ringing.add(alarm(1))
        ringing.add(alarm(2))
        ringing.add(alarm(3))

        assertEquals(alarm(1), ringing.remove(1))

        assertEquals(alarm(2), ringing.front)
        assertEquals(listOf(alarm(2), alarm(3)), ringing.all)
    }

    @Test
    fun `the service stops only when the last alarm is answered`() {
        val ringing = RingingAlarms()
        ringing.add(alarm(1))
        ringing.add(alarm(2))

        ringing.remove(1)
        assertEquals(alarm(2), ringing.front)   // still ringing: audio and service stay

        ringing.remove(2)
        assertNull(ringing.front)               // nothing left: AlarmService.stopSelf()
    }

    @Test
    fun `a reminder ringing again refreshes its content and keeps its place`() {
        val ringing = RingingAlarms()
        ringing.add(alarm(1))
        ringing.add(alarm(2))

        ringing.add(alarm(1, title = "Renamed"))

        // One entry, not two, and still at the front — a repeating reminder that comes due again
        // while unanswered must not queue up twice or reorder the queue.
        assertEquals(listOf(alarm(1, title = "Renamed"), alarm(2)), ringing.all)
        assertEquals("Renamed", ringing.front?.title)
    }

    @Test
    fun `answering an alarm that is not ringing changes nothing`() {
        val ringing = RingingAlarms()
        ringing.add(alarm(1))

        // A Dismiss from a stale notification, for a reminder that already rang out: it must not
        // silence the alarm the user is actually hearing.
        assertNull(ringing.remove(99))

        assertEquals(alarm(1), ringing.front)
        assertEquals(listOf(alarm(1)), ringing.all)
    }
}
