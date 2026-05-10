package com.lifelog.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.csv.CsvManager
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.data.repository.UserPreferences
import com.lifelog.app.data.repository.UserPreferencesRepository
import com.lifelog.app.shortcuts.ShortcutHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepo: UserPreferencesRepository,
    private val eventRepository: EventRepository,
    private val csvManager: CsvManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val prefs: StateFlow<UserPreferences> = prefsRepo.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setDarkTheme(v: Boolean) = viewModelScope.launch { prefsRepo.setDarkTheme(v) }
    fun setAmoledBlack(v: Boolean) = viewModelScope.launch { prefsRepo.setAmoledBlack(v) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { prefsRepo.setDynamicColor(v) }

    fun exportCsv(uri: Uri, eventTypeId: Long, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val eventType = eventRepository.getEventType(eventTypeId) ?: return@launch
                val entries = eventRepository.getAllEntriesForEventType(eventTypeId)
                csvManager.exportToCsv(uri, eventType, entries)
                onResult(true)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    fun importCsv(uri: Uri, eventTypeId: Long, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            try {
                val eventType = eventRepository.getEventType(eventTypeId) ?: return@launch
                val count = csvManager.importFromCsv(uri, eventType)
                onResult(count)
            } catch (e: Exception) {
                onResult(-1)
            }
        }
    }

    fun refreshShortcuts() {
        viewModelScope.launch {
            ShortcutHelper.setupShortcuts(context)
        }
    }
}
