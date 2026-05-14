package com.lifelog.app.notifications

import android.app.KeyguardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifelog.app.data.repository.UserPreferences
import com.lifelog.app.data.repository.UserPreferencesRepository
import com.lifelog.app.ui.theme.LifeLogTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class AlarmDismissActivity : ComponentActivity() {

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn on display
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(KeyguardManager::class.java)
            km.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, -1L)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "LifeLog Alarm"
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, reminderId.toInt())

        setContent {
            val prefs by userPreferencesRepository.userPreferences.collectAsState(UserPreferences())
            LifeLogTheme(amoledBlack = prefs.useAmoledBlack, dynamicColor = prefs.useDynamicColor) {
                AlarmScreen(
                    title = title,
                    message = message,
                    onDismiss = {
                        NotificationHelper.cancelNotification(this@AlarmDismissActivity, notificationId)
                        finish()
                    },
                    onSnooze = {
                        NotificationHelper.cancelNotification(this@AlarmDismissActivity, notificationId)
                        // The BroadcastReceiver handles the actual snooze rescheduling
                        sendBroadcast(
                            android.content.Intent(this@AlarmDismissActivity, ReminderReceiver::class.java).apply {
                                action = ReminderReceiver.ACTION_SNOOZE
                                putExtra(ReminderReceiver.EXTRA_REMINDER_ID, reminderId)
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}

@Composable
private fun AlarmScreen(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val time = remember {
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(48.dp))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Rounded.Alarm,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    time,
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Light
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                if (message.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onSnooze,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.Snooze, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Snooze ${ReminderReceiver.SNOOZE_MINUTES}m")
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Rounded.AlarmOff, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Dismiss")
                }
            }
        }
    }
}
