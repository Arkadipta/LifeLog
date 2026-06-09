package com.lifelog.app.notifications

import android.app.Notification
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

    // Bumped to _v2 deliberately. The original "lifelog_alarms" channel was created (commit 45aeb51)
    // with an alarm sound via setSound(). NotificationChannels are IMMUTABLE once created, so when
    // the setSound() call was later removed (commit 3f335dd) the channel kept its sound on every
    // device that already had it — SystemUI's RingtonePlayer kept playing it as a second audio
    // source alongside AlarmDismissActivity's MediaPlayer (the "reverb"). A new ID forces a fresh,
    // silent channel; the old one is deleted in createChannels().
    const val ALARM_CHANNEL_ID = "lifelog_alarms_v2"
    private const val LEGACY_ALARM_CHANNEL_ID = "lifelog_alarms"

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

        // Delete the legacy alarm channel, which was created with an alarm sound baked in. Channels
        // are immutable, so delete-and-recreate (under a new ID) is the only way to guarantee a
        // silent channel on devices that already had the old one.
        nm.deleteNotificationChannel(LEGACY_ALARM_CHANNEL_ID)

        // Alarm channel is intentionally SILENT. AlarmDismissActivity owns the single audio source
        // (a looping MediaPlayer) so it can be stopped on dismiss/snooze without cancelling the
        // notification. setSound(null, null) is REQUIRED: a channel created without it defaults to
        // the notification sound, which SystemUI would then play as a duplicate source.
        val alarmChannel = NotificationChannel(
            ALARM_CHANNEL_ID,
            "Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alarm-style reminders for LifeLog events"
            enableVibration(true)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            setSound(null, null)
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

    /**
     * Builds the alarm notification (silent channel + full-screen intent). Returned rather than
     * posted because [AlarmService] passes it to startForeground() — the foreground service owns the
     * ongoing notification and the single audio source. The full-screen intent handles the lock
     * screen; the content intent returns the user to the alarm screen if they leave it.
     */
    fun buildAlarmNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        reminderId: Long,
        eventTypeId: Long? = null
    ): Notification {
        // Same Intent that ReminderReceiver launches directly — see AlarmDismissActivity.createIntent.
        // Used for both the full-screen intent (lock screen) and the content tap (recovery).
        val alarmPendingIntent = PendingIntent.getActivity(
            context,
            notificationId * 10 + 4,
            AlarmDismissActivity.createIntent(context, reminderId, title, message, notificationId, eventTypeId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = buildBaseNotification(context, notificationId, title, message, reminderId, ALARM_CHANNEL_ID)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(alarmPendingIntent, true)
            .setContentIntent(alarmPendingIntent)   // tap the (recovery) notification → back to alarm
            .setAutoCancel(false)                    // user must interact with the alarm screen
            .setOngoing(true)

        if (eventTypeId != null) {
            builder.addAction(0, "Add Entry", buildAddEntryIntent(context, notificationId, eventTypeId))
        }

        return builder.build()
    }

    /**
     * Fallback only, used if [AlarmService] cannot start (e.g. a background FGS start is ever
     * disallowed): posts the alarm notification directly. There is no looping audio in this path —
     * the full-screen UI still appears via the receiver's startActivity / the full-screen intent.
     */
    fun showAlarmNotification(
        context: Context,
        notificationId: Int,
        title: String,
        message: String,
        reminderId: Long,
        eventTypeId: Long? = null
    ) {
        try {
            NotificationManagerCompat.from(context).notify(
                notificationId,
                buildAlarmNotification(context, notificationId, title, message, reminderId, eventTypeId)
            )
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted
        }
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
