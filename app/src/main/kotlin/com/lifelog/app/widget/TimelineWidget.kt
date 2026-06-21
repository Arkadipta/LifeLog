package com.lifelog.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider as dayNightColor
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.lifelog.app.MainActivity
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.ui.theme.DarkColorScheme
import com.lifelog.app.ui.theme.LightColorScheme
import com.lifelog.app.ui.theme.bestContentColor
import com.lifelog.app.ui.theme.onAccentTile
import com.lifelog.app.util.relativeTimeLabel
import com.lifelog.app.util.toWidgetTimestamp
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

/** Entries plus the field definitions needed to label every value they carry. */
private data class TimelineData(
    val entries: List<EventEntry> = emptyList(),
    val fieldsByType: Map<Long, List<EventField>> = emptyMap()
)

class TimelineWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    // Declare responsive breakpoints; Glance renders each and picks the best fit
    // at runtime. The layout is the same across sizes (it just tightens spacing
    // and type when narrow); the LazyColumn scrolls when entries overflow.
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(120.dp, 100.dp),
            DpSize(220.dp, 180.dp),
            DpSize(320.dp, 250.dp),
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
            val currentEventName  = currentPrefs[PREF_EVENT_NAME]  ?: ""
            val currentTag        = currentPrefs[PREF_TAG]         ?: ""
            val currentEventId    = currentPrefs[PREF_EVENT_ID]    ?: 0L
            // Stored at config time so a single-event widget renders its accent and
            // header icon correctly even before any entry exists.
            val currentEventColor = currentPrefs[PREF_EVENT_COLOR] ?: EventType.DEFAULT_COLOR
            val currentEventIcon  = currentPrefs[PREF_EVENT_ICON]  ?: "star"
            // PREF_REFRESH_TS is bumped by WidgetUpdater on every entry save, forcing
            // LaunchedEffect to re-fire even when filter keys are unchanged.
            val refreshTs         = currentPrefs[PREF_REFRESH_TS]  ?: 0L

            // Held in remembered state so it survives recompositions; LaunchedEffect
            // re-fetches whenever the filter config or refresh stamp changes.
            var data by remember { mutableStateOf(TimelineData()) }

            LaunchedEffect(currentFilterMode, currentEventId, currentTag, refreshTs) {
                Log.d(
                    TAG,
                    "LaunchedEffect: fetching entries filterMode=$currentFilterMode " +
                    "eventId=$currentEventId tag='$currentTag' refreshTs=$refreshTs ts=${System.currentTimeMillis()}"
                )
                data = if (currentFilterMode != null) {
                    try {
                        withContext(Dispatchers.IO) {
                            val entries = when (currentFilterMode) {
                                FILTER_EVENT -> repo.getRecentEntriesByEventType(currentEventId, MAX_ENTRIES)
                                FILTER_TAG   -> repo.getRecentEntriesByCategory(currentTag, MAX_ENTRIES)
                                else         -> repo.getRecentEntries(MAX_ENTRIES)
                            }
                            // One extra query resolves field names/units for the
                            // event types present, so every value can be labeled.
                            val fields = repo.getFieldsByEventType(entries.map { it.eventTypeId })
                            TimelineData(entries, fields)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "LaunchedEffect: data fetch failed filterMode=$currentFilterMode", e)
                        TimelineData()
                    }
                } else {
                    TimelineData()
                }
                Log.d(TAG, "LaunchedEffect: fetched ${data.entries.size} entries ts=${System.currentTimeMillis()}")
            }

            Log.d(
                TAG,
                "provideContent composing: glanceId=$id currentFilterMode=$currentFilterMode " +
                "entries=${data.entries.size} ts=${System.currentTimeMillis()}"
            )

            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                if (currentFilterMode == null) {
                    UnconfiguredPlaceholder()
                } else {
                    TimelineWidgetContent(
                        data = data,
                        filterMode = currentFilterMode,
                        eventName = currentEventName,
                        tag = currentTag,
                        eventId = currentEventId,
                        eventColor = currentEventColor,
                        eventIcon = currentEventIcon
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
        val PREF_EVENT_COLOR = intPreferencesKey("tl_event_color")
        val PREF_EVENT_ICON  = stringPreferencesKey("tl_event_icon")
        // Bumped by WidgetUpdater on every data change; forces LaunchedEffect to re-fetch.
        val PREF_REFRESH_TS  = longPreferencesKey("tl_refresh_ts")

        const val FILTER_ALL   = "ALL"
        const val FILTER_EVENT = "EVENT"
        const val FILTER_TAG   = "TAG"

        // Max rows fetched per update; display is limited by widget height via LazyColumn.
        private const val MAX_ENTRIES = 10
    }
}

// --- Theming helpers -------------------------------------------------------
// Plain (non-composable) builders so they work inside the Glance tree. Each
// accent ColorProvider carries day/night variants that mirror the in-app
// rememberAccentOnSurface correction, so bright pastel accents stay legible in
// both light and dark.

/** The accent drawn as content (icon/text) over a surface, corrected per theme. */
private fun accentContent(accent: Color): ColorProvider =
    dayNightColor(day = accent.onAccentTile(false), night = accent.onAccentTile(true))

/** The faint accent tint behind an icon tile — a translucent wash over the surface. */
private fun accentTile(accent: Color): ColorProvider =
    ColorProvider(accent.copy(alpha = 0.16f))

/**
 * How strongly each entry card is tinted with its source event's color. Every
 * card carries its event color (in all scopes) so an entry's source is obvious at
 * a glance; the tint is kept low so the card still reads as "mostly surface" and
 * the standard onSurface / onSurfaceVariant text stays well above contrast minimums
 * over it, in both light and dark.
 */
private const val ENTRY_TINT_ALPHA = 0.12f

/** The event-colored background of an entry card. */
private fun entryCardTint(accent: Color): ColorProvider =
    ColorProvider(accent.copy(alpha = ENTRY_TINT_ALPHA))

/** Field value with its unit appended for numeric fields ("120 mmHg"). */
private fun widgetFieldValue(field: EventField, value: FieldValue): String {
    val display = value.displayString()
    return if (value is FieldValue.Numeric && field.unit.isNotBlank()) "$display ${field.unit}" else display
}

@Composable
private fun UnconfiguredPlaceholder() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(12.dp)
            .clickable(actionStartActivity(mainActivityIntent(context)))
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
    data: TimelineData,
    filterMode: String,
    eventName: String,
    tag: String,
    eventId: Long,
    eventColor: Int,
    eventIcon: String
) {
    val context = LocalContext.current
    val size = LocalSize.current
    val isCompact = size.width < 220.dp
    val is24Hour = DateFormat.is24HourFormat(context)
    // Single-event widgets take their accent from the event color; tag / all-event
    // widgets stay on the neutral widget theme. The entries carry the live event
    // color/icon, so prefer those (they stay correct if the event is recolored, and
    // cover widgets placed before color/icon were persisted); the stored config is
    // the fallback for an event with no entries yet.
    val headerEntry = data.entries.firstOrNull()
    val effectiveColor = headerEntry?.eventTypeColor ?: eventColor
    val effectiveIcon  = headerEntry?.eventTypeIcon ?: eventIcon
    val accent = if (filterMode == TimelineWidget.FILTER_EVENT) Color(effectiveColor) else null

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(if (isCompact) 8.dp else 12.dp)
            .clickable(actionStartActivity(mainActivityIntent(context)))
    ) {
        WidgetHeader(
            filterMode = filterMode,
            eventName = eventName,
            tag = tag,
            accent = accent,
            eventIcon = effectiveIcon,
            eventId = eventId,
            isCompact = isCompact
        )
        Spacer(GlanceModifier.height(if (isCompact) 6.dp else 8.dp))

        if (data.entries.isEmpty()) {
            Text(
                "No entries yet. Tap to add.",
                style = TextStyle(
                    fontSize = if (isCompact) 11.sp else 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(data.entries, itemId = { it.id }) { entry ->
                    EntryCard(
                        entry = entry,
                        fields = data.fieldsByType[entry.eventTypeId].orEmpty(),
                        filterMode = filterMode,
                        is24Hour = is24Hour,
                        isCompact = isCompact
                    )
                }
            }
        }
    }
}

/**
 * Scope-aware header. Single-event widgets lead with the event icon, name (in the
 * event accent), and a quick-add "+" that targets that event. Tag and all-event
 * widgets show only the scope title — their per-entry rows carry the event
 * identity instead.
 */
@Composable
private fun WidgetHeader(
    filterMode: String,
    eventName: String,
    tag: String,
    accent: Color?,
    eventIcon: String,
    eventId: Long,
    isCompact: Boolean
) {
    val context = LocalContext.current
    val isEvent = filterMode == TimelineWidget.FILTER_EVENT && accent != null

    val title = when (filterMode) {
        TimelineWidget.FILTER_EVENT -> eventName.ifBlank { "LifeLog" }
        TimelineWidget.FILTER_TAG   -> "#$tag".takeIf { tag.isNotBlank() } ?: "Tag"
        else                        -> "All Events"
    }
    val titleColor = if (isEvent) accentContent(accent!!) else GlanceTheme.colors.primary

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isEvent) {
            WidgetIconTile(
                iconName = eventIcon,
                accent = accent!!,
                tileSize = if (isCompact) 30.dp else 34.dp,
                iconSize = if (isCompact) 18.dp else 20.dp
            )
            Spacer(GlanceModifier.width(8.dp))
        }
        Text(
            title,
            style = TextStyle(
                fontSize = if (isCompact) 14.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor
            ),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight()
        )
        if (isEvent) {
            Spacer(GlanceModifier.width(8.dp))
            QuickAddButton(context, eventId, eventName, accent!!, isCompact)
        }
    }
}

/** Circular accent-filled "+" that opens the Add Entry flow for this event. */
@Composable
private fun QuickAddButton(context: Context, eventId: Long, eventName: String, accent: Color, isCompact: Boolean) {
    val diameter = if (isCompact) 28.dp else 32.dp
    val addIntent = Intent(context, QuickAddActivity::class.java).apply {
        putExtra(QuickAddActivity.EXTRA_EVENT_ID, eventId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val label = if (eventName.isBlank()) "Add entry" else "Add $eventName entry"
    Box(
        modifier = GlanceModifier
            .size(diameter)
            .background(ColorProvider(accent))
            .cornerRadius((diameter.value / 2f).dp)
            .clickable(actionStartActivity(addIntent))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "+",
            style = TextStyle(
                fontSize = if (isCompact) 18.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                color = ColorProvider(accent.bestContentColor())
            )
        )
    }
}

/**
 * A rounded tile holding the event icon. [filled] = true paints a solid accent
 * chip with a black/white icon (used as the per-entry source marker, so it stays
 * distinct on top of the event-tinted card); [filled] = false is the faint tile
 * used in the single-event header, where it sits on the plain widget surface.
 */
@Composable
private fun WidgetIconTile(
    iconName: String,
    accent: Color,
    tileSize: Dp,
    iconSize: Dp,
    filled: Boolean = false
) {
    val context = LocalContext.current
    val px = (iconSize.value * context.resources.displayMetrics.density).toInt()
    val tileBg = if (filled) ColorProvider(accent) else accentTile(accent)
    val iconTint = if (filled) ColorProvider(accent.bestContentColor()) else accentContent(accent)
    Box(
        modifier = GlanceModifier
            .size(tileSize)
            .background(tileBg)
            .cornerRadius(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(widgetIconMask(iconName, px)),
            contentDescription = null,
            colorFilter = ColorFilter.tint(iconTint),
            modifier = GlanceModifier.size(iconSize)
        )
    }
}

/**
 * One logged entry, rendered as an activity-feed card tinted with its source
 * event's color (in every scope), so an entry's origin reads at a glance. The
 * exact timestamp is the primary value with the relative age as quiet metadata
 * beside it; every field is listed in full below. On tag / all-event widgets the
 * card is also prefixed with the event's icon chip and name (and its tag, on
 * all-event widgets); single-event widgets omit that — the header owns it.
 */
@Composable
private fun EntryCard(
    entry: EventEntry,
    fields: List<EventField>,
    filterMode: String,
    is24Hour: Boolean,
    isCompact: Boolean
) {
    val isEvent = filterMode == TimelineWidget.FILTER_EVENT
    val showEventName = !isEvent
    val showTag = filterMode == TimelineWidget.FILTER_ALL
    val accent = Color(entry.eventTypeColor)

    val cardBg = entryCardTint(accent)

    val orderedFields = fields.filter { entry.fieldValues.containsKey(it.id) }
    val fieldFontSize = if (isCompact) 11.sp else 12.sp

    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(cardBg)
                .cornerRadius(14.dp)
                .padding(horizontal = if (isCompact) 8.dp else 10.dp, vertical = if (isCompact) 7.dp else 9.dp)
        ) {
            // Source-event identity (tag / all-event scopes only).
            if (showEventName) {
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WidgetIconTile(
                        iconName = entry.eventTypeIcon,
                        accent = accent,
                        tileSize = 28.dp,
                        iconSize = 16.dp,
                        filled = true
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(
                        entry.eventTypeName,
                        style = TextStyle(
                            fontSize = if (isCompact) 12.sp else 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentContent(accent)
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight()
                    )
                    if (showTag && entry.eventTypeCategory.isNotBlank()) {
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            "#${entry.eventTypeCategory}",
                            style = TextStyle(
                                fontSize = 10.sp,
                                color = GlanceTheme.colors.onSurfaceVariant
                            ),
                            maxLines = 1
                        )
                    }
                }
                Spacer(GlanceModifier.height(5.dp))
            }

            // Exact timestamp (primary) + relative age (secondary). The weighted
            // spacer keeps the exact stamp from being clipped on narrow widgets.
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    entry.createdAt.toWidgetTimestamp(is24Hour),
                    style = TextStyle(
                        fontSize = if (isCompact) 11.sp else 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = GlanceTheme.colors.onSurface
                    ),
                    maxLines = 1
                )
                Spacer(GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    entry.createdAt.relativeTimeLabel(),
                    style = TextStyle(
                        fontSize = 10.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    ),
                    maxLines = 1
                )
            }

            if (orderedFields.isNotEmpty() || entry.note.isNotBlank()) {
                Spacer(GlanceModifier.height(if (isCompact) 5.dp else 6.dp))
            }

            orderedFields.forEach { field ->
                entry.fieldValues[field.id]?.let { value ->
                    FieldLine(field.name, widgetFieldValue(field, value), fieldFontSize)
                }
            }

            if (entry.note.isNotBlank()) {
                Spacer(GlanceModifier.height(3.dp))
                Text(
                    entry.note,
                    style = TextStyle(
                        fontSize = if (isCompact) 10.sp else 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    )
                )
            }
        }
    }
}

/** One field row: muted name on the left, emphasized value filling the rest. */
@Composable
private fun FieldLine(name: String, value: String, fontSize: TextUnit) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            name,
            style = TextStyle(fontSize = fontSize, color = GlanceTheme.colors.onSurfaceVariant),
            maxLines = 1
        )
        Spacer(GlanceModifier.width(8.dp))
        Text(
            value,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                color = GlanceTheme.colors.onSurface
            ),
            modifier = GlanceModifier.defaultWeight()
        )
    }
}

private fun mainActivityIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

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
