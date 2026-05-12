package com.lifelog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.lifelog.app.data.repository.UserPreferencesRepository
import com.lifelog.app.data.repository.dataStore
import com.lifelog.app.ui.navigation.AppNavigation
import com.lifelog.app.ui.theme.LifeLogTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hilt injection fires during super.onCreate(), so read DataStore directly here.
        // runBlocking is acceptable: this is a single disk read on cold start, before any UI exists.
        val isDark = runBlocking {
            applicationContext.dataStore.data.first()[booleanPreferencesKey("dark_theme")] ?: true
        }
        if (isDark) setTheme(R.style.Theme_LifeLog_Splash_Dark)

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
                AppNavigation()
            }
        }
    }
}
