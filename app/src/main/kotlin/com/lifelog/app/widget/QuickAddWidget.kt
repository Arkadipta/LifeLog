package com.lifelog.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lifelog.app.ui.theme.DarkColorScheme
import com.lifelog.app.ui.theme.LightColorScheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QuickAddWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d(TAG, "provideGlance start: glanceId=$id ts=${System.currentTimeMillis()}")
        provideContent {
            val prefs = currentState<Preferences>()
            val eventId = prefs[PREF_EVENT_ID] ?: 0L
            val eventName = prefs[PREF_EVENT_NAME] ?: "Quick Add"

            Log.d(TAG, "QuickAddWidget composing: glanceId=$id eventId=$eventId eventName='$eventName' ts=${System.currentTimeMillis()}")

            GlanceTheme(
                colors = ColorProviders(
                    light = LightColorScheme,
                    dark = DarkColorScheme
                )
            ) {
                QuickAddWidgetContent(context, eventId, eventName)
            }
        }
    }

    companion object {
        private const val TAG = "QuickAddWidget"

        val PREF_EVENT_ID = longPreferencesKey("event_id")
        val PREF_EVENT_NAME = stringPreferencesKey("event_name")
        val PREF_EVENT_COLOR = intPreferencesKey("event_color")
        val PREF_EVENT_ICON = stringPreferencesKey("event_icon")
    }
}

@Composable
private fun QuickAddWidgetContent(context: Context, eventId: Long, eventName: String) {
    val actionIntent = Intent(context, QuickAddActivity::class.java).apply {
        putExtra(QuickAddActivity.EXTRA_EVENT_ID, eventId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity(actionIntent)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.padding(16.dp)
        ) {
            Text(
                text = "+",
                style = TextStyle(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.primary
                )
            )
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = eventName,
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurface
                )
            )
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()

    // Same binding-race guard as TimelineWidgetReceiver, with the same retry + options fix.
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
            "${skippedIds.size} deferred: $skippedIds"
        )

        skippedIds.forEach { id ->
            Log.w(TAG, "onUpdate: scheduling 3s retry for appWidgetId=$id")
            receiverScope.launch {
                delay(3_000L)
                try {
                    if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id) != null) {
                        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(id)
                        QuickAddWidget().update(context, glanceId)
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
     * Reliable first-render trigger for newly placed widgets. The config activity's
     * update() fires before the widget is committed to the host; this callback fires
     * after — guaranteeing provideGlance runs with a live AppWidgetManager slot.
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
                QuickAddWidget().update(context, glanceId)
                Log.d(TAG, "onAppWidgetOptionsChanged: update complete for appWidgetId=$appWidgetId ts=${System.currentTimeMillis()}")
            } catch (e: Exception) {
                Log.e(TAG, "onAppWidgetOptionsChanged: update failed for appWidgetId=$appWidgetId", e)
            }
        }
    }

    companion object {
        private const val TAG = "QuickAddWidgetReceiver"
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
