package com.vaniflow.app.ui.avatar

import com.vaniflow.app.R

/**
 * Per-character presentation configuration mapping visual assets, speaking pace, and animation style.
 */
data class CharacterPresentation(
    val characterId: String,
    val avatarRes: Int,
    val displayName: String,
    val speakingRate: Float = 1.0f,
    val animationStyle: String = "subtle_state_pulse"
)

/**
 * Backward-compatible avatar configuration model.
 */
data class CharacterAvatarConfig(
    val characterId: String,
    val avatarRes: Int,
    val displayName: String
)

object CharacterAvatarRegistry {
    fun configFor(characterId: String): CharacterAvatarConfig = when (characterId.lowercase()) {
        "raya" -> CharacterAvatarConfig("raya", R.drawable.avatar_raya, "Raya")
        "adwaita" -> CharacterAvatarConfig("adwaita", R.drawable.avatar_adwaita, "Adwaita")
        "shub" -> CharacterAvatarConfig("shub", R.drawable.avatar_shub, "Shub")
        "rudra" -> CharacterAvatarConfig("rudra", R.drawable.avatar_rudra, "Rudra")
        else -> CharacterAvatarConfig("shub", R.drawable.avatar_shub, "Shub")
    }

    fun presentationFor(characterId: String): CharacterPresentation = when (characterId.lowercase()) {
        "raya" -> CharacterPresentation("raya", R.drawable.avatar_raya, "Raya", speakingRate = 0.95f)
        "adwaita" -> CharacterPresentation("adwaita", R.drawable.avatar_adwaita, "Adwaita", speakingRate = 1.0f)
        "shub" -> CharacterPresentation("shub", R.drawable.avatar_shub, "Shub", speakingRate = 0.95f)
        "rudra" -> CharacterPresentation("rudra", R.drawable.avatar_rudra, "Rudra", speakingRate = 1.05f)
        else -> CharacterPresentation("shub", R.drawable.avatar_shub, "Shub", speakingRate = 0.95f)
    }
}
