package com.lifelog.app.csv

import android.content.Context
import android.net.Uri
import com.lifelog.app.data.repository.EventRepository
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.util.toIso8601
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val eventRepository: EventRepository
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    fun exportToCsv(uri: Uri, eventType: EventType, entries: List<EventEntry>) {
        context.contentResolver.openOutputStream(uri)?.use { os ->
            writeCsvStream(os, eventType, entries)
        }
    }

    fun writeCsvStream(outputStream: java.io.OutputStream, eventType: EventType, entries: List<EventEntry>) {
        val writer = OutputStreamWriter(outputStream)

        // Header row: id, created_at, updated_at, note, field1, field2, ...
        val headerCols = mutableListOf("id", "created_at", "updated_at", "note")
        eventType.fields.forEach { field ->
            headerCols.add(sanitizeCsvField(field.name))
        }
        writer.write(headerCols.joinToString(",") + "\n")

        // Data rows
        entries.forEach { entry ->
            val cols = mutableListOf(
                entry.id.toString(),
                entry.createdAt.toIso8601(),
                entry.updatedAt.toIso8601(),
                escapeCsv(entry.note)
            )
            eventType.fields.forEach { field ->
                val value = entry.fieldValues[field.id]?.displayString() ?: ""
                cols.add(escapeCsv(value))
            }
            writer.write(cols.joinToString(",") + "\n")
        }
        writer.flush()
        // Caller is responsible for closing outputStream
    }

    suspend fun importFromCsv(uri: Uri, eventType: EventType): Int {
        var count = 0
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            val headerLine = reader.readLine() ?: return 0
            val headers = parseRow(headerLine)

            val fieldsByName = eventType.fields.associateBy { it.name.lowercase() }

            val lines = reader.readLines()
            for (line in lines) {
                if (line.isBlank()) continue
                val cols = parseRow(line)
                val rowMap = headers.zip(cols).toMap()

                val createdAt = rowMap["created_at"]?.let { runCatching { dateFormat.parse(it)?.time }.getOrNull() }
                    ?: System.currentTimeMillis()
                val note = rowMap["note"] ?: ""

                val fieldValues = mutableMapOf<Long, FieldValue>()
                for (field in eventType.fields) {
                    val colName = field.name.lowercase()
                    val rawValue = rowMap[colName] ?: continue
                    if (rawValue.isBlank()) continue

                    val fv: FieldValue? = when (field.type) {
                        FieldType.NUMERIC -> rawValue.toDoubleOrNull()?.let { FieldValue.Numeric(it) }
                        FieldType.TEXT -> FieldValue.Text(rawValue)
                        FieldType.BOOLEAN -> when (rawValue.lowercase()) {
                            "yes", "true", "1" -> FieldValue.Bool(true)
                            else -> FieldValue.Bool(false)
                        }
                        FieldType.CHOICE -> FieldValue.Choice(rawValue)
                        FieldType.MULTI_SELECT -> FieldValue.MultiSelect(
                            rawValue.split(";").map { it.trim() }.filter { it.isNotBlank() }
                        )
                    }
                    if (fv != null) fieldValues[field.id] = fv
                }

                val entry = EventEntry(
                    eventTypeId = eventType.id,
                    fieldValues = fieldValues,
                    note = note,
                    createdAt = createdAt
                )
                eventRepository.saveEntry(entry)
                count++
            }
        }
        return count
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun sanitizeCsvField(value: String): String =
        value.replace(",", "_").replace("\"", "").lowercase()

    private fun parseRow(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}
