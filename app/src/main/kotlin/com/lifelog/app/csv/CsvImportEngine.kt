package com.lifelog.app.csv

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.room.withTransaction
import com.lifelog.app.data.db.LifeLogDatabase
import com.lifelog.app.data.db.dao.EventEntryDao
import com.lifelog.app.data.db.dao.EventFieldDao
import com.lifelog.app.data.db.dao.EventTypeDao
import com.lifelog.app.data.db.toEntity
import com.lifelog.app.domain.csv.CellConversion
import com.lifelog.app.domain.csv.CsvDateTimeParser
import com.lifelog.app.domain.csv.CsvFieldInference
import com.lifelog.app.domain.csv.CsvParser
import com.lifelog.app.domain.csv.ParsedCsv
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates a brand-new event from a CSV file and imports its rows as entries.
 *
 * Reading/parsing ([readFile]) is separate from writing ([import]) so the UI can
 * preview and let the user configure the event, pick the timestamp column, and
 * review field types before anything is persisted. The write is wrapped in a
 * single Room transaction: either the whole event (type + fields + entries)
 * lands, or nothing does.
 *
 * Pure parsing/inference lives in `domain.csv`; this class only owns I/O and the
 * database side-effects.
 */
@Singleton
class CsvImportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: LifeLogDatabase,
    private val eventTypeDao: EventTypeDao,
    private val eventFieldDao: EventFieldDao,
    private val eventEntryDao: EventEntryDao
) {

    /** A column mapped to a field in the new event. */
    data class FieldSpec(
        val columnIndex: Int,
        val name: String,
        val type: FieldType,
        val options: List<String>
    )

    /** Everything needed to materialise the import once the user confirms. */
    data class ImportRequest(
        val event: EventType,            // metadata only; any [EventType.fields] are ignored
        val fields: List<FieldSpec>,     // ordered; one per non-timestamp column to keep
        val timestampColumnIndex: Int,
        val rows: List<List<String>>
    )

    /** Outcome surfaced to the completion screen. */
    data class ImportSummary(
        val eventId: Long,
        val eventName: String,
        val rowsImported: Int,
        val fieldsCreated: Int,
        val warnings: Int,
        val failedRows: Int,
        val warningDetails: List<String>,
        val failedDetails: List<String>
    )

    sealed interface ReadResult {
        data class Success(val parsed: ParsedCsv, val suggestedName: String) : ReadResult
        data class Failure(val message: String) : ReadResult
    }

    /** Read [uri] as UTF-8 text and parse it, validating that ≥1 data row exists. */
    suspend fun readFile(uri: Uri): ReadResult = withContext(Dispatchers.IO) {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: return@withContext ReadResult.Failure(
            "Could not open the file. It may have been moved, or the app may not have permission to read it."
        )

        val parsed = runCatching { CsvParser.parse(text) }.getOrElse {
            return@withContext ReadResult.Failure(
                "This file couldn't be read as CSV. Make sure it's a comma-separated file with a header row."
            )
        }

        when {
            parsed.columnCount == 0 -> ReadResult.Failure(
                "The file appears to be empty or is missing a header row."
            )
            parsed.dataRowCount == 0 -> ReadResult.Failure(
                "The file has a header row but no data rows to import."
            )
            else -> ReadResult.Success(parsed, suggestedNameFor(uri))
        }
    }

    /**
     * Create the event and import [ImportRequest.rows] in one transaction.
     *
     * Rows whose timestamp can't be parsed are skipped and counted as failed;
     * cells that don't fit their field type are kept as text and counted as
     * warnings. [onProgress] is invoked as `(processed, total)` for large files.
     */
    suspend fun import(
        request: ImportRequest,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }
    ): ImportSummary = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        var eventId = 0L
        var rowsImported = 0
        var warnings = 0
        var failedRows = 0
        val warningDetails = mutableListOf<String>()
        val failedDetails = mutableListOf<String>()
        val total = request.rows.size

        db.withTransaction {
            eventId = eventTypeDao.insert(
                request.event.copy(id = 0, createdAt = now, updatedAt = now).toEntity()
            )

            val fieldIds = eventFieldDao.insertAll(
                request.fields.mapIndexed { index, spec ->
                    EventField(
                        eventTypeId = eventId,
                        name = spec.name,
                        type = spec.type,
                        options = spec.options,
                        sortOrder = index
                    ).toEntity()
                }
            )

            request.rows.forEachIndexed { index, row ->
                val rowNumber = index + 1
                val timestamp = CsvDateTimeParser.parse(row.getOrElse(request.timestampColumnIndex) { "" })
                if (timestamp == null) {
                    failedRows++
                    if (failedDetails.size < MAX_DETAILS) {
                        failedDetails += "Row $rowNumber: couldn't read date/time " +
                            quote(row.getOrElse(request.timestampColumnIndex) { "" })
                    }
                } else {
                    val values = HashMap<Long, FieldValue>()
                    request.fields.forEachIndexed { fieldIndex, spec ->
                        val cell = row.getOrElse(spec.columnIndex) { "" }
                        when (val conversion = CsvFieldInference.convertCell(cell, spec.type)) {
                            is CellConversion.Converted ->
                                values[fieldIds[fieldIndex]] = conversion.value
                            is CellConversion.FellBackToText -> {
                                values[fieldIds[fieldIndex]] = conversion.value
                                warnings++
                                if (warningDetails.size < MAX_DETAILS) {
                                    warningDetails += "Row $rowNumber, ${spec.name}: ${quote(cell)} kept as text"
                                }
                            }
                            CellConversion.Skipped -> Unit
                        }
                    }
                    eventEntryDao.insert(
                        EventEntry(
                            eventTypeId = eventId,
                            fieldValues = values,
                            note = "",
                            // Preserve the source timestamp as both created/updated so
                            // chronological ordering (by createdAt) reflects the data.
                            createdAt = timestamp,
                            updatedAt = timestamp
                        ).toEntity()
                    )
                    rowsImported++
                }
                onProgress(index + 1, total)
            }
        }

        ImportSummary(
            eventId = eventId,
            eventName = request.event.name,
            rowsImported = rowsImported,
            fieldsCreated = request.fields.size,
            warnings = warnings,
            failedRows = failedRows,
            warningDetails = warningDetails,
            failedDetails = failedDetails
        )
    }

    private fun suggestedNameFor(uri: Uri): String {
        val raw = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull() ?: uri.lastPathSegment

        return raw
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.replace('_', ' ')
            ?.replace('-', ' ')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Imported Event"
    }

    private fun quote(value: String): String {
        val trimmed = value.trim()
        val shown = if (trimmed.length > 40) trimmed.take(40) + "…" else trimmed
        return "\"$shown\""
    }

    private companion object {
        /** Cap stored example messages so a hugely broken file can't blow up memory. */
        const val MAX_DETAILS = 50
    }
}
