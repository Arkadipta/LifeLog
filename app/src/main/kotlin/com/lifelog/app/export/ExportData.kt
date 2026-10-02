package com.lifelog.app.export

import kotlinx.serialization.Serializable

const val EXPORT_SCHEMA_VERSION = 1

/**
 * Root container for a full LifeLog JSON export. Schema version is
 * incremented whenever the structure of the contained rows changes so
 * consumers of the file can tell formats apart. These exports are
 * write-only: LifeLog restores from SQLite backups, not JSON.
 */
@Serializable
data class ExportData(
    val schemaVersion: Int = EXPORT_SCHEMA_VERSION,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersionCode: Int = 0,
    val eventTypes: List<EventTypeRow> = emptyList(),
    val eventFields: List<EventFieldRow> = emptyList(),
    val eventEntries: List<EventEntryRow> = emptyList(),
    val reminders: List<ReminderRow> = emptyList(),
    val chartConfigs: List<ChartConfigRow> = emptyList()
)

@Serializable
data class EventTypeRow(
    val id: Long,
    val name: String,
    val description: String,
    val category: String,
    val colorArgb: Int,
    val iconName: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class EventFieldRow(
    val id: Long,
    val eventTypeId: Long,
    val name: String,
    val type: String,
    val optionsJson: String,
    val unit: String,
    val isRequired: Boolean,
    val sortOrder: Int
)

@Serializable
data class EventEntryRow(
    val id: Long,
    val eventTypeId: Long,
    val fieldValuesJson: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class ReminderRow(
    val id: Long,
    val eventTypeId: Long?,
    val title: String,
    val message: String,
    val deliveryType: String,
    val recurrenceType: String,
    val recurrenceRuleJson: String,
    val snoozeMinutes: Int,
    val nextTriggerAt: Long,
    val isActive: Boolean
)

@Serializable
data class ChartConfigRow(
    val id: String,
    val eventTypeId: Long,
    val configJson: String,
    val sortOrder: Int,
    val createdAt: Long
)
