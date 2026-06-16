package com.lifelog.app.data.repository

import com.lifelog.app.data.db.toDomain
import com.lifelog.app.data.db.toEntity
import com.lifelog.app.data.db.dao.EventTypeDao
import com.lifelog.app.data.db.dao.ReminderDao
import com.lifelog.app.domain.RecurrenceCalculator
import com.lifelog.app.domain.model.Reminder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepository @Inject constructor(
    private val reminderDao: ReminderDao,
    private val eventTypeDao: EventTypeDao
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAll(): Flow<List<Reminder>> =
        reminderDao.observeAll().flatMapLatest { entities ->
            eventTypeDao.observeAll().map { types ->
                val typeMap = types.associateBy { it.id }
                entities.map { r ->
                    r.toDomain(eventTypeName = r.eventTypeId?.let { typeMap[it]?.name })
                }
            }
        }

    suspend fun getAllActive(): List<Reminder> {
        return reminderDao.getAllActive().map { r ->
            val typeName = r.eventTypeId?.let { eventTypeDao.getById(it)?.name }
            r.toDomain(typeName)
        }
    }

    suspend fun getById(id: Long): Reminder? {
        val entity = reminderDao.getById(id) ?: return null
        val typeName = entity.eventTypeId?.let { eventTypeDao.getById(it)?.name }
        return entity.toDomain(typeName)
    }

    suspend fun save(reminder: Reminder): Long {
        return if (reminder.id == 0L) {
            reminderDao.insert(reminder.toEntity())
        } else {
            reminderDao.update(reminder.toEntity())
            reminder.id
        }
    }

    suspend fun delete(id: Long) {
        reminderDao.deleteById(id)
    }

    /** Re-inserts a previously deleted reminder exactly as it was (same id and schedule). */
    suspend fun restore(reminder: Reminder) {
        reminderDao.insert(reminder.toEntity())
    }

    suspend fun setActive(id: Long, isActive: Boolean) {
        reminderDao.setActive(id, isActive)
    }

    suspend fun updateNextTrigger(id: Long, nextTriggerAt: Long) {
        reminderDao.updateNextTrigger(id, nextTriggerAt)
    }

    /**
     * Called after a new entry is logged for [eventTypeId].
     * Finds all active TIME_SINCE_LAST reminders linked to that event type,
     * computes new nextTriggerAt based on [entryAt], persists it,
     * and invokes [schedule] so the caller can reschedule the alarm.
     */
    suspend fun rescheduleTimeSinceLast(
        eventTypeId: Long,
        entryAt: Long,
        schedule: suspend (Reminder) -> Unit
    ) {
        val entities = reminderDao.getActiveTimeSinceLastByEventType(eventTypeId)
        for (entity in entities) {
            val typeName = eventTypeDao.getById(eventTypeId)?.name
            val reminder = entity.toDomain(typeName)
            val nextTrigger = RecurrenceCalculator.computeNextTrigger(
                rule = reminder.recurrenceRule,
                after = System.currentTimeMillis(),
                lastEntryAt = entryAt
            ) ?: continue
            reminderDao.updateNextTrigger(reminder.id, nextTrigger)
            schedule(reminder.copy(nextTriggerAt = nextTrigger))
        }
    }
}
