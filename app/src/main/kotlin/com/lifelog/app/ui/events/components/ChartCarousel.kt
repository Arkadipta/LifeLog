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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.EventType

@Composable
fun ChartCarousel(
    charts: List<ChartConfig>,
    chartDataMap: Map<String, ChartData>,
    eventType: EventType?,
    onAddChart: () -> Unit,
    onEditChart: (ChartConfig) -> Unit,
    onDeleteChart: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val eventAccentColor = eventType?.let { Color(it.colorArgb) } ?: MaterialTheme.colorScheme.primary

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Analytics",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(charts, key = { it.id }) { config ->
                val chartColor = config.colorArgb?.let { Color(it) } ?: eventAccentColor
                ChartCard(
                    config = config,
                    data = chartDataMap[config.id] ?: ChartData.Empty,
                    accentColor = chartColor,
                    onEdit = { onEditChart(config) },
                    onDelete = { onDeleteChart(config.id) },
                    modifier = Modifier
                        .width(280.dp)
                        .animateItem()
                )
            }
            item(key = "add_chart") {
                AddChartCard(onClick = onAddChart)
            }
        }
    }
}
