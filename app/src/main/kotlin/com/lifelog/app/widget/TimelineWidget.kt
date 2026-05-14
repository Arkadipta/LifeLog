package com.lifelog.app.widget

import android.content.Context
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

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TimelineWidgetEntryPoint {
    fun eventRepository(): EventRepository
}

class TimelineWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 100.dp),   // small: compact list
            DpSize(220.dp, 180.dp),   // medium: name + time + preview
            DpSize(320.dp, 280.dp),   // large: full layout with header
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TimelineWidgetEntryPoint::class.java
        )
        val repo = entryPoint.eventRepository()

        // Read persisted prefs before entering the Composable scope
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val eventId = prefs[PREF_EVENT_ID] ?: 0L
        val eventName = prefs[PREF_EVENT_NAME] ?: ""

        val entries: List<EventEntry> = try {
            if (eventId != 0L) repo.getRecentEntriesForEvent(eventId, 5)
            else repo.getRecentEntries(5)
        } catch (e: Exception) {
            emptyList()
        }

        provideContent {
            GlanceTheme(
                colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)
            ) {
                TimelineWidgetContent(entries, eventName)
            }
        }
    }

    companion object {
        val PREF_EVENT_ID = longPreferencesKey("tl_event_id")
        val PREF_EVENT_NAME = stringPreferencesKey("tl_event_name")

        val SMALL_SIZE = DpSize(120.dp, 100.dp)
        val MEDIUM_SIZE = DpSize(220.dp, 180.dp)
    }
}

@Composable
private fun TimelineWidgetContent(entries: List<EventEntry>, filterEventName: String) {
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

        if (entries.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (isCompact) "No entries" else "No entries yet. Tap to add.",
                    style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(entries) { entry ->
                    Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
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

class TimelineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimelineWidget()
}
