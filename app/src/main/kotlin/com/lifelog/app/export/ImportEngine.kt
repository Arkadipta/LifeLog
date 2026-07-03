package com.lifelog.app.export

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    sealed interface RestoreResult {
        /** File validated and staged; the app must restart to apply it. */
        data class Success(val counts: SqliteRestore.EntityCounts) : RestoreResult

        /** Validation or access failed; the current database is untouched. */
        data class Error(val message: String) : RestoreResult
    }

    /**
     * Validate and stage a full restore from an exported SQLite database at [uri].
     *
     * The picked file is copied to private cache, validated (see
     * [SqliteRestore.validate]), and — only if valid — staged for the next app
     * launch to swap in. The live database is never modified here, so any
     * failure leaves existing data fully intact. On [RestoreResult.Success] the
     * caller is responsible for restarting the app via
     * [SqliteRestore.triggerRestart].
     */
    suspend fun restoreFromSqlite(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val temp = File(context.cacheDir, "restore_candidate.db")
        try {
            val copy = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { input.copyTo(it) }
                    true
                } ?: false
            }
            when {
                copy.isFailure -> return@withContext RestoreResult.Error(
                    "Could not read the selected file: ${copy.exceptionOrNull()?.message ?: "access denied"}."
                )
                copy.getOrNull() != true -> return@withContext RestoreResult.Error(
                    "Could not open the selected file. It may have been moved or deleted."
                )
            }

            when (val v = SqliteRestore.validate(temp)) {
                is SqliteRestore.Validation.Invalid -> RestoreResult.Error(v.reason)
                is SqliteRestore.Validation.Valid -> runCatching {
                    // Stage only — the swap and the success outcome are written by
                    // SqliteRestore.applyStagedRestoreIfPresent on the next launch.
                    temp.copyTo(SqliteRestore.stagedFile(context), overwrite = true)
                }.fold(
                    onSuccess = { RestoreResult.Success(v.counts) },
                    onFailure = { RestoreResult.Error("Could not prepare the restore: ${it.message}") }
                )
            }
        } finally {
            // Validation opens the file read-only, which can spawn -wal/-shm next
            // to it; remove the candidate together with any sidecars.
            runCatching {
                temp.delete()
                File(temp.path + "-wal").delete()
                File(temp.path + "-shm").delete()
                File(temp.path + "-journal").delete()
            }
        }
    }
}
