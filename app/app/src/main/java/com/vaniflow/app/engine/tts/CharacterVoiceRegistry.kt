package com.vaniflow.app.engine.tts

import com.vaniflow.app.domain.model.CharacterVoiceConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry defining distinct acoustic, prosodic, and gender profiles for all 4 AI conversation partners.
 * Ensures Raya & Adwaita use natural Indian English Female voices, and Shub & Rudra use natural Indian English Male voices.
 */
@Singleton
class CharacterVoiceRegistry @Inject constructor() {

    val voiceConfigs: Map<String, CharacterVoiceConfig> = mapOf(
        "raya" to CharacterVoiceConfig(
            characterId = "raya",
            voiceId = "en_IN_raya_female",
            displayName = "Raya (Warm, Friendly & Encouraging)",
            language = "en-IN",
            accent = "Indian English",
            gender = "female",
            speakingRate = 1.0f,
            pitch = 1.30f, // Distinct, warm, sweet feminine pitch
            volume = 1.0f,
            neuralModelId = "tts_piper_raya_warm"
        ),
        "adwaita" to CharacterVoiceConfig(
            characterId = "adwaita",
            voiceId = "en_IN_adwaita_female",
            displayName = "Adwaita (Polished & Confident Professional)",
            language = "en-IN",
            accent = "Indian English",
            gender = "female",
            speakingRate = 0.98f,
            pitch = 1.22f, // Poised, articulate, executive feminine pitch
            volume = 1.0f,
            neuralModelId = null
        ),
        "shub" to CharacterVoiceConfig(
            characterId = "shub",
            voiceId = "en_IN_shub_male",
            displayName = "Shub (Calm, Articulate & Analytical)",
            language = "en-IN",
            accent = "Indian English",
            gender = "male",
            speakingRate = 0.95f,
            pitch = 0.88f, // Deep, calm, articulate masculine pitch
            volume = 1.0f,
            neuralModelId = null
        ),
        "rudra" to CharacterVoiceConfig(
            characterId = "rudra",
            voiceId = "en_IN_rudra_male",
            displayName = "Rudra (Casual, Energetic & Dynamic)",
            language = "en-IN",
            accent = "Indian English",
            gender = "male",
            speakingRate = 1.02f,
            pitch = 0.92f, // Energetic, vibrant masculine pitch
            volume = 1.0f,
            neuralModelId = null
        )
    )

    fun getVoiceConfig(id: String): CharacterVoiceConfig {
        val lower = id.lowercase()
        return when {
            lower.contains("adwaita") -> voiceConfigs["adwaita"]!!
            lower.contains("raya") -> voiceConfigs["raya"]!!
            lower.contains("rudra") -> voiceConfigs["rudra"]!!
            lower.contains("shub") -> voiceConfigs["shub"]!!
            else -> voiceConfigs.values.find {
                it.voiceId.equals(id, ignoreCase = true) || it.characterId.equals(id, ignoreCase = true)
            } ?: voiceConfigs["raya"]!!
        }
    }

    fun getAllConfigs(): List<CharacterVoiceConfig> = voiceConfigs.values.toList()
}
