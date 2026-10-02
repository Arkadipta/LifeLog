package com.lifelog.app.notifications

import android.content.Intent
import com.lifelog.app.domain.model.Reminder

/**
 * One alarm as it is being delivered: everything the notification, the full-screen alarm screen
 * and the actions on both are built from. It travels as Intent extras between the three
 * components that handle a ringing alarm — [ReminderReceiver] reads the reminder row and fills
 * it in, [AlarmService] rings it, [AlarmDismissActivity] shows it — which is why the extra keys
 * live here rather than being restated (identically) in each of them.
 *
 * A snapshot, not a source of truth: the reminder row owns anything that can change, and
 * [ReminderReceiver] re-reads it before every ring. Nothing downstream writes back from here.
 */
data class RingingAlarm(
    val reminderId: Long,
    val title: String,
    val message: String,
    val notificationId: Int,
    val eventTypeId: Long?,
    val snoozeMinutes: Int = Reminder.DEFAULT_SNOOZE_MINUTES
) {
    /** Writes this alarm into [intent], for [toRingingAlarm] to read back. */
    fun putInto(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_REMINDER_ID, reminderId)
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_MESSAGE, message)
        putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        putExtra(EXTRA_EVENT_TYPE_ID, eventTypeId ?: NO_EVENT_TYPE)
        putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
    }

    companion object {
        // Distinct from ReminderReceiver.EXTRA_REMINDER_ID on purpose: that one identifies a
        // reminder to look up in the database, and is deliberately the ONLY thing the receiver's
        // intents carry. These are the delivery payload of an alarm already read from the row.
        const val EXTRA_REMINDER_ID     = "reminder_id"
        const val EXTRA_TITLE           = "title"
        const val EXTRA_MESSAGE         = "message"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_EVENT_TYPE_ID   = "event_type_id"
        const val EXTRA_SNOOZE_MINUTES  = "snooze_minutes"

        /** Extras can't carry a null Long, so "no linked event type" travels as this. */
        internal const val NO_EVENT_TYPE = -1L
    }
}

/** Reads back what [RingingAlarm.putInto] wrote. */
fun Intent.toRingingAlarm(): RingingAlarm {
    val reminderId = getLongExtra(RingingAlarm.EXTRA_REMINDER_ID, -1L)
    return RingingAlarm(
        reminderId = reminderId,
        title = getStringExtra(RingingAlarm.EXTRA_TITLE) ?: "LifeLog Alarm",
        message = getStringExtra(RingingAlarm.EXTRA_MESSAGE) ?: "",
        notificationId = getIntExtra(RingingAlarm.EXTRA_NOTIFICATION_ID, reminderId.toInt()),
        eventTypeId = getLongExtra(RingingAlarm.EXTRA_EVENT_TYPE_ID, RingingAlarm.NO_EVENT_TYPE)
            .takeIf { it != RingingAlarm.NO_EVENT_TYPE },
        snoozeMinutes = getIntExtra(RingingAlarm.EXTRA_SNOOZE_MINUTES, Reminder.DEFAULT_SNOOZE_MINUTES)
    )
}

/**
 * The alarms [AlarmService] is currently ringing, oldest first.
 *
 * Alarms overlap: a second reminder can come due while the first is still unanswered, and both
 * have to keep ringing until each is dismissed on its own. The service holds one audio source
 * for however many are in here, and one of them — the [front] — additionally owns the service's
 * foreground notification.
 *
 * **The front is the oldest, not the newest.** A service has exactly one foreground notification,
 * and designating a different one *cancels* the previous notification; letting a newly arrived
 * alarm take the slot is precisely what used to make it swallow the alarm already ringing. So the
 * slot changes hands only when the alarm holding it is answered — where removing its notification
 * is the point.
 */
class RingingAlarms {

    private val byReminderId = LinkedHashMap<Long, RingingAlarm>()

    /** The alarm holding the foreground notification, or null when nothing is ringing. */
    val front: RingingAlarm? get() = byReminderId.values.firstOrNull()

    /** Every alarm still ringing, oldest first. */
    val all: List<RingingAlarm> get() = byReminderId.values.toList()

    /**
     * Starts [alarm] ringing, or refreshes one that already is with its latest content.
     *
     * A re-fire keeps its place in the queue — LinkedHashMap leaves insertion order alone for a
     * key it already holds — so a repeating reminder that comes due again while unanswered can
     * neither queue up twice nor jump ahead of an older alarm.
     */
    fun add(alarm: RingingAlarm) {
        byReminderId[alarm.reminderId] = alarm
    }

    /**
     * Stops [reminderId] ringing and returns its alarm, or null when it wasn't ringing — the
     * answer that keeps a stale Dismiss (for a reminder that already rang out, say) from
     * silencing whatever is ringing now.
     */
    fun remove(reminderId: Long): RingingAlarm? = byReminderId.remove(reminderId)
}
