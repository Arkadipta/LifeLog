package com.lifelog.app.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that owns the alarm's SINGLE audio source (a looping [MediaPlayer]) and the
 * ongoing full-screen-intent notification.
 *
 * Why a service instead of keeping the MediaPlayer in [AlarmDismissActivity]:
 *  - Audio keeps ringing if the user backgrounds the alarm screen (e.g. presses Home); an Activity
 *    that loses focus can be stopped/killed, taking its player with it.
 *  - The ongoing notification (required by every foreground service) doubles as the recovery path
 *    back to the alarm screen, and its Dismiss/Snooze actions work even when no Activity is showing.
 *  - A single owner of the player guarantees one audio source — no "reverb".
 *
 * Started from [ReminderReceiver] while it handles the exact-alarm broadcast. Exact alarms
 * (setAlarmClock) are exempt from the background foreground-service-start restriction on Android
 * 12+, so this start is allowed.
 */
class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val autoStop = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_NOT_STICKY shouldn't recreate us with a null intent, but guard anyway.
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAlarm(intent)
        // Don't resurrect a killed alarm with stale state — a re-fire comes from a fresh broadcast.
        return START_NOT_STICKY
    }

    private fun startAlarm(intent: Intent) {
        val reminderId     = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val title          = intent.getStringExtra(EXTRA_TITLE) ?: "LifeLog Alarm"
        val message        = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, reminderId.toInt())
        val eventTypeId    = intent.getLongExtra(EXTRA_EVENT_TYPE_ID, -1L).takeIf { it != -1L }

        // Enter the foreground with the ongoing alarm notification. specialUse is the catch-all FGS
        // type for use cases (an alarm) not covered by a dedicated type; it must match the manifest.
        val notification = NotificationHelper.buildAlarmNotification(
            this, notificationId, title, message, reminderId, eventTypeId
        )
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, notificationId, notification, serviceType)

        acquireWakeLock()
        startAudio()

        // Safety net: never ring forever. Mirrors how system alarms auto-silence; a forgotten alarm
        // must not hold a wake lock / drain the battery indefinitely.
        autoStop.removeCallbacks(autoStopRunnable)
        autoStop.postDelayed(autoStopRunnable, MAX_RING_MS)
    }

    private fun startAudio() {
        if (mediaPlayer != null) return
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return

            mediaPlayer = MediaPlayer().apply {
                // USAGE_ALARM bypasses the ringer/silent-mode switch, matching system alarm behavior.
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (_: Exception) {
            // Silent failure: unavailable in restricted environments (e.g. no alarm sound configured).
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        // PARTIAL_WAKE_LOCK keeps the CPU running so audio continues if the screen turns off while
        // ringing (a foreground service alone does not prevent the CPU from sleeping). The timeout
        // is a backstop in case stop() is somehow never called.
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(MAX_RING_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        // Reached via stopService() (Dismiss/Snooze) or stopSelf() (auto-stop). Tear everything down
        // here so cleanup happens regardless of how the service was stopped.
        autoStop.removeCallbacks(autoStopRunnable)
        mediaPlayer?.let { mp ->
            try { mp.stop() } catch (_: Exception) {}
            mp.release()
        }
        mediaPlayer = null
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_REMINDER_ID     = "reminder_id"
        const val EXTRA_TITLE           = "title"
        const val EXTRA_MESSAGE         = "message"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_EVENT_TYPE_ID   = "event_type_id"

        private const val WAKELOCK_TAG = "lifelog:alarm"

        // Auto-silence after 10 minutes. Tune/remove if the alarm should ring indefinitely.
        private const val MAX_RING_MS = 10 * 60 * 1000L

        fun start(
            context: Context,
            reminderId: Long,
            title: String,
            message: String,
            notificationId: Int,
            eventTypeId: Long?
        ) {
            val intent = Intent(context, AlarmService::class.java).apply {
                putExtra(EXTRA_REMINDER_ID, reminderId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                putExtra(EXTRA_EVENT_TYPE_ID, eventTypeId ?: -1L)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // Background FGS start disallowed (rare for exact alarms). Fall back to a plain
                // notification so the full-screen UI / lock-screen path still works — without looping
                // audio, since nothing owns a player in this path.
                NotificationHelper.showAlarmNotification(
                    context, notificationId, title, message, reminderId, eventTypeId
                )
            }
        }

        /**
         * Stops the alarm: tears down audio + wake lock and removes the notification (via onDestroy).
         * Uses stopService (not a started ACTION) because stopService is exempt from the background
         * start restriction, so it works from a notification-action broadcast too. No-op if the
         * service isn't running.
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }
}
