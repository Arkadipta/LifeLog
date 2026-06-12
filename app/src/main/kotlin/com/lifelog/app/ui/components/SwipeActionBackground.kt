package com.lifelog.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Background revealed behind swipeable cards. Start-to-end arms Edit on a
 * primary container, end-to-start arms Delete on an error container; the
 * container only colors in once the swipe passes the dismiss threshold.
 * Lists that enable a single direction get just that action for free.
 */
@Composable
fun RowScope.SwipeActionBackground(state: SwipeToDismissBoxState) {
    val color by animateColorAsState(
        targetValue = when (state.targetValue) {
            SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
            SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
            SwipeToDismissBoxValue.Settled -> Color.Transparent
        },
        label = "swipe_action_bg"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color, MaterialTheme.shapes.large)
            .padding(horizontal = 20.dp)
    ) {
        when (state.targetValue) {
            SwipeToDismissBoxValue.StartToEnd -> Icon(
                Icons.Rounded.Edit,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            SwipeToDismissBoxValue.EndToStart -> Icon(
                Icons.Rounded.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
            SwipeToDismissBoxValue.Settled -> Unit
        }
    }
}
