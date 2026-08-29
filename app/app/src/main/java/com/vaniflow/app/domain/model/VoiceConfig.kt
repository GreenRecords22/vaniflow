package com.vaniflow.app.domain.model

import kotlinx.serialization.Serializable

/**
 * Character voice configuration parameters for on-device TTS.
 * Decoupled from concrete synthesis engine implementations.
 */
@Serializable
data class CharacterVoiceConfig(
    val characterId: String,
    val voiceId: String,
    val displayName: String,
    val language: String = "en-IN",
    val accent: String = "Indian English",
    val gender: String = "female", // "female" or "male"
    val speakingRate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val volume: Float = 1.0f,
    val neuralModelId: String? = null
)
