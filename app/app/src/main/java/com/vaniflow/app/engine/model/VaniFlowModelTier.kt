package com.vaniflow.app.engine.model

import com.vaniflow.app.domain.model.DeviceTier

/**
 * Product-facing model tiers. Internal technical model identifiers (e.g. GGUF
 * model IDs) are NEVER exposed to users; the UI shows only these VaniFlow brands.
 *
 * Phase 3 / Phase 17 of M16.
 */
enum class VaniFlowModelTier(
    val brandedName: String,
    val tagline: String,
    val ramRequirementMb: Int,
    val storageRequirementMb: Int,
    val qualityTier: String,
    val recommendedDeviceTier: DeviceTier
) {
    LITE(
        brandedName = "VaniFlow Lite",
        tagline = "Fast • Lightweight • Everyday conversation",
        ramRequirementMb = 2048,
        storageRequirementMb = 420,
        qualityTier = "Balanced for low-RAM devices",
        recommendedDeviceTier = DeviceTier.LOW
    ),
    CORE(
        brandedName = "VaniFlow Core",
        tagline = "Balanced • Recommended • Better conversation",
        ramRequirementMb = 4096,
        storageRequirementMb = 1100,
        qualityTier = "Best contextual quality for most devices",
        recommendedDeviceTier = DeviceTier.MEDIUM
    ),
    PRO(
        brandedName = "VaniFlow Pro",
        tagline = "Advanced • Highest local quality • High-end devices",
        ramRequirementMb = 8192,
        storageRequirementMb = 2200,
        qualityTier = "Premium conversational depth",
        recommendedDeviceTier = DeviceTier.HIGH
    );

    companion object {
        /** Maps an internal model id to a VaniFlow product tier. */
        fun fromModelId(modelId: String): VaniFlowModelTier = when {
            modelId.contains("05b") || modelId.contains("0_5b") -> LITE
            modelId.contains("15b") || modelId.contains("1_5b") -> CORE
            modelId.contains("3b") || modelId.contains("7b") || modelId.contains("pro") -> PRO
            else -> CORE
        }
    }
}
