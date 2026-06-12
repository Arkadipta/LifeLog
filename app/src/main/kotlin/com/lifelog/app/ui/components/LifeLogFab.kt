package com.lifelog.app.ui.components

import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.lifelog.app.ui.theme.Motion
import kotlinx.coroutines.delay

/**
 * The app's floating action button: pops in with a springy scale shortly
 * after the screen settles. Honors the system's reduced-motion setting.
 * Renders extended when [text] is provided, regular otherwise.
 */
@Composable
fun LifeLogFab(
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    text: String? = null,
    containerColor: Color = FloatingActionButtonDefaults.containerColor,
    contentColor: Color = contentColorFor(containerColor),
    contentDescription: String? = null
) {
    val context = LocalContext.current
    val animationsEnabled = remember {
        AndroidSettings.Global.getFloat(
            context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
    }
    var visible by remember { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(Unit) {
        if (animationsEnabled) {
            delay(200)
            visible = true
        }
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(animationSpec = Motion.spatial(), initialScale = 0.8f) +
            fadeIn(tween(Motion.SHORT)),
        exit = scaleOut(animationSpec = Motion.snappy(), targetScale = 0.85f) +
            fadeOut(tween(Motion.SHORT))
    ) {
        if (text != null) {
            ExtendedFloatingActionButton(
                onClick = onClick,
                icon = { Icon(icon, contentDescription) },
                text = { Text(text) },
                containerColor = containerColor,
                contentColor = contentColor
            )
        } else {
            FloatingActionButton(
                onClick = onClick,
                containerColor = containerColor,
                contentColor = contentColor
            ) {
                Icon(icon, contentDescription)
            }
        }
    }
}
