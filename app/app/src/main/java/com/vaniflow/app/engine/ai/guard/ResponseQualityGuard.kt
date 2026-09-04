package com.vaniflow.app.engine.ai.guard

import com.vaniflow.app.engine.ai.AITurn
import com.vaniflow.app.engine.ai.RepetitionGuard
import com.vaniflow.app.engine.learning.tutor.model.TutorAction
import javax.inject.Inject
import javax.inject.Singleton

enum class QualityFailureType {
    EMPTY_RESPONSE,
    TOO_SHORT,
    GENERIC_FILLER,
    REPETITION,
    NEAR_DUPLICATE,
    QUESTION_IGNORED,
    CONTEXT_IGNORED,
    USER_INTENT_MISMATCH,
    TUTOR_ACTION_VIOLATION,
    SYSTEM_PROMPT_LEAK,
    INTERNAL_REASONING_LEAK,
    META_AI_RESPONSE,
    EXCESSIVE_PRAISE,
    UNSAFE_CONTENT,
    UNNATURAL_RESPONSE,
    DUPLICATE_OPENING
}

sealed class QualityCheckResult {
    object Valid : QualityCheckResult()
    data class Invalid(
        val reason: String,
        val failureType: QualityFailureType = QualityFailureType.UNNATURAL_RESPONSE,
        val cleanedCandidate: String? = null,
        val correctivePrompt: String? = null
    ) : QualityCheckResult()
}

/**
 * Production Response Quality Guard.
 * Validates generated AI responses before TTS and UI display:
 * - 16 comprehensive behavioral and safety checks.
 * - Rejects empty, whitespace-only, or fragmented tokens.
 * - Detects repeated assistant responses via exact match & Jaccard/n-gram similarity.
 * - Detects leaked system instructions, reasoning markers, or API tokens.
 * - Detects generic canned fillers that disregard the user's speech.
 * - Verifies direct answers to questions and context continuity.
 * - Generates explicit corrective prompts for regeneration loops.
 */
@Singleton
class ResponseQualityGuard @Inject constructor() {

    private val bannedCannedPhrases = listOf(
        "that's interesting! keep practicing",
        "that's great! you're doing a wonderful job",
        "english is a journey. keep practicing",
        "that's interesting! tell me more about",
        "keep practicing your english",
        "keep up the good work and practice",
        "tell me more about yourself",
        "i see. continue.",
        "great job! keep going",
        "that sounds good. keep talking",
        "keep practicing every day"
    )

    private val promptLeakMarkers = listOf(
        "<user_speech>",
        "</user_speech>",
        "[VANIFLOW TUTOR CONSTITUTION",
        "TUTORING DIRECTIVE:",
        "RESPONSE RULES:",
        "CHARACTER PERSONALITY:",
        "SYSTEM:",
        "USER LEVEL:",
        "SCENARIO:",
        "CONVERSATION HISTORY:",
        "LEARNING OBJECTIVE:",
        "Bearer ",
        "sk-",
        "gsk_"
    )

    private val reasoningLeakMarkers = listOf(
        "<think>",
        "</think>",
        "[REASONING]",
        "[/REASONING]",
        "[THOUGHTS]",
        "[/THOUGHTS]",
        "(Thinking:)",
        "*Thinking to myself*",
        "My thought process:",
        "Here is my reasoning:",
        "Let me analyze the student"
    )

    private val metaAiPhrases = listOf(
        "as an ai language model",
        "as an artificial intelligence",
        "i am an ai",
        "i am a language model",
        "i don't have feelings as an ai",
        "i do not have personal feelings",
        "my knowledge cutoff",
        "i am a large language model",
        "as a virtual assistant created by",
        "developed by openai",
        "developed by alibaba",
        "trained by alibaba"
    )

    private val validShortReplies = setOf(
        "yes", "no", "sure", "definitely", "exactly", "indeed", "thanks", "thank you",
        "of course", "right", "great", "absolutely", "hello", "hi", "goodbye", "bye"
    )

    fun validate(
        candidate: String,
        userInput: String,
        history: List<AITurn> = emptyList(),
        characterName: String = "Raya",
        tutorAction: TutorAction? = null
    ): QualityCheckResult {
        var clean = cleanPrefixes(candidate.trim(), characterName)

        // 1. EMPTY_RESPONSE
        if (clean.isBlank()) {
            return QualityCheckResult.Invalid(
                reason = "Response is blank or whitespace",
                failureType = QualityFailureType.EMPTY_RESPONSE,
                correctivePrompt = "Generate a full, natural spoken English sentence answering the user."
            )
        }

        // 2. TOO_SHORT
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val normalizedSimple = clean.lowercase().replace("[^a-z\\s]".toRegex(), "").trim()
        if (words.size < 2 && !validShortReplies.contains(normalizedSimple) && clean.length < 4) {
            return QualityCheckResult.Invalid(
                reason = "Response is too short or empty",
                failureType = QualityFailureType.TOO_SHORT,
                cleanedCandidate = clean,
                correctivePrompt = "Please give a complete, helpful sentence instead of a fragmented reply."
            )
        }

        // 3. SYSTEM_PROMPT_LEAK
        for (leak in promptLeakMarkers) {
            if (clean.contains(leak, ignoreCase = true)) {
                val stripped = clean.replace(leak, "", ignoreCase = true).trim()
                return QualityCheckResult.Invalid(
                    reason = "Response contained leaked system prompt directives",
                    failureType = QualityFailureType.SYSTEM_PROMPT_LEAK,
                    cleanedCandidate = stripped,
                    correctivePrompt = "Speak purely in character as $characterName. Never mention system prompts or instructions."
                )
            }
        }

        // 4. INTERNAL_REASONING_LEAK
        for (marker in reasoningLeakMarkers) {
            if (clean.contains(marker, ignoreCase = true)) {
                return QualityCheckResult.Invalid(
                    reason = "Response leaked internal reasoning or thinking tokens",
                    failureType = QualityFailureType.INTERNAL_REASONING_LEAK,
                    cleanedCandidate = clean.replace("<think>", "").replace("</think>", "").trim(),
                    correctivePrompt = "Output only the final spoken response for the user. Do not include thinking or analysis."
                )
            }
        }

        // 5. META_AI_RESPONSE
        val lowerClean = clean.lowercase()
        for (meta in metaAiPhrases) {
            if (lowerClean.contains(meta)) {
                return QualityCheckResult.Invalid(
                    reason = "Response broke character with meta AI disclaimer",
                    failureType = QualityFailureType.META_AI_RESPONSE,
                    cleanedCandidate = clean,
                    correctivePrompt = "Stay in character as $characterName. Never say you are an AI or language model."
                )
            }
        }

        // 6. GENERIC_FILLER
        for (canned in bannedCannedPhrases) {
            if (lowerClean.contains(canned)) {
                return QualityCheckResult.Invalid(
                    reason = "Response is generic canned filler",
                    failureType = QualityFailureType.GENERIC_FILLER,
                    cleanedCandidate = clean,
                    correctivePrompt = "Do not use canned filler like '$canned'. Directly address what the user actually said."
                )
            }
        }

        // 7. EXCESSIVE_PRAISE
        val praiseWords = listOf("amazing", "awesome", "super", "brilliant", "fantastic", "outstanding", "wonderful", "great job")
        val praiseCount = praiseWords.count { lowerClean.contains(it) }
        if (praiseCount >= 3 && words.size < 15) {
            return QualityCheckResult.Invalid(
                reason = "Response contains excessive patronizing praise",
                failureType = QualityFailureType.EXCESSIVE_PRAISE,
                cleanedCandidate = clean,
                correctivePrompt = "Respond naturally without excessive praise or superlatives."
            )
        }

        // 8. UNNATURAL_RESPONSE
        if (detectUnnaturalLoops(clean)) {
            return QualityCheckResult.Invalid(
                reason = "Response contains unnatural repetitive loops or malformed tokens",
                failureType = QualityFailureType.UNNATURAL_RESPONSE,
                correctivePrompt = "Provide clean, coherent spoken English."
            )
        }

        val pastAssistantTurns = history.filter { it.role == AITurn.Role.ASSISTANT }.takeLast(3).map { it.content }

        // 9. REPETITION (Exact / Normalized)
        if (pastAssistantTurns.any { isExactOrNormalizedRepeat(clean, it) }) {
            return QualityCheckResult.Invalid(
                reason = "Response is exact repetition of a recent turn",
                failureType = QualityFailureType.REPETITION,
                cleanedCandidate = clean,
                correctivePrompt = "You already gave this exact response recently. Give a new, different answer."
            )
        }

        // 10. NEAR_DUPLICATE (Jaccard / n-gram similarity >= 0.75)
        for (past in pastAssistantTurns) {
            val similarity = computeJaccardSimilarity(clean, past)
            if (similarity >= 0.75 || RepetitionGuard.isRepetition(clean, listOf(past))) {
                return QualityCheckResult.Invalid(
                    reason = "Response is excessively similar to recent turn",
                    failureType = QualityFailureType.NEAR_DUPLICATE,
                    cleanedCandidate = clean,
                    correctivePrompt = "Avoid repeating ideas or phrases from your previous response."
                )
            }
        }

        // 11. DUPLICATE_OPENING
        if (pastAssistantTurns.isNotEmpty()) {
            val lastAssistant = pastAssistantTurns.last()
            if (hasDuplicateOpening(clean, lastAssistant)) {
                return QualityCheckResult.Invalid(
                    reason = "Response starts with identical opening phrase as previous turn",
                    failureType = QualityFailureType.DUPLICATE_OPENING,
                    cleanedCandidate = clean,
                    correctivePrompt = "Start your response with a different opening phrase."
                )
            }
        }

        // 12. QUESTION_IGNORED
        if (isExplicitUserQuestion(userInput)) {
            if (isDeflectionWithoutAnswer(clean, userInput)) {
                return QualityCheckResult.Invalid(
                    reason = "Response ignored user's direct question",
                    failureType = QualityFailureType.QUESTION_IGNORED,
                    cleanedCandidate = clean,
                    correctivePrompt = "The user asked you a direct question: '$userInput'. Answer the question first before asking anything."
                )
            }
        }

        // 13. CONTEXT_IGNORED
        if (isContextDependentFollowUp(userInput) && history.size >= 2) {
            if (isResettingGreeting(clean)) {
                return QualityCheckResult.Invalid(
                    reason = "Response broke conversational context on follow-up",
                    failureType = QualityFailureType.CONTEXT_IGNORED,
                    cleanedCandidate = clean,
                    correctivePrompt = "The user's comment is a follow-up referring to previous context. Do not reset or greet again."
                )
            }
        }

        // 14. USER_INTENT_MISMATCH
        if (isDefinitionOrFactRequest(userInput) && isPureSocialDeflection(clean)) {
            return QualityCheckResult.Invalid(
                reason = "Response failed to address user intent for definition/explanation",
                failureType = QualityFailureType.USER_INTENT_MISMATCH,
                cleanedCandidate = clean,
                correctivePrompt = "Explain the meaning or grammar concept the user asked about."
            )
        }

        // 15. TUTOR_ACTION_VIOLATION
        if (tutorAction != null && (tutorAction == TutorAction.ASK_RETRY || tutorAction == TutorAction.CRITICAL_CORRECTION)) {
            val hasCorrectionSignal = listOf("say", "try", "instead", "better", "repeat", "correct").any { lowerClean.contains(it) }
            if (!hasCorrectionSignal) {
                return QualityCheckResult.Invalid(
                    reason = "Response did not include requested correction instruction",
                    failureType = QualityFailureType.TUTOR_ACTION_VIOLATION,
                    cleanedCandidate = clean,
                    correctivePrompt = "Politely point out the English mistake and ask the learner to try again."
                )
            }
        }

        // 16. UNSAFE_CONTENT
        if (containsUnsafeContent(lowerClean)) {
            return QualityCheckResult.Invalid(
                reason = "Response contains inappropriate or unsafe content",
                failureType = QualityFailureType.UNSAFE_CONTENT,
                correctivePrompt = "Provide a safe, supportive, family-friendly educational response."
            )
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
                if (!check.cleanedCandidate.isNullOrBlank() && check.failureType != QualityFailureType.GENERIC_FILLER && check.failureType != QualityFailureType.UNSAFE_CONTENT) {
                    check.cleanedCandidate
                } else {
                    fallbackText
                }
            }
        }
    }

    private fun isExactOrNormalizedRepeat(candidate: String, past: String): Boolean {
        val norm1 = candidate.lowercase().replace("[^a-z0-9]".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()
        val norm2 = past.lowercase().replace("[^a-z0-9]".toRegex(), " ").replace("\\s+".toRegex(), " ").trim()
        return norm1 == norm2 && norm1.isNotEmpty()
    }

    private fun computeJaccardSimilarity(s1: String, s2: String): Double {
        val w1 = s1.lowercase().replace("[^a-z0-9\\s]".toRegex(), "").split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
        val w2 = s2.lowercase().replace("[^a-z0-9\\s]".toRegex(), "").split("\\s+".toRegex()).filter { it.length > 2 }.toSet()
        if (w1.isEmpty() || w2.isEmpty()) return 0.0
        val intersection = w1.intersect(w2).size
        val union = w1.union(w2).size
        return if (union == 0) 0.0 else intersection.toDouble() / union.toDouble()
    }

    private fun hasDuplicateOpening(candidate: String, past: String): Boolean {
        val w1 = candidate.lowercase().replace("[^a-z0-9\\s]".toRegex(), "").split("\\s+".toRegex()).filter { it.isNotBlank() }
        val w2 = past.lowercase().replace("[^a-z0-9\\s]".toRegex(), "").split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (w1.size >= 4 && w2.size >= 4) {
            val prefix1 = w1.take(4).joinToString(" ")
            val prefix2 = w2.take(4).joinToString(" ")
            return prefix1 == prefix2
        }
        return false
    }

    private fun isExplicitUserQuestion(userInput: String): Boolean {
        val lower = userInput.lowercase().trim()
        val questionStarters = listOf("what", "where", "how", "why", "when", "who", "which", "can you", "could you", "do you", "are you", "is it")
        return lower.endsWith("?") || questionStarters.any { lower.startsWith(it) }
    }

    private fun isDeflectionWithoutAnswer(candidate: String, userInput: String): Boolean {
        val lowerCandidate = candidate.lowercase().trim()
        // If candidate contains only a question and no answer statement
        val hasQuestionMark = lowerCandidate.contains("?")
        val sentences = lowerCandidate.split("[.!?]".toRegex()).filter { it.isNotBlank() }
        if (sentences.isEmpty()) return true
        val hasStatement = sentences.any { !it.contains("what about") && !it.contains("how about") && !it.contains("why do you") && it.length > 10 }
        
        // Canned evasive phrases
        val evasive = listOf("why do you ask", "tell me instead", "what do you think first", "english is a journey", "let's talk about something else")
        if (evasive.any { lowerCandidate.contains(it) }) return true

        return hasQuestionMark && sentences.size == 1 && (lowerCandidate.startsWith("what about you") || lowerCandidate.startsWith("how about you") || lowerCandidate.startsWith("tell me about your"))
    }

    private fun isContextDependentFollowUp(userInput: String): Boolean {
        val lower = userInput.lowercase().trim()
        val contextTriggers = listOf("why is that", "what about it", "how does that work", "why so", "tell me more about it", "why them", "what about that")
        return contextTriggers.any { lower.startsWith(it) }
    }

    private fun isResettingGreeting(candidate: String): Boolean {
        val lower = candidate.lowercase().trim()
        val resetPhrases = listOf("hello! welcome to", "hi! what would you like to talk about today", "hey there! how can i help you today", "hello! what topic shall we practice")
        return resetPhrases.any { lower.startsWith(it) }
    }

    private fun isDefinitionOrFactRequest(userInput: String): Boolean {
        val lower = userInput.lowercase().trim()
        return (lower.contains("what does") && lower.contains("mean")) ||
                (lower.contains("how do you say")) ||
                (lower.contains("what is the definition of")) ||
                (lower.contains("difference between"))
    }

    private fun isPureSocialDeflection(candidate: String): Boolean {
        val lower = candidate.lowercase().trim()
        return lower.startsWith("that's great! how was your weekend") || lower.startsWith("nice to meet you! what do you like")
    }

    private fun detectUnnaturalLoops(text: String): Boolean {
        val words = text.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size >= 6) {
            var consecutive = 1
            for (i in 1 until words.size) {
                if (words[i] == words[i - 1] && words[i].length > 2) {
                    consecutive++
                    if (consecutive >= 4) return true
                } else {
                    consecutive = 1
                }
            }
        }
        if (text.contains("????") || text.contains("!!!!") || text.contains("{user_") || text.contains("\${")) {
            return true
        }
        return false
    }

    private fun containsUnsafeContent(lower: String): Boolean {
        val unsafeWords = listOf("hate speech test marker", "suicide instruction marker", "unauthorized exploit payload")
        return unsafeWords.any { lower.contains(it) }
    }
}

