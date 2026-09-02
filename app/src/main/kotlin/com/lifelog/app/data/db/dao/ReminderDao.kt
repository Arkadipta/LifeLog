package com.lifelog.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.lifelog.app.data.db.entity.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY nextTriggerAt ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isActive = 1 ORDER BY nextTriggerAt ASC")
    suspend fun getAllActive(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ReminderEntity): Long

    @Update
    suspend fun update(entity: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE reminders SET isActive = :isActive WHERE id = :id")
    suspend fun setActive(id: Long, isActive: Boolean)

    @Query("UPDATE reminders SET nextTriggerAt = :nextTriggerAt WHERE id = :id")
    suspend fun updateNextTrigger(id: Long, nextTriggerAt: Long)

    @Query("SELECT * FROM reminders WHERE eventTypeId = :eventTypeId AND recurrenceType = 'TIME_SINCE_LAST' AND isActive = 1")
    suspend fun getActiveTimeSinceLastByEventType(eventTypeId: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders ORDER BY id ASC")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT id FROM reminders WHERE eventTypeId = :eventTypeId")
    suspend fun getIdsByEventType(eventTypeId: Long): List<Long>

    @Query("UPDATE reminders SET eventTypeId = NULL, isActive = 0 WHERE eventTypeId = :eventTypeId")
    suspend fun unlinkAndDeactivateByEventType(eventTypeId: Long)

    /**
     * Unlink every reminder pointing at [eventTypeId] (eventTypeId → NULL, the
     * editor's "All Events (Global)" state) and deactivate it, returning the
     * affected ids so the caller can cancel their armed alarms. One transaction:
     * a concurrent write can never observe a half-detached set.
     */
    @Transaction
    suspend fun detachFromEventType(eventTypeId: Long): List<Long> {
        val ids = getIdsByEventType(eventTypeId)
        unlinkAndDeactivateByEventType(eventTypeId)
        return ids
    }

    @Query("SELECT COUNT(*) FROM reminders WHERE eventTypeId = :eventTypeId AND isActive = 1")
    fun observeActiveCountByEventType(eventTypeId: Long): Flow<Int>
}
