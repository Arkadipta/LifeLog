package com.lifelog.app.ui.events

import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.ui.components.EmptyStatePlaceholder
import com.lifelog.app.ui.components.TagFilterRow
import com.lifelog.app.ui.theme.LocalAmoledColors
import com.lifelog.app.util.iconForName
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToEvent: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: EventsViewModel = hiltViewModel()
) {
    val eventTypes by viewModel.eventTypes.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val filterState by viewModel.filterState.collectAsStateWithLifecycle()
    val availableTags by viewModel.availableTags.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val animationsEnabled = remember {
        AndroidSettings.Global.getFloat(
            context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
    }
    var fabVisible by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            delay(200)
            fabVisible = true
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Events") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Rounded.Settings, "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = fabVisible,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    initialScale = 0.85f
                ) + fadeIn(tween(150)),
                exit = scaleOut(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    targetScale = 0.85f
                ) + fadeOut(tween(100))
            ) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToCreate,
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("New Event") }
                )
            }
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            DockedSearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = viewModel::setSearchQuery,
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text("Search events…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Rounded.Close, "Clear search")
                                }
                            }
                        }
                    )
                },
                expanded = false,
                onExpandedChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {}

            TagFilterRow(
                tags = availableTags,
                filterState = filterState,
                onFilterChange = viewModel::updateFilter,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            if (eventTypes.isEmpty() && searchQuery.isBlank() && !filterState.hasActiveFilters) {
                EmptyStatePlaceholder(
                    icon = Icons.Rounded.Add,
                    title = "No events yet",
                    subtitle = "Tap + to create your first event to track",
                    modifier = Modifier.fillMaxSize()
                )
            } else if (eventTypes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val message = when {
                        searchQuery.isNotBlank() && filterState.hasActiveFilters ->
                            "No events match \"$searchQuery\" with selected tags"
                        searchQuery.isNotBlank() -> "No events match \"$searchQuery\""
                        else -> "No events match the selected tags"
                    }
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(eventTypes, key = { it.id }) { eventType ->
                        EventTypeCard(
                            eventType = eventType,
                            onClick = { onNavigateToEvent(eventType.id) },
                            modifier = Modifier.animateItem()
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

}


@Composable
private fun EventTypeCard(
    eventType: EventType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmoled = LocalAmoledColors.current.isAmoled
    val color = Color(eventType.colorArgb)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = iconForName(eventType.iconName),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eventType.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (eventType.description.isNotBlank()) {
                    Text(
                        text = eventType.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (eventType.category.isNotBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                eventType.category,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "${eventType.entryCount} entries",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        }
    }
}
