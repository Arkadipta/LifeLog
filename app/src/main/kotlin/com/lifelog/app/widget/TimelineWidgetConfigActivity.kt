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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TimelineWidgetConfigViewModel @Inject constructor(
    private val repository: EventRepository
) : ViewModel() {
    val eventTypes: StateFlow<List<EventType>> = repository.observeAllEventTypes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories

    init {
        viewModelScope.launch {
            _categories.value = repository.getAllCategories()
        }
    }
}

@AndroidEntryPoint
class TimelineWidgetConfigActivity : ComponentActivity() {

    companion object {
        private const val TAG = "TimelineWidgetConfig"
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

        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))

        setContent {
            LifeLogTheme {
                TimelineConfigScreen(
                    onConfigured = { filterMode, eventTypeId, eventName, tag, eventColor, eventIcon ->
                        lifecycleScope.launch {
                            val manager = GlanceAppWidgetManager(this@TimelineWidgetConfigActivity)
                            // getGlanceIdBy() creates a state entry for this appWidgetId even on
                            // first placement (before provideGlance has ever run). Using
                            // getGlanceIds().firstOrNull() would return null for brand-new
                            // widgets, silently skipping the write and leaving the widget showing
                            // the unconfigured placeholder forever.
                            val glanceId = manager.getGlanceIdBy(appWidgetId)
                            // Diagnostic: knownIds is the set of widgets already registered
                            // in the AppWidget host. If it is EMPTY here, the widget has not
                            // yet been committed to the home screen (initial placement). In
                            // that case the update() call below may be silently dropped by
                            // AppWidgetManager; onAppWidgetOptionsChanged in the receiver will
                            // fire the correct render once the launcher adds the widget.
                            val knownIds = manager.getGlanceIds(TimelineWidget::class.java)
                            logD(TAG) {
                                "onConfigured: appWidgetId=$appWidgetId glanceId=$glanceId " +
                                "knownIds=$knownIds widgetAlreadyInHost=${knownIds.isNotEmpty()} " +
                                "filterMode=$filterMode eventId=$eventTypeId " +
                                "eventName='$eventName' tag='$tag'"
                            }

                            updateAppWidgetState(
                                this@TimelineWidgetConfigActivity,
                                PreferencesGlanceStateDefinition,
                                glanceId
                            ) { prefs ->
                                prefs.toMutablePreferences().apply {
                                    this[TimelineWidget.PREF_FILTER_MODE] = filterMode
                                    this[TimelineWidget.PREF_EVENT_ID]    = eventTypeId
                                    this[TimelineWidget.PREF_EVENT_NAME]  = eventName
                                    this[TimelineWidget.PREF_TAG]         = tag
                                    this[TimelineWidget.PREF_EVENT_COLOR] = eventColor
                                    this[TimelineWidget.PREF_EVENT_ICON]  = eventIcon
                                }
                            }
                            logD(TAG) { "onConfigured: state written, triggering update ts=${System.currentTimeMillis()}" }
                            TimelineWidget().update(this@TimelineWidgetConfigActivity, glanceId)
                            logD(TAG) { "onConfigured: update() returned ts=${System.currentTimeMillis()}" }

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

private enum class ConfigStep { FILTER_MODE, PICK_EVENT, PICK_TAG }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineConfigScreen(
    onConfigured: (
        filterMode: String,
        eventTypeId: Long,
        eventName: String,
        tag: String,
        eventColor: Int,
        eventIcon: String
    ) -> Unit,
    onCancel: () -> Unit,
    viewModel: TimelineWidgetConfigViewModel = hiltViewModel()
) {
    val eventTypes by viewModel.eventTypes.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var step by remember { mutableStateOf(ConfigStep.FILTER_MODE) }
    var selectedMode by remember { mutableStateOf(TimelineWidget.FILTER_ALL) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (step) {
                            ConfigStep.FILTER_MODE -> "Timeline Widget"
                            ConfigStep.PICK_EVENT -> "Select Event"
                            ConfigStep.PICK_TAG -> "Select Category"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (step == ConfigStep.FILTER_MODE) onCancel() else step = ConfigStep.FILTER_MODE
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when (step) {
            ConfigStep.FILTER_MODE -> FilterModeStep(
                modifier = Modifier.padding(padding),
                onModeSelected = { mode ->
                    selectedMode = mode
                    when (mode) {
                        TimelineWidget.FILTER_ALL -> onConfigured(
                            mode, 0L, "", "", EventType.DEFAULT_COLOR, "star"
                        )
                        TimelineWidget.FILTER_EVENT -> step = ConfigStep.PICK_EVENT
                        TimelineWidget.FILTER_TAG -> step = ConfigStep.PICK_TAG
                    }
                }
            )

            ConfigStep.PICK_EVENT -> EventPickStep(
                modifier = Modifier.padding(padding),
                eventTypes = eventTypes,
                onEventSelected = { et ->
                    onConfigured(selectedMode, et.id, et.name, "", et.colorArgb, et.iconName)
                }
            )

            ConfigStep.PICK_TAG -> TagPickStep(
                modifier = Modifier.padding(padding),
                categories = categories,
                onTagSelected = { tag ->
                    onConfigured(selectedMode, 0L, "", tag, EventType.DEFAULT_COLOR, "star")
                }
            )
        }
    }
}

@Composable
private fun FilterModeStep(
    modifier: Modifier = Modifier,
    onModeSelected: (String) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Choose what the Timeline widget displays:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))

        FilterModeCard(
            title = "All Events",
            description = "Show the most recent entries across all your events",
            onClick = { onModeSelected(TimelineWidget.FILTER_ALL) }
        )
        FilterModeCard(
            title = "Specific Event",
            description = "Show only entries for one chosen event type",
            onClick = { onModeSelected(TimelineWidget.FILTER_EVENT) }
        )
        FilterModeCard(
            title = "By Category",
            description = "Show entries from events sharing a common category tag",
            onClick = { onModeSelected(TimelineWidget.FILTER_TAG) }
        )
    }
}

@Composable
private fun FilterModeCard(title: String, description: String, onClick: () -> Unit) {
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
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun EventPickStep(
    modifier: Modifier = Modifier,
    eventTypes: List<EventType>,
    onEventSelected: (EventType) -> Unit
) {
    if (eventTypes.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No events yet.\nCreate an event first, then configure the widget.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Select which event to show in the widget:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        items(eventTypes, key = { it.id }) { et ->
            EventPickCard(eventType = et, onClick = { onEventSelected(et) })
        }
    }
}

@Composable
private fun EventPickCard(eventType: EventType, onClick: () -> Unit) {
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

@Composable
private fun TagPickStep(
    modifier: Modifier = Modifier,
    categories: List<String>,
    onTagSelected: (String) -> Unit
) {
    if (categories.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "No categories found.\nAssign categories to your events first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "Select a category to filter by:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
        items(categories) { tag ->
            Card(
                onClick = { onTagSelected(tag) },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Text(
                    tag,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}
