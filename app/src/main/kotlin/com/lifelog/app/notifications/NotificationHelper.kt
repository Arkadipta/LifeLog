package com.lifelog.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lifelog.app.MainActivity
import com.lifelog.app.R
import com.lifelog.app.widget.QuickAddActivity

object NotificationHelper {

    const val REMINDER_CHANNEL_ID = "lifelog_reminders"
    const val ALARM_CHANNEL_ID = "lifelog_alarms"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)

        val reminderChannel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Scheduled reminders for LifeLog events"
            enableVibration(true)
        }

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm-style reminders for LifeLog events"
            enableVibration(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setSound(
                alarmUri,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }

        nm.createNotificationChannel(reminderChannel)
        nm.createNotificationChannel(alarmChannel)
    }

    fun showReminderNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        reminderId: Long,
        eventTypeId: Long? = null
    ) {
        val builder = buildBaseNotification(context, notificationId, title, message, reminderId, REMINDER_CHANNEL_ID)

        if (eventTypeId != null) {
            builder.addAction(0, "Add Entry", buildAddEntryIntent(context, notificationId, eventTypeId))
        }

        tryNotify(context, notificationId, builder)
    }

    fun showAlarmNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        reminderId: Long,
        eventTypeId: Long? = null
    ) {
        val fullScreenIntent = PendingIntent.getActivity(
            context,
            notificationId * 10 + 4,
            Intent(context, AlarmDismissActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                putExtra(AlarmDismissActivity.EXTRA_REMINDER_ID, reminderId)
                putExtra(AlarmDismissActivity.EXTRA_TITLE, title)
                putExtra(AlarmDismissActivity.EXTRA_MESSAGE, message)
                putExtra(AlarmDismissActivity.EXTRA_NOTIFICATION_ID, notificationId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = buildBaseNotification(context, notificationId, title, message, reminderId, ALARM_CHANNEL_ID)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenIntent, true)
            .setAutoCancel(false)   // user must interact with alarm screen
            .setOngoing(true)

        if (eventTypeId != null) {
            builder.addAction(0, "Add Entry", buildAddEntryIntent(context, notificationId, eventTypeId))
        }

        tryNotify(context, notificationId, builder)
    }

    private fun buildBaseNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        reminderId: Long,
        channelId: String
    ): NotificationCompat.Builder {
        val tapPendingIntent = PendingIntent.getActivity(
            context, notificationId,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissPendingIntent = PendingIntent.getBroadcast(
            context, notificationId * 10 + 1,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_DISMISS
                putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, notificationId * 10 + 2,
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_SNOOZE
                putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message.ifBlank { "Tap to open LifeLog" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.ifBlank { "Tap to open LifeLog" }))
            .setContentIntent(tapPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Dismiss", dismissPendingIntent)
            .addAction(0, "Snooze ${ReminderReceiver.SNOOZE_MINUTES}m", snoozePendingIntent)
    }

    private fun buildAddEntryIntent(context: Context, notificationId: Int, eventTypeId: Long): PendingIntent =
        PendingIntent.getActivity(
            context, notificationId * 10 + 3,
            Intent(context, QuickAddActivity::class.java).apply {
                putExtra(QuickAddActivity.EXTRA_EVENT_ID, eventTypeId)
                putExtra(QuickAddActivity.EXTRA_NOTIFICATION_ID, notificationId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun tryNotify(context: Context, id: Int, builder: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted
        }
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }
}
