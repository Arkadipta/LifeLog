package com.lifelog.app.data.repository

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.lifelog.app.data.db.toDomain
import com.lifelog.app.data.db.toEntity
import com.lifelog.app.data.db.dao.EventEntryDao
import com.lifelog.app.data.db.dao.EventFieldDao
import com.lifelog.app.data.db.dao.EventTypeDao
import com.lifelog.app.domain.model.EventEntry
import com.lifelog.app.domain.model.EventType
import com.lifelog.app.widget.ChartWidgetReceiver
import com.lifelog.app.widget.TimelineWidgetReceiver
import com.lifelog.app.widget.WidgetPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val eventTypeDao: EventTypeDao,
    private val eventFieldDao: EventFieldDao,
    private val eventEntryDao: EventEntryDao,
    @ApplicationContext private val context: Context,
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

    fun observeEntriesForEventType(eventTypeId: Long): Flow<List<EventEntry>> {
        val entityFlow = eventTypeDao.observeById(eventTypeId)
        val entriesFlow = eventEntryDao.observeByEventType(eventTypeId)
        return combine(entityFlow, entriesFlow) { entity, entries ->
            entries.map { e ->
                e.toDomain(
                    eventTypeName = entity?.name ?: "",
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
            eventTypeColor = type?.colorArgb ?: EventType.DEFAULT_COLOR,
            eventTypeIcon = type?.iconName ?: "star"
        )
    }

    suspend fun saveEntry(entry: EventEntry): Long {
        val id = if (entry.id == 0L) {
            eventEntryDao.insert(entry.toEntity())
        } else {
            eventEntryDao.update(entry.copy(updatedAt = System.currentTimeMillis()).toEntity())
            entry.id
        }
        refreshWidgets(changedEventTypeId = entry.eventTypeId)
        return id
    }

    suspend fun deleteEntry(id: Long) {
        // Capture the event type before deleting so we can target the right chart widgets.
        val eventTypeId = eventEntryDao.getById(id)?.eventTypeId ?: -1L
        eventEntryDao.deleteById(id)
        refreshWidgets(changedEventTypeId = eventTypeId)
    }

    suspend fun getRecentEntries(limit: Int = 10): List<EventEntry> {
        val entities = eventEntryDao.getRecent(limit)
        val typeIds = entities.map { it.eventTypeId }.distinct()
        val types = typeIds.mapNotNull { eventTypeDao.getById(it) }.associateBy { it.id }
        return entities.map { e ->
            val type = types[e.eventTypeId]
            e.toDomain(
                eventTypeName = type?.name ?: "",
                eventTypeColor = type?.colorArgb ?: EventType.DEFAULT_COLOR,
                eventTypeIcon = type?.iconName ?: "star"
            )
        }
    }

    suspend fun getRecentEntriesForEvent(eventTypeId: Long, limit: Int = 10): List<EventEntry> {
        val type = eventTypeDao.getById(eventTypeId)
        return eventEntryDao.getRecentForEventType(eventTypeId, limit).map { e ->
            e.toDomain(
                eventTypeName = type?.name ?: "",
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
                eventTypeColor = type?.colorArgb ?: EventType.DEFAULT_COLOR,
                eventTypeIcon = type?.iconName ?: "star"
            )
        }
    }

    /**
     * Sends ACTION_APPWIDGET_UPDATE broadcasts so widgets re-render immediately after
     * an entry is saved or deleted.
     *
     * - All Timeline widgets are always refreshed (they may show "all events").
     * - Only Chart widgets whose configured event type matches [changedEventTypeId] are
     *   refreshed; pass -1 to refresh all chart widgets (e.g. after a delete where the
     *   event type couldn't be determined).
     */
    private fun refreshWidgets(changedEventTypeId: Long) {
        val awm = AppWidgetManager.getInstance(context)

        // ── Timeline ────────────────────────────────────────────────────────
        val timelineIds = awm.getAppWidgetIds(
            ComponentName(context, TimelineWidgetReceiver::class.java)
        )
        if (timelineIds.isNotEmpty()) {
            context.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = ComponentName(context, TimelineWidgetReceiver::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, timelineIds)
            })
        }

        // ── Chart ────────────────────────────────────────────────────────────
        val allChartIds = awm.getAppWidgetIds(
            ComponentName(context, ChartWidgetReceiver::class.java)
        )
        val targetChartIds: IntArray = if (changedEventTypeId == -1L) {
            allChartIds
        } else {
            allChartIds
                .filter { WidgetPrefs.getChartEventTypeId(context, it) == changedEventTypeId }
                .toIntArray()
        }
        if (targetChartIds.isNotEmpty()) {
            context.sendBroadcast(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = ComponentName(context, ChartWidgetReceiver::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, targetChartIds)
            })
        }
    }
}
