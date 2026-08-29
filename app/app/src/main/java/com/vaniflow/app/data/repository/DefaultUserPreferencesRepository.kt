package com.vaniflow.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.vaniflow.app.domain.repository.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultUserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : UserPreferencesRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vaniflow_user_prefs", Context.MODE_PRIVATE)

    private val _selectedCharacterId = MutableStateFlow(
        prefs.getString(KEY_CHARACTER_ID, "shub") ?: "shub"
    )
    override val selectedCharacterId: StateFlow<String> = _selectedCharacterId.asStateFlow()

    override suspend fun setSelectedCharacterId(characterId: String) {
        _selectedCharacterId.value = characterId
        withContext(Dispatchers.IO) {
            prefs.edit().putString(KEY_CHARACTER_ID, characterId).apply()
        }
    }

    companion object {
        private const val KEY_CHARACTER_ID = "selected_character_id"
    }
}
