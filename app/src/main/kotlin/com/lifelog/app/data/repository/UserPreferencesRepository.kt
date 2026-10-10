package com.lifelog.app.data.repository

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UserPreferences"

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

private object Keys {
    val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val BACKUP_FREQUENCY = stringPreferencesKey("backup_frequency")
    val LAST_BACKUP_AT = longPreferencesKey("last_backup_at")
    val BACKUP_DIR_URI = stringPreferencesKey("backup_dir_uri")
    val EVENT_SORT_OPTION = stringPreferencesKey("event_sort_option")
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Process-lifetime by design: this is an app-scoped singleton and [loaded] is meant
    // to stay warm from the moment it is constructed until the process dies.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val userPreferences: Flow<UserPreferences> = context.dataStore.data.decodeUserPreferences { e ->
        Log.w(TAG, "Could not read stored preferences; falling back to defaults", e)
    }

    /**
     * The stored preferences once they have been read, `null` until then.
     *
     * Eager on purpose: the read starts when this singleton is constructed — which
     * [com.lifelog.app.LifeLogApp] forces to process start — rather than when a screen
     * first collects. The UI cannot draw an honest frame before it knows the theme, and
     * a collector that subscribes from a composition gets its first value one
     * recomposition *later*, which on a cold start is several frames after the window is
     * already on screen. Callers that can wait (MainActivity holds the splash screen)
     * should draw nothing while this is null; callers that cannot fall back to defaults.
     */
    val loaded: StateFlow<UserPreferences?> =
        userPreferences.stateIn(scope, SharingStarted.Eagerly, null)

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

/** Every stored key, with the default that stands in for a missing or unreadable one. */
internal fun Preferences.toUserPreferences(): UserPreferences = UserPreferences(
    useAmoledBlack = this[Keys.AMOLED_BLACK] ?: false,
    useDynamicColor = this[Keys.DYNAMIC_COLOR] ?: false,
    backupFrequency = this[Keys.BACKUP_FREQUENCY]
        ?.let { runCatching { BackupFrequency.valueOf(it) }.getOrNull() }
        ?: BackupFrequency.OFF,
    lastBackupAt = this[Keys.LAST_BACKUP_AT] ?: 0L,
    backupDirUri = this[Keys.BACKUP_DIR_URI],
    eventSortOption = this[Keys.EVENT_SORT_OPTION]
        ?.let { runCatching { EventSortOption.valueOf(it) }.getOrNull() }
        ?: EventSortOption.DEFAULT
)

/**
 * Decodes the stored entries, and never throws while doing it: a file that cannot be
 * read (IO error) or that holds a value under a key's name with a different type
 * degrades to [UserPreferences] defaults, reported through [onReadFailure] rather than
 * swallowed. Same never-throw-on-read policy the database mappers follow (Mappers.kt),
 * and load-bearing here — MainActivity keeps the splash screen up until this flow
 * emits, so an exception thrown at it would strand the app on the splash forever.
 *
 * The recovery ends the flow, as any `catch` does: after a read failure the app keeps
 * the defaults until it is restarted, instead of dying with the collector.
 */
internal fun Flow<Preferences>.decodeUserPreferences(
    onReadFailure: (Throwable) -> Unit
): Flow<UserPreferences> = map { it.toUserPreferences() }
    .catch { e ->
        if (e is CancellationException) throw e
        onReadFailure(e)
        emit(UserPreferences())
    }
