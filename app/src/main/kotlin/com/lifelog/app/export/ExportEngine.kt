package com.lifelog.app.export

import android.content.Context
import android.net.Uri
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.BufferedOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

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
    private val csvManager: CsvManager
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

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

    // ── Auto-backup to internal storage (no SAF needed) ───────────────────────

    /** An on-device auto-backup, restorable via Settings → Restore from Auto-Backup. */
    data class AutoBackup(val file: File, val modifiedAt: Long, val sizeBytes: Long)

    /**
     * Write a new auto-backup. Always a SQLite copy: it is the only format the
     * in-app restore pipeline accepts, and these files live in private storage
     * where nothing else could consume them.
     */
    suspend fun createAutoBackup(): File = withContext(Dispatchers.IO) {
        val backupDir = autoBackupDir().also { it.mkdirs() }
        val ts = fileTimestampFormat.format(Date())
        val file = File(backupDir, "$AUTO_BACKUP_PREFIX$ts.db")

        try {
            checkpointWal()
            context.getDatabasePath(LifeLogDatabase.DATABASE_NAME).copyTo(file)
        } catch (e: Exception) {
            file.delete() // never leave a partial backup behind, or rotate good ones for it
            throw e
        }
        rotateAutoBackups(backupDir, maxKeep = AUTO_BACKUP_MAX_KEEP)
        file
    }

    suspend fun listAutoBackups(): List<AutoBackup> = withContext(Dispatchers.IO) {
        restorableAutoBackupsIn(autoBackupDir())
            .map { AutoBackup(it, it.lastModified(), it.length()) }
    }

    private fun autoBackupDir() = File(context.filesDir, "backups")

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

    private fun checkpointWal() {
        runCatching { db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(TRUNCATE)") }
    }

    // ── Entity → Row mappers ──────────────────────────────────────────────────

    private fun EventTypeEntity.toRow() = EventTypeRow(id, name, description, category, colorArgb, iconName, createdAt, updatedAt)
    private fun EventFieldEntity.toRow() = EventFieldRow(id, eventTypeId, name, type, optionsJson, unit, isRequired, sortOrder)
    private fun EventEntryEntity.toRow() = EventEntryRow(id, eventTypeId, fieldValuesJson, note, createdAt, updatedAt)
    private fun ReminderEntity.toRow() = ReminderRow(id, eventTypeId, title, message, deliveryType, recurrenceType, recurrenceRuleJson, snoozeMinutes, nextTriggerAt, isActive)
    private fun ChartConfigEntity.toRow() = ChartConfigRow(id, eventTypeId, configJson, sortOrder, createdAt)
}

// ── Auto-backup file management (top-level for unit testing) ──────────────────

internal const val AUTO_BACKUP_PREFIX = "lifelog_backup_"
internal const val AUTO_BACKUP_MAX_KEEP = 7

/**
 * The auto-backup files in [dir] the restore flow can offer, newest first.
 * Only `.db` files qualify: pre-1.0 builds could also write `.json`/`.zip`
 * auto-backups, which nothing can import anymore.
 */
internal fun restorableAutoBackupsIn(dir: File): List<File> =
    dir.listFiles { f -> f.isFile && f.name.startsWith(AUTO_BACKUP_PREFIX) && f.name.endsWith(".db") }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

/**
 * Delete all but the newest [maxKeep] auto-backups in [dir]. Deliberately
 * matches any extension so legacy `.json`/`.zip` backups age out too.
 */
internal fun rotateAutoBackups(dir: File, maxKeep: Int) {
    dir.listFiles { f -> f.isFile && f.name.startsWith(AUTO_BACKUP_PREFIX) }
        ?.sortedByDescending { it.lastModified() }
        ?.drop(maxKeep)
        ?.forEach { it.delete() }
}
