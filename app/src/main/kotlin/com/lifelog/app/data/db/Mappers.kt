package com.lifelog.app.data.db

import com.lifelog.app.data.db.entity.ChartConfigEntity
import com.lifelog.app.data.db.entity.EventEntryEntity
import com.lifelog.app.data.db.entity.EventFieldEntity
import com.lifelog.app.data.db.entity.EventTypeEntity
import com.lifelog.app.data.db.entity.ReminderEntity
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.DeliveryType
import com.lifelog.app.domain.model.EntryRow
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

/**
 * Decodes a `fieldValuesJson` column. Values are salvaged pair-by-pair: a
 * corrupt value (e.g. a subtype from a newer app version) or an unparseable
 * field-id key costs only that pair, never the whole entry — note and timestamps
 * always survive. Unparseable keys are dropped outright; collapsing them onto a
 * shared bogus id would make the survivors overwrite each other.
 *
 * The one decoder for both entry shapes ([toDomain] eagerly, [toRow] on first
 * read), so a lazily-drawn card can never salvage differently from a chart.
 */
internal fun decodeFieldValues(fieldValuesJson: String): Map<Long, FieldValue> {
    val rawValues: Map<String, JsonElement> =
        runCatching { appJson.decodeFromString<Map<String, JsonElement>>(fieldValuesJson) }
            .getOrDefault(emptyMap())
    return buildMap {
        for ((key, element) in rawValues) {
            val fieldId = key.toLongOrNull() ?: continue
            runCatching { appJson.decodeFromJsonElement<FieldValue>(element) }
                .onSuccess { put(fieldId, it) }
        }
    }
}

fun EventEntryEntity.toDomain(
    eventTypeName: String = "",
    eventTypeCategory: String = "",
    eventTypeColor: Int = EventType.DEFAULT_COLOR,
    eventTypeIcon: String = "star"
): EventEntry = EventEntry(
    id = id,
    eventTypeId = eventTypeId,
    eventTypeName = eventTypeName,
    eventTypeCategory = eventTypeCategory,
    eventTypeColor = eventTypeColor,
    eventTypeIcon = eventTypeIcon,
    fieldValues = decodeFieldValues(fieldValuesJson),
    note = note,
    createdAt = createdAt,
    updatedAt = updatedAt
)

/**
 * Resolves the event-type columns an [EventEntry] carries denormalized for
 * display, straight from the type row.
 *
 * Every read path in [com.lifelog.app.data.repository.EventRepository] that
 * joins entries to their type needs the same four values and the same fallbacks
 * when the row is missing. Routing them through this overload states those
 * fallbacks once — in the default arguments above — instead of re-deriving
 * `?: ""` / `?: DEFAULT_COLOR` / `?: "star"` at each call site, where they could
 * drift apart or from the defaults they are meant to mirror.
 */
fun EventEntryEntity.toDomain(type: EventTypeEntity?): EventEntry =
    if (type == null) toDomain() else toDomain(
        eventTypeName = type.name,
        eventTypeCategory = type.category,
        eventTypeColor = type.colorArgb,
        eventTypeIcon = type.iconName
    )

/**
 * The identity an entry falls back to when its event type is gone, read out of
 * [toDomain]'s own default arguments — which is where those fallbacks are stated
 * (A4) — rather than restated here, so [toRow] cannot drift from [toDomain].
 * Only the four type columns are meaningful; the rest of this instance is unused.
 */
private val NO_TYPE = EventEntryEntity(eventTypeId = 0).toDomain()

/**
 * An [EntryRow] that still holds its values as the stored JSON and decodes them
 * the first time a card asks — see [EntryRow] for why lists want this. Equality
 * is by the stored column, so two rows built from the same database row compare
 * equal whether or not either has been decoded.
 */
private class LazyEntryRow(
    override val id: Long,
    override val eventTypeId: Long,
    override val eventTypeName: String,
    override val eventTypeCategory: String,
    override val eventTypeColor: Int,
    override val eventTypeIcon: String,
    override val note: String,
    override val createdAt: Long,
    private val fieldValuesJson: String
) : EntryRow {
    private val values = lazy { decodeFieldValues(fieldValuesJson) }

    override val fieldValues: Map<Long, FieldValue> get() = values.value

    /** Whether [fieldValues] has been read yet — the property the decode-laziness
     *  tests assert on, since laziness is otherwise invisible from outside. */
    internal fun isDecoded(): Boolean = values.isInitialized()

    override fun equals(other: Any?): Boolean = other is LazyEntryRow &&
        id == other.id &&
        eventTypeId == other.eventTypeId &&
        eventTypeName == other.eventTypeName &&
        eventTypeCategory == other.eventTypeCategory &&
        eventTypeColor == other.eventTypeColor &&
        eventTypeIcon == other.eventTypeIcon &&
        note == other.note &&
        createdAt == other.createdAt &&
        fieldValuesJson == other.fieldValuesJson

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + eventTypeId.hashCode()
        result = 31 * result + eventTypeName.hashCode()
        result = 31 * result + eventTypeCategory.hashCode()
        result = 31 * result + eventTypeColor
        result = 31 * result + eventTypeIcon.hashCode()
        result = 31 * result + note.hashCode()
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + fieldValuesJson.hashCode()
        return result
    }
}

/**
 * Maps an entry to a list row **without** decoding its values, resolving the
 * denormalized type columns exactly as [toDomain] does. Cheap enough to run over
 * a whole table on every database change; use it for lists, and [toDomain] when
 * the caller actually needs every value.
 */
fun EventEntryEntity.toRow(type: EventTypeEntity?): EntryRow = LazyEntryRow(
    id = id,
    eventTypeId = eventTypeId,
    eventTypeName = type?.name ?: NO_TYPE.eventTypeName,
    eventTypeCategory = type?.category ?: NO_TYPE.eventTypeCategory,
    eventTypeColor = type?.colorArgb ?: NO_TYPE.eventTypeColor,
    eventTypeIcon = type?.iconName ?: NO_TYPE.eventTypeIcon,
    note = note,
    createdAt = createdAt,
    fieldValuesJson = fieldValuesJson
)

/** Test hook: whether this row's values have been decoded yet. An already-decoded
 *  shape ([EventEntry]) reports true, so a test can assert over a mixed list. */
internal fun EntryRow.isDecodedForTest(): Boolean =
    (this as? LazyEntryRow)?.isDecoded() ?: true

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
