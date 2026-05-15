package com.lifelog.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
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
        Log.d(TAG, "provideGlance start: glanceId=$id")

        val repo = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TimelineWidgetEntryPoint::class.java
        ).eventRepository()

        // Read persisted filter configuration written by TimelineWidgetConfigActivity.
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val filterMode = prefs[PREF_FILTER_MODE]   // null → widget not yet configured
        val eventId   = prefs[PREF_EVENT_ID]   ?: 0L
        val eventName = prefs[PREF_EVENT_NAME] ?: ""
        val tag       = prefs[PREF_TAG]        ?: ""

        Log.d(
            TAG,
            "provideGlance prefs: glanceId=$id filterMode=$filterMode " +
            "eventId=$eventId eventName='$eventName' tag='$tag'"
        )

        // Widget has not been configured yet (e.g., first render before config
        // activity completes). Show a placeholder rather than defaulting to
        // FILTER_ALL, which would mislead the user into thinking the filter
        // selection didn't take effect.
        if (filterMode == null) {
            Log.d(TAG, "provideGlance: not yet configured, showing placeholder glanceId=$id")
            provideContent {
                GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                    UnconfiguredPlaceholder()
                }
            }
            return
        }

        val entries = try {
            when (filterMode) {
                FILTER_EVENT -> repo.getRecentEntriesByEventType(eventId, MAX_ENTRIES)
                FILTER_TAG   -> repo.getRecentEntriesByCategory(tag, MAX_ENTRIES)
                else         -> repo.getRecentEntries(MAX_ENTRIES)
            }
        } catch (e: Exception) {
            Log.e(TAG, "provideGlance: data fetch failed for glanceId=$id filterMode=$filterMode", e)
            emptyList()
        }

        Log.d(TAG, "provideGlance end: glanceId=$id entries=${entries.size}")

        provideContent {
            GlanceTheme(
                colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)
            ) {
                TimelineWidgetContent(
                    entries = entries,
                    filterMode = filterMode,
                    eventName = eventName,
                    tag = tag
                )
            }
        }
    }

    companion object {
        val PREF_FILTER_MODE = stringPreferencesKey("tl_filter_mode")
        val PREF_EVENT_ID    = longPreferencesKey("tl_event_id")
        val PREF_EVENT_NAME  = stringPreferencesKey("tl_event_name")
        val PREF_TAG         = stringPreferencesKey("tl_tag")

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
     * We simply skip IDs that are not yet fully bound; the config activity's
     * explicit update() call (which runs after prefs are written) will render
     * the widget correctly once binding is complete.
     */
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val validIds = appWidgetIds.filter { id ->
            val bound = appWidgetManager.getAppWidgetInfo(id) != null
            if (!bound) {
                Log.w(
                    TAG,
                    "onUpdate: skipping appWidgetId=$id — provider info not yet available " +
                    "(binding race). The config activity update() will render it correctly."
                )
            }
            bound
        }.toIntArray()

        Log.d(TAG, "onUpdate: ${appWidgetIds.size} requested, ${validIds.size} bound and ready")
        if (validIds.isNotEmpty()) {
            super.onUpdate(context, appWidgetManager, validIds)
        }
    }

    companion object {
        private const val TAG = "TimelineWidgetReceiver"
    }
}
