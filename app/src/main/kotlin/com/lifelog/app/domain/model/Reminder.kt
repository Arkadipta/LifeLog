package com.lifelog.app.domain.model

data class Reminder(
    val id: Long = 0,
    val eventTypeId: Long? = null,
    val eventTypeName: String? = null,
    val title: String,
    val message: String = "",
    val deliveryType: DeliveryType = DeliveryType.NOTIFICATION,
    val recurrenceRule: RecurrenceRule = RecurrenceRule(),
    val nextTriggerAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)
