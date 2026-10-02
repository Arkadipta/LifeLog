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
 * notifications of every alarm currently ringing.
 *
 * Why a service instead of keeping the MediaPlayer in [AlarmDismissActivity]:
 *  - Audio keeps ringing if the user backgrounds the alarm screen (e.g. presses Home); an Activity
 *    that loses focus can be stopped/killed, taking its player with it.
 *  - The ongoing notification (required by every foreground service) doubles as the recovery path
 *    back to the alarm screen, and its Dismiss/Snooze actions work even when no Activity is showing.
 *  - A single owner of the player guarantees one audio source — no "reverb".
 *
 * **Alarms overlap**, so this service is shared by however many are unanswered at once: it holds
 * them in [RingingAlarms], rings one sound for all of them, and stops only once the last one is
 * answered. Each has its own notification (see [showNotification]) and is dismissed by id
 * ([stop]), because a reminder the user never answered must not be silenced by another one's
 * Dismiss button.
 *
 * Started from [ReminderReceiver] while it handles the exact-alarm broadcast. Exact alarms
 * (setAlarmClock) are exempt from the background foreground-service-start restriction on Android
 * 12+, so this start is allowed.
 */
class AlarmService : Service() {

    private val ringing = RingingAlarms()
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val autoStop = Handler(Looper.getMainLooper())
    private val autoStops = mutableMapOf<Long, Runnable>()

    /** Notification id the service is currently in the foreground for; null until it enters it. */
    private var foregroundNotificationId: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_NOT_STICKY shouldn't recreate us with a null intent, but guard anyway.
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startAlarm(intent.toRingingAlarm())
        // Don't resurrect a killed alarm with stale state — a re-fire comes from a fresh broadcast.
        return START_NOT_STICKY
    }

    private fun startAlarm(alarm: RingingAlarm) {
        ringing.add(alarm)
        showNotification(alarm)
        acquireWakeLock()
        startAudio()
        scheduleAutoStop(alarm)
    }

    /**
     * Shows [alarm]. The [front][RingingAlarms.front] alarm's notification is the service's
     * foreground notification; every other one posts its own. Both carry the same full-screen
     * intent and the same Dismiss/Snooze actions, so they look and behave alike — the split
     * exists because a service has exactly one foreground notification, and handing that slot to
     * a second alarm cancels the first's.
     */
    private fun showNotification(alarm: RingingAlarm) {
        if (ringing.front?.reminderId == alarm.reminderId) {
            enterForeground(alarm)
        } else {
            NotificationHelper.showAlarmNotification(this, alarm)
        }
    }

    /**
     * Puts [alarm]'s notification in the foreground slot. Re-designating removes the notification
     * that held it before, so this is also how an answered front alarm's notification goes away.
     */
    private fun enterForeground(alarm: RingingAlarm) {
        // specialUse is the catch-all FGS type for use cases (an alarm) not covered by a dedicated
        // type; it must match the manifest.
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            alarm.notificationId,
            NotificationHelper.buildAlarmNotification(this, alarm),
            serviceType
        )
        foregroundNotificationId = alarm.notificationId
    }

    /**
     * One alarm was answered (Dismiss / Snooze / Add Entry) or rang itself out. Anything else
     * still ringing keeps its audio and its notification; the service stops only when the last
     * one goes.
     */
    private fun stopAlarm(reminderId: Long) {
        val answered = ringing.remove(reminderId) ?: return
        autoStops.remove(reminderId)?.let(autoStop::removeCallbacks)

        val front = ringing.front
        when {
            // Nothing left: onDestroy stops the audio and takes the foreground notification with it.
            front == null -> stopSelf()
            // The answered alarm held the foreground slot — handing it on removes its notification.
            answered.notificationId == foregroundNotificationId -> enterForeground(front)
            else -> NotificationHelper.cancelNotification(this, answered.notificationId)
        }
    }

    /**
     * Safety net: never ring forever. Mirrors how system alarms auto-silence; a forgotten alarm
     * must not hold a wake lock / drain the battery indefinitely. Per alarm, so one that comes due
     * late gets its own full window instead of inheriting what is left of an earlier one's.
     */
    private fun scheduleAutoStop(alarm: RingingAlarm) {
        autoStops.remove(alarm.reminderId)?.let(autoStop::removeCallbacks)
        val silence = Runnable { stopAlarm(alarm.reminderId) }
        autoStops[alarm.reminderId] = silence
        autoStop.postDelayed(silence, MAX_RING_MS)
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
        val pm = getSystemService(PowerManager::class.java) ?: return
        // PARTIAL_WAKE_LOCK keeps the CPU running so audio continues if the screen turns off while
        // ringing (a foreground service alone does not prevent the CPU from sleeping). The timeout
        // is a backstop in case the alarm is somehow never stopped; acquiring again on the existing
        // (non-reference-counted) lock re-arms that timeout rather than nesting, so a late alarm is
        // never cut short by an earlier one's deadline.
        val lock = wakeLock ?: pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG)
            .also { it.setReferenceCounted(false); wakeLock = it }
        lock.acquire(MAX_RING_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        // Reached via stopSelf() once the last alarm is answered or rings out. Tear everything down
        // here so cleanup happens regardless of how the service was stopped.
        instance = null
        autoStop.removeCallbacksAndMessages(null)
        autoStops.clear()
        // stopForeground below removes the foreground notification only. Anything else still
        // ringing — a stop that wasn't an answer, e.g. the system tearing the service down —
        // would otherwise leave an ongoing notification behind with nothing to dismiss it.
        ringing.all.forEach { alarm ->
            if (alarm.notificationId != foregroundNotificationId) {
                NotificationHelper.cancelNotification(this, alarm.notificationId)
            }
        }
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
        private const val WAKELOCK_TAG = "lifelog:alarm"

        // Auto-silence after 10 minutes. Tune/remove if the alarm should ring indefinitely.
        private const val MAX_RING_MS = 10 * 60 * 1000L

        /**
         * The live service, or null while nothing is ringing.
         *
         * Answering one of several ringing alarms has to reach the service that holds them all,
         * and must never *start* it — a command Intent would, and a started-service command from
         * a notification-action broadcast is the background start that stopService() was chosen
         * to sidestep in the first place. Everything that talks to the service ([ReminderReceiver],
         * [AlarmDismissActivity]) lives in this same process and calls from the main thread, where
         * onCreate/onDestroy also run, so this is a plain main-thread hand-off and not shared state.
         */
        private var instance: AlarmService? = null

        /**
         * The alarm currently at the front of the ringing queue, or null if none is — what
         * [AlarmDismissActivity] shows after the user answers the one on screen.
         */
        fun ringingNow(): RingingAlarm? = instance?.ringing?.front

        fun start(context: Context, alarm: RingingAlarm) {
            val intent = alarm.putInto(Intent(context, AlarmService::class.java))
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // Background FGS start disallowed (rare for exact alarms). Fall back to a plain
                // notification so the full-screen UI / lock-screen path still works — without looping
                // audio, since nothing owns a player in this path.
                NotificationHelper.showAlarmNotification(context, alarm)
            }
        }

        /**
         * Stops [reminderId] ringing: its notification goes, and the audio + wake lock with it if
         * it was the last one ringing.
         *
         * Per reminder, not a blanket stop: a Dismiss belongs to the alarm it was posted for, and
         * stopping the service outright silenced every other alarm the user hadn't answered yet.
         * A no-op when that reminder isn't ringing — including when nothing is, which is the
         * non-alarm reminder case where the service was never started.
         */
        fun stop(reminderId: Long) {
            instance?.stopAlarm(reminderId)
        }
    }
}
