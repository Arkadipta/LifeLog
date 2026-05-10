package com.lifelog.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lifelog.app.MainActivity
import com.lifelog.app.R
import com.lifelog.app.widget.QuickAddActivity

object NotificationHelper {

    const val REMINDER_CHANNEL_ID = "lifelog_reminders"
    private const val REMINDER_CHANNEL_NAME = "Reminders"
    private const val REMINDER_CHANNEL_DESC = "Scheduled reminders for LifeLog events"

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            REMINDER_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = REMINDER_CHANNEL_DESC
            enableVibration(true)
        }
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    fun showReminderNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        reminderId: Long,
        eventTypeId: Long? = null
    ) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val tapPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 1,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_DISMISS
                putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 10 + 2,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_SNOOZE
                putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message.ifBlank { "Tap to open LifeLog" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.ifBlank { "Tap to open LifeLog" }))
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .addAction(0, "Snooze ${ReminderReceiver.SNOOZE_MINUTES}m", snoozePendingIntent)

        if (eventTypeId != null) {
            val addEntryPendingIntent = PendingIntent.getActivity(
                context,
                notificationId * 10 + 3,
                Intent(context, QuickAddActivity::class.java).apply {
                    putExtra(QuickAddActivity.EXTRA_EVENT_ID, eventTypeId)
                    putExtra(QuickAddActivity.EXTRA_NOTIFICATION_ID, notificationId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Add Entry", addEntryPendingIntent)
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — user will see no notification
        }
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
