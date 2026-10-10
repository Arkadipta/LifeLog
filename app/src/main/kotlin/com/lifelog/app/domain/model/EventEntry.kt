package com.lifelog.app.domain.model

/**
 * A logged entry with its field values already decoded. This is the form used
 * by everything that reads values in bulk — charts, value search, CSV export,
 * the entry editor. Lists that only draw cards should take the lighter
 * [EntryRow] instead, which this satisfies.
 */
data class EventEntry(
    override val id: Long = 0,
    override val eventTypeId: Long,
    override val eventTypeName: String = "",
    override val eventTypeCategory: String = "",
    override val eventTypeColor: Int = EventType.DEFAULT_COLOR,
    override val eventTypeIcon: String = "star",
    override val fieldValues: Map<Long, FieldValue> = emptyMap(),
    override val note: String = "",
    override val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : EntryRow
