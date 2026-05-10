package com.lifelog.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lifelog.app.MainActivity
import com.lifelog.app.R

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
        reminderId: Long
    ) {
        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message.ifBlank { "Tap to open LifeLog" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.ifBlank { "Tap to open LifeLog" }))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted - silently skip
        }
    }
}
