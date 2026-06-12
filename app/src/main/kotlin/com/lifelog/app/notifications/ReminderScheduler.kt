package com.lifelog.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import com.lifelog.app.domain.model.DeliveryType
import com.lifelog.app.domain.model.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: Reminder) {
        if (!reminder.isActive) return

        val receiverIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            ReminderReceiver.buildIntent(context, reminder),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = reminder.nextTriggerAt.coerceAtLeast(System.currentTimeMillis() + 1_000)

        if (reminder.deliveryType == DeliveryType.ALARM) {
            scheduleAlarmClock(triggerAt, receiverIntent)
        } else {
            scheduleExact(triggerAt, receiverIntent)
        }
    }

    private fun scheduleAlarmClock(triggerAt: Long, intent: PendingIntent) {
        // setAlarmClock shows an alarm icon in the status bar and fires reliably in Doze
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, intent), intent)
    }

    private fun scheduleExact(triggerAt: Long, intent: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            }
        } catch (e: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
        }
    }

    fun cancel(reminderId: Long) {
        // Build from the same component + action base as schedule() so the PendingIntents match.
        // Intent.filterEquals() (used to match PendingIntents) compares the action and ignores
        // extras; the old actionless cancel intent never matched our ACTION_REMINDER alarm, so
        // cancel was a silent no-op that left the OS-level alarm armed after a reminder was deleted
        // or disabled. Skip FLAG_NO_CREATE so cancellation works even if the token isn't cached in
        // this process (e.g. after a restart); alarmManager.cancel() matches by identity, not object.
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            ReminderReceiver.alarmIntent(context),
            PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    fun rescheduleAll(reminders: List<Reminder>) {
        reminders.filter { it.isActive }.forEach { schedule(it) }
    }
}
