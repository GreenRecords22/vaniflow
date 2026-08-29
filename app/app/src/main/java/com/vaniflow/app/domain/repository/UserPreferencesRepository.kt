package com.vaniflow.app.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface UserPreferencesRepository {
    val selectedCharacterId: StateFlow<String>
    suspend fun setSelectedCharacterId(characterId: String)
}
