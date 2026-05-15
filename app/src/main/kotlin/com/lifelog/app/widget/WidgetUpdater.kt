package com.lifelog.app.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidgetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized widget refresh utility. Call [refreshAll] after any data mutation that
 * should be reflected on homescreen widgets (entry saves, deletes, event type changes).
 *
 * Widgets are snapshot-based; they do not observe Room flows. Every write path that
 * changes visible data MUST call this to keep widgets in sync.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Refreshes all registered widget instances of every widget type.
     * Safe to call from any coroutine context; switches to [Dispatchers.IO] internally.
     * Errors are caught and logged — never throws.
     */
    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        try {
            val manager = GlanceAppWidgetManager(context)

            val timelineIds = manager.getGlanceIds(TimelineWidget::class.java)
            Log.d(TAG, "refreshAll: updating ${timelineIds.size} TimelineWidget instance(s)")
            timelineIds.forEach { id -> TimelineWidget().update(context, id) }

            val quickAddIds = manager.getGlanceIds(QuickAddWidget::class.java)
            Log.d(TAG, "refreshAll: updating ${quickAddIds.size} QuickAddWidget instance(s)")
            quickAddIds.forEach { id -> QuickAddWidget().update(context, id) }
        } catch (e: Exception) {
            Log.e(TAG, "refreshAll: widget refresh failed", e)
        }
    }

    /**
     * Refreshes only TimelineWidget instances. Use when entry data changes but
     * event type metadata (shown in QuickAddWidget) is unchanged.
     */
    suspend fun refreshTimeline() = withContext(Dispatchers.IO) {
        try {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(TimelineWidget::class.java)
            Log.d(TAG, "refreshTimeline: updating ${ids.size} TimelineWidget instance(s)")
            ids.forEach { id -> TimelineWidget().update(context, id) }
        } catch (e: Exception) {
            Log.e(TAG, "refreshTimeline: failed", e)
        }
    }

    companion object {
        private const val TAG = "WidgetUpdater"
    }
}
