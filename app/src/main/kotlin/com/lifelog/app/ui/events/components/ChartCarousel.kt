package com.lifelog.app.ui.events.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.StoredChartConfig
import com.lifelog.app.ui.components.SectionHeader
import com.lifelog.app.ui.theme.Spacing

@Composable
fun ChartCarousel(
    charts: List<StoredChartConfig>,
    chartDataMap: Map<String, ChartData>,
    eventType: EventType?,
    onAddChart: () -> Unit,
    onEditChart: (ChartConfig) -> Unit,
    onDeleteChart: (String) -> Unit,
    modifier: Modifier = Modifier,
    showAddCard: Boolean = true
) {
    val eventAccentColor = eventType?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Analytics",
            modifier = Modifier.padding(start = Spacing.screenEdge, bottom = Spacing.sm)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            contentPadding = PaddingValues(horizontal = Spacing.screenEdge)
        ) {
            items(charts, key = { it.id }) { stored ->
                val cardModifier = Modifier
                    .width(280.dp)
                    .animateItem()
                when (stored) {
                    is StoredChartConfig.Readable -> ChartCard(
                        config = stored.config,
                        data = chartDataMap[stored.id] ?: ChartData.Empty,
                        eventAccentColor = eventAccentColor,
                        onEdit = { onEditChart(stored.config) },
                        onDelete = { onDeleteChart(stored.id) },
                        modifier = cardModifier
                    )
                    // A corrupt row keeps its slot: there is nothing left to
                    // edit, so the card only explains itself and offers delete.
                    is StoredChartConfig.Unreadable -> UnreadableChartCard(
                        onDelete = { onDeleteChart(stored.id) },
                        modifier = cardModifier
                    )
                }
            }
            if (showAddCard) {
                item(key = "add_chart") {
                    AddChartCard(onClick = onAddChart)
                }
            }
        }
    }
}
