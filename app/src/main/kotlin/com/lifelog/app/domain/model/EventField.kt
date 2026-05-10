package com.lifelog.app.domain.model

data class EventField(
    val id: Long = 0,
    val eventTypeId: Long = 0,
    val name: String,
    val type: FieldType,
    val options: List<String> = emptyList(),
    val unit: String = "",
    val isRequired: Boolean = false,
    val sortOrder: Int = 0
)
