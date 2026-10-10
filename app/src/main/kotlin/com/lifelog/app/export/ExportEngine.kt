package com.lifelog.app.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.lifelog.app.csv.CsvManager
import com.lifelog.app.data.db.LifeLogDatabase
import com.lifelog.app.data.db.dao.ChartConfigDao
import com.lifelog.app.data.db.dao.EventEntryDao
import com.lifelog.app.data.db.dao.EventFieldDao
import com.lifelog.app.data.db.dao.EventTypeDao
import com.lifelog.app.data.db.dao.ReminderDao
import com.lifelog.app.data.db.entity.ChartConfigEntity
import com.lifelog.app.data.db.entity.EventEntryEntity
import com.lifelog.app.data.db.entity.EventFieldEntity
import com.lifelog.app.data.db.entity.EventTypeEntity
import com.lifelog.app.data.db.entity.ReminderEntity
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.data.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Where auto-backups are currently written, as shown in Settings. */
sealed interface BackupLocationStatus {
    /** Default: app-private storage, wiped on uninstall. */
    data object AppStorage : BackupLocationStatus

    /** A user-selected folder that is currently reachable and writable. */
    data class Folder(val name: String) : BackupLocationStatus

    /**
     * A folder is configured but cannot be used right now: deleted, renamed,
     * on an ejected SD card, or its permission was revoked (e.g. after a
     * reinstall). Backups fall back to app storage until it is re-selected.
     */
    data object FolderUnreachable : BackupLocationStatus
}

@Singleton
class ExportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: LifeLogDatabase,
    private val eventTypeDao: EventTypeDao,
    private val eventFieldDao: EventFieldDao,
    private val eventEntryDao: EventEntryDao,
    private val reminderDao: ReminderDao,
    private val chartConfigDao: ChartConfigDao,
    private val eventRepository: EventRepository,
    private val csvManager: CsvManager,
    private val prefsRepo: UserPreferencesRepository
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    // This is a @Singleton reached from both the auto-backup worker and manual
    // exports; DateTimeFormatter is thread-safe where SimpleDateFormat was not.
    private val fileTimestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)

    // ── Public export-to-URI API (manual exports via SAF) ─────────────────────

    suspend fun exportJson(uri: Uri) = withContext(Dispatchers.IO) {
        val data = buildExportData()
        context.contentResolver.openOutputStream(uri)?.use { os ->
            os.write(json.encodeToString(ExportData.serializer(), data).toByteArray(Charsets.UTF_8))
        }
    }

    suspend fun exportZipCsv(uri: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            writeZipCsv(BufferedOutputStream(os))
        }
    }

    suspend fun exportSqlite(uri: Uri) = withContext(Dispatchers.IO) {
        checkpointWal()
        val dbFile = context.getDatabasePath(LifeLogDatabase.DATABASE_NAME)
        context.contentResolver.openOutputStream(uri)?.use { os ->
            dbFile.inputStream().use { it.copyTo(os) }
        }
    }

    // ── Auto-backup (app storage by default, user-selected SAF folder if set) ──

    /** An auto-backup, restorable via Settings → Restore from Auto-Backup. */
    data class AutoBackup(val source: Source, val modifiedAt: Long, val sizeBytes: Long) {
        sealed interface Source {
            /** App-private storage; restored by reading the file directly. */
            data class AppStorage(val file: File) : Source

            /** User-selected backup folder; restored through the content resolver. */
            data class Folder(val uri: Uri) : Source
        }
    }

    /**
     * Write a new auto-backup. Always a SQLite copy: it is the only format the
     * in-app restore pipeline accepts.
     *
     * The backup goes to the user-selected folder when one is configured and
     * usable; if that folder is unreachable (deleted, renamed, SD card ejected,
     * permission revoked) the backup falls back to app-private storage so a
     * scheduled backup is never lost — Settings surfaces the broken folder.
     */
    suspend fun createAutoBackup(): Unit = withContext(Dispatchers.IO) {
        checkpointWal()
        val treeUri = backupDirUri()
        if (treeUri != null) {
            val attempt = runCatching { writeBackupToTree(treeUri) }
            if (attempt.isSuccess) return@withContext
            Log.w(TAG, "Backup folder write failed; falling back to app storage.", attempt.exceptionOrNull())
        }
        writeBackupToAppStorage()
    }

    /**
     * The auto-backups the restore picker can offer, newest first — the ones in
     * app storage plus, when a backup folder is configured and reachable, the
     * ones in that folder.
     */
    suspend fun listAutoBackups(): List<AutoBackup> = withContext(Dispatchers.IO) {
        val internal = restorableBackups(autoBackupDir().backupCandidates())
            .map { AutoBackup(AutoBackup.Source.AppStorage(it.handle), it.modifiedAt, it.sizeBytes) }
        val folder = backupDirUri()?.let { treeUri ->
            runCatching { restorableBackups(queryTreeBackups(treeUri)) }
                .getOrDefault(emptyList())
                .map { AutoBackup(AutoBackup.Source.Folder(it.handle), it.modifiedAt, it.sizeBytes) }
        }.orEmpty()
        (internal + folder).sortedByDescending { it.modifiedAt }
    }

    /**
     * Point auto-backups at [treeUri] (from ACTION_OPEN_DOCUMENT_TREE), or back
     * at app storage when null. Takes a persistable grant on the new tree and
     * releases the previous one. Existing backups stay where they are.
     */
    suspend fun setBackupLocation(treeUri: Uri?): Unit = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val previous = backupDirUri()
        if (treeUri != null) resolver.takePersistableUriPermission(treeUri, PERSIST_FLAGS)
        prefsRepo.setBackupDirUri(treeUri?.toString())
        if (previous != null && previous != treeUri) {
            runCatching { resolver.releasePersistableUriPermission(previous, PERSIST_FLAGS) }
        }
    }

    /** Status of the configured backup location, for the Settings row. */
    suspend fun locationStatusFor(backupDirUri: String?): BackupLocationStatus =
        withContext(Dispatchers.IO) {
            val treeUri = backupDirUri?.let(Uri::parse)
                ?: return@withContext BackupLocationStatus.AppStorage
            val held = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isReadPermission && it.isWritePermission
            }
            if (!held) return@withContext BackupLocationStatus.FolderUnreachable
            writableTreeName(treeUri)?.let { BackupLocationStatus.Folder(it) }
                ?: BackupLocationStatus.FolderUnreachable
        }

    private suspend fun backupDirUri(): Uri? =
        prefsRepo.userPreferences.first().backupDirUri?.let(Uri::parse)

    private fun autoBackupDir() = File(context.filesDir, "backups")

    private fun backupFileName() =
        "$AUTO_BACKUP_PREFIX${fileTimestampFormat.format(LocalDateTime.now())}.db"

    private fun writeBackupToAppStorage() {
        val backupDir = autoBackupDir().also { it.mkdirs() }
        val file = File(backupDir, backupFileName())
        try {
            context.getDatabasePath(LifeLogDatabase.DATABASE_NAME).copyTo(file)
        } catch (e: Exception) {
            file.delete() // never leave a partial backup behind, or rotate good ones for it
            throw e
        }
        rotateAutoBackups(backupDir, maxKeep = AUTO_BACKUP_MAX_KEEP)
    }

    // ── SAF tree backend ──────────────────────────────────────────────────────

    private fun writeBackupToTree(treeUri: Uri) {
        val resolver = context.contentResolver
        val doc = DocumentsContract.createDocument(
            resolver, treeDocumentUri(treeUri), "application/octet-stream", backupFileName()
        ) ?: throw IOException("Could not create a file in the backup folder")
        try {
            resolver.openOutputStream(doc)?.use { os ->
                context.getDatabasePath(LifeLogDatabase.DATABASE_NAME).inputStream().use { it.copyTo(os) }
            } ?: throw IOException("Could not open the new backup file for writing")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw e
        }
        // The backup itself succeeded — a rotation hiccup must not fail it (and
        // trigger a redundant fallback write), only leave an extra file behind.
        runCatching {
            rotationVictims(queryTreeBackups(treeUri), maxKeep = AUTO_BACKUP_MAX_KEEP)
                .forEach { DocumentsContract.deleteDocument(resolver, it) }
        }.onFailure { Log.w(TAG, "Backup folder rotation failed.", it) }
    }

    /** One bulk query for the folder's files (DocumentFile would query per property). */
    private fun queryTreeBackups(treeUri: Uri): List<BackupCandidate<Uri>> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        val cursor = context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE
            ),
            null, null, null
        ) ?: throw IOException("The backup folder is unreachable")
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val docId = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (c.getString(4) == DocumentsContract.Document.MIME_TYPE_DIR) continue
                    add(
                        BackupCandidate(
                            handle = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                            name = name,
                            modifiedAt = if (c.isNull(2)) 0L else c.getLong(2),
                            sizeBytes = if (c.isNull(3)) 0L else c.getLong(3)
                        )
                    )
                }
            }
        }
    }

    private fun treeDocumentUri(treeUri: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(
        treeUri, DocumentsContract.getTreeDocumentId(treeUri)
    )

    /** Display name for a tree that exists and allows creating files; null otherwise. */
    private fun writableTreeName(treeUri: Uri): String? = runCatching {
        context.contentResolver.query(
            treeDocumentUri(treeUri),
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_FLAGS
            ),
            null, null, null
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            if (c.getString(1) != DocumentsContract.Document.MIME_TYPE_DIR) return@use null
            val flags = if (c.isNull(2)) 0 else c.getInt(2)
            if (flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE == 0) return@use null
            pathFromTreeDocId(treeUri) ?: c.getString(0) ?: "Selected folder"
        }
    }.getOrNull()

    /** "Backups/LifeLog" from an ExternalStorageProvider id like "primary:Backups/LifeLog". */
    private fun pathFromTreeDocId(treeUri: Uri): String? {
        if (treeUri.authority != "com.android.externalstorage.documents") return null
        return DocumentsContract.getTreeDocumentId(treeUri)
            .substringAfter(':', "").takeIf { it.isNotBlank() }
    }

    // ── Core data gathering ───────────────────────────────────────────────────

    suspend fun buildExportData(): ExportData = withContext(Dispatchers.IO) {
        val appVersionCode = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        }.getOrDefault(0)

        ExportData(
            exportedAt = System.currentTimeMillis(),
            appVersionCode = appVersionCode,
            eventTypes = eventTypeDao.getAll().map { it.toRow() },
            eventFields = eventFieldDao.getAll().map { it.toRow() },
            eventEntries = eventEntryDao.getAllEntries().map { it.toRow() },
            reminders = reminderDao.getAll().map { it.toRow() },
            chartConfigs = chartConfigDao.getAll().map { it.toRow() }
        )
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private suspend fun writeZipCsv(outputStream: BufferedOutputStream) {
        val eventTypes = eventRepository.getAllEventTypesForExport()
        ZipOutputStream(outputStream).use { zip ->
            // Metadata without entries (for structure + field definitions)
            val data = buildExportData()
            val metaOnly = data.copy(eventEntries = emptyList())
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(json.encodeToString(ExportData.serializer(), metaOnly).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // One CSV per event type
            for (eventType in eventTypes) {
                val entries = eventRepository.getAllEntriesForEventType(eventType.id)
                val safeName = eventType.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                zip.putNextEntry(ZipEntry("events/${safeName}_${eventType.id}.csv"))
                // Write without closing the underlying stream
                csvManager.writeCsvStream(zip, eventType, entries)
                zip.closeEntry()
            }
        }
    }

    /**
     * Flush the WAL into the main database file so that a copy of that single
     * file is a complete snapshot. Failure must fail the export/backup: the
     * PRAGMA returns a `busy` row instead of throwing when it could not finish,
     * and silently shipping a stale snapshot is worse than an error the user
     * (or the backup worker) can retry.
     */
    private fun checkpointWal() {
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { c ->
            val busy = if (c.moveToFirst()) c.getInt(0) else 1
            if (busy != 0) {
                throw IOException("Could not flush pending changes into the database file")
            }
        }
    }

    // ── Entity → Row mappers ──────────────────────────────────────────────────

    private fun EventTypeEntity.toRow() = EventTypeRow(id, name, description, category, colorArgb, iconName, createdAt, updatedAt)
    private fun EventFieldEntity.toRow() = EventFieldRow(id, eventTypeId, name, type, optionsJson, unit, isRequired, sortOrder)
    private fun EventEntryEntity.toRow() = EventEntryRow(id, eventTypeId, fieldValuesJson, note, createdAt, updatedAt)
    private fun ReminderEntity.toRow() = ReminderRow(id, eventTypeId, title, message, deliveryType, recurrenceType, recurrenceRuleJson, snoozeMinutes, nextTriggerAt, isActive)
    private fun ChartConfigEntity.toRow() = ChartConfigRow(id, eventTypeId, configJson, sortOrder, createdAt)

    private companion object {
        const val TAG = "ExportEngine"
        const val PERSIST_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}

// ── Auto-backup file management (top-level for unit testing) ──────────────────

internal const val AUTO_BACKUP_PREFIX = "lifelog_backup_"
internal const val AUTO_BACKUP_MAX_KEEP = 7

/**
 * A file in a backup location, seen through whatever [handle] that location
 * uses (a [File] in app storage, a document [Uri] in a SAF tree). The pure
 * selection logic below is shared by both backends.
 */
internal data class BackupCandidate<T>(
    val handle: T,
    val name: String,
    val modifiedAt: Long,
    val sizeBytes: Long = 0L
)

/**
 * The backups among [files] the restore flow can offer, newest first. Only
 * `lifelog_backup_*.db` names qualify: pre-1.0 builds could also write
 * `.json`/`.zip` auto-backups, which nothing can import anymore.
 */
internal fun <T> restorableBackups(files: List<BackupCandidate<T>>): List<BackupCandidate<T>> =
    files.filter { it.name.startsWith(AUTO_BACKUP_PREFIX) && it.name.endsWith(".db") }
        .sortedByDescending { it.modifiedAt }

/**
 * The backups to delete so that only the newest [maxKeep] remain. Deliberately
 * matches any extension so legacy `.json`/`.zip` backups age out too; files
 * without the backup prefix are never touched.
 */
internal fun <T> rotationVictims(files: List<BackupCandidate<T>>, maxKeep: Int): List<T> =
    files.filter { it.name.startsWith(AUTO_BACKUP_PREFIX) }
        .sortedByDescending { it.modifiedAt }
        .drop(maxKeep)
        .map { it.handle }

private fun File.backupCandidates(): List<BackupCandidate<File>> =
    listFiles { f -> f.isFile }
        ?.map { BackupCandidate(it, it.name, it.lastModified(), it.length()) }
        ?: emptyList()

/** The restorable auto-backup files in [dir], newest first. */
internal fun restorableAutoBackupsIn(dir: File): List<File> =
    restorableBackups(dir.backupCandidates()).map { it.handle }

/** Delete all but the newest [maxKeep] auto-backups in [dir]. */
internal fun rotateAutoBackups(dir: File, maxKeep: Int) {
    rotationVictims(dir.backupCandidates(), maxKeep).forEach { it.delete() }
}
