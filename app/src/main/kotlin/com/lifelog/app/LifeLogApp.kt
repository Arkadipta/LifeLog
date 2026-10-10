package com.lifelog.app

import android.app.Application
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.lifelog.app.data.repository.UserPreferencesRepository
import com.lifelog.app.export.SqliteRestore
import com.lifelog.app.notifications.NotificationHelper
import com.lifelog.app.notifications.ReminderCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LifeLogApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var reminderCoordinator: ReminderCoordinator
    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        // Apply any staged database restore before Room (or anything else) opens
        // the database — the file must be swapped while no handle is held.
        val restored = SqliteRestore.applyStagedRestoreIfPresent(this)
        super.onCreate()
        NotificationHelper.createChannels(this)

        // OS alarms don't follow the database: the in-app restore above just swapped it, and a
        // Google Auto Backup / device-transfer restore or a force-stop leaves reminder rows
        // whose alarms the system no longer holds — with no broadcast to re-arm from. Verify on
        // every start and re-arm only when they were lost.
        appScope.launch { reminderCoordinator.ensureArmedOnAppStart(databaseRestored = restored) }

        // Read the stored preferences now rather than when whichever screen opens first asks
        // for them: nothing can draw an honest frame before the theme is known, and a screen
        // that cannot wait for it (the alarm screen) is exactly the one a cold process starts
        // for. Awaiting the value is also what keeps this — R8 drops a field that is only ever
        // injected, and with it the construction that starts the read.
        appScope.launch { userPreferencesRepository.loaded.first { it != null } }
    }
}
