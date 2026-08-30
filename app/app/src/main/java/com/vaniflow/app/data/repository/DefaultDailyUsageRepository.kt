package com.vaniflow.app.data.repository

import com.vaniflow.app.data.local.db.dao.DailyUsageDao
import com.vaniflow.app.data.local.db.entity.DailyUsageEntity
import com.vaniflow.app.domain.repository.DailyUsageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultDailyUsageRepository @Inject constructor(
    private val dailyUsageDao: DailyUsageDao
) : DailyUsageRepository {

    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    constructor(dailyUsageDao: DailyUsageDao, dispatcher: CoroutineDispatcher) : this(dailyUsageDao) {
        this.ioDispatcher = dispatcher
    }

    override suspend fun getUsageForDate(date: String): DailyUsageEntity? = withContext(ioDispatcher) {
        dailyUsageDao.getUsageByDate(date)
    }

    override suspend fun saveUsage(usage: DailyUsageEntity): Unit = withContext(ioDispatcher) {
        dailyUsageDao.saveUsage(usage)
    }

    override fun getAllUsage(): Flow<List<DailyUsageEntity>> {
        return dailyUsageDao.getAllUsage()
    }

    override suspend fun clearUsage(): Unit = withContext(ioDispatcher) {
        dailyUsageDao.deleteAllUsage()
    }
}
