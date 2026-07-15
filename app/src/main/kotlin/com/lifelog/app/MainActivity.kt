package com.lifelog.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.lifelog.app.data.repository.UserPreferencesRepository
import com.lifelog.app.ui.navigation.AppNavigation
import com.lifelog.app.ui.theme.LifeLogTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Where an incoming `lifelog://` URI routes. The launcher's static shortcuts
 * (res/xml/shortcuts.xml) reach [MainActivity] with an explicit component plus
 * one of these URIs as data — no manifest intent-filter is involved, so the
 * scheme stays unexported. An unrecognized URI maps to null: the app just
 * opens normally instead of failing.
 */
enum class ShortcutDestination {
    TIMELINE, QUICK_ADD;

    companion object {
        fun fromUri(scheme: String?, host: String?): ShortcutDestination? {
            if (!"lifelog".equals(scheme, ignoreCase = true)) return null
            return when (host?.lowercase()) {
                "timeline" -> TIMELINE
                "quick_add" -> QUICK_ADD
                else -> null
            }
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    // Event id to route to on launch, set by an incoming intent (e.g. the
    // quick-add widget's "History" shortcut). Cleared once consumed by the nav.
    private var pendingEventId by mutableStateOf<Long?>(null)

    // Destination of a launcher-shortcut lifelog:// URI. Same one-shot contract
    // as pendingEventId.
    private var pendingShortcut by mutableStateOf<ShortcutDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only on a fresh launch — a config-change recreation must not re-route.
        if (savedInstanceState == null) {
            pendingEventId = intent.openEventIdExtra()
            pendingShortcut = intent.shortcutDestination()
        }

        setContent {
            val prefs by userPreferencesRepository.userPreferences.collectAsState(
                initial = com.lifelog.app.data.repository.UserPreferences()
            )
            LifeLogTheme(
                amoledBlack = prefs.useAmoledBlack,
                dynamicColor = prefs.useDynamicColor
            ) {
                AppNavigation(
                    openEventId = pendingEventId,
                    onEventOpened = { pendingEventId = null },
                    shortcut = pendingShortcut,
                    onShortcutHandled = { pendingShortcut = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.openEventIdExtra()?.let { pendingEventId = it }
        intent.shortcutDestination()?.let { pendingShortcut = it }
    }

    private fun Intent.openEventIdExtra(): Long? =
        getLongExtra(EXTRA_OPEN_EVENT_ID, -1L).takeIf { it > 0L }

    private fun Intent.shortcutDestination(): ShortcutDestination? =
        ShortcutDestination.fromUri(data?.scheme, data?.host)

    companion object {
        /** Long extra: open this event's detail screen on launch. */
        const val EXTRA_OPEN_EVENT_ID = "open_event_id"
    }
}
