package com.lifelog.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
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

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 100.dp),
            DpSize(220.dp, 180.dp),
            DpSize(320.dp, 280.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Resolve the Android widget ID so we can look up SharedPreferences.
        // GlanceAppWidgetManager.getAppWidgetId() is a plain (non-suspend) function in
        // Glance 1.1.0 — it just unwraps the internal int from the GlanceId.
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

        val isConfigured = WidgetPrefs.isTimelineConfigured(context, appWidgetId)
        val eventId = WidgetPrefs.getTimelineEventId(context, appWidgetId)
        val eventName = WidgetPrefs.getTimelineEventName(context, appWidgetId)

        val repo = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TimelineWidgetEntryPoint::class.java
        ).eventRepository()

        val entries: List<EventEntry> = if (!isConfigured) {
            // Widget placed but config activity not finished yet — show nothing until configured.
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

    companion object {
        val SMALL_SIZE = DpSize(120.dp, 100.dp)
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
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Long-press to configure",
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
            }
            entries.isEmpty() -> {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (isCompact) "No entries" else "No entries yet. Tap to add.",
                        style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
            }
            else -> {
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
}

class TimelineWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimelineWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Clean up SharedPreferences when the widget is removed from the home screen.
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_DELETED) {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID)
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetPrefs.removeTimeline(context, id)
            }
        }
    }
}
