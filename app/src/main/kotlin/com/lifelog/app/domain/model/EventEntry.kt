package com.lifelog.app.domain.model

data class EventEntry(
    val id: Long = 0,
    val eventTypeId: Long,
    val eventTypeName: String = "",
    val eventTypeColor: Int = EventType.DEFAULT_COLOR,
    val eventTypeIcon: String = "star",
    val fieldValues: Map<Long, FieldValue> = emptyMap(),
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
