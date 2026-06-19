package com.lifelog.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.lifelog.app.data.repository.UserPreferences
import com.lifelog.app.data.repository.UserPreferencesRepository
import com.lifelog.app.export.AutoBackupWorker
import com.lifelog.app.export.BackupFrequency
import com.lifelog.app.export.ExportEngine
import com.lifelog.app.export.ExportFormat
import com.lifelog.app.export.ImportEngine
import com.lifelog.app.export.SqliteRestore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExportUiState(
    val isExporting: Boolean = false,
    val lastExportError: String? = null,
    val exportSuccess: Boolean = false
)

/** Phases of the SQLite restore flow, surfaced to the Settings UI. */
data class RestoreUiState(
    val isRestoring: Boolean = false,
    val isRestarting: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepo: UserPreferencesRepository,
    private val exportEngine: ExportEngine,
    private val importEngine: ImportEngine
) : ViewModel() {

    val prefs: StateFlow<UserPreferences> = prefsRepo.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _exportState = MutableStateFlow(ExportUiState())
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private val _restoreState = MutableStateFlow(RestoreUiState())
    val restoreState: StateFlow<RestoreUiState> = _restoreState.asStateFlow()

    fun setAmoledBlack(v: Boolean) = viewModelScope.launch { prefsRepo.setAmoledBlack(v) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { prefsRepo.setDynamicColor(v) }

    fun setBackupFrequency(freq: BackupFrequency) = viewModelScope.launch {
        prefsRepo.setBackupFrequency(freq)
        val wm = WorkManager.getInstance(context)
        AutoBackupWorker.schedule(wm, freq)
    }

    fun setBackupFormat(format: ExportFormat) = viewModelScope.launch {
        prefsRepo.setBackupFormat(format)
        // Re-schedule if an active backup is running so it picks up the new format
        val freq = prefs.value.backupFrequency
        if (freq != BackupFrequency.OFF) {
            AutoBackupWorker.schedule(WorkManager.getInstance(context), freq)
        }
    }

    fun exportNow(uri: Uri, format: ExportFormat) = viewModelScope.launch {
        _exportState.update { it.copy(isExporting = true, lastExportError = null, exportSuccess = false) }
        runCatching {
            when (format) {
                ExportFormat.SQLITE -> exportEngine.exportSqlite(uri)
                ExportFormat.JSON -> exportEngine.exportJson(uri)
                ExportFormat.ZIP_CSV -> exportEngine.exportZipCsv(uri)
            }
        }.onSuccess {
            _exportState.update { it.copy(isExporting = false, exportSuccess = true) }
        }.onFailure { e ->
            _exportState.update { it.copy(isExporting = false, lastExportError = e.message ?: "Export failed") }
        }
    }

    fun clearExportResult() {
        _exportState.update { it.copy(lastExportError = null, exportSuccess = false) }
    }

    /**
     * Validate, stage, and (on success) apply a full restore from the chosen
     * SQLite database. On success the app is relaunched so Room re-opens the
     * restored database in a fresh process; the success message is shown after
     * the restart. On failure the current database is left untouched.
     */
    fun restoreFromSqlite(uri: Uri) = viewModelScope.launch {
        _restoreState.update { it.copy(isRestoring = true, error = null) }
        when (val result = importEngine.restoreFromSqlite(uri)) {
            is ImportEngine.RestoreResult.Error ->
                _restoreState.update { it.copy(isRestoring = false, error = result.message) }
            is ImportEngine.RestoreResult.Success -> {
                _restoreState.update { it.copy(isRestoring = false, isRestarting = true) }
                delay(1200) // let the "restarting" dialog register before the process dies
                SqliteRestore.triggerRestart(context)
            }
        }
    }

    fun clearRestoreError() {
        _restoreState.update { it.copy(error = null) }
    }
}
