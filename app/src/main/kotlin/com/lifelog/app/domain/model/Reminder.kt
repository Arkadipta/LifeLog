package com.lifelog.app.domain.model

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
    companion object {
        /** Legacy global snooze; the default for new reminders and rows migrated from before v4. */
        const val DEFAULT_SNOOZE_MINUTES = 10

        /** Allowed snooze range surfaced in the editor: 1 minute … 1 week. */
        const val MIN_SNOOZE_MINUTES = 1
        const val MAX_SNOOZE_MINUTES = 7 * 24 * 60
    }
}
