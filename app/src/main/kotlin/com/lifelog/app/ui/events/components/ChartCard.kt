package com.lifelog.app.ui.events.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.ui.components.LifeLogCard
import com.lifelog.app.ui.theme.Sizing
import com.lifelog.app.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ChartCard(
    config: ChartConfig,
    data: ChartData,
    eventAccentColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    LifeLogCard(modifier = modifier.height(Sizing.chartCard)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            ChartCardHeader(config, data, onEdit, onDelete)

            Spacer(Modifier.height(Spacing.xs))

            val chartDescription = remember(config, data) { chartContentDescription(config, data) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .semantics { contentDescription = chartDescription }
            ) {
                when (data) {
                    is ChartData.Cartesian -> CartesianChartContent(data = data, eventAccentColor = eventAccentColor)
                    is ChartData.Pie -> PieChartContent(data = data)
                    ChartData.Empty, ChartData.InsufficientData -> ChartEmptyState(data = data)
                }
            }

            val legendItems = legendItems(data, eventAccentColor)
            if (legendItems.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                ChartLegend(items = legendItems)
            }
        }
    }
}

@Composable
private fun ChartCardHeader(
    config: ChartConfig,
    data: ChartData,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = config.title.ifBlank { config.type.displayName + " Chart" },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            anchoredCaption(data)?.let { caption ->
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        Box {
            var menuOpen by remember { mutableStateOf(false) }
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Rounded.MoreVert,
                    contentDescription = "Chart options",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit chart") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete chart") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

private fun legendItems(data: ChartData, eventAccentColor: Color): List<LegendItem> =
    when (data) {
        is ChartData.Cartesian ->
            // A single series is already identified by the card title.
            if (data.series.size < 2) emptyList()
            else resolveSeriesColors(data.series, eventAccentColor)
                .zip(data.series) { color, series ->
                    val label = if (series.unit.isBlank()) series.fieldName
                                else "${series.fieldName} (${series.unit})"
                    LegendItem(label, color)
                }
        is ChartData.Pie -> data.slices.map { LegendItem(it.label, Color(it.colorArgb)) }
        else -> emptyList()
    }

/** "as of <date>" when the window is anchored to the latest entry, else null. */
private fun anchoredCaption(data: ChartData): String? {
    val endMs = when (data) {
        is ChartData.Cartesian -> data.anchoredEndMs
        is ChartData.Pie -> data.anchoredEndMs
        else -> null
    } ?: return null
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val endYear = Calendar.getInstance().apply { timeInMillis = endMs }.get(Calendar.YEAR)
    val pattern = if (endYear == currentYear) "MMM d" else "MMM d, yyyy"
    return "as of " + SimpleDateFormat(pattern, Locale.getDefault()).format(Date(endMs))
}

private fun chartContentDescription(config: ChartConfig, data: ChartData): String =
    when (data) {
        is ChartData.Cartesian -> {
            val names = data.series.joinToString { it.fieldName }
            val anchored = anchoredCaption(data)?.let { ", $it" } ?: ""
            "${config.type.displayName} chart of $names$anchored"
        }
        is ChartData.Pie -> "Pie chart with ${data.slices.size} categories"
        else -> "${config.type.displayName} chart, no data"
    }
