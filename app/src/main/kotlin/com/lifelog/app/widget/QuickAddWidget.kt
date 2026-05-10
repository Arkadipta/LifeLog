package com.lifelog.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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

class QuickAddWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val eventId = prefs[PREF_EVENT_ID] ?: 0L
            val eventName = prefs[PREF_EVENT_NAME] ?: "Quick Add"

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
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
}
