package com.lifelog.app.domain.model

import com.lifelog.app.domain.RecurrenceCalculator

data class Reminder(
    val id: Long = 0,
    val eventTypeId: Long? = null,
    val eventTypeName: String? = null,
    val title: String,
    val message: String = "",
    val deliveryType: DeliveryType = DeliveryType.NOTIFICATION,
    val recurrenceRule: RecurrenceRule = RecurrenceRule(),
    /** Minutes to defer the reminder when the user taps Snooze. Defaults to 10 (legacy behavior). */
    val snoozeMinutes: Int = Reminder.DEFAULT_SNOOZE_MINUTES,
    val nextTriggerAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
) {
    /**
     * Copy armed for re-enabling a disabled reminder: active, keeping the stored trigger if it
     * is still ahead or recomputing it from the rule if it has elapsed. Persist both fields and
     * hand this same copy to the scheduler — it refuses reminders whose isActive flag is false,
     * and an elapsed trigger would be coerced to "now" and fire the moment the switch flips.
     */
    fun reactivated(now: Long = System.currentTimeMillis()): Reminder = copy(
        isActive = true,
        nextTriggerAt = RecurrenceCalculator.computeReactivationTrigger(recurrenceRule, nextTriggerAt, now)
    )

    companion object {
        /** Legacy global snooze; the default for new reminders and rows migrated from before v4. */
        const val DEFAULT_SNOOZE_MINUTES = 10

        /** Allowed snooze range surfaced in the editor: 1 minute … 1 week. */
        const val MIN_SNOOZE_MINUTES = 1
        const val MAX_SNOOZE_MINUTES = 7 * 24 * 60
    }
}
