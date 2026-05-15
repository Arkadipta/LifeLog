package com.lifelog.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.material3.ColorProviders
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.lifelog.app.ui.theme.DarkColorScheme
import com.lifelog.app.ui.theme.LightColorScheme

class QuickAddWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Config resolution priority:
        //   1. SharedPreferences — written by config activity; works for new widgets where
        //      the Glance DataStore entry may not exist yet during config.
        //   2. Glance DataStore fallback — for widgets configured before this deployment.
        val (eventId, eventName) = resolveConfig(context, id)

        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                QuickAddWidgetContent(context, eventId, eventName)
            }
        }
    }

    private suspend fun resolveConfig(context: Context, id: GlanceId): Pair<Long, String> {
        // 1 — SharedPreferences
        try {
            val awId = GlanceAppWidgetManager(context).getAppWidgetId(id)
            if (awId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val spId = WidgetPrefs.getQuickAddEventId(context, awId)
                if (spId != 0L) {
                    return Pair(spId, WidgetPrefs.getQuickAddEventName(context, awId))
                }
            }
        } catch (_: Exception) {}

        // 2 — Glance DataStore fallback
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val storedId = prefs[PREF_EVENT_ID] ?: 0L
        val storedName = prefs[PREF_EVENT_NAME] ?: "Quick Add"
        return Pair(storedId, storedName)
    }

    companion object {
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

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_DELETED) {
            val id = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetPrefs.removeQuickAdd(context, id)
            }
        }
    }
}
