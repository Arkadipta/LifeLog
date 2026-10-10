package com.lifelog.app.data.repository

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lifelog.app.domain.model.EventSortOption
import com.lifelog.app.export.BackupFrequency
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * The preferences read path: the decode itself, and the policy that it never throws.
 *
 * The second half is what MainActivity's splash-screen gate rests on — it keeps the
 * splash up until this flow emits, so a read that threw instead of degrading would
 * strand the app on the splash screen rather than merely losing a setting.
 */
class UserPreferencesDecodeTest {

    private var reported: Throwable? = null

    private fun Flow<Preferences>.decode(): Flow<UserPreferences> =
        decodeUserPreferences { reported = it }

    // ── Decode ───────────────────────────────────────────────────────────────

    @Test
    fun `stored values decode into preferences`() = runBlocking {
        val stored = mutablePreferencesOf(
            booleanPreferencesKey("amoled_black") to true,
            booleanPreferencesKey("dynamic_color") to true,
            stringPreferencesKey("backup_frequency") to BackupFrequency.DAILY.name,
            longPreferencesKey("last_backup_at") to 1_700_000_000_000L,
            stringPreferencesKey("backup_dir_uri") to "content://tree/backups",
            stringPreferencesKey("event_sort_option") to EventSortOption.NAME_ASC.name
        )

        assertEquals(
            UserPreferences(
                useAmoledBlack = true,
                useDynamicColor = true,
                backupFrequency = BackupFrequency.DAILY,
                lastBackupAt = 1_700_000_000_000L,
                backupDirUri = "content://tree/backups",
                eventSortOption = EventSortOption.NAME_ASC
            ),
            flowOf(stored).decode().first()
        )
        assertNull(reported)
    }

    @Test
    fun `an unwritten file decodes to the defaults`() = runBlocking {
        assertEquals(UserPreferences(), flowOf(emptyPreferences()).decode().first())
        assertNull(reported)
    }

    @Test
    fun `an enum name the app no longer knows falls back to its default`() = runBlocking {
        // Written by a build whose enums had other constants — losing one setting is the
        // whole cost, the rest of the file still decodes.
        val stored = mutablePreferencesOf(
            booleanPreferencesKey("amoled_black") to true,
            stringPreferencesKey("backup_frequency") to "FORTNIGHTLY",
            stringPreferencesKey("event_sort_option") to "BY_VIBES"
        )

        val decoded = flowOf(stored).decode().first()

        assertTrue(decoded.useAmoledBlack)
        assertEquals(BackupFrequency.OFF, decoded.backupFrequency)
        assertEquals(EventSortOption.DEFAULT, decoded.eventSortOption)
        assertNull(reported)
    }

    // ── Never throw on read ──────────────────────────────────────────────────

    @Test
    fun `a file that cannot be read emits the defaults and reports why`() = runBlocking {
        val failure = IOException("corrupted")

        val decoded = flow<Preferences> { throw failure }.decode().first()

        assertEquals(UserPreferences(), decoded)
        assertSame(failure, reported)
    }

    @Test
    fun `a value stored under a key of another type emits the defaults`() = runBlocking {
        // Keys compare by name, so a string written where a boolean is expected is handed
        // back unchecked and blows up on use — a decode failure, not a read failure.
        val stored = mutablePreferencesOf(stringPreferencesKey("amoled_black") to "yes")

        assertEquals(UserPreferences(), flowOf(stored).decode().first())
        assertTrue(reported is ClassCastException)
    }

    @Test
    fun `cancellation is not mistaken for a read failure`() = runBlocking {
        val cancelled = flow<Preferences> { throw CancellationException("collector went away") }

        try {
            cancelled.decode().first()
            fail("cancellation should propagate instead of emitting defaults")
        } catch (e: CancellationException) {
            assertEquals("collector went away", e.message)
        }
        assertNull(reported)
    }
}
