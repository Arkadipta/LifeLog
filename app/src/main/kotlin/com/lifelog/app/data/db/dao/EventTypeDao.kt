package com.lifelog.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lifelog.app.data.db.entity.EventTypeEntity
import kotlinx.coroutines.flow.Flow

/** Projection for [EventTypeDao.observeEntryCounts]: one row per event type that has entries. */
data class EntryCountByType(
    val eventTypeId: Long,
    val count: Int
)

/** Projection for [EventTypeDao.observeLatestEntryTimes]: most recent entry time per type that has entries. */
data class LatestEntryByType(
    val eventTypeId: Long,
    val latestAt: Long
)

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

    /**
     * Observable per-type entry counts. Reads the `event_entries` table so Room's
     * invalidation tracker re-emits whenever an entry is created, imported, edited,
     * or deleted — keeping the count on [EventsScreen] live. Types with zero entries
     * are absent (GROUP BY), so callers must default missing ids to 0.
     */
    @Query("SELECT eventTypeId, COUNT(*) AS count FROM event_entries GROUP BY eventTypeId")
    fun observeEntryCounts(): Flow<List<EntryCountByType>>

    /**
     * Observable most-recent entry time per type. Like [observeEntryCounts] this
     * reads `event_entries`, so Room re-emits when an entry is added, edited, or
     * deleted — keeping "Recent activity" sorting on [EventsScreen] live. Types
     * with zero entries are absent (GROUP BY), so callers default missing ids to null.
     */
    @Query("SELECT eventTypeId, MAX(createdAt) AS latestAt FROM event_entries GROUP BY eventTypeId")
    fun observeLatestEntryTimes(): Flow<List<LatestEntryByType>>

    @Query("SELECT * FROM event_types ORDER BY id ASC")
    suspend fun getAll(): List<EventTypeEntity>
}
