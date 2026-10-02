package com.lifelog.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Contract of [Reminder.reactivated]: the copy handed to ReminderScheduler.schedule() when the
 * user re-enables a reminder must always be active with a usable trigger — schedule() silently
 * drops inactive reminders, and an elapsed trigger would be coerced to "now" and ring the
 * moment the switch flips (the original H1 bugs).
 */
class ReminderReactivationTest {

    private val now: Long = Calendar.getInstance().apply {
        set(2025, Calendar.JUNE, 10, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val daily8am = RecurrenceRule(type = RecurrenceType.DAILY, timeOfDayMinutes = 8 * 60)

    private fun disabledReminder(triggerAt: Long, rule: RecurrenceRule = daily8am) = Reminder(
        id = 7L,
        eventTypeId = 3L,
        title = "Log water",
        message = "hydrate",
        deliveryType = DeliveryType.ALARM,
        recurrenceRule = rule,
        snoozeMinutes = 15,
        nextTriggerAt = triggerAt,
        isActive = false
    )

    @Test
    fun `reactivated is always active`() {
        assertTrue(disabledReminder(triggerAt = now - 1_000).reactivated(now).isActive)
    }

    @Test
    fun `reactivated recomputes an elapsed trigger into the future`() {
        val armed = disabledReminder(triggerAt = now - 86_400_000L).reactivated(now)
        assertTrue(armed.nextTriggerAt > now)
    }

    @Test
    fun `reactivated keeps a stored trigger that is still ahead`() {
        val stored = now + 3600_000L
        val armed = disabledReminder(triggerAt = stored).reactivated(now)
        assertEquals(stored, armed.nextTriggerAt)
    }

    @Test
    fun `reactivated leaves identity and configuration untouched`() {
        val original = disabledReminder(triggerAt = now - 1_000)
        val armed = original.reactivated(now)
        assertEquals(original.copy(isActive = true, nextTriggerAt = armed.nextTriggerAt), armed)
    }

    @Test
    fun `reactivated one-shot gets a future trigger instead of none`() {
        val oneShot = RecurrenceRule(type = RecurrenceType.NONE, timeOfDayMinutes = 9 * 60)
        val armed = disabledReminder(triggerAt = now - 1_000, rule = oneShot).reactivated(now)
        assertTrue(armed.nextTriggerAt > now)
    }
}
