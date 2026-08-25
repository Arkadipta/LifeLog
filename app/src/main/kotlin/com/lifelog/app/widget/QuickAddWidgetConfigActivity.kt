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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.ui.theme.LifeLogTheme
import com.lifelog.app.ui.components.IconTile
import com.lifelog.app.util.iconForName
import com.lifelog.app.util.logD
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickAddWidgetConfigViewModel @Inject constructor(
    repository: EventRepository
) : ViewModel() {
    val eventTypes: StateFlow<List<EventType>> = repository.observeAllEventTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@AndroidEntryPoint
class QuickAddWidgetConfigActivity : ComponentActivity() {

    companion object {
        private const val TAG = "QuickAddWidgetConfig"
    }

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

        // Default: canceled (widget not placed if user backs out)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        setContent {
            LifeLogTheme {
                ConfigScreen(
                    onEventSelected = { eventType ->
                        lifecycleScope.launch {
                            val manager = GlanceAppWidgetManager(this@QuickAddWidgetConfigActivity)
                            // getGlanceIdBy() creates the state entry for this appWidgetId even
                            // on first placement (before Glance has initialised a session).
                            // getGlanceIds().firstOrNull() would return null for a brand-new
                            // widget, silently skipping the write and leaving the widget without
                            // any configured event.
                            val glanceId = manager.getGlanceIdBy(appWidgetId)
                            // Diagnostic: knownIds empty = widget not yet in host (initial
                            // placement). update() below may be silently dropped; the
                            // onAppWidgetOptionsChanged callback in the receiver will fire
                            // the correct render once the launcher adds the widget.
                            val knownIds = manager.getGlanceIds(QuickAddWidget::class.java)
                            logD(TAG) {
                                "onEventSelected: appWidgetId=$appWidgetId glanceId=$glanceId " +
                                "knownIds=$knownIds widgetAlreadyInHost=${knownIds.isNotEmpty()} " +
                                "eventId=${eventType.id} eventName='${eventType.name}'"
                            }

                            updateAppWidgetState(
                                this@QuickAddWidgetConfigActivity,
                                PreferencesGlanceStateDefinition,
                                glanceId
                            ) { prefs ->
                                prefs.toMutablePreferences().apply {
                                    this[QuickAddWidget.PREF_EVENT_ID]    = eventType.id
                                    this[QuickAddWidget.PREF_EVENT_NAME]  = eventType.name
                                    this[QuickAddWidget.PREF_EVENT_COLOR] = eventType.colorArgb
                                    this[QuickAddWidget.PREF_EVENT_ICON]  = eventType.iconName
                                }
                            }
                            logD(TAG) { "onEventSelected: state written, triggering update ts=${System.currentTimeMillis()}" }
                            QuickAddWidget().update(this@QuickAddWidgetConfigActivity, glanceId)
                            logD(TAG) { "onEventSelected: update() returned ts=${System.currentTimeMillis()}" }

                            setResult(
                                RESULT_OK,
                                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
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
private fun ConfigScreen(
    onEventSelected: (EventType) -> Unit,
    onCancel: () -> Unit,
    viewModel: QuickAddWidgetConfigViewModel = hiltViewModel()
) {
    val eventTypes by viewModel.eventTypes.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose Event for Widget", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Cancel")
                    }
                }
            )
        }
    ) { padding ->
        if (eventTypes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No events yet.\nCreate an event first, then add the widget.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "Select which event this widget will log to:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(eventTypes, key = { it.id }) { eventType ->
                    EventPickerCard(
                        eventType = eventType,
                        onClick = { onEventSelected(eventType) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EventPickerCard(eventType: EventType, onClick: () -> Unit) {
    val color = Color(eventType.colorArgb)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconTile(
                icon = iconForName(eventType.iconName),
                tint = color,
                size = 44.dp
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(eventType.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (eventType.description.isNotBlank()) {
                    Text(
                        eventType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
