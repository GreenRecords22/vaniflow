package com.vaniflow.app.domain.repository

import com.vaniflow.app.engine.learning.tutor.model.MasteryState
import kotlinx.coroutines.flow.Flow

interface ConceptMasteryRepository {
    fun getAllMasteryFlow(): Flow<List<MasteryState>>
    suspend fun getAllMastery(): List<MasteryState>
    suspend fun getMastery(conceptId: String): MasteryState?
    suspend fun saveMastery(mastery: MasteryState)
    suspend fun saveAllMastery(list: List<MasteryState>)
    suspend fun clearAllMastery()
}
