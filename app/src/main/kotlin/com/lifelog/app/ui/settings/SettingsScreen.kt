package com.lifelog.app.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.text.format.Formatter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.export.BackupFrequency
import com.lifelog.app.export.BackupLocationStatus
import com.lifelog.app.export.ExportEngine
import com.lifelog.app.export.ExportFormat
import com.lifelog.app.ui.components.DialogOption
import com.lifelog.app.ui.components.LifeLogCard
import com.lifelog.app.ui.components.SectionHeader
import com.lifelog.app.ui.components.SingleChoiceDialog
import com.lifelog.app.ui.theme.Spacing
import com.lifelog.app.util.relativeTimeLabel
import com.lifelog.app.util.toDisplayDateTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToImport: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
    val restoreState by viewModel.restoreState.collectAsStateWithLifecycle()
    val autoBackups by viewModel.autoBackups.collectAsStateWithLifecycle()
    val backupLocation by viewModel.backupLocationStatus.collectAsStateWithLifecycle()
    val systemInDarkTheme = isSystemInDarkTheme()

    // SAF launchers – one per format (MIME type is fixed at registration time)
    val sqliteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { viewModel.exportNow(it, ExportFormat.SQLITE) } }

    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportNow(it, ExportFormat.JSON) } }

    val zipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { viewModel.exportNow(it, ExportFormat.ZIP_CSV) } }

    // Restore picks an existing .db file; confirmation is shown before importing.
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingRestoreUri = uri }

    // Restore from an on-device auto-backup; same confirmation before importing.
    var pendingRestoreBackup by remember { mutableStateOf<ExportEngine.AutoBackup?>(null) }

    // Folder that future auto-backups are written into.
    val backupFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(viewModel::setBackupFolder) }

    var showFormatPicker by remember { mutableStateOf(false) }
    var showFrequencyPicker by remember { mutableStateOf(false) }
    var showLocationPicker by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(exportState.exportSuccess, exportState.lastExportError) {
        when {
            exportState.exportSuccess -> {
                snackbarHostState.showSnackbar("Export completed successfully")
                viewModel.clearExportResult()
            }
            exportState.lastExportError != null -> {
                snackbarHostState.showSnackbar("Export failed: ${exportState.lastExportError}")
                viewModel.clearExportResult()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.screenEdge,
                end = Spacing.screenEdge,
                top = Spacing.sm,
                bottom = Spacing.xl
            )
        ) {
            // ── Appearance ────────────────────────────────────────────────────
            item { SettingsSectionLabel("Appearance") }
            item {
                SettingsGroup {
                    SettingsToggleItem(
                        title = "Pure Black (AMOLED)",
                        subtitle = "True black background for OLED screens (dark mode only)",
                        checked = prefs.useAmoledBlack,
                        onCheckedChange = viewModel::setAmoledBlack,
                        enabled = systemInDarkTheme
                    )
                    SettingsToggleItem(
                        title = "Dynamic Color",
                        subtitle = "Use colors from your wallpaper (Android 12+)",
                        checked = prefs.useDynamicColor,
                        onCheckedChange = viewModel::setDynamicColor
                    )
                }
            }

            // ── Data & Backup ─────────────────────────────────────────────────
            item { SettingsSectionLabel("Data & Backup") }
            item {
                SettingsGroup {
                    SettingsClickItem(
                        title = "Export Now",
                        subtitle = "Save a copy of all your data to a file",
                        trailingContent = {
                            if (exportState.isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        enabled = !exportState.isExporting,
                        onClick = { showFormatPicker = true }
                    )
                    SettingsClickItem(
                        title = "Auto-Backup Frequency",
                        subtitle = "Keeps the last 7 database backups",
                        trailingContent = {
                            Text(
                                prefs.backupFrequency.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = { showFrequencyPicker = true }
                    )
                    val locationUnreachable = backupLocation is BackupLocationStatus.FolderUnreachable
                    SettingsClickItem(
                        title = "Backup Location",
                        subtitle = when (val location = backupLocation) {
                            null -> "Checking…"
                            BackupLocationStatus.AppStorage ->
                                "App storage — backups are removed if you uninstall"
                            is BackupLocationStatus.Folder -> location.name
                            BackupLocationStatus.FolderUnreachable ->
                                "Backup folder unreachable — tap to re-select. " +
                                    "Backups go to app storage until then."
                        },
                        subtitleColor = if (locationUnreachable) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        trailingContent = if (locationUnreachable) {
                            {
                                Icon(
                                    Icons.Rounded.WarningAmber,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else null,
                        onClick = { showLocationPicker = true }
                    )
                    if (prefs.backupFrequency != BackupFrequency.OFF) {
                        SettingsItem(
                            title = "Last Backup",
                            subtitle = if (prefs.lastBackupAt == 0L) "Never"
                                       else prefs.lastBackupAt.relativeTimeLabel()
                        )
                    }
                }
            }

            // ── Import / Restore ──────────────────────────────────────────────
            item { SettingsSectionLabel("Import") }
            item {
                SettingsGroup {
                    SettingsClickItem(
                        title = "Import Event from CSV",
                        subtitle = "Create a new event and its entries from a CSV file",
                        onClick = onNavigateToImport
                    )
                    SettingsClickItem(
                        title = "Restore from SQLite Database",
                        subtitle = "Replace all current data with an exported .db backup",
                        enabled = !restoreState.isRestoring && !restoreState.isRestarting,
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) }
                    )
                    SettingsClickItem(
                        title = "Restore from Auto-Backup",
                        subtitle = "Replace all current data with an automatic backup",
                        enabled = !restoreState.isRestoring && !restoreState.isRestarting,
                        onClick = viewModel::showAutoBackupPicker
                    )
                }
            }
        }
    }

    // ── Export format picker ──────────────────────────────────────────────────
    if (showFormatPicker) {
        val ts = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) }
        SingleChoiceDialog(
            title = "Choose Export Format",
            options = ExportFormat.entries.map { format ->
                DialogOption(
                    label = format.displayName,
                    description = when (format) {
                        ExportFormat.SQLITE -> "Best for full restore. Exact database copy."
                        ExportFormat.JSON -> "Structured text for other apps. Cannot be re-imported."
                        ExportFormat.ZIP_CSV -> "Human-readable. One CSV per event type."
                    }
                )
            },
            selectedIndex = -1,
            onDismiss = { showFormatPicker = false },
            onSelect = { idx ->
                showFormatPicker = false
                when (ExportFormat.entries[idx]) {
                    ExportFormat.SQLITE -> sqliteLauncher.launch("lifelog_$ts.db")
                    ExportFormat.JSON -> jsonLauncher.launch("lifelog_$ts.json")
                    ExportFormat.ZIP_CSV -> zipLauncher.launch("lifelog_$ts.zip")
                }
            }
        )
    }

    // ── Auto-backup frequency picker ──────────────────────────────────────────
    if (showFrequencyPicker) {
        SingleChoiceDialog(
            title = "Auto-Backup Frequency",
            options = BackupFrequency.entries.map { DialogOption(it.displayName) },
            selectedIndex = BackupFrequency.entries.indexOf(prefs.backupFrequency),
            onDismiss = { showFrequencyPicker = false },
            onSelect = { idx ->
                viewModel.setBackupFrequency(BackupFrequency.entries[idx])
                showFrequencyPicker = false
            }
        )
    }

    // ── Backup location picker ────────────────────────────────────────────────
    if (showLocationPicker) {
        val usingFolder = backupLocation is BackupLocationStatus.Folder ||
            backupLocation is BackupLocationStatus.FolderUnreachable
        SingleChoiceDialog(
            title = "Backup Location",
            options = listOf(
                DialogOption(
                    label = "App storage",
                    description = "Private to LifeLog. Backups are removed when the app is uninstalled."
                ),
                DialogOption(
                    label = "Folder I choose",
                    description = "Backups survive uninstall. After reinstalling, bring one back via " +
                        "“Restore from SQLite Database”, then pick the folder again."
                )
            ),
            selectedIndex = if (usingFolder) 1 else 0,
            onDismiss = { showLocationPicker = false },
            onSelect = { idx ->
                showLocationPicker = false
                if (idx == 0) viewModel.useAppStorageForBackups()
                else backupFolderLauncher.launch(null)
            }
        )
    }

    // ── Auto-backup picker ────────────────────────────────────────────────────
    autoBackups?.let { backups ->
        if (backups.isEmpty()) {
            AlertDialog(
                onDismissRequest = viewModel::dismissAutoBackupPicker,
                title = { Text("No auto-backups yet") },
                text = {
                    Text(
                        "No automatic backups were found. " +
                            "Set an Auto-Backup Frequency above to start creating them."
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissAutoBackupPicker) { Text("OK") }
                }
            )
        } else {
            val context = LocalContext.current
            SingleChoiceDialog(
                title = "Choose Auto-Backup",
                options = backups.map { backup ->
                    DialogOption(
                        label = backup.modifiedAt.toDisplayDateTime(),
                        description = "${backup.modifiedAt.relativeTimeLabel()} · " +
                            Formatter.formatShortFileSize(context, backup.sizeBytes) + " · " +
                            when (backup.source) {
                                is ExportEngine.AutoBackup.Source.AppStorage -> "App storage"
                                is ExportEngine.AutoBackup.Source.Folder -> "Backup folder"
                            }
                    )
                },
                selectedIndex = -1,
                onDismiss = viewModel::dismissAutoBackupPicker,
                onSelect = { idx ->
                    pendingRestoreBackup = backups[idx]
                    viewModel.dismissAutoBackupPicker()
                }
            )
        }
    }

    // ── Restore confirmation ──────────────────────────────────────────────────
    pendingRestoreUri?.let { uri ->
        RestoreConfirmDialog(
            text = "All current app data will be permanently overwritten with the contents " +
                "of the selected database. This action cannot be undone.\n\n" +
                "Only restore a database that was exported from LifeLog.",
            onConfirm = {
                viewModel.restoreFromSqlite(uri)
                pendingRestoreUri = null
            },
            onDismiss = { pendingRestoreUri = null }
        )
    }
    pendingRestoreBackup?.let { backup ->
        RestoreConfirmDialog(
            text = "All current app data will be permanently overwritten with the backup " +
                "from ${backup.modifiedAt.toDisplayDateTime()}. This action cannot be undone.",
            onConfirm = {
                viewModel.restoreFromAutoBackup(backup)
                pendingRestoreBackup = null
            },
            onDismiss = { pendingRestoreBackup = null }
        )
    }

    // ── Restore progress (blocks interaction until the app restarts) ──────────
    if (restoreState.isRestoring || restoreState.isRestarting) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { },
            icon = { Icon(Icons.Rounded.Restore, contentDescription = null) },
            title = {
                Text(if (restoreState.isRestarting) "Restore successful" else "Restoring…")
            },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        if (restoreState.isRestarting)
                            "Restarting the app to load your restored data…"
                        else
                            "Validating and preparing the backup…"
                    )
                }
            }
        )
    }

    // ── Restore error ─────────────────────────────────────────────────────────
    restoreState.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearRestoreError,
            icon = {
                Icon(
                    Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Restore failed") },
            text = { Text("$message\n\nYour existing data was not changed.") },
            confirmButton = {
                TextButton(onClick = viewModel::clearRestoreError) { Text("OK") }
            }
        )
    }
}

// ── Private composables ───────────────────────────────────────────────────────

/** Destructive-restore confirmation, shared by the SAF and auto-backup paths. */
@Composable
private fun RestoreConfirmDialog(
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Restore from backup?") },
        text = { Text(text) },
        confirmButton = {
            FilledTonalButton(
                onClick = onConfirm,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) { Text("Overwrite & Restore") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SettingsSectionLabel(title: String) {
    SectionHeader(
        title,
        modifier = Modifier.padding(start = Spacing.xs, top = Spacing.lg, bottom = Spacing.sm)
    )
}

/** Rounded container that groups related settings rows into one card. */
@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    LifeLogCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
            content()
        }
    }
}

private val TransparentListItemColors
    @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun SettingsItem(title: String, subtitle: String) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = TransparentListItemColors
    )
}

@Composable
private fun SettingsToggleItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = TransparentListItemColors
    )
}

@Composable
private fun SettingsClickItem(
    title: String,
    subtitle: String,
    subtitleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingContent: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                title,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor
            )
        },
        trailingContent = trailingContent,
        colors = TransparentListItemColors,
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier
    )
}
