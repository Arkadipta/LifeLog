package com.lifelog.app.ui.csvimport

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifelog.app.csv.CsvImportEngine
import com.lifelog.app.domain.csv.ParsedCsv
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.ui.components.LabelChip
import com.lifelog.app.ui.components.LifeLogCard
import com.lifelog.app.ui.components.SectionHeader
import com.lifelog.app.ui.events.CreateEventScreen
import com.lifelog.app.ui.events.EventDraftConfig
import com.lifelog.app.ui.theme.Sizing
import com.lifelog.app.ui.theme.Spacing

/**
 * The "Import Event from CSV" wizard: a single destination that walks through
 * file selection → event configuration → timestamp column → field review →
 * import → summary. All cross-step state lives in [ImportCsvViewModel]; the
 * "Configure Event" step reuses [CreateEventScreen] in draft mode.
 */
@Composable
fun ImportCsvScreen(
    onClose: () -> Unit,
    onOpenEvent: (Long) -> Unit,
    viewModel: ImportCsvViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::onFileSelected) }
    val pickFile: () -> Unit = { filePicker.launch(CSV_MIME_TYPES) }

    // Map system-back to "previous step"; the first step and the summary exit.
    BackHandler {
        when (state.step) {
            ImportStep.SELECT_FILE -> onClose()
            ImportStep.CONFIGURE_EVENT -> viewModel.goToStep(ImportStep.SELECT_FILE)
            ImportStep.SELECT_TIMESTAMP -> viewModel.goToStep(ImportStep.CONFIGURE_EVENT)
            ImportStep.REVIEW_FIELDS -> viewModel.goToStep(ImportStep.SELECT_TIMESTAMP)
            ImportStep.IMPORTING -> Unit // block back mid-import
            ImportStep.SUMMARY -> onClose()
        }
    }

    when (state.step) {
        ImportStep.SELECT_FILE -> SelectFileStep(
            state = state,
            onPickFile = pickFile,
            onRetry = { viewModel.dismissParseError(); pickFile() },
            onContinue = viewModel::continueFromPreview,
            onClose = onClose
        )

        ImportStep.CONFIGURE_EVENT -> {
            val draftConfig = remember(state.suggestedName) {
                EventDraftConfig(
                    initialName = state.suggestedName,
                    onConfirm = viewModel::setDraft
                )
            }
            CreateEventScreen(
                onNavigateBack = { viewModel.goToStep(ImportStep.SELECT_FILE) },
                draftConfig = draftConfig
            )
        }

        ImportStep.SELECT_TIMESTAMP -> SelectTimestampStep(
            state = state,
            onSelectColumn = viewModel::selectTimestampColumn,
            onBack = { viewModel.goToStep(ImportStep.CONFIGURE_EVENT) },
            onContinue = viewModel::continueFromTimestamp
        )

        ImportStep.REVIEW_FIELDS -> ReviewFieldsStep(
            state = state,
            onSetFieldType = viewModel::setFieldType,
            onSetFieldName = viewModel::setFieldName,
            onBack = { viewModel.goToStep(ImportStep.SELECT_TIMESTAMP) },
            onImport = viewModel::startImport
        )

        ImportStep.IMPORTING -> ImportingStep(
            state = state,
            onRetry = viewModel::dismissImportError
        )

        ImportStep.SUMMARY -> SummaryStep(
            summary = state.summary,
            onOpenEvent = onOpenEvent,
            onClose = onClose
        )
    }
}

// ── Step 1: select file + preview ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectFileStep(
    state: ImportCsvUiState,
    onPickFile: () -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
    onClose: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import from CSV") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close")
                    }
                }
            )
        },
        bottomBar = {
            val parsed = state.parsed
            if (parsed != null && state.parseError == null) {
                WizardBottomBar(
                    primaryText = "Continue",
                    primaryEnabled = parsed.dataRowCount > 0,
                    onPrimary = onContinue,
                    secondaryText = "Choose another file",
                    onSecondary = onPickFile
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isParsing -> CenteredStatus(loading = true, text = "Reading your file…")

                state.parseError != null -> ParseErrorContent(
                    message = state.parseError,
                    onRetry = onRetry
                )

                state.parsed != null -> PreviewContent(state.parsed)

                else -> EmptySelectPrompt(onPickFile = onPickFile)
            }
        }
    }
}

@Composable
private fun EmptySelectPrompt(onPickFile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.UploadFile,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(Spacing.lg))
        Text("Import a new event from a CSV", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            "Pick a comma-separated file with a header row. Each column becomes a field and each row becomes an entry.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onPickFile, modifier = Modifier.height(Sizing.cta)) {
            Icon(Icons.Rounded.UploadFile, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("Select CSV File")
        }
    }
}

@Composable
private fun ParseErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(Spacing.lg))
        Text("Couldn't read this file", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(Spacing.sm))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Spacing.xl))
        Button(onClick = onRetry, modifier = Modifier.height(Sizing.cta)) {
            Text("Try another file")
        }
    }
}

@Composable
private fun PreviewContent(parsed: ParsedCsv) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screenEdge)
            .padding(top = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Text(
            "${parsed.columnCount} columns · ${parsed.dataRowCount} rows",
            style = MaterialTheme.typography.titleMedium
        )
        SectionHeader("Preview")
        LifeLogCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(Spacing.md)
            ) {
                // Header
                Row {
                    parsed.headers.forEachIndexed { index, header ->
                        PreviewCell(
                            text = header.ifBlank { "Column ${index + 1}" },
                            weight = FontWeight.SemiBold
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                parsed.rows.take(PREVIEW_ROWS).forEach { row ->
                    Row {
                        row.forEach { cell -> PreviewCell(text = cell) }
                    }
                }
            }
        }
        if (parsed.dataRowCount > PREVIEW_ROWS) {
            Text(
                "Showing first $PREVIEW_ROWS of ${parsed.dataRowCount} rows",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PreviewCell(text: String, weight: FontWeight = FontWeight.Normal) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = weight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .width(120.dp)
            .padding(end = Spacing.sm, bottom = 2.dp)
    )
}

// ── Step 3: select timestamp column ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectTimestampStep(
    state: ImportCsvUiState,
    onSelectColumn: (Int) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val parsed = state.parsed ?: return
    val validation = state.timestampValidation
    val canContinue = state.timestampColumnIndex != null &&
        validation != null &&
        !validation.allFailed

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Date & Time Column") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            WizardBottomBar(
                primaryText = "Continue",
                primaryEnabled = canContinue,
                onPrimary = onContinue
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.screenEdge,
                end = Spacing.screenEdge,
                top = Spacing.sm,
                bottom = Spacing.lg
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            item {
                Text(
                    "Which column has the date and time for each entry? It's used to order your entries.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.sm)
                )
            }
            itemsIndexed(parsed.headers) { index, header ->
                val sample = parsed.rows.firstOrNull()?.getOrNull(index).orEmpty()
                TimestampColumnRow(
                    name = header.ifBlank { "Column ${index + 1}" },
                    sample = sample,
                    selected = state.timestampColumnIndex == index,
                    onClick = { onSelectColumn(index) }
                )
            }
            item {
                Spacer(Modifier.height(Spacing.sm))
                TimestampValidationCard(
                    isValidating = state.isValidatingTimestamp,
                    validation = validation
                )
            }
        }
    }
}

@Composable
private fun TimestampColumnRow(
    name: String,
    sample: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (sample.isNotBlank()) {
                    Text(
                        "e.g. $sample",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun TimestampValidationCard(
    isValidating: Boolean,
    validation: TimestampValidation?
) {
    when {
        isValidating -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(Spacing.sm)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text("Checking this column…", style = MaterialTheme.typography.bodyMedium)
        }

        validation == null -> Unit

        !validation.hasFailures -> StatusRow(
            icon = Icons.Rounded.CheckCircle,
            tint = MaterialTheme.colorScheme.primary,
            title = "All ${validation.total} values look like valid dates."
        )

        validation.allFailed -> StatusRow(
            icon = Icons.Rounded.ErrorOutline,
            tint = MaterialTheme.colorScheme.error,
            title = "None of these values could be read as a date.",
            subtitle = "Pick a different column."
        )

        else -> LifeLogCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                StatusRow(
                    icon = Icons.Rounded.WarningAmber,
                    tint = MaterialTheme.colorScheme.error,
                    title = "${validation.failedCount} of ${validation.total} values couldn't be read as a date.",
                    subtitle = "These rows will be skipped during import."
                )
                validation.sampleFailures.forEach { issue ->
                    Text(
                        "Row ${issue.rowNumber}: ${issue.value.ifBlank { "(empty)" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ── Step 5: review fields ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewFieldsStep(
    state: ImportCsvUiState,
    onSetFieldType: (Int, FieldType) -> Unit,
    onSetFieldName: (Int, String) -> Unit,
    onBack: () -> Unit,
    onImport: () -> Unit
) {
    val timestampName = state.timestampColumnIndex
        ?.let { state.parsed?.headers?.getOrNull(it) }
        ?.ifBlank { "Column ${(state.timestampColumnIndex ?: 0) + 1}" }
        .orEmpty()
    val rowCount = state.parsed?.dataRowCount ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Fields") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        },
        bottomBar = {
            WizardBottomBar(
                primaryText = "Create event & import",
                // A timestamp-only CSV (no other columns) is still importable —
                // it just produces entries with only a date/time.
                primaryEnabled = true,
                onPrimary = onImport
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.screenEdge,
                end = Spacing.screenEdge,
                top = Spacing.sm,
                bottom = Spacing.lg
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                StatusRow(
                    icon = Icons.Rounded.CalendarMonth,
                    tint = MaterialTheme.colorScheme.primary,
                    title = "Date/time: $timestampName",
                    subtitle = "Used as each entry's time."
                )
            }
            item {
                Text(
                    if (state.fields.isEmpty())
                        "This file has no other columns, so entries will only have a date/time."
                    else
                        "Review the field type detected for each column. Tap a type to change it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            itemsIndexed(state.fields) { index, field ->
                FieldReviewCard(
                    field = field,
                    onTypeChange = { onSetFieldType(index, it) },
                    onNameChange = { onSetFieldName(index, it) }
                )
            }
            item {
                Text(
                    "$rowCount entries will be created.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldReviewCard(
    field: FieldDraft,
    onTypeChange: (FieldType) -> Unit,
    onNameChange: (String) -> Unit
) {
    LifeLogCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            OutlinedTextField(
                value = field.name,
                onValueChange = onNameChange,
                label = { Text("Field name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FieldType.entries.forEach { type ->
                    FilterChip(
                        selected = field.type == type,
                        onClick = { onTypeChange(type) },
                        label = { Text(type.displayName, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
            if (field.type == FieldType.CHOICE || field.type == FieldType.MULTI_SELECT) {
                OptionsPreview(field.options)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OptionsPreview(options: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            if (options.isEmpty()) "No values found in this column"
            else "${options.size} options from your data",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (options.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                options.take(MAX_OPTION_CHIPS).forEach { LabelChip(it) }
                if (options.size > MAX_OPTION_CHIPS) {
                    LabelChip("+${options.size - MAX_OPTION_CHIPS} more")
                }
            }
        }
    }
}

// ── Step 6: importing ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportingStep(
    state: ImportCsvUiState,
    onRetry: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Importing") }) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.xl),
            contentAlignment = Alignment.Center
        ) {
            if (state.importError != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(Spacing.lg))
                    Text("Import failed", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        "${state.importError}\n\nNo event was created.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Spacing.xl))
                    Button(onClick = onRetry, modifier = Modifier.height(Sizing.cta)) {
                        Text("Back to fields")
                    }
                }
            } else {
                val progress = state.importProgress
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (progress != null && progress.second > 0) {
                        LinearProgressIndicator(
                            progress = { progress.first.toFloat() / progress.second.toFloat() },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(Spacing.lg))
                        Text(
                            "Importing ${progress.first} of ${progress.second}…",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(Spacing.lg))
                        Text("Creating your event…", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

// ── Completion summary ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SummaryStep(
    summary: CsvImportEngine.ImportSummary?,
    onOpenEvent: (Long) -> Unit,
    onClose: () -> Unit
) {
    if (summary == null) {
        CenteredStatus(loading = true, text = "Finishing up…")
        return
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Import Complete") }) },
        bottomBar = {
            WizardBottomBar(
                primaryText = "View event",
                primaryEnabled = true,
                onPrimary = { onOpenEvent(summary.eventId) },
                secondaryText = "Done",
                onSecondary = onClose
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenEdge),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Column {
                    Text(summary.eventName, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Imported successfully",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LifeLogCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    SummaryRow("Rows imported", summary.rowsImported.toString())
                    SummaryRow("Fields created", summary.fieldsCreated.toString())
                    SummaryRow("Warnings", summary.warnings.toString())
                    SummaryRow(
                        "Failed rows",
                        summary.failedRows.toString(),
                        highlight = summary.failedRows > 0
                    )
                }
            }

            if (summary.failedDetails.isNotEmpty()) {
                DetailSection(
                    title = "Skipped rows",
                    icon = Icons.Rounded.ErrorOutline,
                    tint = MaterialTheme.colorScheme.error,
                    details = summary.failedDetails,
                    total = summary.failedRows
                )
            }
            if (summary.warningDetails.isNotEmpty()) {
                DetailSection(
                    title = "Values kept as text",
                    icon = Icons.Rounded.WarningAmber,
                    tint = MaterialTheme.colorScheme.tertiary,
                    details = summary.warningDetails,
                    total = summary.warnings
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    details: List<String>,
    total: Int
) {
    var expanded by remember { mutableStateOf(false) }
    LifeLogCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                Text("$title ($total)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    if (expanded) "Hide" else "Show",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                details.forEach {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (total > details.size) {
                    Text(
                        "…and ${total - details.size} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Shared pieces ───────────────────────────────────────────────────────────────

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CenteredStatus(loading: Boolean, text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator()
                Spacer(Modifier.height(Spacing.lg))
            }
            Text(text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun WizardBottomBar(
    primaryText: String,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (secondaryText != null && onSecondary != null) {
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .height(Sizing.cta)
                ) { Text(secondaryText) }
            }
            Button(
                onClick = onPrimary,
                enabled = primaryEnabled,
                modifier = Modifier
                    .weight(1f)
                    .height(Sizing.cta)
            ) { Text(primaryText) }
        }
    }
}

private val CSV_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/csv",
    "application/vnd.ms-excel",
    "application/octet-stream"
)

private const val PREVIEW_ROWS = 8
private const val MAX_OPTION_CHIPS = 12
