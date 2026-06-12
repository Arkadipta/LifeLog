package com.lifelog.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.export.BackupFrequency
import com.lifelog.app.export.ExportFormat
import com.lifelog.app.ui.components.DialogOption
import com.lifelog.app.ui.components.LifeLogCard
import com.lifelog.app.ui.components.SectionHeader
import com.lifelog.app.ui.components.SingleChoiceDialog
import com.lifelog.app.ui.theme.Spacing
import com.lifelog.app.util.relativeTimeLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val exportState by viewModel.exportState.collectAsStateWithLifecycle()
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

    var showFormatPicker by remember { mutableStateOf(false) }
    var showFrequencyPicker by remember { mutableStateOf(false) }
    var showBackupFormatPicker by remember { mutableStateOf(false) }

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
                        subtitle = "Saves backup to internal app storage",
                        trailingContent = {
                            Text(
                                prefs.backupFrequency.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = { showFrequencyPicker = true }
                    )
                    if (prefs.backupFrequency != BackupFrequency.OFF) {
                        SettingsClickItem(
                            title = "Backup Format",
                            subtitle = "Format used for automatic backups",
                            trailingContent = {
                                Text(
                                    prefs.backupFormat.displayName,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = { showBackupFormatPicker = true }
                        )
                        SettingsItem(
                            title = "Last Backup",
                            subtitle = if (prefs.lastBackupAt == 0L) "Never"
                                       else prefs.lastBackupAt.relativeTimeLabel()
                        )
                    }
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
                        ExportFormat.JSON -> "Version-aware structured text. Importable."
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

    // ── Backup format picker ──────────────────────────────────────────────────
    if (showBackupFormatPicker) {
        SingleChoiceDialog(
            title = "Backup Format",
            options = ExportFormat.entries.map { DialogOption(it.displayName) },
            selectedIndex = ExportFormat.entries.indexOf(prefs.backupFormat),
            onDismiss = { showBackupFormatPicker = false },
            onSelect = { idx ->
                viewModel.setBackupFormat(ExportFormat.entries[idx])
                showBackupFormatPicker = false
            }
        )
    }
}

// ── Private composables ───────────────────────────────────────────────────────

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
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = trailingContent,
        colors = TransparentListItemColors,
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier
    )
}
