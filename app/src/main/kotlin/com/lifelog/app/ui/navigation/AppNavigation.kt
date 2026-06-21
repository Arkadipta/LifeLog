package com.lifelog.app.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lifelog.app.export.SqliteRestore
import com.lifelog.app.ui.csvimport.ImportCsvScreen
import com.lifelog.app.ui.events.CreateEventScreen
import com.lifelog.app.ui.events.EventDetailScreen
import com.lifelog.app.ui.events.EventsScreen
import com.lifelog.app.ui.reminders.CreateReminderScreen
import com.lifelog.app.ui.reminders.RemindersScreen
import com.lifelog.app.ui.settings.SettingsScreen
import com.lifelog.app.ui.theme.Motion
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

@Composable
fun AppNavigation(
    openEventId: Long? = null,
    onEventOpened: () -> Unit = {}
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

    // Surface the result of a database restore that completed on the last launch.
    val context = LocalContext.current
    var restoreOutcome by remember { mutableStateOf<SqliteRestore.Outcome?>(null) }
    LaunchedEffect(Unit) {
        restoreOutcome = withContext(Dispatchers.IO) { SqliteRestore.consumeOutcome(context) }
    }

    Scaffold(
        bottomBar = { BottomNavigationBar(navController) }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Events.route,
            modifier = Modifier.padding(paddingValues),
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
                EventsScreen(
                    onNavigateToCreate = { navController.navigate(Screen.CreateEvent.route) },
                    onNavigateToEvent = { id -> navController.navigate(Screen.EventDetail.route(id)) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            composable(Screen.CreateEvent.route) {
                CreateEventScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.ImportCsv.route) {
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

            composable(
                route = Screen.EditEvent.route,
                arguments = listOf(navArgument("eventId") { type = NavType.LongType })
            ) { back ->
                val eventId = back.arguments?.getLong("eventId") ?: return@composable
                CreateEventScreen(
                    eventId = eventId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.EventDetail.route,
                arguments = listOf(navArgument("eventId") { type = NavType.LongType })
            ) { back ->
                val eventId = back.arguments?.getLong("eventId") ?: return@composable
                EventDetailScreen(
                    eventId = eventId,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { id -> navController.navigate(Screen.EditEvent.route(id)) }
                )
            }

            composable(Screen.Timeline.route) {
                TimelineScreen(
                    onNavigateToEvent = { id -> navController.navigate(Screen.EventDetail.route(id)) }
                )
            }

            composable(Screen.Reminders.route) {
                RemindersScreen(
                    onNavigateToCreate = { navController.navigate(Screen.CreateReminder.route) },
                    onNavigateToEdit = { id -> navController.navigate(Screen.EditReminder.route(id)) }
                )
            }

            composable(Screen.CreateReminder.route) {
                CreateReminderScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.EditReminder.route,
                arguments = listOf(navArgument("reminderId") { type = NavType.LongType })
            ) { back ->
                val reminderId = back.arguments?.getLong("reminderId") ?: return@composable
                CreateReminderScreen(
                    reminderId = reminderId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToImport = { navController.navigate(Screen.ImportCsv.route) }
                )
            }
        }
    }

    restoreOutcome?.let { outcome ->
        RestoreOutcomeDialog(outcome = outcome, onDismiss = { restoreOutcome = null })
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

@Composable
private fun BottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(item.screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.screen.label
                    )
                },
                label = { Text(item.screen.label) }
            )
        }
    }
}
