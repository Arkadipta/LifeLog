package com.lifelog.app.widget

import android.content.Context

/**
 * SharedPreferences-backed storage for widget configuration, keyed by Android appWidgetId.
 *
 * Glance's DataStore-based state storage requires a GlanceId that only exists after
 * provideGlance has been called at least once. For brand-new widgets the config activity
 * runs before provideGlance, so GlanceId lookup returns null and no state is ever saved.
 * SharedPreferences don't have this chicken-and-egg problem: they're always available,
 * survive reboots, and are isolated per widget instance via the int appWidgetId key.
 */
object WidgetPrefs {

    private const val TIMELINE_PREFS = "lifelog_tl_widget"
    private const val CHART_PREFS = "lifelog_cw_widget"

    // ── Timeline ────────────────────────────────────────────────────────────

    fun saveTimeline(
        context: Context,
        appWidgetId: Int,
        eventId: Long,
        eventName: String,
    ) {
        context.getSharedPreferences(TIMELINE_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(key(appWidgetId, "event_id"), eventId)
            .putString(key(appWidgetId, "event_name"), eventName)
            .putBoolean(key(appWidgetId, "configured"), true)
            .apply()
    }

    fun getTimelineEventId(context: Context, appWidgetId: Int): Long =
        context.getSharedPreferences(TIMELINE_PREFS, Context.MODE_PRIVATE)
            .getLong(key(appWidgetId, "event_id"), 0L)

    fun getTimelineEventName(context: Context, appWidgetId: Int): String =
        context.getSharedPreferences(TIMELINE_PREFS, Context.MODE_PRIVATE)
            .getString(key(appWidgetId, "event_name"), "") ?: ""

    fun isTimelineConfigured(context: Context, appWidgetId: Int): Boolean =
        context.getSharedPreferences(TIMELINE_PREFS, Context.MODE_PRIVATE)
            .getBoolean(key(appWidgetId, "configured"), false)

    fun removeTimeline(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(TIMELINE_PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(appWidgetId, "event_id"))
            .remove(key(appWidgetId, "event_name"))
            .remove(key(appWidgetId, "configured"))
            .apply()
    }

    // ── Chart ───────────────────────────────────────────────────────────────

    fun saveChart(
        context: Context,
        appWidgetId: Int,
        eventTypeId: Long,
        chartConfigId: String,
        eventTypeName: String,
        chartTitle: String,
        eventColor: Int,
    ) {
        context.getSharedPreferences(CHART_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(key(appWidgetId, "event_type_id"), eventTypeId)
            .putString(key(appWidgetId, "chart_config_id"), chartConfigId)
            .putString(key(appWidgetId, "event_type_name"), eventTypeName)
            .putString(key(appWidgetId, "chart_title"), chartTitle)
            .putInt(key(appWidgetId, "event_color"), eventColor)
            .apply()
    }

    fun getChartEventTypeId(context: Context, appWidgetId: Int): Long =
        context.getSharedPreferences(CHART_PREFS, Context.MODE_PRIVATE)
            .getLong(key(appWidgetId, "event_type_id"), 0L)

    fun getChartConfigId(context: Context, appWidgetId: Int): String =
        context.getSharedPreferences(CHART_PREFS, Context.MODE_PRIVATE)
            .getString(key(appWidgetId, "chart_config_id"), "") ?: ""

    fun getChartEventTypeName(context: Context, appWidgetId: Int): String =
        context.getSharedPreferences(CHART_PREFS, Context.MODE_PRIVATE)
            .getString(key(appWidgetId, "event_type_name"), "") ?: ""

    fun getChartTitle(context: Context, appWidgetId: Int): String =
        context.getSharedPreferences(CHART_PREFS, Context.MODE_PRIVATE)
            .getString(key(appWidgetId, "chart_title"), "") ?: ""

    fun removeChart(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(CHART_PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(appWidgetId, "event_type_id"))
            .remove(key(appWidgetId, "chart_config_id"))
            .remove(key(appWidgetId, "event_type_name"))
            .remove(key(appWidgetId, "chart_title"))
            .remove(key(appWidgetId, "event_color"))
            .apply()
    }

    // ── QuickAdd ────────────────────────────────────────────────────────────

    private const val QUICK_ADD_PREFS = "lifelog_qa_widget"

    fun saveQuickAdd(
        context: Context,
        appWidgetId: Int,
        eventId: Long,
        eventName: String,
        eventColor: Int,
        eventIcon: String,
    ) {
        context.getSharedPreferences(QUICK_ADD_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(key(appWidgetId, "event_id"), eventId)
            .putString(key(appWidgetId, "event_name"), eventName)
            .putInt(key(appWidgetId, "event_color"), eventColor)
            .putString(key(appWidgetId, "event_icon"), eventIcon)
            .apply()
    }

    fun getQuickAddEventId(context: Context, appWidgetId: Int): Long =
        context.getSharedPreferences(QUICK_ADD_PREFS, Context.MODE_PRIVATE)
            .getLong(key(appWidgetId, "event_id"), 0L)

    fun getQuickAddEventName(context: Context, appWidgetId: Int): String =
        context.getSharedPreferences(QUICK_ADD_PREFS, Context.MODE_PRIVATE)
            .getString(key(appWidgetId, "event_name"), "Quick Add") ?: "Quick Add"

    fun removeQuickAdd(context: Context, appWidgetId: Int) {
        context.getSharedPreferences(QUICK_ADD_PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(appWidgetId, "event_id"))
            .remove(key(appWidgetId, "event_name"))
            .remove(key(appWidgetId, "event_color"))
            .remove(key(appWidgetId, "event_icon"))
            .apply()
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun key(appWidgetId: Int, field: String) = "${appWidgetId}_$field"
}
