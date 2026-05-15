package com.lifelog.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TimelineWidgetEntryPoint {
    fun eventRepository(): EventRepository
}

class TimelineWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 100.dp),
            DpSize(220.dp, 180.dp),
            DpSize(320.dp, 280.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TimelineWidgetEntryPoint::class.java
        ).eventRepository()

        // Config resolution priority:
        //   1. SharedPreferences — written by config activity for new/reconfigured widgets.
        //      This is the only path that works for brand-new widgets where the GlanceId
        //      registry entry doesn't exist yet during the config activity.
        //   2. Glance DataStore fallback — for widgets that were already configured before
        //      this code was deployed (backward compatibility).
        val (eventId, eventName, isConfigured) = resolveConfig(context, id)

        val entries: List<EventEntry> = if (!isConfigured) {
            emptyList()
        } else {
            try {
                if (eventId != 0L) repo.getRecentEntriesForEvent(eventId, 5)
                else repo.getRecentEntries(5)
            } catch (e: Exception) {
                emptyList()
            }
        }

        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                TimelineWidgetContent(entries, eventName, isConfigured)
            }
        }
    }

    private suspend fun resolveConfig(
        context: Context,
        id: GlanceId,
    ): Triple<Long, String, Boolean> {
        // 1 — SharedPreferences (set by config activity for any widget, new or existing)
        try {
            val awId = GlanceAppWidgetManager(context).getAppWidgetId(id)
            if (awId != AppWidgetManager.INVALID_APPWIDGET_ID &&
                WidgetPrefs.isTimelineConfigured(context, awId)
            ) {
                return Triple(
                    WidgetPrefs.getTimelineEventId(context, awId),
                    WidgetPrefs.getTimelineEventName(context, awId),
                    true,
                )
            }
        } catch (_: Exception) {}

        // 2 — Glance DataStore fallback (widgets configured before SharedPrefs migration)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val storedId = prefs[PREF_EVENT_ID]
        return if (storedId != null) {
            Triple(storedId, prefs[PREF_EVENT_NAME] ?: "", true)
        } else {
            Triple(0L, "", false)
        }
    }

    companion object {
        val PREF_EVENT_ID = longPreferencesKey("tl_event_id")
        val PREF_EVENT_NAME = stringPreferencesKey("tl_event_name")

        val MEDIUM_SIZE = DpSize(220.dp, 180.dp)
    }
}

@Composable
private fun TimelineWidgetContent(
    entries: List<EventEntry>,
    filterEventName: String,
    isConfigured: Boolean,
) {
    val size = LocalSize.current
    val isCompact = size.width < TimelineWidget.MEDIUM_SIZE.width

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(if (isCompact) 8.dp else 12.dp)
            .clickable(actionStartActivity<MainActivity>())
    ) {
        if (!isCompact) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (filterEventName.isNotBlank()) filterEventName else "LifeLog",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlanceTheme.colors.primary
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
            }
            Spacer(GlanceModifier.height(6.dp))
        }

        when {
            !isConfigured -> {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Long-press to configure",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }
            }
            entries.isEmpty() -> {
                Box(
                    modifier = GlanceModifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isCompact) "No entries" else "No entries yet. Tap to add.",
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onSurfaceVariant
                        )
                    )
                }
            }
            else -> {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(entries) { entry ->
                        Column(
                            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)
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
                                        fontSize = 9.sp,
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
                                        style = TextStyle(
                                            fontSize = 10.sp,
                                            color = GlanceTheme.colors.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

class TimelineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimelineWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_DELETED) {
            val id = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetPrefs.removeTimeline(context, id)
            }
        }
    }
}
