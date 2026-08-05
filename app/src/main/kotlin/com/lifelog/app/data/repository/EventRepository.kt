package com.lifelog.app.data.repository

import androidx.room.withTransaction
import com.lifelog.app.data.db.LifeLogDatabase
import com.lifelog.app.data.db.toDomain
import com.lifelog.app.data.db.toEntity
import com.lifelog.app.data.db.dao.EventEntryDao
import com.lifelog.app.data.db.dao.EventFieldDao
import com.lifelog.app.data.db.dao.EventTypeDao
import com.lifelog.app.data.db.entity.EventFieldEntity
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventField
import com.lifelog.app.domain.model.EventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val db: LifeLogDatabase,
    private val eventTypeDao: EventTypeDao,
    private val eventFieldDao: EventFieldDao,
    private val eventEntryDao: EventEntryDao
) {

    /**
     * The event list, live. Four whole-table reads combined and joined in memory —
     * deliberately not one field query per event type inside the transform, which
     * is what this used to do: that ran N queries on every emission of any of the
     * other flows, on a path six screens/widgets subscribe to (twice each on the
     * Events and Timeline screens).
     *
     * Observing `event_fields` rather than reading it one-shot also fixes what the
     * query count hid: with only the three flows below, an edit that touched just
     * a field definition — [addFieldOption] adding a choice mid-entry — changed no
     * observed table, so the list kept serving stale fields until an unrelated
     * write woke it.
     */
    fun observeAllEventTypes(): Flow<List<EventType>> =
        combine(
            eventTypeDao.observeAll(),
            // Observed (not a one-shot getEntryCount) so adding/importing/deleting an
            // entry re-emits the list with a fresh count without an event-type edit.
            eventTypeDao.observeEntryCounts(),
            // Drives "Recent activity" sorting; re-emits on any entry change too.
            eventTypeDao.observeLatestEntryTimes(),
            eventFieldDao.observeAll()
        ) { entities, counts, latestTimes, fields ->
            val countByType = counts.associate { it.eventTypeId to it.count }
            val latestByType = latestTimes.associate { it.eventTypeId to it.latestAt }
            val fieldsByType = fields.groupByEventType()
            entities.map { entity ->
                entity.toDomain(
                    fields = fieldsByType[entity.id].orEmpty(),
                    entryCount = countByType[entity.id] ?: 0,
                    lastEntryAt = latestByType[entity.id]
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeEventType(id: Long): Flow<EventType?> =
        eventTypeDao.observeById(id).flatMapLatest { entity ->
            eventFieldDao.observeByEventType(id).map { fieldEntities ->
                entity?.toDomain(fieldEntities.map { it.toDomain() })
            }
        }

    suspend fun getEventType(id: Long): EventType? {
        val entity = eventTypeDao.getById(id) ?: return null
        val fields = eventFieldDao.getByEventType(id).map { it.toDomain() }
        val count = eventTypeDao.getEntryCount(id)
        return entity.toDomain(fields, count)
    }

    /**
     * Persists an event type and its fields atomically. The field set is
     * replaced (delete-all then re-insert), so the whole operation runs inside a
     * single Room transaction — an interrupted save can never leave the event
     * with a partially-updated or missing set of field definitions.
     */
    suspend fun saveEventType(eventType: EventType): Long = db.withTransaction {
        val id = if (eventType.id == 0L) {
            eventTypeDao.insert(eventType.toEntity())
        } else {
            eventTypeDao.update(eventType.copy(updatedAt = System.currentTimeMillis()).toEntity())
            eventType.id
        }
        eventFieldDao.deleteAllForEventType(id)
        eventFieldDao.insertAll(
            eventType.fields.mapIndexed { index, field ->
                field.copy(eventTypeId = id, sortOrder = index).toEntity()
            }
        )
        id
    }

    suspend fun deleteEventType(id: Long) {
        eventTypeDao.deleteById(id)
    }

    /**
     * Appends [option] to a Choice/MultiSelect field's stored definition — a
     * targeted single-row update rather than [saveEventType]'s wholesale field
     * replacement, so an option added mid-entry can't race a concurrent edit of
     * the other fields. Works from a fresh read of the row (not a caller-held
     * copy) and returns the field as now persisted, or null when the field no
     * longer exists. An already-present option is left as-is.
     */
    suspend fun addFieldOption(fieldId: Long, option: String): EventField? {
        val stored = eventFieldDao.getById(fieldId)?.toDomain() ?: return null
        if (option in stored.options) return stored
        val updated = stored.copy(options = stored.options + option)
        eventFieldDao.update(updated.toEntity())
        return updated
    }

    fun observeEntriesForEventType(eventTypeId: Long): Flow<List<EventEntry>> {
        val entityFlow = eventTypeDao.observeById(eventTypeId)
        val entriesFlow = eventEntryDao.observeByEventType(eventTypeId)
        return combine(entityFlow, entriesFlow) { entity, entries ->
            entries.map { it.toDomain(entity) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllEntries(): Flow<List<EventEntry>> =
        eventEntryDao.observeAll().flatMapLatest { entries ->
            eventTypeDao.observeAll().map { types ->
                val typeMap = types.associateBy { it.id }
                entries.map { it.toDomain(typeMap[it.eventTypeId]) }
            }
        }

    suspend fun getEntry(id: Long): EventEntry? {
        val entity = eventEntryDao.getById(id) ?: return null
        return entity.toDomain(eventTypeDao.getById(entity.eventTypeId))
    }

    suspend fun saveEntry(entry: EventEntry): Long {
        return if (entry.id == 0L) {
            eventEntryDao.insert(entry.toEntity())
        } else {
            eventEntryDao.update(entry.copy(updatedAt = System.currentTimeMillis()).toEntity())
            entry.id
        }
    }

    suspend fun deleteEntry(id: Long) {
        eventEntryDao.deleteById(id)
    }

    suspend fun getRecentEntries(limit: Int = 10): List<EventEntry> {
        val entities = eventEntryDao.getRecent(limit)
        val typeIds = entities.map { it.eventTypeId }.distinct()
        val types = typeIds.mapNotNull { eventTypeDao.getById(it) }.associateBy { it.id }
        return entities.map { it.toDomain(types[it.eventTypeId]) }
    }

    suspend fun getAllEventTypesForExport(): List<EventType> {
        // Same whole-table shape as observeAllEventTypes: three reads for the
        // export, not the two-per-type (fields + count) this used to issue.
        val fieldsByType = eventFieldDao.getAll().groupByEventType()
        val countByType = eventTypeDao.getEntryCounts().associate { it.eventTypeId to it.count }
        return eventTypeDao.getAll().map { entity ->
            entity.toDomain(
                fields = fieldsByType[entity.id].orEmpty(),
                entryCount = countByType[entity.id] ?: 0
            )
        }
    }

    suspend fun getAllEntriesForEventType(eventTypeId: Long): List<EventEntry> {
        val type = eventTypeDao.getById(eventTypeId)
        return eventEntryDao.getAllForExport(eventTypeId).map { it.toDomain(type) }
    }

    suspend fun getRecentEntriesByEventType(eventTypeId: Long, limit: Int = 10): List<EventEntry> {
        val entities = eventEntryDao.getRecentByEventType(eventTypeId, limit)
        val type = eventTypeDao.getById(eventTypeId)
        return entities.map { it.toDomain(type) }
    }

    suspend fun getRecentEntriesByCategory(category: String, limit: Int = 10): List<EventEntry> {
        val matchingTypes = eventTypeDao.getAll().filter { it.category == category }
        if (matchingTypes.isEmpty()) return emptyList()
        val typeIds = matchingTypes.map { it.id }
        val typeMap = matchingTypes.associateBy { it.id }
        return eventEntryDao.getRecentByEventTypes(typeIds, limit).map { it.toDomain(typeMap[it.eventTypeId]) }
    }

    /**
     * Field definitions for the given event types, keyed by event-type id with
     * each list ordered by sortOrder. The timeline widget needs these to label
     * every stored value — an [EventEntry] only carries field id → value, so the
     * names ("Systolic", "Pulse", …) and units live on the field definitions.
     *
     * Every requested id gets an entry, empty when the type defines no fields.
     */
    suspend fun getFieldsByEventType(eventTypeIds: List<Long>): Map<Long, List<EventField>> {
        if (eventTypeIds.isEmpty()) return emptyMap()
        // One read for the table beats one per requested id: the widget calls this
        // with the type of every entry it renders, on every widget update.
        val fieldsByType = eventFieldDao.getAll().groupByEventType()
        return eventTypeIds.distinct().associateWith { fieldsByType[it].orEmpty() }
    }

    suspend fun getAllCategories(): List<String> =
        eventTypeDao.getAll()
            .mapNotNull { it.category.takeIf { c -> c.isNotBlank() } }
            .distinct()
            .sorted()
}

/**
 * Groups a whole-table field read by owning event type, mapping each row to its
 * domain model.
 *
 * Both DAO reads behind this order by (eventTypeId, sortOrder) and [groupBy]
 * preserves encounter order, so each type's list arrives in sortOrder without a
 * second sort — a query that drops that ORDER BY silently scrambles field order
 * in every form built from it.
 */
private fun List<EventFieldEntity>.groupByEventType(): Map<Long, List<EventField>> =
    groupBy { it.eventTypeId }.mapValues { (_, rows) -> rows.map { it.toDomain() } }
