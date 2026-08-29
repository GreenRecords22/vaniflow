package com.vaniflow.app.ui.avatar

import com.vaniflow.app.R

/**
 * Per-character avatar configuration. Each character maps to its own asset.
 * The avatar layer remains independent of AI logic (Phase 12 of M16).
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
}
