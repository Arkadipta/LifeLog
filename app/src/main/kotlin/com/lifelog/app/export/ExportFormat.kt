package com.lifelog.app.export

/** Formats offered by the manual "Export Now" flow. Auto-backups are always SQLite. */
enum class ExportFormat(val displayName: String) {
    SQLITE("SQLite Database"),
    ZIP_CSV("ZIP Archive (CSV per event)"),
    JSON("JSON")
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
