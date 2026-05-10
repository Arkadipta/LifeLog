package com.lifelog.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            TimelineWidgetEntryPoint::class.java
        )
        val entries = try {
            entryPoint.eventRepository().getRecentEntries(5)
        } catch (e: Exception) {
            emptyList()
        }

        provideContent {
            GlanceTheme(
                colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)
            ) {
                TimelineWidgetContent(entries)
            }
        }
    }
}

@Composable
private fun TimelineWidgetContent(entries: List<EventEntry>) {
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

        if (entries.isEmpty()) {
            Text(
                "No entries yet. Tap to add.",
                style = TextStyle(fontSize = 12.sp, color = GlanceTheme.colors.onSurfaceVariant)
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(entries) { entry ->
                    Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = GlanceModifier.fillMaxWidth()) {
                            Text(
                                entry.eventTypeName,
                                style = TextStyle(
                                    fontSize = 11.sp,
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
                        val preview = entry.note.ifBlank {
                            entry.fieldValues.values.firstOrNull()?.displayString() ?: ""
                        }
                        if (preview.isNotBlank()) {
                            Text(
                                preview,
                                style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurface)
                            )
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
