package com.lifelog.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lifelog.app.export.BackupFrequency
import com.lifelog.app.export.ExportFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class UserPreferences(
    val useAmoledBlack: Boolean = false,
    val useDynamicColor: Boolean = false,
    val backupFrequency: BackupFrequency = BackupFrequency.OFF,
    val backupFormat: ExportFormat = ExportFormat.SQLITE,
    val lastBackupAt: Long = 0L
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
        val BACKUP_FORMAT = stringPreferencesKey("backup_format")
        val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            useAmoledBlack = prefs[Keys.AMOLED_BLACK] ?: false,
            useDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            backupFrequency = prefs[Keys.BACKUP_FREQUENCY]
                ?.let { runCatching { BackupFrequency.valueOf(it) }.getOrNull() }
                ?: BackupFrequency.OFF,
            backupFormat = prefs[Keys.BACKUP_FORMAT]
                ?.let { runCatching { ExportFormat.valueOf(it) }.getOrNull() }
                ?: ExportFormat.SQLITE,
            lastBackupAt = prefs[Keys.LAST_BACKUP_AT] ?: 0L
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

    suspend fun setBackupFormat(format: ExportFormat) {
        context.dataStore.edit { it[Keys.BACKUP_FORMAT] = format.name }
    }

    suspend fun setLastBackupAt(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_BACKUP_AT] = timestamp }
    }
}
