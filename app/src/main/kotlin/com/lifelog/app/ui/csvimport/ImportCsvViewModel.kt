package com.lifelog.app.ui.csvimport

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.csv.CsvImportEngine
import com.lifelog.app.domain.csv.CsvDateTimeParser
import com.lifelog.app.domain.csv.CsvFieldInference
import com.lifelog.app.domain.csv.ParsedCsv
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.ui.events.EventDraft
import com.lifelog.app.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The sequential steps of the CSV → new event import wizard. */
enum class ImportStep {
    SELECT_FILE,        // pick + preview a CSV (Step 1)
    CONFIGURE_EVENT,    // name/color/icon via CreateEventScreen (Step 2)
    SELECT_TIMESTAMP,   // choose the date/time column (Step 3)
    REVIEW_FIELDS,      // review inferred field types (Steps 4 & 5)
    IMPORTING,          // create event + import rows (Step 6)
    SUMMARY             // completion report
}

/** A CSV column staged to become an event field, with its (editable) type. */
data class FieldDraft(
    val columnIndex: Int,
    val name: String,
    val type: FieldType,
    val options: List<String>
)

/** A row whose timestamp could not be parsed, for surfacing to the user. */
data class RowIssue(val rowNumber: Int, val value: String)

/** Result of parsing every value in the chosen timestamp column. */
data class TimestampValidation(
    val total: Int,
    val parsed: Int,
    val sampleFailures: List<RowIssue>
) {
    val failedCount: Int get() = total - parsed
    val hasFailures: Boolean get() = parsed < total
    val allFailed: Boolean get() = parsed == 0 && total > 0
}

data class ImportCsvUiState(
    val step: ImportStep = ImportStep.SELECT_FILE,
    val isParsing: Boolean = false,
    val parseError: String? = null,
    val parsed: ParsedCsv? = null,
    val suggestedName: String = "",
    val draft: EventDraft? = null,
    val timestampColumnIndex: Int? = null,
    val isValidatingTimestamp: Boolean = false,
    val timestampValidation: TimestampValidation? = null,
    val fields: List<FieldDraft> = emptyList(),
    val fieldsForTimestampIndex: Int? = null,
    val importProgress: Pair<Int, Int>? = null,
    val importError: String? = null,
    val summary: CsvImportEngine.ImportSummary? = null
)

@HiltViewModel
class ImportCsvViewModel @Inject constructor(
    private val engine: CsvImportEngine,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    private val _state = MutableStateFlow(ImportCsvUiState())
    val state: StateFlow<ImportCsvUiState> = _state.asStateFlow()

    // ── Step 1: select + parse ──────────────────────────────────────────────────

    fun onFileSelected(uri: Uri) {
        _state.update { it.copy(isParsing = true, parseError = null, parsed = null) }
        viewModelScope.launch {
            when (val result = engine.readFile(uri)) {
                is CsvImportEngine.ReadResult.Success -> _state.update {
                    it.copy(isParsing = false, parsed = result.parsed, suggestedName = result.suggestedName)
                }
                is CsvImportEngine.ReadResult.Failure -> _state.update {
                    it.copy(isParsing = false, parseError = result.message, parsed = null)
                }
            }
        }
    }

    fun dismissParseError() = _state.update { it.copy(parseError = null) }

    fun continueFromPreview() {
        if (_state.value.parsed?.let { it.dataRowCount > 0 } == true) {
            _state.update { it.copy(step = ImportStep.CONFIGURE_EVENT) }
        }
    }

    // ── Step 2: configure event ─────────────────────────────────────────────────

    fun setDraft(draft: EventDraft) {
        val parsed = _state.value.parsed
        _state.update { it.copy(draft = draft, step = ImportStep.SELECT_TIMESTAMP) }
        // Pre-select a likely timestamp column the first time we reach this step.
        if (_state.value.timestampColumnIndex == null && parsed != null) {
            suggestTimestampColumn(parsed)?.let { selectTimestampColumn(it) }
        }
    }

    // ── Step 3: select timestamp column ─────────────────────────────────────────

    fun selectTimestampColumn(index: Int) {
        val parsed = _state.value.parsed ?: return
        _state.update {
            it.copy(timestampColumnIndex = index, isValidatingTimestamp = true, timestampValidation = null)
        }
        viewModelScope.launch {
            val validation = withContext(Dispatchers.Default) {
                val values = parsed.column(index)
                var ok = 0
                val samples = mutableListOf<RowIssue>()
                values.forEachIndexed { i, value ->
                    if (CsvDateTimeParser.parse(value) != null) {
                        ok++
                    } else if (samples.size < MAX_TIMESTAMP_SAMPLES) {
                        samples += RowIssue(i + 1, value)
                    }
                }
                TimestampValidation(total = values.size, parsed = ok, sampleFailures = samples)
            }
            _state.update {
                // Ignore stale results if the user already picked another column.
                if (it.timestampColumnIndex == index) {
                    it.copy(timestampValidation = validation, isValidatingTimestamp = false)
                } else it
            }
        }
    }

    fun continueFromTimestamp() {
        val st = _state.value
        val parsed = st.parsed ?: return
        val index = st.timestampColumnIndex ?: return
        if (st.timestampValidation?.allFailed != false) return

        // Regenerate fields only when the timestamp column changed, so returning
        // to this step without changing it preserves any field-type edits.
        val needsRegen = st.fields.isEmpty() || st.fieldsForTimestampIndex != index
        val fields = if (needsRegen) generateFields(parsed, index) else st.fields
        _state.update {
            it.copy(step = ImportStep.REVIEW_FIELDS, fields = fields, fieldsForTimestampIndex = index)
        }
    }

    // ── Step 5: review fields ───────────────────────────────────────────────────

    fun setFieldType(index: Int, type: FieldType) {
        _state.update { st ->
            val parsed = st.parsed ?: return@update st
            val fields = st.fields.toMutableList()
            val field = fields.getOrNull(index) ?: return@update st
            val values = parsed.column(field.columnIndex)
            val options = when (type) {
                FieldType.CHOICE -> CsvFieldInference.choiceOptions(values)
                FieldType.MULTI_SELECT -> CsvFieldInference.tagOptions(values)
                else -> emptyList()
            }
            fields[index] = field.copy(type = type, options = options)
            st.copy(fields = fields)
        }
    }

    fun setFieldName(index: Int, name: String) {
        _state.update { st ->
            val fields = st.fields.toMutableList()
            val field = fields.getOrNull(index) ?: return@update st
            fields[index] = field.copy(name = name)
            st.copy(fields = fields)
        }
    }

    // ── Step 6: import ──────────────────────────────────────────────────────────

    fun startImport() {
        val st = _state.value
        val parsed = st.parsed ?: return
        val draft = st.draft ?: return
        val timestampIndex = st.timestampColumnIndex ?: return

        _state.update {
            it.copy(
                step = ImportStep.IMPORTING,
                importError = null,
                importProgress = 0 to parsed.dataRowCount
            )
        }

        viewModelScope.launch {
            val request = CsvImportEngine.ImportRequest(
                event = EventType(
                    name = draft.name,
                    description = draft.description,
                    category = draft.category,
                    colorArgb = draft.colorArgb,
                    iconName = draft.iconName
                ),
                fields = st.fields.map {
                    CsvImportEngine.FieldSpec(it.columnIndex, it.name.trim().ifBlank { fallbackName(it.columnIndex) }, it.type, it.options)
                },
                timestampColumnIndex = timestampIndex,
                rows = parsed.rows
            )
            runCatching {
                engine.import(request) { processed, total ->
                    _state.update { it.copy(importProgress = processed to total) }
                }
            }.onSuccess { summary ->
                widgetUpdater.refreshAll()
                _state.update {
                    it.copy(step = ImportStep.SUMMARY, summary = summary, importProgress = null)
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(importError = e.message ?: "The import could not be completed.")
                }
            }
        }
    }

    /** From the import-error state, return to field review to retry. */
    fun dismissImportError() {
        _state.update {
            it.copy(step = ImportStep.REVIEW_FIELDS, importError = null, importProgress = null)
        }
    }

    // ── Navigation ──────────────────────────────────────────────────────────────

    fun goToStep(step: ImportStep) = _state.update { it.copy(step = step) }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private fun generateFields(parsed: ParsedCsv, timestampIndex: Int): List<FieldDraft> =
        parsed.headers.indices
            .filter { it != timestampIndex }
            .map { col ->
                FieldDraft(
                    columnIndex = col,
                    name = parsed.headers[col].trim().ifBlank { fallbackName(col) },
                    type = CsvFieldInference.infer(parsed.column(col)),
                    options = emptyList()
                )
            }

    private fun fallbackName(columnIndex: Int) = "Column ${columnIndex + 1}"

    private fun suggestTimestampColumn(parsed: ParsedCsv): Int? {
        val keywords = listOf("date", "time", "timestamp", "created", "updated", "when", "datetime", "day")
        parsed.headers.indexOfFirst { header ->
            keywords.any { header.lowercase().contains(it) }
        }.takeIf { it >= 0 }?.let { return it }

        // Otherwise, the first column whose first non-blank value parses as a date.
        return parsed.headers.indices.firstOrNull { col ->
            parsed.column(col).firstOrNull { it.isNotBlank() }
                ?.let { CsvDateTimeParser.parse(it) != null } == true
        }
    }

    private companion object {
        const val MAX_TIMESTAMP_SAMPLES = 20
    }
}
