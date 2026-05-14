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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefsRepo: UserPreferencesRepository,
    private val exportEngine: ExportEngine
) : ViewModel() {

    val prefs: StateFlow<UserPreferences> = prefsRepo.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private val _exportState = MutableStateFlow(ExportUiState())
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

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
}
