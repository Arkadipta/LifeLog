package com.lifelog.app.ui.events.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.ChartData

@Composable
fun ChartEmptyState(data: ChartData, modifier: Modifier = Modifier) {
    val stale = data is ChartData.StaleConfig
    val icon: ImageVector = when {
        stale -> Icons.Rounded.WarningAmber
        data is ChartData.InsufficientData -> Icons.Rounded.SearchOff
        else -> Icons.Rounded.BarChart
    }
    val message = when {
        stale -> "This chart references a field that is no longer numeric and can't be " +
            "rendered. Edit or delete it from the chart menu."
        data is ChartData.InsufficientData -> "Not enough data yet"
        else -> "No data for this chart"
    }
    // Stale configs are an actionable problem, so they read in the attention-
    // grabbing tertiary tone; ordinary empties stay muted.
    val tint = if (stale) MaterialTheme.colorScheme.tertiary
               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val textColor = if (stale) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = tint
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
