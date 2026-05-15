package com.lifelog.app.widget

import android.appwidget.AppWidgetManager
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.ui.theme.LifeLogTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val ALL_EVENTS_ID = 0L

@AndroidEntryPoint
class TimelineWidgetConfigActivity : ComponentActivity() {

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
                TimelineConfigScreen(
                    onSelected = { eventType ->
                        lifecycleScope.launch {
                            val eventId = eventType?.id ?: ALL_EVENTS_ID
                            val eventName = eventType?.name ?: ""

                            WidgetPrefs.saveTimeline(
                                context = this@TimelineWidgetConfigActivity,
                                appWidgetId = appWidgetId,
                                eventId = eventId,
                                eventName = eventName,
                            )

                            // For existing widgets: write DataStore and trigger a direct update
                            // (prevents double-scheduling from both this and system's post-RESULT_OK broadcast).
                            // For new widgets (glanceId == null): SP is enough;
                            // system sends ACTION_APPWIDGET_UPDATE after RESULT_OK.
                            val manager = GlanceAppWidgetManager(this@TimelineWidgetConfigActivity)
                            val glanceId = manager.getGlanceIds(TimelineWidget::class.java)
                                .firstOrNull { manager.getAppWidgetId(it) == appWidgetId }

                            if (glanceId != null) {
                                updateAppWidgetState(
                                    this@TimelineWidgetConfigActivity,
                                    PreferencesGlanceStateDefinition,
                                    glanceId
                                ) { prefs ->
                                    prefs.toMutablePreferences().apply {
                                        this[TimelineWidget.PREF_EVENT_ID] = eventId
                                        this[TimelineWidget.PREF_EVENT_NAME] = eventName
                                    }
                                }
                                TimelineWidget().update(this@TimelineWidgetConfigActivity, glanceId)
                            }

                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
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
private fun TimelineConfigScreen(
    onSelected: (EventType?) -> Unit,
    onCancel: () -> Unit,
    viewModel: QuickAddWidgetConfigViewModel = hiltViewModel()
) {
    val eventTypes by viewModel.eventTypes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configure Timeline Widget", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cancel")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "Choose which entries to display:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            item {
                Card(
                    onClick = { onSelected(null) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "All Events",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Show the most recent entries from all event types",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            if (eventTypes.isNotEmpty()) {
                item {
                    Text(
                        "Or choose a specific event:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                items(eventTypes, key = { it.id }) { eventType ->
                    EventPickerCard(
                        eventType = eventType,
                        onClick = { onSelected(eventType) }
                    )
                }
            }
        }
    }
}
