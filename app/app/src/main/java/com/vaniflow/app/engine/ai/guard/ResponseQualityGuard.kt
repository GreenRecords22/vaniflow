package com.vaniflow.app.engine.ai.guard

import com.vaniflow.app.engine.ai.AITurn
import com.vaniflow.app.engine.ai.RepetitionGuard
import javax.inject.Inject
import javax.inject.Singleton

sealed class QualityCheckResult {
    object Valid : QualityCheckResult()
    data class Invalid(val reason: String, val cleanedCandidate: String? = null) : QualityCheckResult()
}

/**
 * Lightweight Response Quality Guard.
 * Validates generated AI responses before TTS and UI display:
 * - Rejects empty or whitespace-only tokens.
 * - Detects repeated assistant responses.
 * - Detects leaked system instructions, prompt markers, or API tokens.
 * - Detects generic canned fillers that disregard the user's speech.
 * - Provides immediate sanitization or safe contextual recovery without multiple slow network retries.
 */
@Singleton
class ResponseQualityGuard @Inject constructor() {

    private val bannedCannedPhrases = listOf(
        "that's interesting! keep practicing",
        "that's great! you're doing a wonderful job",
        "english is a journey. keep practicing",
        "that's interesting! tell me more about",
        "keep practicing your english!"
    )

    private val promptLeakMarkers = listOf(
        "<user_speech>",
        "</user_speech>",
        "[VANIFLOW TUTOR CONSTITUTION",
        "TUTORING DIRECTIVE:",
        "RESPONSE RULES:",
        "CHARACTER PERSONALITY:",
        "SYSTEM:",
        "Bearer ",
        "sk-",
        "gsk_"
    )

    fun validate(
        candidate: String,
        userInput: String,
        history: List<AITurn> = emptyList(),
        characterName: String = "Raya"
    ): QualityCheckResult {
        var clean = cleanPrefixes(candidate.trim(), characterName)

        if (clean.isBlank()) {
            return QualityCheckResult.Invalid("Response is blank or whitespace")
        }

        // 1. Check for prompt leak markers
        for (leak in promptLeakMarkers) {
            if (clean.contains(leak, ignoreCase = true)) {
                clean = clean.replace(leak, "", ignoreCase = true).trim()
                if (clean.isBlank()) {
                    return QualityCheckResult.Invalid("Response contained only leaked prompt directives")
                }
            }
        }

        // 2. Check repetition against recent turns
        val pastAssistantTurns = history.filter { it.role == AITurn.Role.ASSISTANT }.takeLast(3).map { it.content }
        if (RepetitionGuard.isRepetition(clean, pastAssistantTurns)) {
            return QualityCheckResult.Invalid("Response is excessively similar to recent turn", cleanedCandidate = clean)
        }

        // 3. Check for generic canned clichés that ignore the question
        val lower = clean.lowercase()
        for (canned in bannedCannedPhrases) {
            if (lower.contains(canned)) {
                return QualityCheckResult.Invalid("Response is generic canned filler", cleanedCandidate = clean)
            }
        }

        return QualityCheckResult.Valid
    }

    fun cleanPrefixes(text: String, characterName: String): String {
        var clean = text.trim()
        val prefixes = listOf(
            "$characterName:", "$characterName :",
            "Assistant:", "Assistant :",
            "System:", "System :",
            "AI:", "AI :",
            "User:", "User :",
            "Tutor:", "Tutor :"
        )
        for (prefix in prefixes) {
            if (clean.startsWith(prefix, ignoreCase = true)) {
                clean = clean.substring(prefix.length).trim()
            }
        }
        return clean
    }

    fun sanitizeOrFallback(
        candidate: String,
        userInput: String,
        characterName: String,
        fallbackText: String
    ): String {
        val check = validate(candidate, userInput, characterName = characterName)
        return when (check) {
            is QualityCheckResult.Valid -> cleanPrefixes(candidate, characterName)
            is QualityCheckResult.Invalid -> {
                if (!check.cleanedCandidate.isNullOrBlank() && check.reason != "Response is generic canned filler") {
                    check.cleanedCandidate
                } else {
                    fallbackText
                }
            }
        }
    }
}
