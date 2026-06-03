package com.lifelog.app.notifications

import android.app.KeyguardManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifelog.app.data.repository.UserPreferences
import com.lifelog.app.data.repository.UserPreferencesRepository
import com.lifelog.app.ui.theme.LifeLogTheme
import com.lifelog.app.widget.QuickAddActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class AlarmDismissActivity : ComponentActivity() {

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    private val viewModel: AlarmDismissViewModel by viewModels()

    // MediaPlayer is held in the Activity (not ViewModel) because it wraps a Context-bound resource.
    // android:configChanges in the manifest prevents recreation on orientation/screen-size changes,
    // so onDestroy (and thus release()) is only called when the user explicitly leaves the screen.
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeScreen()

        val reminderId    = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val title         = intent.getStringExtra(EXTRA_TITLE) ?: "LifeLog Alarm"
        val message       = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, reminderId.toInt())
        val eventTypeId   = intent.getLongExtra(EXTRA_EVENT_TYPE_ID, -1L).takeIf { it != -1L }

        viewModel.loadNextTrigger(reminderId)

        // Prevent the back gesture from bypassing the alarm screen; the user must tap an action.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        startAlarmAudio()

        setContent {
            val prefs      by userPreferencesRepository.userPreferences.collectAsState(UserPreferences())
            val nextTrigger by viewModel.nextTriggerAt.collectAsState()

            LifeLogTheme(amoledBlack = prefs.useAmoledBlack, dynamicColor = prefs.useDynamicColor) {
                AlarmScreen(
                    title        = title,
                    message      = message,
                    nextTriggerAt = nextTrigger,
                    onDismiss = {
                        stopAlarmAudio()
                        NotificationHelper.cancelNotification(this@AlarmDismissActivity, notificationId)
                        finish()
                    },
                    onSnooze = {
                        stopAlarmAudio()
                        NotificationHelper.cancelNotification(this@AlarmDismissActivity, notificationId)
                        // Delegate rescheduling to ReminderReceiver, which owns the snooze logic
                        sendBroadcast(Intent(this@AlarmDismissActivity, ReminderReceiver::class.java).apply {
                            action = ReminderReceiver.ACTION_SNOOZE
                            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
                        })
                        finish()
                    },
                    onAddEntry = eventTypeId?.let { etId ->
                        {
                            stopAlarmAudio()
                            NotificationHelper.cancelNotification(this@AlarmDismissActivity, notificationId)
                            startActivity(Intent(this@AlarmDismissActivity, QuickAddActivity::class.java).apply {
                                putExtra(QuickAddActivity.EXTRA_EVENT_ID, etId)
                                putExtra(QuickAddActivity.EXTRA_NOTIFICATION_ID, notificationId)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            finish()
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarmAudio()
    }

    // ── Wake / lock-screen helpers ─────────────────────────────────────────────

    private fun wakeScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            // Recommended APIs for API 27+: no deprecated window flags needed
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // Attempt to dismiss the keyguard so user can interact directly.
            // On secured lock screens this is a no-op; the activity still shows over the keyguard.
            getSystemService(KeyguardManager::class.java)
                ?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // FLAG_KEEP_SCREEN_ON works on all API levels and keeps the display on
        // while the alarm is visible without requiring WAKE_LOCK permission.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ── Audio helpers ──────────────────────────────────────────────────────────

    private fun startAlarmAudio() {
        if (mediaPlayer != null) return
        try {
            // Fall back to ringtone if no alarm sound is configured
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return

            mediaPlayer = MediaPlayer().apply {
                // USAGE_ALARM bypasses the ringer/silent mode switch, matching system alarm behavior
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmDismissActivity, uri)
                isLooping = true
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (_: Exception) {
            // Silent failure: unavailable in restricted environments (e.g. tests, no alarm sound set)
        }
    }

    private fun stopAlarmAudio() {
        mediaPlayer?.let { mp ->
            try { mp.stop() } catch (_: Exception) {}
            mp.release()
        }
        mediaPlayer = null
    }

    companion object {
        const val EXTRA_REMINDER_ID    = "reminder_id"
        const val EXTRA_TITLE          = "title"
        const val EXTRA_MESSAGE        = "message"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_EVENT_TYPE_ID  = "event_type_id"
    }
}

// ── Full-screen alarm UI ───────────────────────────────────────────────────────

@Composable
private fun AlarmScreen(
    title: String,
    message: String,
    nextTriggerAt: Long?,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
    onAddEntry: (() -> Unit)?
) {
    // Update the displayed time every second
    val currentTime by produceState(
        initialValue = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
    ) {
        while (true) {
            delay(1_000)
            value = SimpleDateFormat("h:mm", Locale.getDefault()).format(Date())
        }
    }
    val amPm = SimpleDateFormat("a", Locale.getDefault()).format(Date())

    // Gentle pulse on the alarm icon to convey urgency without being jarring
    val infiniteTransition = rememberInfiniteTransition(label = "alarm_pulse")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale"
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ── Top: identity ──────────────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(80.dp)
                        .scale(iconScale)
                )

                // Large clock
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = currentTime,
                        style = MaterialTheme.typography.displayLarge,
                        fontWeight = FontWeight.Light
                    )
                    Text(
                        text = amPm,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // Event / reminder name
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                if (message.isNotBlank()) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                if (nextTriggerAt != null) {
                    val nextLabel = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())
                        .format(Date(nextTriggerAt))
                    Text(
                        text = "Next: $nextLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Bottom: actions ────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onAddEntry != null) {
                    Button(
                        onClick = onAddEntry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Add Entry",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onSnooze,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Icon(Icons.Rounded.Snooze, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Snooze ${ReminderReceiver.SNOOZE_MINUTES}m")
                    }

                    FilledTonalButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Rounded.AlarmOff, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}
