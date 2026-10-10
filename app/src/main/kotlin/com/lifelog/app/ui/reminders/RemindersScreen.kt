package com.lifelog.app.ui.reminders

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.NotificationAdd
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.domain.RecurrenceCalculator
import com.lifelog.app.domain.model.DeliveryType
import com.lifelog.app.domain.model.RecurrenceType
import com.lifelog.app.domain.model.Reminder
import com.lifelog.app.ui.components.DeleteConfirmDialog
import com.lifelog.app.ui.components.EmptyStatePlaceholder
import com.lifelog.app.ui.components.IconTile
import com.lifelog.app.ui.components.LifeLogCard
import com.lifelog.app.ui.components.LifeLogFab
import com.lifelog.app.ui.components.SwipeActionBackground
import com.lifelog.app.ui.theme.Sizing
import com.lifelog.app.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    onNavigateToCreate: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: RemindersViewModel = hiltViewModel()
) {
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Reminder?>(null) }

    val context = LocalContext.current
    var notificationState by remember(context) { mutableStateOf(readNotificationState(context)) }
    // True once the system will no longer show the permission dialog, so "Allow" would do
    // nothing at all. Only ever set from a denied result, where the rationale flag is
    // unambiguous — before the first request it reads false for a quite different reason.
    var mustUseSettings by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationState = readNotificationState(context)
        if (!granted) {
            mustUseSettings = context.findActivity()?.let {
                !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
            } ?: true
        }
    }

    // Both conditions are changed from outside the app — the system permission dialog, or
    // the notification settings the banner sends people to — and neither recreates the
    // activity, so a stale banner would otherwise outlast the problem it describes (or
    // hide one that just appeared).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationState = readNotificationState(context)
    }

    LaunchedEffect(Unit) {
        if (notificationState == NotificationState.PERMISSION_MISSING && !mustUseSettings) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Reminders") },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            LifeLogFab(
                onClick = onNavigateToCreate,
                icon = Icons.Rounded.NotificationAdd,
                contentDescription = "New Reminder"
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.screenEdge,
                end = Spacing.screenEdge,
                top = Spacing.sm,
                bottom = Spacing.fabClearance
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)
        ) {
            if (notificationState != NotificationState.OK) {
                item {
                    // Only the permission case can still be resolved by the system dialog;
                    // notifications switched off for the app, and a permission denied for
                    // good, are reachable only through settings. Offering "Allow" for those
                    // would be the same silent failure one screen earlier.
                    val viaSettings = notificationState == NotificationState.TURNED_OFF || mustUseSettings
                    NotificationBlockedBanner(
                        message = when (notificationState) {
                            NotificationState.TURNED_OFF ->
                                "Notifications are switched off for LifeLog, so reminders can't alert you."
                            else ->
                                "Grant notification permission so reminders can alert you."
                        },
                        actionLabel = if (viaSettings) "Settings" else "Allow",
                        onAction = {
                            if (viaSettings) context.openNotificationSettings()
                            else permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    )
                }
            }

            if (reminders.isEmpty()) {
                item {
                    EmptyStatePlaceholder(
                        icon = Icons.Rounded.Alarm,
                        title = "No reminders",
                        subtitle = "Tap + to set up a reminder",
                        modifier = Modifier.fillParentMaxSize()
                    )
                }
            } else {
                items(reminders, key = { it.id }) { reminder ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) deleteTarget = reminder
                            false
                        }
                    )
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = { SwipeActionBackground(dismissState) },
                        enableDismissFromEndToStart = true,
                        enableDismissFromStartToEnd = false,
                        modifier = Modifier.animateItem()
                    ) {
                        ReminderCard(
                            reminder = reminder,
                            onToggle = { viewModel.toggleActive(reminder) },
                            onClick = { onNavigateToEdit(reminder.id) }
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        DeleteConfirmDialog(
            title = "Delete reminder?",
            text = "\"${target.title}\" will be deleted.",
            onConfirm = {
                viewModel.delete(target)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }
}

/**
 * Why a reminder would not reach the user. Both states are silent at delivery time — the
 * alarm fires, the notification goes nowhere — so the screen that lists reminders is where
 * they have to be visible. [NotificationHelper] logs the same two conditions when it hits
 * them, for the case where nobody thought to look here.
 */
private enum class NotificationState { OK, PERMISSION_MISSING, TURNED_OFF }

/**
 * Two independent switches, checked in the order the user would have to fix them: on API 33+
 * the runtime permission gates everything, and past that notifications can still be switched
 * off for the app or its channels — which `notify` reports by doing nothing at all.
 */
private fun readNotificationState(context: Context): NotificationState = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED -> NotificationState.PERMISSION_MISSING

    !NotificationManagerCompat.from(context).areNotificationsEnabled() -> NotificationState.TURNED_OFF

    else -> NotificationState.OK
}

/** The activity hosting this composition, for permission-rationale checks. */
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/** Opens LifeLog's notification settings, falling back to its app info page. */
private fun Context.openNotificationSettings() {
    val notificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
    val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
    val target = if (notificationSettings.resolveActivity(packageManager) != null) {
        notificationSettings
    } else {
        appDetails
    }
    runCatching { startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun NotificationBlockedBanner(message: String, actionLabel: String, onAction: () -> Unit) {
    LifeLogCard(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.NotificationsOff, null)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Reminders can't notify you",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * One reminder: tap to edit, switch to arm/disarm, swipe left to delete.
 */
@Composable
private fun ReminderCard(
    reminder: Reminder,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAlarm = reminder.deliveryType == DeliveryType.ALARM
    val accent = if (reminder.isActive) MaterialTheme.colorScheme.primary
                 else MaterialTheme.colorScheme.onSurfaceVariant

    LifeLogCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = Sizing.listCardMin)
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            IconTile(
                icon = if (isAlarm) Icons.Rounded.Alarm else Icons.Rounded.NotificationsActive,
                tint = accent,
                contentDescription = if (isAlarm) "Alarm" else "Notification"
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (reminder.eventTypeName != null) {
                    Text(
                        reminder.eventTypeName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val rule = reminder.recurrenceRule
                if (rule.type == RecurrenceType.WEEKLY && rule.daysOfWeek.isNotEmpty()) {
                    CompactWeekdayBadge(activeDays = rule.daysOfWeek)
                }
                Text(
                    RecurrenceCalculator.describeRule(rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (reminder.message.isNotBlank()) {
                    Text(
                        reminder.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Switch(checked = reminder.isActive, onCheckedChange = { onToggle() })
        }
    }
}
