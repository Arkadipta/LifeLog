package com.lifelog.app.widget

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import com.lifelog.app.MainActivity
import com.lifelog.app.data.repository.UserPreferences
import com.lifelog.app.data.repository.UserPreferencesRepository
import com.lifelog.app.ui.events.EntryFormMode
import com.lifelog.app.ui.events.EntryFormSheet
import com.lifelog.app.ui.theme.LifeLogTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, 0L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (eventId == 0L) {
            // A widget whose event was deleted (its state is cleared to id 0) or a
            // malformed intent — nothing can be logged, so say why instead of a
            // silent no-op tap.
            finishMissingEvent(notificationId)
            return
        }

        setContent {
            val prefs by userPreferencesRepository.userPreferences.collectAsState(
                initial = UserPreferences()
            )
            LifeLogTheme(
                amoledBlack = prefs.useAmoledBlack,
                dynamicColor = prefs.useDynamicColor
            ) {
                EntryFormSheet(
                    mode = EntryFormMode.New(eventId),
                    onDismiss = {
                        if (notificationId != -1) {
                            NotificationManagerCompat.from(this).cancel(notificationId)
                        }
                        finish()
                    },
                    // The event was deleted after this widget/notification was
                    // created — explain rather than flash an unusable form.
                    onEventMissing = { finishMissingEvent(notificationId) },
                    onViewHistory = { id ->
                        if (notificationId != -1) {
                            NotificationManagerCompat.from(this).cancel(notificationId)
                        }
                        // Leave the transient overlay and open the full app on the
                        // event's detail screen so past entries can be reviewed.
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, id)
                                addFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK or
                                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                                )
                            }
                        )
                        finish()
                    }
                )
            }
        }
    }

    private fun finishMissingEvent(notificationId: Int) {
        if (notificationId != -1) {
            NotificationManagerCompat.from(this).cancel(notificationId)
        }
        Toast.makeText(this, "This event no longer exists", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
