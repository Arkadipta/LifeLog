package com.lifelog.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.export.BackupFrequency
import com.lifelog.app.export.ExportFormat
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
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // ── Appearance ────────────────────────────────────────────────────
            item { SettingsSectionHeader("Appearance") }
            item {
                SettingsToggleItem(
                    title = "Pure Black (AMOLED)",
                    subtitle = "Use true black backgrounds for OLED screens (dark mode only)",
                    checked = prefs.useAmoledBlack,
                    onCheckedChange = viewModel::setAmoledBlack,
                    enabled = systemInDarkTheme
                )
            }
            item {
                SettingsToggleItem(
                    title = "Dynamic Color",
                    subtitle = "Use colors from your wallpaper (Android 12+)",
                    checked = prefs.useDynamicColor,
                    onCheckedChange = viewModel::setDynamicColor
                )
            }

            // ── Data & Backup ─────────────────────────────────────────────────
            item { SettingsSectionHeader("Data & Backup") }
            item {
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
            }
            item {
                SettingsClickItem(
                    title = "Auto-Backup Frequency",
                    subtitle = "Saves backup to internal app storage",
                    trailingContent = {
                        Text(
                            prefs.backupFrequency.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = { showFrequencyPicker = true }
                )
            }
            if (prefs.backupFrequency != BackupFrequency.OFF) {
                item {
                    SettingsClickItem(
                        title = "Backup Format",
                        subtitle = "Format used for automatic backups",
                        trailingContent = {
                            Text(
                                prefs.backupFormat.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        onClick = { showBackupFormatPicker = true }
                    )
                }
                item {
                    ListItem(
                        headlineContent = { Text("Last Backup") },
                        supportingContent = {
                            Text(
                                if (prefs.lastBackupAt == 0L) "Never"
                                else prefs.lastBackupAt.relativeTimeLabel(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    )
                }
            }
        }
    }

    // ── Export format picker ──────────────────────────────────────────────────
    if (showFormatPicker) {
        val ts = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) }
        AlertDialog(
            onDismissRequest = { showFormatPicker = false },
            title = { Text("Choose Export Format") },
            text = {
                Column {
                    ExportFormat.entries.forEach { format ->
                        ListItem(
                            headlineContent = { Text(format.displayName) },
                            supportingContent = {
                                Text(
                                    when (format) {
                                        ExportFormat.SQLITE -> "Best for full restore. Exact database copy."
                                        ExportFormat.JSON -> "Version-aware structured text. Importable."
                                        ExportFormat.ZIP_CSV -> "Human-readable. One CSV per event type."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            modifier = Modifier.clickable {
                                showFormatPicker = false
                                when (format) {
                                    ExportFormat.SQLITE -> sqliteLauncher.launch("lifelog_$ts.db")
                                    ExportFormat.JSON -> jsonLauncher.launch("lifelog_$ts.json")
                                    ExportFormat.ZIP_CSV -> zipLauncher.launch("lifelog_$ts.zip")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = AlertDialogDefaults.containerColor)
                        )
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showFormatPicker = false }) { Text("Cancel") } }
        )
    }

    // ── Auto-backup frequency picker ──────────────────────────────────────────
    if (showFrequencyPicker) {
        SingleChoiceDialog(
            title = "Auto-Backup Frequency",
            options = BackupFrequency.entries.map { it.displayName },
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
            options = ExportFormat.entries.map { it.displayName },
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
private fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        fontWeight = FontWeight.Bold
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
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
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
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = trailingContent,
        modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier
    )
}

@Composable
private fun SingleChoiceDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { idx, label ->
                    ListItem(
                        headlineContent = { Text(label) },
                        trailingContent = {
                            if (idx == selectedIndex) {
                                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        modifier = Modifier.clickable { onSelect(idx) },
                        colors = ListItemDefaults.colors(containerColor = AlertDialogDefaults.containerColor)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
