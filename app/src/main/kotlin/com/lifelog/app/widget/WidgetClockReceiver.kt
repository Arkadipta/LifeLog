package com.lifelog.app.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.lifelog.app.util.logD
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

/**
 * Re-renders the Timeline widget when the way the device presents the clock changes underneath it.
 *
 * TimelineWidget reads `DateFormat.is24HourFormat()` and the system zone at render time, so both
 * answers are right whenever it draws — but a widget only draws when something asks it to, and
 * neither setting asks. Without this the widget keeps showing "20 Jun 2026, 09:14 AM" after the
 * user switches the device to 24-hour time, until the next entry is logged or the widget is
 * refreshed by hand. An Activity closes the same gap with a ContentObserver on
 * `Settings.System.TIME_12_24` (see LocalIs24HourFormat); a widget has no live composition to
 * hang an observer on, so the broadcast is the hook it has.
 *
 * ACTION_TIME_CHANGED is literally "android.intent.action.TIME_SET", and the platform sends it
 * when the user sets the time *format* preference as well as when the clock moves — that extra
 * duty is the whole reason this works. TIMEZONE_CHANGED is here for the same defect one step
 * larger: `toWidgetTimestamp` resolves the zone per call, so every rendered stamp is off by the
 * offset until something re-renders. Both actions are exempt from the API 26+ implicit-broadcast
 * restrictions.
 *
 * Only the Timeline widget renders a time, so QuickAddWidget is deliberately left out of the
 * refresh. And this is deliberately its own receiver rather than two more cases in
 * ReminderReceiver, which already declares both actions to re-anchor alarms: nothing about
 * repainting a widget belongs to reminder scheduling, and a filter per concern keeps either
 * side removable on its own.
 */
@AndroidEntryPoint
class WidgetClockReceiver : BroadcastReceiver() {

    @Inject lateinit var widgetUpdater: WidgetUpdater

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIME_CHANGED -> Unit
            Intent.ACTION_TIMEZONE_CHANGED ->
                // `TimeZone.getDefault()` — which `ZoneId.systemDefault()`, and so every stamp
                // the widget draws, resolves through — is cached per process, and the framework
                // clears that cache while handling this same broadcast. That clear is NOT ordered
                // against this receiver: measured on API 36, refreshing here ran first and
                // repainted every stamp in the OLD zone, where it then sat until some unrelated
                // refresh happened by. Dropping the cache ourselves is the same reset the
                // framework performs moments later, just early enough for the render below.
                TimeZone.setDefault(null)
            else -> return
        }

        logD(TAG) { "onReceive: ${intent.action} — refreshing timeline widgets" }
        // The refresh reads the DB and drives a Glance update, so it outlives onReceive; without
        // the goAsync token the process is killable the moment this method returns.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                widgetUpdater.refreshTimeline()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "WidgetClockReceiver"
    }
}
