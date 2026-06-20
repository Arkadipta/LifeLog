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
import com.lifelog.app.ui.components.SectionHeader
import com.lifelog.app.ui.theme.Spacing

@Composable
fun ChartCarousel(
    charts: List<ChartConfig>,
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
            items(charts, key = { it.id }) { config ->
                ChartCard(
                    config = config,
                    data = chartDataMap[config.id] ?: ChartData.Empty,
                    eventAccentColor = eventAccentColor,
                    onEdit = { onEditChart(config) },
                    onDelete = { onDeleteChart(config.id) },
                    modifier = Modifier
                        .width(280.dp)
                        .animateItem()
                )
            }
            if (showAddCard) {
                item(key = "add_chart") {
                    AddChartCard(onClick = onAddChart)
                }
            }
        }
    }
}
