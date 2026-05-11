package com.lifelog.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lifelog.app.ui.events.CreateEventScreen
import com.lifelog.app.ui.events.EventDetailScreen
import com.lifelog.app.ui.events.EventsScreen
import com.lifelog.app.ui.reminders.CreateReminderScreen
import com.lifelog.app.ui.reminders.RemindersScreen
import com.lifelog.app.ui.settings.SettingsScreen
import com.lifelog.app.ui.timeline.TimelineScreen

sealed class Screen(val route: String, val label: String) {
    // Events graph
    object Events : Screen("events", "Events")
    object CreateEvent : Screen("events/create", "New Event")
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
fun AppNavigation(startDestination: String? = null) {
    val navController = rememberNavController()

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
                    fadeIn(tween(250))
                } else {
                    slideInHorizontally(
                        initialOffsetX = { it / 5 },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(300))
                }
            },
            exitTransition = {
                if (initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes) {
                    fadeOut(tween(200))
                } else {
                    slideOutHorizontally(
                        targetOffsetX = { -it / 5 },
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(200))
                }
            },
            popEnterTransition = {
                // Back always slides in from the left (reverse of push)
                slideInHorizontally(
                    initialOffsetX = { -it / 5 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it / 5 },
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(tween(200))
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
                TimelineScreen()
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
                SettingsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
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
                label = { Text(item.screen.label) },
                alwaysShowLabel = false,
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}
