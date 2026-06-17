package com.lifelog.app.domain.model

data class EventType(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val category: String = "",
    val colorArgb: Int = DEFAULT_COLOR,
    val iconName: String = "star",
    val fields: List<EventField> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val entryCount: Int = 0
) {
    companion object {
        // Soft Blue — the first swatch in EventColors. Keep these in sync.
        const val DEFAULT_COLOR = 0xFF7FB0F2.toInt()
    }
}
