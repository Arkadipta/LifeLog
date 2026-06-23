package com.lifelog.app.data.db

import com.lifelog.app.data.db.entity.ChartConfigEntity
import com.lifelog.app.data.db.entity.EventEntryEntity
import com.lifelog.app.data.db.entity.EventFieldEntity
import com.lifelog.app.data.db.entity.EventTypeEntity
import com.lifelog.app.data.db.entity.ReminderEntity
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.DeliveryType
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.domain.model.FieldType
import com.lifelog.app.domain.model.FieldValue
import com.lifelog.app.domain.model.RecurrenceRule
import com.lifelog.app.domain.model.Reminder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val appJson = Json { ignoreUnknownKeys = true; isLenient = true }

fun EventTypeEntity.toDomain(
    fields: List<EventField> = emptyList(),
    entryCount: Int = 0,
    lastEntryAt: Long? = null
) = EventType(
    id = id,
    name = name,
    description = description,
    category = category,
    colorArgb = colorArgb,
    iconName = iconName,
    fields = fields,
    createdAt = createdAt,
    updatedAt = updatedAt,
    entryCount = entryCount,
    lastEntryAt = lastEntryAt
)

fun EventType.toEntity() = EventTypeEntity(
    id = id,
    name = name,
    description = description,
    category = category,
    colorArgb = colorArgb,
    iconName = iconName,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun EventFieldEntity.toDomain() = EventField(
    id = id,
    eventTypeId = eventTypeId,
    name = name,
    type = FieldType.valueOf(type),
    options = appJson.decodeFromString<List<String>>(optionsJson),
    unit = unit,
    isRequired = isRequired,
    sortOrder = sortOrder
)

fun EventField.toEntity() = EventFieldEntity(
    id = id,
    eventTypeId = eventTypeId,
    name = name,
    type = type.name,
    optionsJson = appJson.encodeToString<List<String>>(options),
    unit = unit,
    isRequired = isRequired,
    sortOrder = sortOrder
)

fun EventEntryEntity.toDomain(
    eventTypeName: String = "",
    eventTypeCategory: String = "",
    eventTypeColor: Int = EventType.DEFAULT_COLOR,
    eventTypeIcon: String = "star"
): EventEntry {
    val rawValues: Map<String, FieldValue> = try {
        appJson.decodeFromString(fieldValuesJson)
    } catch (e: Exception) {
        emptyMap()
    }
    return EventEntry(
        id = id,
        eventTypeId = eventTypeId,
        eventTypeName = eventTypeName,
        eventTypeCategory = eventTypeCategory,
        eventTypeColor = eventTypeColor,
        eventTypeIcon = eventTypeIcon,
        fieldValues = rawValues.mapKeys { it.key.toLongOrNull() ?: 0L },
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun EventEntry.toEntity() = EventEntryEntity(
    id = id,
    eventTypeId = eventTypeId,
    fieldValuesJson = appJson.encodeToString<Map<String, FieldValue>>(
        fieldValues.mapKeys { it.key.toString() }
    ),
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ReminderEntity.toDomain(eventTypeName: String? = null): Reminder {
    val rule = resolveRecurrenceRule()
    return Reminder(
        id = id,
        eventTypeId = eventTypeId,
        eventTypeName = eventTypeName,
        title = title,
        message = message,
        deliveryType = runCatching { DeliveryType.valueOf(deliveryType) }.getOrDefault(DeliveryType.NOTIFICATION),
        recurrenceRule = rule,
        snoozeMinutes = snoozeMinutes,
        nextTriggerAt = nextTriggerAt,
        isActive = isActive
    )
}

private fun ReminderEntity.resolveRecurrenceRule(): RecurrenceRule =
    runCatching { appJson.decodeFromString<RecurrenceRule>(recurrenceRuleJson) }
        .getOrNull() ?: RecurrenceRule()

fun Reminder.toEntity() = ReminderEntity(
    id = id,
    eventTypeId = eventTypeId,
    title = title,
    message = message,
    deliveryType = deliveryType.name,
    recurrenceType = recurrenceRule.type.name,
    recurrenceRuleJson = appJson.encodeToString(recurrenceRule),
    snoozeMinutes = snoozeMinutes,
    nextTriggerAt = nextTriggerAt,
    isActive = isActive
)

fun ChartConfigEntity.toDomain(): ChartConfig =
    appJson.decodeFromString<ChartConfig>(configJson).copy(
        id = id,
        eventTypeId = eventTypeId,
        sortOrder = sortOrder,
        createdAt = createdAt
    )

fun ChartConfig.toEntity() = ChartConfigEntity(
    id = id,
    eventTypeId = eventTypeId,
    configJson = appJson.encodeToString(ChartConfig.serializer(), this),
    sortOrder = sortOrder,
    createdAt = createdAt
)
