package com.lifelog.app.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.NotificationManagerCompat
import com.lifelog.app.data.repository.UserPreferences
import com.lifelog.app.data.repository.UserPreferencesRepository
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
        if (eventId == 0L) {
            finish()
            return
        }
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        setContent {
            val prefs by userPreferencesRepository.userPreferences.collectAsState(
                initial = UserPreferences()
            )
            LifeLogTheme(
                darkTheme = prefs.useDarkTheme,
                amoledBlack = prefs.useAmoledBlack,
                dynamicColor = prefs.useDynamicColor
            ) {
                EntryFormSheet(
                    eventTypeId = eventId,
                    editingEntryId = null,
                    onDismiss = {
                        if (notificationId != -1) {
                            NotificationManagerCompat.from(this).cancel(notificationId)
                        }
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
