package com.lifelog.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lifelog.app.domain.model.EventSortOption
import com.lifelog.app.export.BackupFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class UserPreferences(
    val useAmoledBlack: Boolean = false,
    val useDynamicColor: Boolean = false,
    val backupFrequency: BackupFrequency = BackupFrequency.OFF,
    val lastBackupAt: Long = 0L,
    /** SAF tree uri auto-backups are written to; null = app-private storage. */
    val backupDirUri: String? = null,
    val eventSortOption: EventSortOption = EventSortOption.DEFAULT
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val BACKUP_FREQUENCY = stringPreferencesKey("backup_frequency")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
        val BACKUP_DIR_URI = stringPreferencesKey("backup_dir_uri")
        val EVENT_SORT_OPTION = stringPreferencesKey("event_sort_option")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            useAmoledBlack = prefs[Keys.AMOLED_BLACK] ?: false,
            useDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            backupFrequency = prefs[Keys.BACKUP_FREQUENCY]
                ?.let { runCatching { BackupFrequency.valueOf(it) }.getOrNull() }
                ?: BackupFrequency.OFF,
            lastBackupAt = prefs[Keys.LAST_BACKUP_AT] ?: 0L,
            backupDirUri = prefs[Keys.BACKUP_DIR_URI],
            eventSortOption = prefs[Keys.EVENT_SORT_OPTION]
                ?.let { runCatching { EventSortOption.valueOf(it) }.getOrNull() }
                ?: EventSortOption.DEFAULT
        )
    }

    suspend fun setAmoledBlack(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AMOLED_BLACK] = enabled }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setBackupFrequency(freq: BackupFrequency) {
        context.dataStore.edit { it[Keys.BACKUP_FREQUENCY] = freq.name }
    }

    suspend fun setLastBackupAt(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_BACKUP_AT] = timestamp }
    }

    suspend fun setBackupDirUri(uri: String?) {
        context.dataStore.edit {
            if (uri == null) it.remove(Keys.BACKUP_DIR_URI) else it[Keys.BACKUP_DIR_URI] = uri
        }
    }

    suspend fun setEventSortOption(option: EventSortOption) {
        context.dataStore.edit { it[Keys.EVENT_SORT_OPTION] = option.name }
    }
}
