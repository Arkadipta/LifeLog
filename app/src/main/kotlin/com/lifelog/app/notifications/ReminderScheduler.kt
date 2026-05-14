package com.lifelog.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
        val intent = PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        intent?.let { alarmManager.cancel(it) }
    }

    fun rescheduleAll(reminders: List<Reminder>) {
        reminders.filter { it.isActive }.forEach { schedule(it) }
    }
}
