package com.lifelog.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.dp

/**
 * Spacing scale. Every padding and gap in the app pulls from here so screens
 * share one rhythm instead of ad-hoc dp values.
 */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp

    /** Horizontal inset for screen-level content. */
    val screenEdge = 16.dp

    /** Horizontal inset for bottom-sheet content. */
    val sheetEdge = 24.dp

    /** Bottom inset for scrolling lists so the last card clears the FAB. */
    val fabClearance = 96.dp

    /** Vertical gap between sibling cards in a list. */
    val cardGap = 12.dp
}

/** Fixed component dimensions shared across screens. */
object Sizing {
    /** Leading icon tile on list cards. */
    val iconTile = 48.dp
    val iconTileSmall = 40.dp

    /** Uniform height for every card in the analytics carousel. */
    val chartCard = 224.dp

    /** Uniform minimum height for list cards (events, reminders). */
    val listCardMin = 88.dp

    /** Height of primary call-to-action buttons. */
    val cta = 56.dp
}

/**
 * Motion vocabulary, M3 Expressive flavored: spatial changes use gently
 * bouncy springs, fades use short tweens with emphasized easing.
 */
object Motion {
    /** Spring for things that move, grow, or change size. */
    fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Non-bouncy spring for small component transitions. */
    fun <T> snappy(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    val emphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val SHORT = 150
    const val MEDIUM = 250
    const val LONG = 300
}
