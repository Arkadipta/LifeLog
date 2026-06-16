package com.lifelog.app.data.repository

import androidx.room.withTransaction
import com.lifelog.app.data.db.LifeLogDatabase
import com.lifelog.app.data.db.toDomain
import com.lifelog.app.data.db.toEntity
import com.lifelog.app.data.db.dao.ChartConfigDao
import com.lifelog.app.data.db.dao.EventEntryDao
import com.lifelog.app.data.db.dao.EventFieldDao
import com.lifelog.app.data.db.dao.EventTypeDao
import com.lifelog.app.data.db.entity.ChartConfigEntity
import com.lifelog.app.data.db.entity.EventEntryEntity
import com.lifelog.app.data.db.entity.EventFieldEntity
import com.lifelog.app.data.db.entity.EventTypeEntity
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything that cascade-deletes with an event type, captured before deletion so
 * the whole bundle can be restored on undo. Deleting the [type] row removes its
 * [fields], [entries], and [charts] via ON DELETE CASCADE.
 */
data class DeletedEventType(
    val type: EventTypeEntity,
    val fields: List<EventFieldEntity>,
    val entries: List<EventEntryEntity>,
    val charts: List<ChartConfigEntity>
)

@Singleton
class EventRepository @Inject constructor(
    private val db: LifeLogDatabase,
    private val eventTypeDao: EventTypeDao,
    private val eventFieldDao: EventFieldDao,
    private val eventEntryDao: EventEntryDao,
    private val chartConfigDao: ChartConfigDao
) {

    fun observeAllEventTypes(): Flow<List<EventType>> =
        eventTypeDao.observeAll().map { entities ->
            entities.map { entity ->
                val fields = eventFieldDao.getByEventType(entity.id).map { it.toDomain() }
                val count = eventTypeDao.getEntryCount(entity.id)
                entity.toDomain(fields, count)
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

    suspend fun saveEventType(eventType: EventType): Long {
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
        return id
    }

    suspend fun deleteEventType(id: Long) {
        eventTypeDao.deleteById(id)
    }

    /**
     * Deletes an event type and returns a snapshot of it plus every child the
     * cascade removed (fields, entries, charts), or null if it no longer exists.
     * Pair with [restoreEventType] to undo. Atomic so a snapshot always matches
     * exactly what was deleted.
     */
    suspend fun deleteEventTypeReturningSnapshot(id: Long): DeletedEventType? =
        db.withTransaction {
            val type = eventTypeDao.getById(id) ?: return@withTransaction null
            val snapshot = DeletedEventType(
                type = type,
                fields = eventFieldDao.getByEventType(id),
                entries = eventEntryDao.getAllForExport(id),
                charts = chartConfigDao.getByEventType(id)
            )
            eventTypeDao.deleteById(id) // cascades fields, entries, and charts
            snapshot
        }

    /** Re-inserts an event type and all of its children captured by a delete snapshot. */
    suspend fun restoreEventType(snapshot: DeletedEventType) = db.withTransaction {
        eventTypeDao.insert(snapshot.type) // parent first so child FKs resolve
        eventFieldDao.insertAll(snapshot.fields)
        snapshot.entries.forEach { eventEntryDao.insert(it) }
        snapshot.charts.forEach { chartConfigDao.upsert(it) }
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

    /** Re-inserts a previously deleted entry exactly as it was (same id and timestamps). */
    suspend fun restoreEntry(entry: EventEntry) {
        eventEntryDao.insert(entry.toEntity())
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

    suspend fun getAllCategories(): List<String> =
        eventTypeDao.getAll()
            .mapNotNull { it.category.takeIf { c -> c.isNotBlank() } }
            .distinct()
            .sorted()
}
