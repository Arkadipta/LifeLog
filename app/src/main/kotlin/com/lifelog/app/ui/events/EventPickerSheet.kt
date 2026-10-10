package com.lifelog.app.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.ui.components.IconTile
import com.lifelog.app.ui.theme.Sizing
import com.lifelog.app.ui.theme.Spacing
import com.lifelog.app.util.iconForName

/**
 * Bottom sheet listing every event type so the user can pick which one to log
 * an entry for — the in-app landing point of the launcher's "Log Entry"
 * shortcut, which arrives without an event id (unlike the QuickAdd widget,
 * which is bound to one event when placed). Rows follow the Events list's
 * order (the user's persisted sort).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventPickerSheet(
    onPick: (eventTypeId: Long) -> Unit,
    onCreateEvent: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: EventsViewModel = hiltViewModel()
) {
    val eventTypes by viewModel.eventTypes.collectAsStateWithLifecycle()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text(
                text = "Log an entry",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(
                    start = Spacing.sheetEdge,
                    end = Spacing.sheetEdge,
                    bottom = Spacing.sm
                )
            )

            if (eventTypes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.sheetEdge, vertical = Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    Text(
                        text = "No events yet — create one to start logging entries.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onCreateEvent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(Sizing.cta)
                    ) {
                        Text("Create an event")
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = Spacing.lg)
                ) {
                    eventTypes.forEach { eventType ->
                        EventPickerRow(
                            eventType = eventType,
                            onClick = { onPick(eventType.id) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

@Composable
private fun EventPickerRow(
    eventType: EventType,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = Sizing.iconTile + Spacing.lg)
            .padding(horizontal = Spacing.sheetEdge, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        IconTile(
            icon = iconForName(eventType.iconName),
            tint = Color(eventType.colorArgb),
            size = Sizing.iconTileSmall
        )
        Text(
            text = eventType.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
