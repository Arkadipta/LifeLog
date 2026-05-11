package com.lifelog.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.data.repository.UserPreferences
import com.lifelog.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefsRepo: UserPreferencesRepository
) : ViewModel() {

    val prefs: StateFlow<UserPreferences> = prefsRepo.userPreferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    fun setDarkTheme(v: Boolean) = viewModelScope.launch { prefsRepo.setDarkTheme(v) }
    fun setAmoledBlack(v: Boolean) = viewModelScope.launch { prefsRepo.setAmoledBlack(v) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { prefsRepo.setDynamicColor(v) }
}
