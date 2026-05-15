package com.lifelog.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.ChartRepository
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartType
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.ui.theme.LifeLogTheme
import com.lifelog.app.util.iconForName
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChartWidgetConfigViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val chartRepository: ChartRepository
) : ViewModel() {

    val eventTypes: StateFlow<List<EventType>> = eventRepository.observeAllEventTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _charts = MutableStateFlow<List<ChartConfig>>(emptyList())
    val charts: StateFlow<List<ChartConfig>> = _charts.asStateFlow()

    fun loadChartsForEvent(eventTypeId: Long) {
        viewModelScope.launch {
            _charts.value = chartRepository.getChartsForEvent(eventTypeId)
        }
    }
}

private sealed interface ConfigStep {
    data object PickEvent : ConfigStep
    data class PickChart(val eventType: EventType) : ConfigStep
}

@AndroidEntryPoint
class ChartWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_CANCELED)
            finish()
            return
        }

        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        setContent {
            LifeLogTheme {
                ChartConfigFlow(
                    onComplete = { eventType, chart ->
                        lifecycleScope.launch {
                            val chartTitle = chart.title.ifBlank { eventType.name }

                            // Always write SharedPreferences — reliable for new widgets
                            // where the GlanceId registry entry may not exist yet.
                            WidgetPrefs.saveChart(
                                context = this@ChartWidgetConfigActivity,
                                appWidgetId = appWidgetId,
                                eventTypeId = eventType.id,
                                chartConfigId = chart.id,
                                eventTypeName = eventType.name,
                                chartTitle = chartTitle,
                                eventColor = eventType.colorArgb,
                            )

                            // Also write Glance DataStore when GlanceId is available.
                            val manager = GlanceAppWidgetManager(this@ChartWidgetConfigActivity)
                            val glanceId = manager.getGlanceIds(ChartWidget::class.java)
                                .firstOrNull { manager.getAppWidgetId(it) == appWidgetId }

                            if (glanceId != null) {
                                updateAppWidgetState(
                                    this@ChartWidgetConfigActivity,
                                    PreferencesGlanceStateDefinition,
                                    glanceId
                                ) { prefs ->
                                    prefs.toMutablePreferences().apply {
                                        this[ChartWidget.PREF_EVENT_TYPE_ID] = eventType.id
                                        this[ChartWidget.PREF_CHART_CONFIG_ID] = chart.id
                                        this[ChartWidget.PREF_EVENT_TYPE_NAME] = eventType.name
                                        this[ChartWidget.PREF_CHART_TITLE] = chartTitle
                                        this[ChartWidget.PREF_EVENT_COLOR] = eventType.colorArgb
                                    }
                                }
                                ChartWidget().update(this@ChartWidgetConfigActivity, glanceId)
                            } else {
                                sendBroadcast(
                                    Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                                        component = ComponentName(
                                            this@ChartWidgetConfigActivity,
                                            ChartWidgetReceiver::class.java
                                        )
                                        putExtra(
                                            AppWidgetManager.EXTRA_APPWIDGET_IDS,
                                            intArrayOf(appWidgetId)
                                        )
                                    }
                                )
                            }

                            setResult(
                                RESULT_OK,
                                Intent().putExtra(
                                    AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId
                                )
                            )
                            finish()
                        }
                    },
                    onCancel = {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChartConfigFlow(
    onComplete: (EventType, ChartConfig) -> Unit,
    onCancel: () -> Unit,
    viewModel: ChartWidgetConfigViewModel = hiltViewModel()
) {
    var step by remember { mutableStateOf<ConfigStep>(ConfigStep.PickEvent) }
    val eventTypes by viewModel.eventTypes.collectAsStateWithLifecycle()
    val charts by viewModel.charts.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (step) {
                            ConfigStep.PickEvent -> "Choose Event"
                            is ConfigStep.PickChart -> "Choose Chart"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (step) {
                            ConfigStep.PickEvent -> onCancel()
                            is ConfigStep.PickChart -> step = ConfigStep.PickEvent
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (val currentStep = step) {
            ConfigStep.PickEvent -> {
                PickEventStep(
                    eventTypes = eventTypes,
                    padding = padding,
                    onEventSelected = { eventType ->
                        viewModel.loadChartsForEvent(eventType.id)
                        step = ConfigStep.PickChart(eventType)
                    }
                )
            }
            is ConfigStep.PickChart -> {
                PickChartStep(
                    eventType = currentStep.eventType,
                    charts = charts,
                    padding = padding,
                    onChartSelected = { chart -> onComplete(currentStep.eventType, chart) }
                )
            }
        }
    }
}

@Composable
private fun PickEventStep(
    eventTypes: List<EventType>,
    padding: PaddingValues,
    onEventSelected: (EventType) -> Unit,
) {
    if (eventTypes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No events yet.\nCreate events and add charts first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Select an event to display its chart:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        items(eventTypes, key = { it.id }) { eventType ->
            ChartEventPickerCard(eventType = eventType, onClick = { onEventSelected(eventType) })
        }
    }
}

@Composable
private fun PickChartStep(
    eventType: EventType,
    charts: List<ChartConfig>,
    padding: PaddingValues,
    onChartSelected: (ChartConfig) -> Unit,
) {
    if (charts.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "No charts for \"${eventType.name}\"",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Open the event in the app and add\na chart before using this widget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Select a chart from \"${eventType.name}\":",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        items(charts, key = { it.id }) { chart ->
            ChartPickerCard(
                chart = chart,
                eventColor = Color(eventType.colorArgb),
                onClick = { onChartSelected(chart) }
            )
        }
    }
}

@Composable
private fun ChartEventPickerCard(eventType: EventType, onClick: () -> Unit) {
    val color = Color(eventType.colorArgb)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconForName(eventType.iconName),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    eventType.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (eventType.description.isNotBlank()) {
                    Text(
                        eventType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    "${eventType.entryCount} entries",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChartPickerCard(chart: ChartConfig, eventColor: Color, onClick: () -> Unit) {
    val typeLabel = when (chart.type) {
        ChartType.LINE -> "Line"
        ChartType.BAR -> "Bar"
        ChartType.PIE -> "Pie"
    }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = eventColor.copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        when (chart.type) {
                            ChartType.LINE -> "〜"
                            ChartType.BAR -> "▊"
                            ChartType.PIE -> "◕"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = eventColor
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chart.title.ifBlank { "$typeLabel Chart" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "$typeLabel · ${if (chart.timeRangeDays == null) "All time" else "${chart.timeRangeDays}d"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
