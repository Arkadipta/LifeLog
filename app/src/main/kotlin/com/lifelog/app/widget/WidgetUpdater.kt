package com.lifelog.app.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.lifelog.app.util.logD
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized widget refresh utility. Call [refreshAll] after any data mutation that
 * should be reflected on homescreen widgets (entry saves, deletes, event type changes).
 *
 * TimelineWidget uses LaunchedEffect inside provideContent to fetch entries reactively.
 * Bumping PREF_REFRESH_TS via updateAppWidgetState() forces that LaunchedEffect to
 * re-fire and re-fetch fresh data, regardless of whether the filter config changed.
 *
 * update() MUST run on Dispatchers.Main — it interacts with the Glance session
 * machinery which holds a main-thread lock. After withContext(IO) for getGlanceIds(),
 * the coroutine may be on the IO pool regardless of the caller's original dispatcher,
 * so we always switch back to Main explicitly.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun refreshAll() {
        try {
            val manager = GlanceAppWidgetManager(context)

            val timelineIds = withContext(Dispatchers.IO) {
                manager.getGlanceIds(TimelineWidget::class.java)
            }
            logD(TAG) { "refreshAll: ${timelineIds.size} TimelineWidget instance(s) ts=${System.currentTimeMillis()}" }
            val refreshTs = System.currentTimeMillis()
            timelineIds.forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[TimelineWidget.PREF_REFRESH_TS] = refreshTs
                    }
                }
                logD(TAG) { "refreshAll: PREF_REFRESH_TS written id=$id ts=$refreshTs" }
            }
            withContext(Dispatchers.Main) {
                timelineIds.forEach { id ->
                    TimelineWidget().update(context, id)
                    logD(TAG) { "refreshAll: TimelineWidget update() returned id=$id ts=${System.currentTimeMillis()}" }
                }
            }

            val quickAddIds = withContext(Dispatchers.IO) {
                manager.getGlanceIds(QuickAddWidget::class.java)
            }
            logD(TAG) { "refreshAll: ${quickAddIds.size} QuickAddWidget instance(s) ts=${System.currentTimeMillis()}" }
            withContext(Dispatchers.Main) {
                quickAddIds.forEach { id ->
                    QuickAddWidget().update(context, id)
                    logD(TAG) { "refreshAll: QuickAddWidget update() returned id=$id ts=${System.currentTimeMillis()}" }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshAll: widget refresh failed", e)
        }
    }

    suspend fun refreshTimeline() {
        try {
            val manager = GlanceAppWidgetManager(context)
            val ids = withContext(Dispatchers.IO) {
                manager.getGlanceIds(TimelineWidget::class.java)
            }
            logD(TAG) { "refreshTimeline: ${ids.size} TimelineWidget instance(s) ts=${System.currentTimeMillis()}" }
            val refreshTs = System.currentTimeMillis()
            ids.forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        this[TimelineWidget.PREF_REFRESH_TS] = refreshTs
                    }
                }
                logD(TAG) { "refreshTimeline: PREF_REFRESH_TS written id=$id ts=$refreshTs" }
            }
            withContext(Dispatchers.Main) {
                ids.forEach { id ->
                    TimelineWidget().update(context, id)
                    logD(TAG) { "refreshTimeline: update() returned id=$id ts=${System.currentTimeMillis()}" }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshTimeline: failed", e)
        }
    }

    /**
     * Unbinds any QuickAddWidget configured for the given event type. Called when an
     * event type is deleted: a refresh alone re-renders the widget from its stored
     * prefs, so it would keep the dead event's identity and keep launching Quick Add
     * with an id that can no longer resolve. Clearing the id (and relabeling) turns
     * the widget into an inert "Event removed" card instead.
     */
    suspend fun clearQuickAddForEvent(eventTypeId: Long) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val staleIds = withContext(Dispatchers.IO) {
                manager.getGlanceIds(QuickAddWidget::class.java).filter { id ->
                    getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
                        .get(QuickAddWidget.PREF_EVENT_ID) == eventTypeId
                }
            }
            logD(TAG) { "clearQuickAddForEvent: ${staleIds.size} widget(s) bound to eventTypeId=$eventTypeId" }
            staleIds.forEach { id ->
                updateAppWidgetState(context, PreferencesGlanceStateDefinition, id) { prefs ->
                    prefs.toMutablePreferences().apply {
                        remove(QuickAddWidget.PREF_EVENT_ID)
                        remove(QuickAddWidget.PREF_EVENT_COLOR)
                        remove(QuickAddWidget.PREF_EVENT_ICON)
                        this[QuickAddWidget.PREF_EVENT_NAME] = "Event removed"
                    }
                }
            }
            withContext(Dispatchers.Main) {
                staleIds.forEach { id ->
                    QuickAddWidget().update(context, id)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "clearQuickAddForEvent: failed for eventTypeId=$eventTypeId", e)
        }
    }

    companion object {
        private const val TAG = "WidgetUpdater"
    }
}
