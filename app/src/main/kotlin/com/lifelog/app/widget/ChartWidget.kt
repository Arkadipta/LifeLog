package com.lifelog.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityIntent
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
import com.lifelog.app.data.repository.ChartRepository
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.ChartDataProcessor
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.ui.theme.DarkColorScheme
import com.lifelog.app.ui.theme.LightColorScheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ChartWidgetEntryPoint {
    fun eventRepository(): EventRepository
    fun chartRepository(): ChartRepository
}

class ChartWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(160.dp, 160.dp),
            DpSize(280.dp, 200.dp),
            DpSize(380.dp, 280.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ChartWidgetEntryPoint::class.java
        )
        val eventRepo = entryPoint.eventRepository()
        val chartRepo = entryPoint.chartRepository()

        // Config resolution priority:
        //   1. SharedPreferences — written by config activity; the only reliable path for
        //      brand-new widgets where the GlanceId registry entry doesn't exist yet.
        //   2. Glance DataStore fallback — for widgets configured before this deployment.
        val (eventTypeId, chartConfigId, eventTypeName, chartTitle) =
            resolveConfig(context, id)

        val renderData: ChartRenderData = if (eventTypeId != 0L && chartConfigId.isNotBlank()) {
            try {
                val config = chartRepo.getChart(chartConfigId)
                val eventType = eventRepo.getEventType(eventTypeId)
                if (config != null && eventType != null) {
                    val entries = eventRepo.getAllEntriesForEventType(eventTypeId)
                    val chartData = ChartDataProcessor.process(config, entries, eventType.fields)
                    ChartRenderData.Ready(
                        chartData = chartData,
                        eventType = eventType,
                        chartTitle = chartTitle.ifBlank { eventTypeName }
                    )
                } else {
                    ChartRenderData.NotConfigured
                }
            } catch (e: Exception) {
                ChartRenderData.Error
            }
        } else {
            ChartRenderData.NotConfigured
        }

        provideContent {
            GlanceTheme(colors = ColorProviders(light = LightColorScheme, dark = DarkColorScheme)) {
                ChartWidgetContent(renderData, context, eventTypeId)
            }
        }
    }

    private suspend fun resolveConfig(
        context: Context,
        id: GlanceId,
    ): ChartConfig {
        // 1 — SharedPreferences (set by config activity for any widget, new or existing)
        try {
            val awId = GlanceAppWidgetManager(context).getAppWidgetId(id)
            if (awId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val spId = WidgetPrefs.getChartEventTypeId(context, awId)
                val spChart = WidgetPrefs.getChartConfigId(context, awId)
                if (spId != 0L && spChart.isNotBlank()) {
                    return ChartConfig(
                        eventTypeId = spId,
                        chartConfigId = spChart,
                        eventTypeName = WidgetPrefs.getChartEventTypeName(context, awId),
                        chartTitle = WidgetPrefs.getChartTitle(context, awId),
                    )
                }
            }
        } catch (_: Exception) {}

        // 2 — Glance DataStore fallback (widgets configured before SharedPrefs migration)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        return ChartConfig(
            eventTypeId = prefs[PREF_EVENT_TYPE_ID] ?: 0L,
            chartConfigId = prefs[PREF_CHART_CONFIG_ID] ?: "",
            eventTypeName = prefs[PREF_EVENT_TYPE_NAME] ?: "",
            chartTitle = prefs[PREF_CHART_TITLE] ?: "",
        )
    }

    private data class ChartConfig(
        val eventTypeId: Long,
        val chartConfigId: String,
        val eventTypeName: String,
        val chartTitle: String,
    )

    companion object {
        val PREF_EVENT_TYPE_ID = longPreferencesKey("cw_event_type_id")
        val PREF_CHART_CONFIG_ID = stringPreferencesKey("cw_chart_config_id")
        val PREF_EVENT_TYPE_NAME = stringPreferencesKey("cw_event_type_name")
        val PREF_CHART_TITLE = stringPreferencesKey("cw_chart_title")
        val PREF_EVENT_COLOR = intPreferencesKey("cw_event_color")
    }
}

private sealed interface ChartRenderData {
    data class Ready(
        val chartData: ChartData,
        val eventType: EventType,
        val chartTitle: String,
    ) : ChartRenderData
    data object NotConfigured : ChartRenderData
    data object Error : ChartRenderData
}

@Composable
private fun ChartWidgetContent(
    data: ChartRenderData,
    context: Context,
    eventTypeId: Long,
) {
    val size = LocalSize.current
    val isSmall = size.width < 200.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(if (isSmall) 8.dp else 10.dp)
        ) {
            when (data) {
                is ChartRenderData.Ready -> {
                    if (!isSmall && data.chartTitle.isNotBlank()) {
                        Text(
                            data.chartTitle,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = GlanceTheme.colors.primary
                            )
                        )
                        Spacer(GlanceModifier.height(4.dp))
                    }

                    val chartData = data.chartData
                    if (chartData is ChartData.Empty || chartData is ChartData.InsufficientData) {
                        Box(
                            modifier = GlanceModifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Not enough data",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    color = GlanceTheme.colors.onSurfaceVariant
                                )
                            )
                        }
                    } else {
                        val density = context.resources.displayMetrics.density
                        val bitmapW = (size.width.value * density).toInt().coerceAtLeast(160)
                        val headerH =
                            if (!isSmall && data.chartTitle.isNotBlank()) (28 * density).toInt()
                            else 0
                        val bitmapH =
                            ((size.height.value * density).toInt() - headerH).coerceAtLeast(80)

                        val nightMask = android.content.res.Configuration.UI_MODE_NIGHT_MASK
                        val nightYes = android.content.res.Configuration.UI_MODE_NIGHT_YES
                        val isDark =
                            context.resources.configuration.uiMode and nightMask == nightYes

                        val bitmap: Bitmap = ChartBitmapRenderer.render(
                            data = chartData,
                            widthPx = bitmapW,
                            heightPx = bitmapH,
                            isDark = isDark,
                            density = density
                        )
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = data.chartTitle,
                            modifier = GlanceModifier.fillMaxSize()
                        )
                    }
                }

                ChartRenderData.NotConfigured -> {
                    Box(
                        modifier = GlanceModifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "LifeLog Chart",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = GlanceTheme.colors.primary
                                )
                            )
                            Spacer(GlanceModifier.height(4.dp))
                            Text(
                                "Long-press to configure",
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    color = GlanceTheme.colors.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                ChartRenderData.Error -> {
                    Box(
                        modifier = GlanceModifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Error loading chart",
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = GlanceTheme.colors.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }

        // Plus button overlay for quick entry
        if (data is ChartRenderData.Ready && eventTypeId != 0L) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.BottomEnd
            ) {
                val addIntent = Intent(context, QuickAddActivity::class.java).apply {
                    putExtra(QuickAddActivity.EXTRA_EVENT_ID, eventTypeId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                Box(
                    modifier = GlanceModifier
                        .padding(8.dp)
                        .background(GlanceTheme.colors.primaryContainer)
                        .clickable(actionStartActivityIntent(addIntent)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+",
                        style = TextStyle(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onPrimaryContainer
                        ),
                        modifier = GlanceModifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

class ChartWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChartWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_DELETED) {
            val id = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
            if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetPrefs.removeChart(context, id)
            }
        }
    }
}
