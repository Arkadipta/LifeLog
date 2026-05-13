package com.lifelog.app.ui.events.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.ChartData
import com.lifelog.app.domain.model.ChartType
import com.lifelog.app.ui.theme.LocalAmoledColors

@Composable
fun ChartCard(
    config: ChartConfig,
    data: ChartData,
    eventAccentColor: Color,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAmoled = LocalAmoledColors.current.isAmoled

    Card(
        modifier = modifier.height(220.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color.Black else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (isAmoled) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = config.title.ifBlank {
                        config.type.displayName + " Chart"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Edit chart",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = "Delete chart",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Box(modifier = Modifier.fillMaxSize()) {
                when (data) {
                    is ChartData.Cartesian -> CartesianChartContent(data = data, eventAccentColor = eventAccentColor)
                    is ChartData.Pie -> PieChartContent(data = data)
                    ChartData.Empty, ChartData.InsufficientData -> ChartEmptyState(data = data)
                }
            }
        }
    }
}
