package com.lifelog.app.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.ui.components.IconTile
import com.lifelog.app.ui.components.LifeLogCard
import com.lifelog.app.ui.components.SheetHeader
import com.lifelog.app.ui.theme.Sizing
import com.lifelog.app.ui.theme.Spacing
import com.lifelog.app.util.formatEntryCount
import com.lifelog.app.util.iconForName

/**
 * Searchable bottom-sheet picker for linking a reminder to an event type.
 *
 * Replaces the old radio-list dialog: a sticky search field filters the full list
 * instantly (client-side — the list is already in memory), each event reads with its
 * own icon and color for fast recognition, and the current link is highlighted. A
 * pinned "All Events (Global)" row clears the link. Single-selection — tapping a row
 * commits and closes, matching the app's other sheets.
 *
 * @param selectedEventTypeId currently linked event id, or null for the global option.
 * @param onSelect receives the chosen [EventType], or null for the global option.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventPickerSheet(
    eventTypes: List<EventType>,
    selectedEventTypeId: Long?,
    onSelect: (EventType?) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, eventTypes) {
        val q = query.trim()
        if (q.isBlank()) eventTypes
        else eventTypes.filter {
            it.name.contains(q, ignoreCase = true) || it.category.contains(q, ignoreCase = true)
        }
    }

    // Open scrolled to the linked event so it's visible without hunting. Offset by 1
    // for the pinned global row; only scrolls when a real event is selected.
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val idx = eventTypes.indexOfFirst { it.id == selectedEventTypeId }
        if (idx >= 0) listState.scrollToItem(idx + 1)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .navigationBarsPadding()
        ) {
            SheetHeader(title = "Link to Event", onClose = onDismiss)

            // Sticky search — stays put above the scrolling list so filtering never
            // requires scrolling back to the top.
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.sheetEdge)
                    .padding(bottom = Spacing.sm),
                placeholder = { Text("Search events…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .imePadding(),
                contentPadding = PaddingValues(
                    horizontal = Spacing.sheetEdge,
                    vertical = Spacing.sm
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Global option — only while not searching, so a search shows only matches.
                if (query.isBlank()) {
                    item(key = "__global__") {
                        EventPickerRow(
                            icon = Icons.Rounded.Public,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "All Events (Global)",
                            meta = "Not linked to a specific event",
                            selected = selectedEventTypeId == null,
                            onClick = {
                                onSelect(null)
                                onDismiss()
                            }
                        )
                    }
                }

                if (filtered.isEmpty()) {
                    item(key = "__empty__") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xxl),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (query.isBlank())
                                    "No events yet. Create an event to link it here."
                                else
                                    "No events match “${query.trim()}”",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { eventType ->
                        EventPickerRow(
                            icon = iconForName(eventType.iconName),
                            iconTint = Color(eventType.colorArgb),
                            title = eventType.name,
                            meta = eventType.category.ifBlank { formatEntryCount(eventType.entryCount) },
                            selected = eventType.id == selectedEventTypeId,
                            onClick = {
                                onSelect(eventType)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * One selectable event row, built from the same [LifeLogCard] + [IconTile] primitives as
 * the events list so it reads identically. Selected rows shift to a tonal container and
 * carry a trailing check; the icon tile keeps the event's accent color either way.
 */
@Composable
private fun EventPickerRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    meta: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    LifeLogCard(
        onClick = onClick,
        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                         else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                       else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            IconTile(icon = icon, tint = iconTint, size = Sizing.iconTileSmall)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (meta != null) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}
