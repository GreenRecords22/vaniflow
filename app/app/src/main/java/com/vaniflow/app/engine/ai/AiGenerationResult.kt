package com.vaniflow.app.engine.ai

/**
 * Structured runtime result for AI generation with authentic provider provenance.
 */
data class AiGenerationResult(
    val text: String,
    val provider: String,
    val model: String,
    val routingLevel: AIRoutingLevel,
    val latencyMs: Long,
    val tokenCount: Int,
    val wasFallback: Boolean = false,
    val qualityStatus: String = "PASS"
)