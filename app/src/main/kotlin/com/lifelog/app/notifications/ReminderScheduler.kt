package com.lifelog.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.lifelog.app.domain.model.Reminder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    private val context: Context
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(reminder: Reminder) {
        if (!reminder.isActive) return

        val intent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            ReminderReceiver.buildIntent(context, reminder),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = reminder.nextTriggerAt.coerceAtLeast(System.currentTimeMillis() + 1_000)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            } else {
                // Inexact fallback — within a 10-min window
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    10 * 60 * 1_000L,
                    intent
                )
            }
        } catch (e: SecurityException) {
            // Fallback when exact alarm permission not granted
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, intent)
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
