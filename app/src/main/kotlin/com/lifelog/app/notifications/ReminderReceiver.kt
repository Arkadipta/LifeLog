package com.lifelog.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifelog.app.data.repository.ReminderRepository
import com.lifelog.app.domain.model.DeliveryType
import com.lifelog.app.domain.model.Reminder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var reminderCoordinator: ReminderCoordinator

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_RESCHEDULE_ALL -> rescheduleAll(clockChanged = false)
            // Armed alarms are absolute RTC epochs; when the zone or the wall clock moves
            // underneath them, wall-clock rules must be re-anchored to the new local time or
            // they keep firing at the old zone's instant (wrong local time until the next fire).
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> rescheduleAll(clockChanged = true)
            ACTION_REMINDER -> handleReminder(context, intent)
            ACTION_SNOOZE   -> handleSnooze(context, intent)
            ACTION_DISMISS  -> handleDismiss(context, intent)
        }
    }

    private fun handleReminder(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L).takeIf { it != -1L } ?: return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // The DB is the source of truth — not the intent extras (a snapshot from schedule
                // time). Verify the reminder still exists and is active BEFORE ringing: a deleted or
                // disabled reminder can still have an OS-level alarm armed — it may have fired in the
                // gap between delete and cancel, or it was armed by an older build whose cancel()
                // never matched the alarm. Bailing here keeps those ghost alarms silent, which the
                // platform otherwise can't reach (AlarmManager pending alarms aren't enumerable).
                val reminder = reminderRepository.getById(reminderId)
                if (reminder == null || !reminder.isActive) return@launch

                ring(context, reminder)
                reminderCoordinator.onFired(reminder)
            } finally {
                pending.finish()
            }
        }
    }

    /** Rings [reminder] from its freshly-loaded DB row: alarm audio + full-screen UI, or a notification. */
    private fun ring(context: Context, reminder: Reminder) {
        val notifId = reminder.id.toInt()
        val title = reminder.title.ifBlank { "LifeLog Reminder" }
        val message = reminder.message
        val eventTypeId = reminder.eventTypeId

        if (reminder.deliveryType == DeliveryType.ALARM) {
            // The foreground service owns the single audio source and posts the ongoing full-screen-
            // intent notification. Exact alarms (setAlarmClock) get a temporary background-start
            // allowlist when they fire, which holds for the duration of this broadcast (goAsync),
            // so starting the service after the quick DB read above is still allowed.
            AlarmService.start(context, reminder.id, title, message, notifId, eventTypeId, reminder.snoozeMinutes)
            // setFullScreenIntent only auto-launches the activity when the screen is locked/off; while
            // the device is unlocked the system shows a heads-up notification instead and waits for a
            // tap. Launch the activity directly so the full-screen alarm appears in BOTH states.
            // AlarmDismissActivity is singleInstance, so this and any full-screen-intent launch
            // collapse into one instance.
            try {
                context.startActivity(
                    AlarmDismissActivity.createIntent(context, reminder.id, title, message, notifId, eventTypeId, reminder.snoozeMinutes)
                )
            } catch (_: Exception) {
                // Background-activity-launch blocked (rare): the full-screen-intent notification is the fallback.
            }
        } else {
            NotificationHelper.showReminderNotification(context, notifId, title, message, reminder.id, eventTypeId, reminder.snoozeMinutes)
        }
    }

    private fun handleSnooze(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L).takeIf { it != -1L } ?: return
        AlarmService.stop(context)   // stop alarm audio + remove the foreground-service notification (no-op for non-alarm)
        NotificationHelper.cancelNotification(context, reminderId.toInt())
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // The snooze duration is read from the DB row inside the coordinator, so a stale
                // intent extra can never override the user's setting.
                reminderCoordinator.snooze(reminderId)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleDismiss(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L).takeIf { it != -1L } ?: return
        AlarmService.stop(context)   // stop alarm audio + remove the foreground-service notification (no-op for non-alarm)
        NotificationHelper.cancelNotification(context, reminderId.toInt())
    }

    private fun rescheduleAll(clockChanged: Boolean) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                reminderCoordinator.rescheduleAll(clockChanged)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.lifelog.app.ACTION_REMINDER"
        const val ACTION_SNOOZE   = "com.lifelog.app.ACTION_SNOOZE"
        const val ACTION_DISMISS  = "com.lifelog.app.ACTION_DISMISS"
        /** Re-arm OS alarms for all active reminders, e.g. after a database restore. */
        const val ACTION_RESCHEDULE_ALL = "com.lifelog.app.ACTION_RESCHEDULE_ALL"

        const val EXTRA_REMINDER_ID   = "reminder_id"
        const val EXTRA_TITLE         = "title"
        const val EXTRA_MESSAGE       = "message"
        const val EXTRA_EVENT_TYPE_ID = "event_type_id"
        const val EXTRA_IS_ALARM      = "is_alarm"

        /**
         * The bare component + action that define a scheduled alarm's PendingIntent identity.
         * Intent.filterEquals() — which matches PendingIntents for update/cancel — compares the
         * action and component but ignores extras, so [schedule][ReminderScheduler.schedule] and
         * [cancel][ReminderScheduler.cancel] MUST build from this same base or cancellation silently
         * fails. Defined once, here, so the two can never drift apart again.
         */
        fun alarmIntent(context: Context): Intent =
            Intent(context, ReminderReceiver::class.java).apply { action = ACTION_REMINDER }

        fun buildIntent(context: Context, reminder: Reminder): Intent =
            alarmIntent(context).apply {
                putExtra(EXTRA_REMINDER_ID, reminder.id)
                putExtra(EXTRA_TITLE, reminder.title)
                putExtra(EXTRA_MESSAGE, reminder.message)
                putExtra(EXTRA_EVENT_TYPE_ID, reminder.eventTypeId ?: -1L)
                putExtra(EXTRA_IS_ALARM, reminder.deliveryType == DeliveryType.ALARM)
            }
    }
}
