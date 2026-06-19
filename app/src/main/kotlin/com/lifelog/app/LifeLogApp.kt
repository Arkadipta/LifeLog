package com.lifelog.app

import android.app.Application
import android.content.Intent
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.lifelog.app.export.SqliteRestore
import com.lifelog.app.notifications.NotificationHelper
import com.lifelog.app.notifications.ReminderReceiver
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class LifeLogApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

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

        // The restored reminders live in the new DB but their OS alarms aren't
        // armed yet (alarms don't survive a data swap), so re-arm them.
        if (restored) {
            sendBroadcast(
                Intent(this, ReminderReceiver::class.java)
                    .setAction(ReminderReceiver.ACTION_RESCHEDULE_ALL)
            )
        }
    }
}
