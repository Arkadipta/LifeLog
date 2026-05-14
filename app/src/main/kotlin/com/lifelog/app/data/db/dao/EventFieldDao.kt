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
}
