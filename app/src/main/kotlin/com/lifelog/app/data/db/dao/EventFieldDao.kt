package com.lifelog.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lifelog.app.data.db.entity.EventFieldEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventFieldDao {

    @Query("SELECT * FROM event_fields WHERE eventTypeId = :eventTypeId ORDER BY sortOrder ASC")
    fun observeByEventType(eventTypeId: Long): Flow<List<EventFieldEntity>>

    @Query("SELECT * FROM event_fields WHERE eventTypeId = :eventTypeId ORDER BY sortOrder ASC")
    suspend fun getByEventType(eventTypeId: Long): List<EventFieldEntity>

    @Query("SELECT * FROM event_fields WHERE id = :id")
    suspend fun getById(id: Long): EventFieldEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EventFieldEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<EventFieldEntity>): List<Long>

    @Update
    suspend fun update(entity: EventFieldEntity)

    @Query("DELETE FROM event_fields WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM event_fields WHERE eventTypeId = :eventTypeId")
    suspend fun deleteAllForEventType(eventTypeId: Long)

    @Query("SELECT * FROM event_fields ORDER BY eventTypeId ASC, sortOrder ASC")
    suspend fun getAll(): List<EventFieldEntity>

    /**
     * Every field definition in one read, ordered so grouping by `eventTypeId`
     * yields each type's fields already in sortOrder.
     *
     * Two things depend on this being a whole-table Flow rather than a per-type
     * query: it keeps [com.lifelog.app.data.repository.EventRepository.observeAllEventTypes]
     * to a fixed number of queries per emission instead of one per event type,
     * and — because Room's invalidation tracker sees `event_fields` — it is what
     * makes a field-only edit (an option added mid-entry, a rename) re-emit the
     * list at all. A combine over `event_types`/`event_entries` alone never did.
     */
    @Query("SELECT * FROM event_fields ORDER BY eventTypeId ASC, sortOrder ASC")
    fun observeAll(): Flow<List<EventFieldEntity>>
}
