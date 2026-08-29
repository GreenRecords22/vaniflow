package com.vaniflow.app.domain.model

import kotlinx.serialization.Serializable

enum class ModelType {
    STT,
    TTS,
    LLM,
    VAD
}

enum class ModelState {
    NOT_INSTALLED,
    DOWNLOADING,
    VERIFYING,
    READY,
    INSTALLED, // Alias for READY
    CORRUPTED,
    FAILED,
    ERROR, // Alias for FAILED
    CANCELLED
}

enum class DeviceTier {
    LOW,     // 3-4 GB RAM
    MEDIUM,  // 6 GB RAM
    HIGH     // 8+ GB RAM
}

@Serializable
data class ModelMetadata(
    val id: String,
    val name: String,
    val type: ModelType,
    val version: String,
    val sizeBytes: Long,
    val sha256Checksum: String,
    val recommendedTier: DeviceTier,
    val downloadUrl: String,
    val localFileName: String,
    val description: String,
    /** User-facing VaniFlow brand name (never the internal technical model name). */
    val brandedName: String = name,
    /** VaniFlow product tier (LITE/CORE/PRO), if this is a conversational LLM. Null otherwise. */
    val vaniFlowTier: String? = null
)

data class ModelDownloadProgress(
    val modelId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val progressPercent: Float,
    val state: ModelState
)
