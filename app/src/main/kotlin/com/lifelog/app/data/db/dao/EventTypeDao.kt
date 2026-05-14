package com.lifelog.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lifelog.app.data.db.entity.EventTypeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EventTypeDao {

    @Query("SELECT * FROM event_types ORDER BY name ASC")
    fun observeAll(): Flow<List<EventTypeEntity>>

    @Query("SELECT * FROM event_types WHERE id = :id")
    suspend fun getById(id: Long): EventTypeEntity?

    @Query("SELECT * FROM event_types WHERE id = :id")
    fun observeById(id: Long): Flow<EventTypeEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: EventTypeEntity): Long

    @Update
    suspend fun update(entity: EventTypeEntity)

    @Query("DELETE FROM event_types WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM event_entries WHERE eventTypeId = :eventTypeId")
    suspend fun getEntryCount(eventTypeId: Long): Int

    @Query("SELECT * FROM event_types ORDER BY id ASC")
    suspend fun getAll(): List<EventTypeEntity>
}
