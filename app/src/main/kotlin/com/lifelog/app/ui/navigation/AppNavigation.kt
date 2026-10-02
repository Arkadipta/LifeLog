package com.lifelog.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lifelog.app.ShortcutDestination
import com.lifelog.app.export.SqliteRestore
import com.lifelog.app.ui.ReadableWidth
import com.lifelog.app.ui.WindowLayout
import com.lifelog.app.ui.components.EmptyStatePlaceholder
import com.lifelog.app.ui.csvimport.ImportCsvScreen
import com.lifelog.app.ui.events.CreateEventScreen
import com.lifelog.app.ui.events.EntryFormMode
import com.lifelog.app.ui.events.EntryFormSheet
import com.lifelog.app.ui.events.EventDetailScreen
import com.lifelog.app.ui.events.EventPickerSheet
import com.lifelog.app.ui.events.EventsScreen
import com.lifelog.app.ui.reminders.CreateReminderScreen
import com.lifelog.app.ui.reminders.RemindersScreen
import com.lifelog.app.ui.settings.SettingsScreen
import com.lifelog.app.ui.theme.Motion
import com.lifelog.app.ui.theme.Sizing
import com.lifelog.app.ui.timeline.TimelineScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class Screen(val route: String, val label: String) {
    // Events graph
    object Events : Screen("events", "Events")
    object CreateEvent : Screen("events/create", "New Event")
    object ImportCsv : Screen("events/import", "Import from CSV")
    object EditEvent : Screen("events/{eventId}/edit", "Edit Event") {
        fun route(id: Long) = "events/$id/edit"
    }
    object EventDetail : Screen("events/{eventId}", "Event") {
        fun route(id: Long) = "events/$id"
    }

    // Timeline
    object Timeline : Screen("timeline", "Timeline")

    // Reminders
    object Reminders : Screen("reminders", "Reminders")
    object CreateReminder : Screen("reminders/create", "New Reminder")
    object EditReminder : Screen("reminders/{reminderId}/edit", "Edit Reminder") {
        fun route(id: Long) = "reminders/$id/edit"
    }

    // Settings
    object Settings : Screen("settings", "Settings")
}

data class BottomNavItem(
    val screen: Screen,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val tabRoutes = setOf(Screen.Events.route, Screen.Timeline.route, Screen.Reminders.route)

private val bottomNavItems = listOf(
    BottomNavItem(Screen.Events, Icons.AutoMirrored.Rounded.List, Icons.AutoMirrored.Outlined.List),
    BottomNavItem(Screen.Timeline, Icons.Rounded.Timeline, Icons.Outlined.Timeline),
    BottomNavItem(Screen.Reminders, Icons.Rounded.Alarm, Icons.Outlined.Alarm),
)

/**
 * The top-level destination a route belongs to, or null for one that belongs to none
 * (Settings). Routes are namespaced under their tab — `events/{eventId}/edit` is part of
 * Events — so the prefix is the whole answer.
 *
 * This is what the navigation bar or rail lights up. Matching the tab's own route alone
 * left nothing selected on every screen beneath a tab, which is at its most wrong in the
 * two-pane layout: the Events list on screen, and the rail claiming no section is open.
 */
internal fun owningTabRoute(route: String?): String? =
    tabRoutes.firstOrNull { tab -> route == tab || route?.startsWith("$tab/") == true }

/** The routes that show the Events list beside them when the window has room for both. */
private val listDetailRoutes = setOf(Screen.Events.route, Screen.EventDetail.route)

@Composable
fun AppNavigation(
    openEventId: Long? = null,
    onEventOpened: () -> Unit = {},
    shortcut: ShortcutDestination? = null,
    onShortcutHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    // An event id handed in from outside (e.g. the quick-add widget's "History"
    // shortcut) routes straight to that event's detail screen, once.
    LaunchedEffect(openEventId) {
        if (openEventId != null) {
            navController.navigate(Screen.EventDetail.route(openEventId))
            onEventOpened()
        }
    }

    // Launcher-shortcut routing (lifelog:// URIs, parsed by MainActivity):
    // Timeline switches tab exactly like a tap on the navigation bar; Log Entry opens the
    // event picker, which hands the chosen id to the entry form below. Both
    // sheet states are plain `remember`, matching every other entry sheet in
    // the app: rotation closes the sheet rather than reloading (and thereby
    // clobbering) a form mid-edit.
    var quickAddPickerVisible by remember { mutableStateOf(false) }
    var quickAddEventId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(shortcut) {
        when (shortcut) {
            ShortcutDestination.TIMELINE -> navController.navigateToTab(Screen.Timeline)
            ShortcutDestination.QUICK_ADD -> quickAddPickerVisible = true
            null -> {}
        }
        if (shortcut != null) onShortcutHandled()
    }

    // Surface the result of a database restore that completed on the last launch.
    val context = LocalContext.current
    var restoreOutcome by remember { mutableStateOf<SqliteRestore.Outcome?>(null) }
    LaunchedEffect(Unit) {
        restoreOutcome = withContext(Dispatchers.IO) { SqliteRestore.consumeOutcome(context) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = WindowLayout.of(maxWidth, maxHeight)
        // Destinations read the layout through a State so the NavHost's graph builder captures
        // one stable object: a builder that captured the layout itself would be a new lambda
        // on every window resize, and NavHost rebuilds its graph whenever the builder changes.
        val currentLayout by rememberUpdatedState(layout)

        val currentEntry by navController.currentBackStackEntryAsState()
        val currentRoute = currentEntry?.destination?.route

        // The list pane borrows the Events destination's own ViewModel — that entry is the
        // start destination, so it is on the back stack under everything — which keeps the
        // search, tag filter and sort the same list whether it is drawn as a destination
        // (one pane) or as a pane (two), including across a resize between the two.
        val eventsEntry = remember(currentEntry) {
            runCatching { navController.getBackStackEntry(Screen.Events.route) }.getOrNull()
        }
        val openEventIdInPane = currentEntry
            ?.takeIf { it.destination.route == Screen.EventDetail.route }
            ?.arguments?.getLong("eventId")

        NavigationSuiteScaffold(
            navigationSuiteItems = {
                val selectedTab = owningTabRoute(currentRoute)
                bottomNavItems.forEach { item ->
                    val selected = item.screen.route == selectedTab
                    item(
                        selected = selected,
                        onClick = { navController.navigateToTab(item.screen) },
                        icon = {
                            Icon(
                                imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.screen.label
                            )
                        },
                        label = { Text(item.screen.label) }
                    )
                }
            },
            layoutType = if (layout.usesNavigationRail) NavigationSuiteType.NavigationRail
                         else NavigationSuiteType.NavigationBar
        ) {
            // Plain padding, not an inset-consuming one: every screen's own app bar also pads
            // for the status bar, and the app has always been laid out with both (the Scaffold
            // this replaced handed the same inset down as content padding). Kept so that no
            // screen moves; whether the doubled gap should exist at all is a separate question.
            Row(Modifier.padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())) {
                AnimatedVisibility(
                    visible = layout.showsListBesideDetail && currentRoute in listDetailRoutes &&
                        eventsEntry != null
                ) {
                    Row {
                        Box(Modifier.width(Sizing.listPane).fillMaxHeight()) {
                            eventsEntry?.let { entry ->
                                EventsScreen(
                                    onNavigateToCreate = { navController.navigate(Screen.CreateEvent.route) },
                                    onNavigateToEvent = { id ->
                                        // Replace the open event rather than stacking each
                                        // one visited; tapping the open one again is a no-op
                                        // instead of a reload that loses its scroll position.
                                        if (id != openEventIdInPane) {
                                            navController.navigate(Screen.EventDetail.route(id)) {
                                                popUpTo(Screen.Events.route)
                                            }
                                        }
                                    },
                                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                                    selectedEventId = openEventIdInPane,
                                    viewModel = hiltViewModel(entry)
                                )
                            }
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Screen.Events.route,
                    modifier = Modifier.weight(1f),
                    enterTransition = {
                        // Tab siblings fade; push-forward slides in from the right
                        if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) {
                            fadeIn(tween(Motion.MEDIUM))
                        } else {
                            slideInHorizontally(
                                initialOffsetX = { it / 5 },
                                animationSpec = tween(Motion.LONG, easing = Motion.emphasizedEasing)
                            ) + fadeIn(tween(Motion.LONG))
                        }
                    },
                    exitTransition = {
                        if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) {
                            fadeOut(tween(Motion.SHORT + 50))
                        } else {
                            slideOutHorizontally(
                                targetOffsetX = { -it / 5 },
                                animationSpec = tween(Motion.LONG, easing = Motion.emphasizedEasing)
                            ) + fadeOut(tween(Motion.SHORT + 50))
                        }
                    },
                    popEnterTransition = {
                        // Back always slides in from the left (reverse of push)
                        slideInHorizontally(
                            initialOffsetX = { -it / 5 },
                            animationSpec = tween(Motion.LONG, easing = Motion.emphasizedEasing)
                        ) + fadeIn(tween(Motion.LONG))
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = { it / 5 },
                            animationSpec = tween(Motion.LONG, easing = Motion.emphasizedEasing)
                        ) + fadeOut(tween(Motion.SHORT + 50))
                    }
                ) {
                    composable(Screen.Events.route) {
                        // Beside the list pane this destination is the "nothing open yet" half; the
                        // list itself is drawn once, outside the NavHost, for both list-detail routes.
                        if (currentLayout.showsListBesideDetail) {
                            EmptyStatePlaceholder(
                                icon = Icons.AutoMirrored.Rounded.List,
                                title = "Select an event",
                                subtitle = "Its entries and charts open here",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            ReadableWidth {
                                EventsScreen(
                                    onNavigateToCreate = { navController.navigate(Screen.CreateEvent.route) },
                                    onNavigateToEvent = { id -> navController.navigate(Screen.EventDetail.route(id)) },
                                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                                )
                            }
                        }
                    }

                    composable(Screen.CreateEvent.route) {
                        ReadableWidth {
                            CreateEventScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }

                    composable(Screen.ImportCsv.route) {
                        ReadableWidth {
                            ImportCsvScreen(
                                onClose = { navController.popBackStack() },
                                onOpenEvent = { id ->
                                    // Land on the new event with Events beneath it, so back goes
                                    // to the list rather than through the (now finished) wizard.
                                    navController.navigate(Screen.EventDetail.route(id)) {
                                        popUpTo(Screen.Events.route)
                                    }
                                }
                            )
                        }
                    }

                    composable(
                        route = Screen.EditEvent.route,
                        arguments = listOf(navArgument("eventId") { type = NavType.LongType })
                    ) { back ->
                        val eventId = back.arguments?.getLong("eventId") ?: return@composable
                        ReadableWidth {
                            CreateEventScreen(
                                eventId = eventId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }

                    composable(
                        route = Screen.EventDetail.route,
                        arguments = listOf(navArgument("eventId") { type = NavType.LongType })
                    ) { back ->
                        val eventId = back.arguments?.getLong("eventId") ?: return@composable
                        // With the list beside it the pane takes the room it is given, and going
                        // "back" to that list means nothing — unless the event was opened from
                        // somewhere else (Timeline's "view history"), which back still returns to.
                        val besideList = currentLayout.showsListBesideDetail
                        val openedFromList =
                            navController.previousBackStackEntry?.destination?.route == Screen.Events.route
                        ReadableWidth(maxWidth = if (besideList) Dp.Unspecified else Sizing.singlePaneMax) {
                            EventDetailScreen(
                                eventId = eventId,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToEdit = { id -> navController.navigate(Screen.EditEvent.route(id)) },
                                showBackButton = !(besideList && openedFromList)
                            )
                        }
                    }

                    composable(Screen.Timeline.route) {
                        ReadableWidth {
                            TimelineScreen(
                                onNavigateToEvent = { id -> navController.navigate(Screen.EventDetail.route(id)) }
                            )
                        }
                    }

                    composable(Screen.Reminders.route) {
                        ReadableWidth {
                            RemindersScreen(
                                onNavigateToCreate = { navController.navigate(Screen.CreateReminder.route) },
                                onNavigateToEdit = { id -> navController.navigate(Screen.EditReminder.route(id)) }
                            )
                        }
                    }

                    composable(Screen.CreateReminder.route) {
                        ReadableWidth {
                            CreateReminderScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }

                    composable(
                        route = Screen.EditReminder.route,
                        arguments = listOf(navArgument("reminderId") { type = NavType.LongType })
                    ) { back ->
                        val reminderId = back.arguments?.getLong("reminderId") ?: return@composable
                        ReadableWidth {
                            CreateReminderScreen(
                                reminderId = reminderId,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }

                    composable(Screen.Settings.route) {
                        ReadableWidth {
                            SettingsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToImport = { navController.navigate(Screen.ImportCsv.route) }
                            )
                        }
                    }
                }
            }
        }
    }

    restoreOutcome?.let { outcome ->
        RestoreOutcomeDialog(outcome = outcome, onDismiss = { restoreOutcome = null })
    }

    if (quickAddPickerVisible) {
        EventPickerSheet(
            onPick = { id ->
                quickAddPickerVisible = false
                quickAddEventId = id
            },
            onCreateEvent = {
                quickAddPickerVisible = false
                navController.navigate(Screen.CreateEvent.route)
            },
            onDismiss = { quickAddPickerVisible = false }
        )
    }

    quickAddEventId?.let { eventId ->
        EntryFormSheet(
            mode = EntryFormMode.New(eventId),
            onDismiss = { quickAddEventId = null },
            onViewHistory = { id ->
                quickAddEventId = null
                navController.navigate(Screen.EventDetail.route(id))
            }
        )
    }
}

@Composable
private fun RestoreOutcomeDialog(
    outcome: SqliteRestore.Outcome,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (outcome.success) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (outcome.success) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.error
            )
        },
        title = { Text(if (outcome.success) "Restore complete" else "Restore failed") },
        text = {
            if (outcome.success) {
                val c = outcome.counts
                Text(
                    "Your data was restored successfully:\n\n" +
                        "• ${c.eventTypes} events\n" +
                        "• ${c.eventEntries} entries\n" +
                        "• ${c.reminders} reminders\n" +
                        "• ${c.chartConfigs} charts"
                )
            } else {
                Text(outcome.error ?: "The restore could not be completed.")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

/** Switch top-level destination, keeping each tab's own back stack and scroll state. */
private fun NavHostController.navigateToTab(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
