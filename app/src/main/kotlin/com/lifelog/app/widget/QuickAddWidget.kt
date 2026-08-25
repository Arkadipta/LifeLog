package com.lifelog.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.ui.theme.DarkColorScheme
import com.lifelog.app.ui.theme.LightColorScheme
import com.lifelog.app.util.logD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QuickAddWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Exact sizing so the layout can adapt to whatever size the user gives the
    // widget: the event chip and name scale, the layout flips to a row when the
    // widget is wide and short, and the "+ Add entry" hint appears only when there
    // is room. (Default placement is 2×2; it resizes down to ~1×1.)
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        logD(TAG) { "provideGlance start: glanceId=$id ts=${System.currentTimeMillis()}" }
        provideContent {
            val prefs = currentState<Preferences>()
            val eventId = prefs[PREF_EVENT_ID] ?: 0L
            val eventName = prefs[PREF_EVENT_NAME] ?: "Quick Add"
            // Persisted at config time alongside the name, so the widget can surface
            // the event's full identity — its color and icon — not just its label.
            val eventColor = prefs[PREF_EVENT_COLOR] ?: EventType.DEFAULT_COLOR
            val eventIcon = prefs[PREF_EVENT_ICON] ?: "star"

            logD(TAG) { "QuickAddWidget composing: glanceId=$id eventId=$eventId eventName='$eventName' ts=${System.currentTimeMillis()}" }

            GlanceTheme(
                colors = ColorProviders(
                    light = LightColorScheme,
                    dark = DarkColorScheme
                )
            ) {
                QuickAddWidgetContent(context, eventId, eventName, eventColor, eventIcon)
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

/**
 * The Quick Add widget as a single event-identity card. The whole widget surface
 * is one rounded card washed with the event's color (the same low tint a Timeline
 * entry card uses), anchored by a solid accent chip carrying the event icon and
 * labelled with the event name in the theme-corrected accent — so an event is
 * recognizable at a glance, and several Quick Add widgets sitting side by side
 * stay distinct. Tapping anywhere opens the Add Entry flow for that event, exactly
 * as before; only the presentation changed.
 */
@Composable
private fun QuickAddWidgetContent(
    context: Context,
    eventId: Long,
    eventName: String,
    eventColor: Int,
    eventIcon: String
) {
    val size = LocalSize.current
    val w = size.width.value
    val h = size.height.value
    val lim = minOf(w, h)
    val accent = Color(eventColor)
    // eventId 0 = the bound event was deleted and the widget's state cleared; it
    // still renders (relabeled by the clear), but must not promise an add action.
    val configured = eventId != 0L
    val displayName = eventName.ifBlank { "Quick Add" }
    val a11yLabel = when {
        !configured -> displayName
        eventName.isBlank() -> "Add entry"
        else -> "Add $eventName entry"
    }

    val actionIntent = Intent(context, QuickAddActivity::class.java).apply {
        putExtra(QuickAddActivity.EXTRA_EVENT_ID, eventId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // Visual card modifier (no click) — the click + a11y live on the outer Box so
    // the whole widget, including its margin, stays the tap target it was before.
    val card = GlanceModifier
        .fillMaxSize()
        .background(entryCardTint(accent))
        .cornerRadius(if (lim < 110f) 18.dp else 24.dp)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(if (lim < 110f) 6.dp else 8.dp)
            .clickable(actionStartActivity(actionIntent))
            .semantics { contentDescription = a11yLabel },
        contentAlignment = Alignment.Center
    ) {
        // Wide and short → lay the chip and label out in a row; otherwise stack
        // them centered. The row keeps a 2×1-style widget from collapsing to an
        // icon with no room for its name.
        if (w >= h * 1.6f && h < 120f) {
            QuickAddRow(card, accent, eventIcon, displayName, w, h, showAddHint = configured)
        } else {
            QuickAddColumn(card, accent, eventIcon, displayName, w, h, showAddHint = configured)
        }
    }
}

/** Sizes for the accent chip and surrounding spacing at a given limiting dimension. */
private data class ChipMetrics(val tile: Dp, val glyph: Dp, val radius: Dp, val pad: Dp, val gap: Dp)

private fun chipMetricsFor(lim: Float): ChipMetrics = when {
    lim < 92f  -> ChipMetrics(tile = 36.dp, glyph = 22.dp, radius = 12.dp, pad = 8.dp, gap = 6.dp)
    lim < 130f -> ChipMetrics(tile = 46.dp, glyph = 27.dp, radius = 14.dp, pad = 10.dp, gap = 8.dp)
    else       -> ChipMetrics(tile = 54.dp, glyph = 31.dp, radius = 16.dp, pad = 14.dp, gap = 10.dp)
}

/** Centered stack: accent chip over the event name (and the add hint, when tall). */
@Composable
private fun QuickAddColumn(
    card: GlanceModifier,
    accent: Color,
    eventIcon: String,
    name: String,
    w: Float,
    h: Float,
    showAddHint: Boolean
) {
    val lim = minOf(w, h)
    val m = chipMetricsFor(lim)
    val showName = lim >= 84f
    val showAdd = showAddHint && h >= 132f && lim >= 100f

    Column(
        modifier = card.padding(m.pad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WidgetIconTile(
            iconName = eventIcon,
            accent = accent,
            tileSize = m.tile,
            iconSize = m.glyph,
            filled = true,
            cornerRadius = m.radius
        )
        if (showName) {
            Spacer(GlanceModifier.height(m.gap))
            Text(
                text = name,
                style = TextStyle(
                    fontSize = if (lim < 130f) 13.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentContent(accent),
                    textAlign = TextAlign.Center
                ),
                maxLines = if (h >= 168f) 2 else 1
            )
        }
        if (showAdd) {
            Spacer(GlanceModifier.height(6.dp))
            AddAffordance(accent)
        }
    }
}

/** Wide-and-short layout: accent chip beside the event name (and the add hint). */
@Composable
private fun QuickAddRow(
    card: GlanceModifier,
    accent: Color,
    eventIcon: String,
    name: String,
    w: Float,
    h: Float,
    showAddHint: Boolean
) {
    val tile = if (h < 84f) 40.dp else 48.dp
    val glyph = if (h < 84f) 24.dp else 28.dp
    val showAdd = showAddHint && w >= 230f

    Row(
        modifier = card.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetIconTile(
            iconName = eventIcon,
            accent = accent,
            tileSize = tile,
            iconSize = glyph,
            filled = true,
            cornerRadius = 14.dp
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = name,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentContent(accent)
                ),
                maxLines = 2
            )
            if (showAdd) {
                Spacer(GlanceModifier.height(3.dp))
                AddAffordance(accent)
            }
        }
    }
}

/**
 * The quiet "+ Add entry" hint that preserves the widget's add affordance: a bold
 * accent "+" (tying it to the action) next to a muted label, so the card reads as
 * something you tap to log — not just an event badge.
 */
@Composable
private fun AddAffordance(accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "+",
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accentContent(accent)
            )
        )
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = "Add entry",
            style = TextStyle(
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurfaceVariant
            )
        )
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

        logD(TAG) {
            "onUpdate: ${appWidgetIds.size} requested, ${validIds.size} ready, " +
            "${skippedIds.size} deferred: $skippedIds"
        }

        skippedIds.forEach { id ->
            Log.w(TAG, "onUpdate: scheduling 3s retry for appWidgetId=$id")
            receiverScope.launch {
                delay(3_000L)
                try {
                    if (AppWidgetManager.getInstance(context).getAppWidgetInfo(id) != null) {
                        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(id)
                        QuickAddWidget().update(context, glanceId)
                        logD(TAG) { "onUpdate retry: update complete for appWidgetId=$id" }
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
        logD(TAG) { "onAppWidgetOptionsChanged: appWidgetId=$appWidgetId ts=${System.currentTimeMillis()}" }
        receiverScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                QuickAddWidget().update(context, glanceId)
                logD(TAG) { "onAppWidgetOptionsChanged: update complete for appWidgetId=$appWidgetId ts=${System.currentTimeMillis()}" }
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
