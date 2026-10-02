package com.lifelog.app.csv

import android.content.Context
import android.net.Uri
import com.lifelog.app.domain.csv.CsvWriter
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.io.OutputStreamWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * I/O shell around [CsvWriter]: per-event CSV export to a SAF uri (Event Detail
 * → Export CSV) or into an already-open stream (the ZIP export's per-event
 * files). There is no import here — the CSV import wizard ([CsvImportEngine])
 * creates a new event from any CSV, including the ones written here.
 */
@Singleton
class CsvManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun exportToCsv(uri: Uri, eventType: EventType, entries: List<EventEntry>): Unit =
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("Could not open the selected file for writing")
            stream.use { writeCsvStream(it, eventType, entries) }
        }

    /** Caller is responsible for closing [outputStream]. */
    fun writeCsvStream(outputStream: OutputStream, eventType: EventType, entries: List<EventEntry>) {
        val writer = OutputStreamWriter(outputStream, Charsets.UTF_8)
        CsvWriter.writeEntries(writer, eventType, entries)
        writer.flush()
    }
}
