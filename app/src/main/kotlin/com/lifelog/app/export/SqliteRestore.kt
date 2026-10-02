package com.lifelog.app.export

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import com.lifelog.app.data.db.LifeLogDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.io.File

/**
 * Full-database restore from a previously exported SQLite file.
 *
 * Room keeps an open handle on its database file, so the contents cannot be
 * hot-swapped while the app is running. The restore therefore happens in two
 * phases:
 *
 *  1. **Stage** (app running): the picked file is validated and, only if valid,
 *     copied to [STAGED_DB_NAME] in internal storage. The live database is left
 *     completely untouched. See [ImportEngine.restoreFromSqlite].
 *  2. **Apply** (next process start): [applyStagedRestoreIfPresent] swaps the
 *     staged file over the live database *before* Room opens it, keeping a
 *     rollback copy so a failed swap never destroys existing data.
 *
 * The two-phase design is what guarantees the "current database must remain
 * untouched if any validation or restore step fails" requirement.
 */
object SqliteRestore {

    private const val TAG = "SqliteRestore"

    /** Schema versions we can restore: anything this app can open and migrate. */
    private const val CURRENT_SCHEMA_VERSION = LifeLogDatabase.SCHEMA_VERSION

    private const val STAGED_DB_NAME = "restore_staged.db"
    private const val ROLLBACK_DB_NAME = "restore_rollback.db"
    private const val OUTCOME_FILE_NAME = "restore_outcome.json"

    /** ASCII prefix that opens the 16-byte header of every SQLite 3 file. */
    private val SQLITE_MAGIC = "SQLite format 3".toByteArray(Charsets.US_ASCII)

    /** Tables that must exist for the file to be a usable LifeLog backup. */
    private val REQUIRED_TABLES = listOf(
        "event_types", "event_fields", "event_entries", "reminders", "chart_configs"
    )

    private val json = Json { ignoreUnknownKeys = true }

    // ── Public types ────────────────────────────────────────────────────────────

    @Serializable
    data class EntityCounts(
        val eventTypes: Int = 0,
        val eventFields: Int = 0,
        val eventEntries: Int = 0,
        val reminders: Int = 0,
        val chartConfigs: Int = 0
    )

    sealed interface Validation {
        data class Valid(val counts: EntityCounts) : Validation
        data class Invalid(val reason: String) : Validation
    }

    /** Result of a restore, persisted across the app restart so it can be shown. */
    @Serializable
    data class Outcome(
        val success: Boolean,
        val counts: EntityCounts = EntityCounts(),
        val error: String? = null
    ) {
        companion object {
            fun success(counts: EntityCounts) = Outcome(success = true, counts = counts)
            fun failure(message: String) = Outcome(success = false, error = message)
        }
    }

    fun stagedFile(context: Context): File = File(context.filesDir, STAGED_DB_NAME)

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Validate [file] as a restorable LifeLog SQLite database without modifying
     * anything. Checks, in order: SQLite signature, openability, integrity
     * (corruption), schema-version compatibility, and required-table presence.
     */
    fun validate(file: File): Validation {
        if (!file.exists() || file.length() == 0L) {
            return Validation.Invalid("The selected file is empty or could not be read.")
        }

        // Cheap signature check before handing the file to SQLite.
        val signatureOk = runCatching {
            DataInputStream(file.inputStream()).use { input ->
                val header = ByteArray(SQLITE_MAGIC.size)
                input.readFully(header)
                header.contentEquals(SQLITE_MAGIC)
            }
        }.getOrDefault(false)
        if (!signatureOk) {
            return Validation.Invalid("This file is not a valid SQLite database.")
        }

        var db: SQLiteDatabase? = null
        try {
            db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)

            // Corruption check.
            val integrity = db.rawQuery("PRAGMA integrity_check", null).use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            if (!"ok".equals(integrity, ignoreCase = true)) {
                return Validation.Invalid("The database file is corrupted and cannot be restored.")
            }

            // Schema-version compatibility. Room stores its version in user_version.
            when (val version = db.version) {
                in 1..CURRENT_SCHEMA_VERSION -> Unit // ok — Room migrates older ones on open
                in Int.MIN_VALUE..0 -> return Validation.Invalid(
                    "This file was not created by LifeLog (unrecognized schema)."
                )
                // Above ours is ambiguous: a newer app release, or a pre-1.0 build
                // (the 1.0 schema reset restarted numbering at 1, so legacy backups
                // carry higher numbers that future releases will reuse). The number
                // alone can't tell them apart, so the message must claim neither.
                else -> return Validation.Invalid(
                    "This backup uses a database format (schema v$version) that this " +
                        "version of LifeLog can't read. If it came from a newer version " +
                        "of the app, update LifeLog and try again; backups from pre-1.0 " +
                        "builds can no longer be restored."
                )
            }

            // Required tables.
            val tables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'", null
            ).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            val missing = REQUIRED_TABLES.filterNot { it in tables }
            if (missing.isNotEmpty()) {
                return Validation.Invalid(
                    "The backup is missing required tables: ${missing.joinToString()}."
                )
            }

            val counts = EntityCounts(
                eventTypes = db.count("event_types"),
                eventFields = db.count("event_fields"),
                eventEntries = db.count("event_entries"),
                reminders = db.count("reminders"),
                chartConfigs = db.count("chart_configs")
            )
            return Validation.Valid(counts)
        } catch (e: SQLiteException) {
            return Validation.Invalid("This file is not a readable SQLite database.")
        } catch (e: Exception) {
            return Validation.Invalid("Could not validate the database: ${e.message}")
        } finally {
            runCatching { db?.close() }
        }
    }

    private fun SQLiteDatabase.count(table: String): Int =
        rawQuery("SELECT COUNT(*) FROM `$table`", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    /**
     * Delete this SQLite file together with its sidecars. Validation opens files
     * read-only, which can still spawn -wal/-shm next to them, so plain delete()
     * would leak those siblings.
     */
    private fun File.deleteWithSidecars() {
        delete()
        deleteSqliteSidecars()
    }

    // ── Apply (startup) ─────────────────────────────────────────────────────────

    /** True if a validated restore is waiting to be applied on next launch. */
    fun hasStagedRestore(context: Context): Boolean = stagedFile(context).exists()

    /**
     * If a staged restore exists, swap it over the live database. Must be called
     * from [com.lifelog.app.LifeLogApp.onCreate] before Room opens the database.
     *
     * The live database is backed up first and restored if the swap fails, so an
     * interrupted or failing restore never leaves the app without its data.
     *
     * @return true if a restore was applied successfully (so callers can re-arm
     *   alarms / refresh derived state for the freshly restored data).
     */
    fun applyStagedRestoreIfPresent(context: Context): Boolean {
        val staged = stagedFile(context)
        if (!staged.exists()) return false

        val live = context.getDatabasePath(LifeLogDatabase.DATABASE_NAME)
        val rollback = File(context.filesDir, ROLLBACK_DB_NAME)

        // Re-validate immediately before the destructive swap.
        val counts = when (val validation = validate(staged)) {
            is Validation.Invalid -> {
                Log.w(TAG, "Discarding staged restore: ${validation.reason}")
                writeOutcome(context, Outcome.failure("The staged backup was invalid and was discarded."))
                staged.deleteWithSidecars()
                return false
            }
            is Validation.Valid -> validation.counts
        }

        return try {
            live.parentFile?.mkdirs()
            if (live.exists()) live.copyTo(rollback, overwrite = true)

            // Stale WAL/SHM sidecars belong to the OLD database; replaying them
            // onto the restored file would corrupt it, so they must be removed.
            live.deleteSqliteSidecars()

            staged.copyTo(live, overwrite = true)

            staged.deleteWithSidecars()
            rollback.deleteWithSidecars()
            writeOutcome(context, Outcome.success(counts))
            Log.i(TAG, "Database restore applied.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed; rolling back to previous database.", e)
            runCatching { if (rollback.exists()) rollback.copyTo(live, overwrite = true) }
            staged.deleteWithSidecars()
            rollback.deleteWithSidecars()
            writeOutcome(
                context,
                Outcome.failure("The restore could not be completed. Your previous data was kept.")
            )
            false
        }
    }

    // ── Outcome persistence (survives the restart) ────────────────────────────────

    fun writeOutcome(context: Context, outcome: Outcome) {
        runCatching {
            File(context.filesDir, OUTCOME_FILE_NAME)
                .writeText(json.encodeToString(Outcome.serializer(), outcome))
        }.onFailure { Log.w(TAG, "Could not persist restore outcome", it) }
    }

    /** Read and delete the pending restore outcome, if any. */
    fun consumeOutcome(context: Context): Outcome? {
        val file = File(context.filesDir, OUTCOME_FILE_NAME)
        if (!file.exists()) return null
        val outcome = runCatching {
            json.decodeFromString(Outcome.serializer(), file.readText())
        }.getOrNull()
        file.delete()
        return outcome
    }

    // ── Restart ───────────────────────────────────────────────────────────────

    /**
     * Relaunch the app from scratch so the freshly restored database is opened by
     * a brand-new process — every repository, view model and cache is rebuilt.
     */
    fun triggerRestart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            context.startActivity(launch)
        }
        Runtime.getRuntime().exit(0)
    }
}

/**
 * Delete this file's SQLite sidecars (-wal/-shm/-journal), if present — without
 * touching the file itself. [SqliteRestore.validate] opens files read-only,
 * which can still spawn -wal/-shm next to them.
 */
internal fun File.deleteSqliteSidecars() {
    File("$path-wal").delete()
    File("$path-shm").delete()
    File("$path-journal").delete()
}
