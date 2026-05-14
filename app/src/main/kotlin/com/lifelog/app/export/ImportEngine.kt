package com.lifelog.app.export

import android.content.Context
import android.net.Uri
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventTypeDao: EventTypeDao,
    private val eventFieldDao: EventFieldDao,
    private val eventEntryDao: EventEntryDao,
    private val reminderDao: ReminderDao,
    private val chartConfigDao: ChartConfigDao
) {
    data class ImportResult(
        val eventTypes: Int = 0,
        val eventFields: Int = 0,
        val eventEntries: Int = 0,
        val reminders: Int = 0,
        val chartConfigs: Int = 0,
        val skippedSchemaVersion: Int? = null,
        val errors: List<String> = emptyList()
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Restore all data from a JSON export stream. Uses INSERT OR REPLACE so
     * existing rows with the same ID are overwritten (idempotent restore).
     * Caller is responsible for clearing existing data beforehand if a clean
     * restore is desired.
     */
    suspend fun importFromJson(inputStream: InputStream): ImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val raw = inputStream.readBytes().toString(Charsets.UTF_8)

        val data = runCatching { json.decodeFromString<ExportData>(raw) }.getOrElse { e ->
            return@withContext ImportResult(errors = listOf("Failed to parse JSON: ${e.message}"))
        }

        if (data.schemaVersion > EXPORT_SCHEMA_VERSION) {
            return@withContext ImportResult(
                skippedSchemaVersion = data.schemaVersion,
                errors = listOf("Export schema v${data.schemaVersion} is newer than supported v$EXPORT_SCHEMA_VERSION. Please update the app.")
            )
        }

        var types = 0; var fields = 0; var entries = 0; var reminders = 0; var charts = 0

        for (row in data.eventTypes) {
            runCatching {
                eventTypeDao.insert(row.toEntity())
                types++
            }.onFailure { errors += "EventType ${row.id}: ${it.message}" }
        }
        for (row in data.eventFields) {
            runCatching {
                eventFieldDao.insert(row.toEntity())
                fields++
            }.onFailure { errors += "EventField ${row.id}: ${it.message}" }
        }
        for (row in data.eventEntries) {
            runCatching {
                eventEntryDao.insert(row.toEntity())
                entries++
            }.onFailure { errors += "EventEntry ${row.id}: ${it.message}" }
        }
        for (row in data.reminders) {
            runCatching {
                reminderDao.insert(row.toEntity())
                reminders++
            }.onFailure { errors += "Reminder ${row.id}: ${it.message}" }
        }
        for (row in data.chartConfigs) {
            runCatching {
                chartConfigDao.upsert(row.toEntity())
                charts++
            }.onFailure { errors += "ChartConfig ${row.id}: ${it.message}" }
        }

        ImportResult(types, fields, entries, reminders, charts, errors = errors)
    }

    /**
     * Stage a SQLite database file for restore. The file is copied to a
     * staging path; the caller must restart the app to apply it (Room cannot
     * hot-swap its database). Returns true if staging succeeded.
     *
     * Full restore UI is deferred — this provides the groundwork.
     */
    suspend fun stageSqliteRestore(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val staging = File(context.filesDir, "restore_staged.db")
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                staging.outputStream().use { input.copyTo(it) }
            }
            true
        }.getOrDefault(false)
    }

    fun hasStagedRestore(): Boolean =
        File(context.filesDir, "restore_staged.db").exists()

    // ── Row → Entity mappers ──────────────────────────────────────────────────

    private fun EventTypeRow.toEntity() = EventTypeEntity(id, name, description, category, colorArgb, iconName, createdAt, updatedAt)
    private fun EventFieldRow.toEntity() = EventFieldEntity(id, eventTypeId, name, type, optionsJson, unit, isRequired, sortOrder)
    private fun EventEntryRow.toEntity() = EventEntryEntity(id, eventTypeId, fieldValuesJson, note, createdAt, updatedAt)
    private fun ReminderRow.toEntity() = ReminderEntity(
        id = id, eventTypeId = eventTypeId, title = title, message = message,
        deliveryType = deliveryType, recurrenceType = recurrenceType,
        recurrenceRuleJson = recurrenceRuleJson, nextTriggerAt = nextTriggerAt, isActive = isActive
    )
    private fun ChartConfigRow.toEntity() = ChartConfigEntity(id, eventTypeId, configJson, sortOrder, createdAt)
}
