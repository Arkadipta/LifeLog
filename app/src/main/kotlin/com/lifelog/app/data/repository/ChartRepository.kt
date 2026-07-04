package com.lifelog.app.data.repository

import com.lifelog.app.data.db.dao.ChartConfigDao
import com.lifelog.app.data.db.toDomain
import com.lifelog.app.data.db.toEntity
import com.lifelog.app.domain.model.ChartConfig
import com.lifelog.app.domain.model.StoredChartConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChartRepository @Inject constructor(
    private val chartConfigDao: ChartConfigDao
) {
    /**
     * Every chart row for the event, in carousel order — rows whose stored JSON
     * no longer decodes come through as [StoredChartConfig.Unreadable] so the
     * UI can show them and offer deletion instead of hiding them forever.
     */
    fun observeCharts(eventTypeId: Long): Flow<List<StoredChartConfig>> =
        chartConfigDao.observeByEventType(eventTypeId).map { it.map { e -> e.toDomain() } }

    suspend fun saveChart(config: ChartConfig) =
        chartConfigDao.upsert(config.toEntity())

    suspend fun deleteChart(id: String) =
        chartConfigDao.deleteById(id)
}
