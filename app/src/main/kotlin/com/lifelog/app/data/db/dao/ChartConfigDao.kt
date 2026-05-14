package com.lifelog.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.lifelog.app.data.db.entity.ChartConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChartConfigDao {
    @Query("SELECT * FROM chart_configs WHERE eventTypeId = :eventTypeId ORDER BY sortOrder ASC, createdAt ASC")
    fun observeByEventType(eventTypeId: Long): Flow<List<ChartConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ChartConfigEntity)

    @Query("DELETE FROM chart_configs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM chart_configs ORDER BY eventTypeId ASC, sortOrder ASC")
    suspend fun getAll(): List<ChartConfigEntity>
}
