package com.lifelog.app.data.repository

import androidx.room.withTransaction
import com.lifelog.app.data.db.LifeLogDatabase
import com.lifelog.app.data.db.toDomain
import com.lifelog.app.data.db.toEntity
import com.lifelog.app.data.db.dao.EventEntryDao
import com.lifelog.app.data.db.dao.EventFieldDao
import com.lifelog.app.data.db.dao.EventTypeDao
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

    fun observeAllEventTypes(): Flow<List<EventType>> =
        combine(
            eventTypeDao.observeAll(),
            // Observed (not a one-shot getEntryCount) so adding/importing/deleting an
            // entry re-emits the list with a fresh count without an event-type edit.
            eventTypeDao.observeEntryCounts(),
            // Drives "Recent activity" sorting; re-emits on any entry change too.
            eventTypeDao.observeLatestEntryTimes()
        ) { entities, counts, latestTimes ->
            val countByType = counts.associate { it.eventTypeId to it.count }
            val latestByType = latestTimes.associate { it.eventTypeId to it.latestAt }
            entities.map { entity ->
                val fields = eventFieldDao.getByEventType(entity.id).map { it.toDomain() }
                entity.toDomain(fields, countByType[entity.id] ?: 0, latestByType[entity.id])
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

    fun observeEntriesForEventType(eventTypeId: Long): Flow<List<EventEntry>> {
        val entityFlow = eventTypeDao.observeById(eventTypeId)
        val entriesFlow = eventEntryDao.observeByEventType(eventTypeId)
        return combine(entityFlow, entriesFlow) { entity, entries ->
            entries.map { e ->
                e.toDomain(
                    eventTypeName = entity?.name ?: "",
                    eventTypeCategory = entity?.category ?: "",
                    eventTypeColor = entity?.colorArgb ?: EventType.DEFAULT_COLOR,
                    eventTypeIcon = entity?.iconName ?: "star"
                )
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllEntries(): Flow<List<EventEntry>> =
        eventEntryDao.observeAll().flatMapLatest { entries ->
            eventTypeDao.observeAll().map { types ->
                val typeMap = types.associateBy { it.id }
                entries.map { e ->
                    val type = typeMap[e.eventTypeId]
                    e.toDomain(
                        eventTypeName = type?.name ?: "",
                        eventTypeCategory = type?.category ?: "",
                        eventTypeColor = type?.colorArgb ?: EventType.DEFAULT_COLOR,
                        eventTypeIcon = type?.iconName ?: "star"
                    )
                }
            }
        }

    suspend fun getEntry(id: Long): EventEntry? {
        val entity = eventEntryDao.getById(id) ?: return null
        val type = eventTypeDao.getById(entity.eventTypeId)
        return entity.toDomain(
            eventTypeName = type?.name ?: "",
            eventTypeCategory = type?.category ?: "",
            eventTypeColor = type?.colorArgb ?: EventType.DEFAULT_COLOR,
            eventTypeIcon = type?.iconName ?: "star"
        )
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
        return entities.map { e ->
            val type = types[e.eventTypeId]
            e.toDomain(
                eventTypeName = type?.name ?: "",
                eventTypeCategory = type?.category ?: "",
                eventTypeColor = type?.colorArgb ?: EventType.DEFAULT_COLOR,
                eventTypeIcon = type?.iconName ?: "star"
            )
        }
    }

    suspend fun getAllEventTypesForExport(): List<EventType> {
        return eventTypeDao.getAll().map { entity ->
            val fields = eventFieldDao.getByEventType(entity.id).map { it.toDomain() }
            val count = eventTypeDao.getEntryCount(entity.id)
            entity.toDomain(fields, count)
        }
    }

    suspend fun getAllEntriesForEventType(eventTypeId: Long): List<EventEntry> {
        val type = eventTypeDao.getById(eventTypeId)
        return eventEntryDao.getAllForExport(eventTypeId).map { e ->
            e.toDomain(
                eventTypeName = type?.name ?: "",
                eventTypeCategory = type?.category ?: "",
                eventTypeColor = type?.colorArgb ?: EventType.DEFAULT_COLOR,
                eventTypeIcon = type?.iconName ?: "star"
            )
        }
    }

    suspend fun getRecentEntriesByEventType(eventTypeId: Long, limit: Int = 10): List<EventEntry> {
        val entities = eventEntryDao.getRecentByEventType(eventTypeId, limit)
        val type = eventTypeDao.getById(eventTypeId)
        return entities.map { e ->
            e.toDomain(
                eventTypeName = type?.name ?: "",
                eventTypeCategory = type?.category ?: "",
                eventTypeColor = type?.colorArgb ?: EventType.DEFAULT_COLOR,
                eventTypeIcon = type?.iconName ?: "star"
            )
        }
    }

    suspend fun getRecentEntriesByCategory(category: String, limit: Int = 10): List<EventEntry> {
        val matchingTypes = eventTypeDao.getAll().filter { it.category == category }
        if (matchingTypes.isEmpty()) return emptyList()
        val typeIds = matchingTypes.map { it.id }
        val typeMap = matchingTypes.associateBy { it.id }
        return eventEntryDao.getRecentByEventTypes(typeIds, limit).map { e ->
            val type = typeMap[e.eventTypeId]
            e.toDomain(
                eventTypeName = type?.name ?: "",
                eventTypeCategory = type?.category ?: "",
                eventTypeColor = type?.colorArgb ?: EventType.DEFAULT_COLOR,
                eventTypeIcon = type?.iconName ?: "star"
            )
        }
    }

    /**
     * Field definitions for the given event types, keyed by event-type id with
     * each list ordered by sortOrder. The timeline widget needs these to label
     * every stored value — an [EventEntry] only carries field id → value, so the
     * names ("Systolic", "Pulse", …) and units live on the field definitions.
     */
    suspend fun getFieldsByEventType(eventTypeIds: List<Long>): Map<Long, List<EventField>> =
        eventTypeIds.distinct().associateWith { id ->
            eventFieldDao.getByEventType(id).map { it.toDomain() }
        }

    suspend fun getAllCategories(): List<String> =
        eventTypeDao.getAll()
            .mapNotNull { it.category.takeIf { c -> c.isNotBlank() } }
            .distinct()
            .sorted()
}
