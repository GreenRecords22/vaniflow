package com.vaniflow.app.domain.repository

import com.vaniflow.app.data.local.db.entity.DailyUsageEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for persistent tracking of daily speaking time and token consumption.
 */
interface DailyUsageRepository {
    suspend fun getUsageForDate(date: String): DailyUsageEntity?
    suspend fun saveUsage(usage: DailyUsageEntity)
    fun getAllUsage(): Flow<List<DailyUsageEntity>>
    suspend fun clearUsage()
}
