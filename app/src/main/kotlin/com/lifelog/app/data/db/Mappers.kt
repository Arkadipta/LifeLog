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
import com.lifelog.app.domain.model.StoredChartConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

val appJson = Json { ignoreUnknownKeys = true; isLenient = true }

/*
 * Read-side policy: mapping an entity out of the database never throws.
 * Columns holding serialized state (enum names, JSON) can arrive corrupt — a
 * damaged or hand-edited backup, or a restore from a newer app version — and
 * one bad row must degrade only itself, visibly where possible, instead of
 * crash-looping every screen that reads the table. Write-side mappers stay
 * strict: they only serialize values the domain layer just produced.
 */

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
    // TEXT keeps an unreadable field alive: dropping the row instead would erase
    // the definition for good on the next event-type save (field sets are
    // replaced wholesale), while TEXT still renders any stored value — non-text
    // ones surface through the legacy-mismatch chip rather than silently.
    type = runCatching { FieldType.valueOf(type) }.getOrDefault(FieldType.TEXT),
    options = runCatching { appJson.decodeFromString<List<String>>(optionsJson) }
        .getOrDefault(emptyList()),
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
    // Values are salvaged pair-by-pair: a corrupt value (e.g. a subtype from a
    // newer app version) or an unparseable field-id key costs only that pair,
    // never the whole entry — note and timestamps always survive. Unparseable
    // keys are dropped outright; collapsing them onto a shared bogus id would
    // make the survivors overwrite each other.
    val rawValues: Map<String, JsonElement> =
        runCatching { appJson.decodeFromString<Map<String, JsonElement>>(fieldValuesJson) }
            .getOrDefault(emptyMap())
    val fieldValues = buildMap {
        for ((key, element) in rawValues) {
            val fieldId = key.toLongOrNull() ?: continue
            runCatching { appJson.decodeFromJsonElement<FieldValue>(element) }
                .onSuccess { put(fieldId, it) }
        }
    }
    return EventEntry(
        id = id,
        eventTypeId = eventTypeId,
        eventTypeName = eventTypeName,
        eventTypeCategory = eventTypeCategory,
        eventTypeColor = eventTypeColor,
        eventTypeIcon = eventTypeIcon,
        fieldValues = fieldValues,
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

fun ChartConfigEntity.toDomain(): StoredChartConfig =
    runCatching { appJson.decodeFromString<ChartConfig>(configJson) }
        .map { decoded ->
            // DB columns override their copies inside the JSON — they are what
            // Room filters and sorts on, so they are the authoritative values.
            StoredChartConfig.Readable(
                decoded.copy(id = id, eventTypeId = eventTypeId, sortOrder = sortOrder, createdAt = createdAt)
            )
        }
        .getOrElse { StoredChartConfig.Unreadable(id) }

fun ChartConfig.toEntity() = ChartConfigEntity(
    id = id,
    eventTypeId = eventTypeId,
    configJson = appJson.encodeToString(ChartConfig.serializer(), this),
    sortOrder = sortOrder,
    createdAt = createdAt
)
