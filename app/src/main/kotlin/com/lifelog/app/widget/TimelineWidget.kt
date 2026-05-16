package com.lifelog.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.lifelog.app.MainActivity
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.ui.theme.DarkColorScheme
import com.lifelog.app.ui.theme.LightColorScheme
import com.lifelog.app.util.relativeTimeLabel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "TimelineWidget"

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TimelineWidgetEntryPoint {
    fun eventRepository(): EventRepository
}

class TimelineWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Declare responsive breakpoints; Glance renders each and picks the best fit at runtime.
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 100.dp),  // compact: event name + time only
            DpSize(220.dp, 180.dp),  // normal: name + time + preview text
            DpSize(320.dp, 250.dp),  // wide: + category badge, more rows
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val startMs = System.currentTimeMillis()
        Log.d(TAG, "provideGlance start: glanceId=$id thread=${Thread.currentThread().name} ts=$startMs")

        val repo = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TimelineWidgetEntryPoint::class.java
        ).eventRepository()

        Log.d(TAG, "provideGlance: calling provideContent glanceId=$id elapsed=${System.currentTimeMillis() - startMs}ms")

        provideContent {
            // currentState<Preferences>() makes this composable reactive: Glance
            // recomposes whenever updateAppWidgetState() is called (e.g. from the
            // config activity or WidgetUpdater), bypassing the need for provideGlance
            // to restart.
            val currentPrefs      = currentState<Preferences>()
            val currentFilterMode = currentPrefs[PREF_FILTER_MODE]
            val currentEventName  = currentPrefs[PREF_EVENT_NAME] ?: ""
            val currentTag        = currentPrefs[PREF_TAG]        ?: ""
            val currentEventId    = currentPrefs[PREF_EVENT_ID]   ?: 0L
            // PREF_REFRESH_TS is bumped by WidgetUpdater on every entry save, forcing
            // LaunchedEffect to re-fire even when filter keys are unchanged.
            val refreshTs         = currentPrefs[PREF_REFRESH_TS] ?: 0L

            // Entries are held in remembered state so they survive recompositions.
            // LaunchedEffect re-fetches whenever the filter config or refresh stamp changes.
            var entries by remember { mutableStateOf<List<EventEntry>>(emptyList()) }

            LaunchedEffect(currentFilterMode, currentEventId, currentTag, refreshTs) {
                Log.d(
                    TAG,
                    "LaunchedEffect: fetching entries filterMode=$currentFilterMode " +
                    "eventId=$currentEventId tag='$currentTag' refreshTs=$refreshTs ts=${System.currentTimeMillis()}"
                )
                entries = if (currentFilterMode != null) {
                    try {
                        withContext(Dispatchers.IO) {
                            when (currentFilterMode) {
                                FILTER_EVENT -> repo.getRecentEntriesByEventType(currentEventId, MAX_ENTRIES)
                                FILTER_TAG   -> repo.getRecentEntriesByCategory(currentTag, MAX_ENTRIES)
                                else         -> repo.getRecentEntries(MAX_ENTRIES)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "LaunchedEffect: data fetch failed filterMode=$currentFilterMode", e)
                        emptyList()
                    }
                } else {
                    emptyList()
                }
                Log.d(TAG, "LaunchedEffect: fetched ${entries.size} entries ts=${System.currentTimeMillis()}")
            }

            Log.d(
                TAG,
                "provideContent composing: glanceId=$id currentFilterMode=$currentFilterMode " +
                "entries=${entries.size} ts=${System.currentTimeMillis()}"
            )

            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                if (currentFilterMode == null) {
                    UnconfiguredPlaceholder()
                } else {
                    TimelineWidgetContent(
                        entries = entries,
                        filterMode = currentFilterMode,
                        eventName = currentEventName,
                        tag = currentTag
                    )
                }
            }
        }
    }

    companion object {
        val PREF_FILTER_MODE = stringPreferencesKey("tl_filter_mode")
        val PREF_EVENT_ID    = longPreferencesKey("tl_event_id")
        val PREF_EVENT_NAME  = stringPreferencesKey("tl_event_name")
        val PREF_TAG         = stringPreferencesKey("tl_tag")
        // Bumped by WidgetUpdater on every data change; forces LaunchedEffect to re-fetch.
        val PREF_REFRESH_TS  = longPreferencesKey("tl_refresh_ts")

        const val FILTER_ALL   = "ALL"
        const val FILTER_EVENT = "EVENT"
        const val FILTER_TAG   = "TAG"

        // Max rows fetched per update; display is limited by widget height via LazyColumn.
        private const val MAX_ENTRIES = 10
    }
}

@Composable
private fun UnconfiguredPlaceholder() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        Text(
            "LifeLog",
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.primary
            )
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            "Tap to configure widget",
            style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
        )
    }
}

@Composable
private fun TimelineWidgetContent(
    entries: List<EventEntry>,
    filterMode: String,
    eventName: String,
    tag: String
) {
    val size = LocalSize.current
    val isCompact = size.width < 220.dp
    val isWide    = size.width >= 320.dp

    val filterLabel: String? = when (filterMode) {
        TimelineWidget.FILTER_EVENT -> eventName.ifBlank { null }
        TimelineWidget.FILTER_TAG   -> "#$tag".takeIf { tag.isNotBlank() }
        else -> null
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(if (isCompact) 8.dp else 12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        WidgetHeader(filterLabel = filterLabel, isCompact = isCompact)
        Spacer(GlanceModifier.height(if (isCompact) 4.dp else 8.dp))

        if (entries.isEmpty()) {
            Text(
                "No entries yet. Tap to add.",
                style = TextStyle(
                    fontSize = if (isCompact) 10.sp else 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(entries) { entry ->
                    EntryRow(entry = entry, isCompact = isCompact, showCategory = isWide)
                }
            }
        }
    }
}

@Composable
private fun WidgetHeader(filterLabel: String?, isCompact: Boolean) {
    if (isCompact) {
        Text(
            filterLabel ?: "LifeLog",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = GlanceTheme.colors.primary
            )
        )
    } else {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                "LifeLog",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.primary
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            if (filterLabel != null) {
                Text(
                    filterLabel,
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Composable
private fun EntryRow(entry: EventEntry, isCompact: Boolean, showCategory: Boolean) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = if (isCompact) 2.dp else 4.dp)
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Text(
                entry.eventTypeName,
                style = TextStyle(
                    fontSize = if (isCompact) 10.sp else 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.primary
                ),
                modifier = GlanceModifier.defaultWeight()
            )
            Text(
                entry.createdAt.relativeTimeLabel(),
                style = TextStyle(
                    fontSize = 10.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
        if (!isCompact) {
            val preview = entry.note.ifBlank {
                entry.fieldValues.values.firstOrNull()?.displayString() ?: ""
            }
            if (preview.isNotBlank()) {
                Text(
                    preview,
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurface)
                )
            }
            if (showCategory && entry.eventTypeCategory.isNotBlank()) {
                Text(
                    "#${entry.eventTypeCategory}",
                    style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.tertiary)
                )
            }
        }
    }
}

class TimelineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimelineWidget()

    /**
     * Guard against the race condition where the launcher sends APPWIDGET_UPDATE
     * before Android finishes binding the widget provider. In that state,
     * AppWidgetManager.getAppWidgetInfo() returns null, which causes Glance's
     * internal AppWidgetSession to throw:
     *   IllegalArgumentException: No app widget info for <id>
     *
     * IDs that are not yet bound are retried after a short delay instead of
     * being silently dropped. The config activity's update() call handles
     * reconfiguration; onAppWidgetOptionsChanged handles first placement.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val validIds   = mutableListOf<Int>()
        val skippedIds = mutableListOf<Int>()

        appWidgetIds.forEach { id ->
            if (appWidgetManager.getAppWidgetInfo(id) != null) {
                validIds.add(id)
            } else {
                skippedIds.add(id)
            }
        }

        Log.d(
            TAG,
            "onUpdate: ${appWidgetIds.size} requested, ${validIds.size} ready, " +
            "${skippedIds.size} deferred (not yet bound): $skippedIds"
        )

        // Retry deferred IDs after a short delay. Without this, any APPWIDGET_UPDATE
        // broadcast that arrives before the provider is fully bound is permanently lost
        // because updatePeriodMillis=0 means there is no periodic fallback.
        skippedIds.forEach { id ->
            Log.w(TAG, "onUpdate: scheduling 3s retry for appWidgetId=$id")
            receiverScope.launch {
                delay(3_000L)
                try {
                    if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id) != null) {
                        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(id)
                        TimelineWidget().update(context, glanceId)
                        Log.d(TAG, "onUpdate retry: update complete for appWidgetId=$id")
                    } else {
                        Log.e(TAG, "onUpdate retry: appWidgetId=$id still not bound after 3s — giving up")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "onUpdate retry: failed for appWidgetId=$id", e)
                }
            }
        }

        if (validIds.isNotEmpty()) {
            super.onUpdate(context, appWidgetManager, validIds.toIntArray())
        }
    }

    /**
     * Called by Android when the widget is first given space on the home screen
     * (and when its available space changes). This is the most reliable trigger
     * for the first render of a newly placed widget: the config activity's
     * update() call fires before the widget is committed to the host and can be
     * silently dropped; this callback is guaranteed to fire after the widget IS
     * in the host.
     *
     * State was already written by TimelineWidgetConfigActivity before it
     * returned RESULT_OK, so provideGlance will read the configured filter.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        Log.d(TAG, "onAppWidgetOptionsChanged: appWidgetId=$appWidgetId ts=${System.currentTimeMillis()}")
        receiverScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                TimelineWidget().update(context, glanceId)
                Log.d(TAG, "onAppWidgetOptionsChanged: update complete for appWidgetId=$appWidgetId ts=${System.currentTimeMillis()}")
            } catch (e: Exception) {
                Log.e(TAG, "onAppWidgetOptionsChanged: update failed for appWidgetId=$appWidgetId", e)
            }
        }
    }

    companion object {
        private const val TAG = "TimelineWidgetReceiver"
        // Scoped to the process lifetime — acceptable for a system BroadcastReceiver.
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
