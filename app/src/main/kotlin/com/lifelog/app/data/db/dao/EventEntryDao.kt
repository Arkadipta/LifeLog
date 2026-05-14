package com.lifelog.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lifelog.app.data.db.entity.EventEntryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventEntryDao {

    @Query("SELECT * FROM event_entries ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<EventEntryEntity>>

    @Query("SELECT * FROM event_entries WHERE eventTypeId = :eventTypeId ORDER BY createdAt DESC")
    fun observeByEventType(eventTypeId: Long): Flow<List<EventEntryEntity>>

    @Query("SELECT * FROM event_entries WHERE id = :id")
    suspend fun getById(id: Long): EventEntryEntity?

    @Query("SELECT * FROM event_entries ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<EventEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EventEntryEntity): Long

    @Update
    suspend fun update(entity: EventEntryEntity)

    @Query("DELETE FROM event_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM event_entries ORDER BY createdAt ASC")
    suspend fun getAllEntries(): List<EventEntryEntity>

    @Query("SELECT * FROM event_entries WHERE eventTypeId = :eventTypeId ORDER BY createdAt ASC")
    suspend fun getAllForExport(eventTypeId: Long): List<EventEntryEntity>

    @Query("SELECT * FROM event_entries WHERE eventTypeId = :eventTypeId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestForEventType(eventTypeId: Long): EventEntryEntity?
}
