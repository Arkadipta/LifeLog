package com.lifelog.app.data.repository

import com.lifelog.app.data.db.dao.ChartConfigDao
import com.lifelog.app.data.db.toDomain
import com.lifelog.app.data.db.toEntity
import com.lifelog.app.domain.model.ChartConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChartRepository @Inject constructor(
    private val chartConfigDao: ChartConfigDao
) {
    fun observeCharts(eventTypeId: Long): Flow<List<ChartConfig>> =
        chartConfigDao.observeByEventType(eventTypeId).map { it.map { e -> e.toDomain() } }

    suspend fun saveChart(config: ChartConfig) =
        chartConfigDao.upsert(config.toEntity())

    suspend fun deleteChart(id: String) =
        chartConfigDao.deleteById(id)
}
