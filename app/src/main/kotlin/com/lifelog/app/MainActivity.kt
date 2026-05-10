package com.lifelog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.lifelog.app.data.repository.UserPreferencesRepository
import com.lifelog.app.ui.navigation.AppNavigation
import com.lifelog.app.ui.theme.LifeLogTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val prefs by userPreferencesRepository.userPreferences.collectAsState(
                initial = com.lifelog.app.data.repository.UserPreferences()
            )
            LifeLogTheme(
                darkTheme = prefs.useDarkTheme,
                amoledBlack = prefs.useAmoledBlack,
                dynamicColor = prefs.useDynamicColor
            ) {
                AppNavigation(
                    startDestination = intent?.data?.let { uri ->
                        when {
                            uri.scheme == "lifelog" && uri.host == "quick_add" ->
                                "events/${uri.getQueryParameter("eventId") ?: ""}"
                            else -> null
                        }
                    }
                )
            }
        }
    }
}
