package com.lifelog.app.notifications

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifelog.app.data.repository.UserPreferences
import com.lifelog.app.data.repository.UserPreferencesRepository
import com.lifelog.app.domain.model.Reminder
import com.lifelog.app.ui.theme.LifeLogTheme
import com.lifelog.app.util.snoozeShortLabel
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

    // This Activity is pure UI. The alarm audio + ongoing notification are owned by AlarmService, so
    // the alarm keeps ringing even if this screen is backgrounded; the buttons just tell the service
    // to stop. Nothing audio-related lives here anymore.

    // singleInstance means a second alarm arrives here via onNewIntent rather than a fresh onCreate,
    // so the extras must live in observable state rather than local onCreate vals — otherwise the
    // screen keeps showing the first alarm's title while Snooze/Add Entry act on its reminderId.
    private lateinit var extras: MutableState<AlarmExtras>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wakeScreen()

        extras = mutableStateOf(intent.toAlarmExtras())
        viewModel.loadNextTrigger(extras.value.reminderId)

        // Prevent the back gesture from bypassing the alarm screen; the user must tap an action.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            // Unlike the other screens this one cannot wait for the stored theme — it is an
            // opaque full-screen window the user is already looking at — so it takes the
            // eagerly read value if it has landed (it has, unless the alarm started this
            // process moments ago) and defaults otherwise.
            val prefs      by userPreferencesRepository.loaded.collectAsState()
            val theme      = prefs ?: UserPreferences()
            val nextTrigger by viewModel.nextTriggerAt.collectAsState()
            val current    by extras

            LifeLogTheme(amoledBlack = theme.useAmoledBlack, dynamicColor = theme.useDynamicColor) {
                AlarmScreen(
                    title        = current.title,
                    message      = current.message,
                    nextTriggerAt = nextTrigger,
                    snoozeMinutes = current.snoozeMinutes,
                    onDismiss = {
                        AlarmService.stop(this@AlarmDismissActivity)
                        finish()
                    },
                    onSnooze = {
                        // Stop now for an instant audio cut; ReminderReceiver (which owns the snooze
                        // logic) also stops the service and reschedules — stop() is idempotent.
                        AlarmService.stop(this@AlarmDismissActivity)
                        sendBroadcast(Intent(this@AlarmDismissActivity, ReminderReceiver::class.java).apply {
                            action = ReminderReceiver.ACTION_SNOOZE
                            putExtra(ReminderReceiver.EXTRA_REMINDER_ID, current.reminderId)
                        })
                        finish()
                    },
                    onAddEntry = current.eventTypeId?.let { etId ->
                        {
                            AlarmService.stop(this@AlarmDismissActivity)
                            startActivity(Intent(this@AlarmDismissActivity, QuickAddActivity::class.java).apply {
                                putExtra(QuickAddActivity.EXTRA_EVENT_ID, etId)
                                putExtra(QuickAddActivity.EXTRA_NOTIFICATION_ID, current.notificationId)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            finish()
                        }
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extras.value = intent.toAlarmExtras()
        viewModel.loadNextTrigger(extras.value.reminderId)
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

    companion object {
        const val EXTRA_REMINDER_ID    = "reminder_id"
        const val EXTRA_TITLE          = "title"
        const val EXTRA_MESSAGE        = "message"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_EVENT_TYPE_ID  = "event_type_id"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"

        /**
         * Single source of truth for the launch Intent, shared by the full-screen-intent
         * PendingIntent (NotificationHelper, lock-screen path) and ReminderReceiver's direct
         * startActivity (unlocked path). FLAG_ACTIVITY_NEW_TASK is mandatory when launching an
         * Activity from a non-Activity context; FLAG_ACTIVITY_NO_USER_ACTION suppresses
         * onUserLeaveHint so the alarm isn't treated as a user-initiated app switch.
         */
        fun createIntent(
            context: Context,
            reminderId: Long,
            title: String,
            message: String,
            notificationId: Int,
            eventTypeId: Long?,
            snoozeMinutes: Int = Reminder.DEFAULT_SNOOZE_MINUTES
        ): Intent = Intent(context, AlarmDismissActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(EXTRA_EVENT_TYPE_ID, eventTypeId ?: -1L)
            putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
        }
    }
}

private data class AlarmExtras(
    val reminderId: Long,
    val title: String,
    val message: String,
    val notificationId: Int,
    val eventTypeId: Long?,
    val snoozeMinutes: Int
)

private fun Intent.toAlarmExtras(): AlarmExtras {
    val reminderId = getLongExtra(AlarmDismissActivity.EXTRA_REMINDER_ID, -1L)
    return AlarmExtras(
        reminderId = reminderId,
        title = getStringExtra(AlarmDismissActivity.EXTRA_TITLE) ?: "LifeLog Alarm",
        message = getStringExtra(AlarmDismissActivity.EXTRA_MESSAGE) ?: "",
        notificationId = getIntExtra(AlarmDismissActivity.EXTRA_NOTIFICATION_ID, reminderId.toInt()),
        eventTypeId = getLongExtra(AlarmDismissActivity.EXTRA_EVENT_TYPE_ID, -1L).takeIf { it != -1L },
        snoozeMinutes = getIntExtra(AlarmDismissActivity.EXTRA_SNOOZE_MINUTES, Reminder.DEFAULT_SNOOZE_MINUTES)
    )
}

// ── Full-screen alarm UI ───────────────────────────────────────────────────────

@Composable
private fun AlarmScreen(
    title: String,
    message: String,
    nextTriggerAt: Long?,
    snoozeMinutes: Int,
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

                Spacer(Modifier.height(8.dp))

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
                    FilledTonalButton(
                        onClick = onSnooze,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Icon(Icons.Rounded.Snooze, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Snooze ${snoozeShortLabel(snoozeMinutes)}")
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
