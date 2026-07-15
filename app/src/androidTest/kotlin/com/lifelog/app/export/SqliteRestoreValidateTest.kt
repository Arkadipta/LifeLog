package com.lifelog.app.export

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifelog.app.data.db.LifeLogDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Pins [SqliteRestore.validate] against real SQLite files on device — every
 * rejection reason a picked "backup" can hit, plus the count-exact Valid path.
 * (The apply/swap phase stays emulator-verified with the H3 restore round trip;
 * it rewrites the app's real database file, which a test must not do.)
 */
@RunWith(AndroidJUnit4::class)
class SqliteRestoreValidateTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val created = mutableListOf<File>()

    private val requiredTables =
        listOf("event_types", "event_fields", "event_entries", "reminders", "chart_configs")

    @After
    fun cleanUp() {
        created.forEach { f ->
            f.delete()
            f.deleteSqliteSidecars()
        }
    }

    private fun newFile(name: String): File =
        File(context.cacheDir, name).also { it.delete(); created += it }

    /** Build a minimal LifeLog-shaped SQLite file. */
    private fun createDbFile(
        name: String,
        userVersion: Int = LifeLogDatabase.SCHEMA_VERSION,
        omitTable: String? = null,
        rowCounts: Map<String, Int> = emptyMap()
    ): File {
        val file = newFile(name)
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            requiredTables.filterNot { it == omitTable }.forEach { table ->
                db.execSQL("CREATE TABLE `$table` (id INTEGER PRIMARY KEY)")
                repeat(rowCounts[table] ?: 0) {
                    db.execSQL("INSERT INTO `$table` (id) VALUES (NULL)")
                }
            }
            db.version = userVersion
        }
        return file
    }

    private fun invalidReason(file: File): String {
        val v = SqliteRestore.validate(file)
        assertTrue("expected Invalid, got $v", v is SqliteRestore.Validation.Invalid)
        return (v as SqliteRestore.Validation.Invalid).reason
    }

    @Test
    fun currentSchemaVersion_validatesWithExactCounts() {
        val file = createDbFile(
            "valid.db",
            rowCounts = mapOf("event_types" to 2, "event_entries" to 5, "reminders" to 1)
        )
        val v = SqliteRestore.validate(file)
        assertTrue("expected Valid, got $v", v is SqliteRestore.Validation.Valid)
        val counts = (v as SqliteRestore.Validation.Valid).counts
        assertEquals(2, counts.eventTypes)
        assertEquals(0, counts.eventFields)
        assertEquals(5, counts.eventEntries)
        assertEquals(1, counts.reminders)
        assertEquals(0, counts.chartConfigs)
    }

    @Test
    fun versionZero_isRejectedAsNotALifeLogFile() {
        val reason = invalidReason(createDbFile("v0.db", userVersion = 0))
        assertTrue(reason, "not created by LifeLog" in reason)
    }

    @Test
    fun versionAboveCurrent_isRejectedWithoutClaimingItIsNewer() {
        val above = LifeLogDatabase.SCHEMA_VERSION + 1
        val reason = invalidReason(createDbFile("newer.db", userVersion = above))
        // The M13 wording: names the version, offers both possible causes, asserts neither.
        assertTrue(reason, "schema v$above" in reason)
        assertTrue(reason, "newer version" in reason)
        assertTrue(reason, "pre-1.0" in reason)
    }

    @Test
    fun missingRequiredTable_isRejectedNamingIt() {
        val reason = invalidReason(createDbFile("no-reminders.db", omitTable = "reminders"))
        assertTrue(reason, "reminders" in reason)
    }

    @Test
    fun nonSqliteFile_isRejectedBySignatureCheck() {
        val file = newFile("garbage.db")
        file.writeText("definitely not a sqlite database, padded to pass the length check")
        val reason = invalidReason(file)
        assertTrue(reason, "not a valid SQLite database" in reason)
    }

    @Test
    fun emptyFile_isRejected() {
        val reason = invalidReason(newFile("empty.db").also { it.createNewFile() })
        assertTrue(reason, "empty" in reason)
    }
}
