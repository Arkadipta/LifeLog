package com.lifelog.app.export

enum class ExportFormat(
    val mimeType: String,
    val extension: String,
    val displayName: String
) {
    SQLITE("application/octet-stream", "db", "SQLite Database"),
    ZIP_CSV("application/zip", "zip", "ZIP Archive (CSV per event)"),
    JSON("application/json", "json", "JSON")
}

enum class BackupFrequency(
    val displayName: String,
    val intervalHours: Long
) {
    OFF("Off", 0L),
    DAILY("Daily", 24L),
    WEEKLY("Weekly", 24L * 7),
    MONTHLY("Monthly", 24L * 30)
}
