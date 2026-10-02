package com.lifelog.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lifelog.app.ui.theme.Sizing

/**
 * How the app arranges itself in the window it has been given.
 *
 * Decided from the window's size alone — not the device type or its orientation — so a
 * tablet in split-screen, an unfolded foldable and a phone on its side are all answered by
 * the space they actually have. The three arrangements differ only in presentation: the
 * navigation routes and the back stack are identical in each, which is what lets a window
 * be resized (or a device folded) mid-task without losing its place.
 */
enum class WindowLayout(
    /** Top-level destinations move from the bottom bar to a rail along the start edge. */
    val usesNavigationRail: Boolean,
    /** The Events list stays on screen beside the event it opened. */
    val showsListBesideDetail: Boolean
) {
    /** A phone held upright: bottom bar, one pane. */
    COMPACT(usesNavigationRail = false, showsListBesideDetail = false),

    /** Room for a rail but not for two panes: small tablets, foldables, phones on their side. */
    MEDIUM(usesNavigationRail = true, showsListBesideDetail = false),

    /** Room for the list and the detail at once. */
    EXPANDED(usesNavigationRail = true, showsListBesideDetail = true);

    companion object {
        /** Material's width breakpoints. */
        val MEDIUM_MIN_WIDTH = 600.dp
        val EXPANDED_MIN_WIDTH = 840.dp

        /**
         * Below this a window is too short to give a second pane's app bar and list anything
         * worth showing — a phone on its side is wide enough for two panes and far too short
         * for them — so it keeps one pane however wide it is. It still gets the rail, which
         * hands the height of a bottom bar back to the content exactly where it is scarcest.
         */
        val TWO_PANE_MIN_HEIGHT = 480.dp

        fun of(width: Dp, height: Dp): WindowLayout = when {
            width < MEDIUM_MIN_WIDTH -> COMPACT
            width >= EXPANDED_MIN_WIDTH && height >= TWO_PANE_MIN_HEIGHT -> EXPANDED
            else -> MEDIUM
        }
    }
}

/**
 * Holds a screen to a readable width, centred, in a window wider than that; in a narrower
 * one it changes nothing.
 *
 * The screen keeps its own app bar and floating button, so those stay attached to the
 * column they belong to rather than drifting to the far corners of a wide window. The
 * background is painted here because the margins would otherwise show whatever is behind
 * the composition, which outside the main activity is the bare window.
 */
@Composable
fun ReadableWidth(
    modifier: Modifier = Modifier,
    maxWidth: Dp = Sizing.singlePaneMax,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(Modifier.widthIn(max = maxWidth)) { content() }
    }
}
