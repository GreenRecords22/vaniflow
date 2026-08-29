package com.vaniflow.app.engine.ai.memory

import com.vaniflow.app.engine.ai.AITurn
import com.vaniflow.app.engine.ai.ContextManager
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compact structured representation of conversation memory (<500 tokens).
 */
data class CompactConversationContext(
    val rollingTurns: List<AITurn>,
    val userFacts: List<String>,
    val userPreferences: List<String>,
    val currentTopic: String,
    val previousTopic: String?,
    val summary: String,
    val estimatedTokens: Int
)

/**
 * Smart conversation memory manager.
 * Maintains rolling recent turns, user facts, preferences, evolving summaries,
 * and enables direct memory recall without unnecessary API requests.
 */
@Singleton
class ConversationMemoryManager @Inject constructor() {

    private val turns = Collections.synchronizedList(mutableListOf<AITurn>())
    private val keyFacts = Collections.synchronizedList(mutableListOf<String>())
    private val userPreferences = Collections.synchronizedList(mutableListOf<String>())
    private var summary: String = ""
    private var currentTopic: String = "General English Conversation"
    private var previousTopic: String? = null
    private val maxRollingTurns: Int = 8

    fun addTurn(speaker: String, text: String) {
        val role = if (speaker.equals("user", ignoreCase = true) || speaker.equals("learner", ignoreCase = true)) AITurn.Role.USER else AITurn.Role.ASSISTANT
        turns.add(AITurn(role = role, content = text))
        updateTopic(text)
        if (role == AITurn.Role.USER) {
            extractKeyFacts(text)
        }
        if (turns.size > maxRollingTurns * 2) {
            condenseMemory()
        }
    }

    private fun extractKeyFacts(text: String) {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()

        // Questions asked by user do not represent stated user facts/preferences
        if (trimmed.endsWith("?") || lower.startsWith("what") || lower.startsWith("who") || lower.startsWith("where") || lower.startsWith("how") || lower.startsWith("why") || lower.startsWith("do you") || lower.startsWith("can you")) {
            return
        }

        // 1. User Name Extraction
        val nameMatch = Regex("(?:my name is|i am|i'm)\\s+([a-zA-Z]+)", RegexOption.IGNORE_CASE).find(text)
        if (nameMatch != null) {
            val name = nameMatch.groupValues[1].replaceFirstChar { it.uppercase() }
            if (name !in listOf("A", "The", "Here", "Ready", "Good", "Fine", "Tired", "Excited")) {
                addFact("User name is $name")
            }
        }

        // 2. Preferences & Favorites
        if (lower.contains("favorite") || lower.contains("favourite") || lower.contains("love") || lower.contains("like")) {
            val pref = text.take(70).trim()
            addPreference("User likes: $pref")
            addFact("User shared preference: $pref")
        }

        // 3. Life / Travel / Work Context
        if (lower.contains("went to") || lower.contains("visited") || lower.contains("live in") || lower.contains("from") || lower.contains("work as") || lower.contains("going to")) {
            val fact = text.take(70).trim()
            addFact("User context: $fact")
        }
    }

    private fun addFact(fact: String) {
        synchronized(keyFacts) {
            if (!keyFacts.contains(fact)) {
                if (keyFacts.size >= 8) {
                    keyFacts.removeAt(0)
                }
                keyFacts.add(fact)
            }
        }
    }

    private fun addPreference(pref: String) {
        synchronized(userPreferences) {
            if (!userPreferences.contains(pref)) {
                if (userPreferences.size >= 8) {
                    userPreferences.removeAt(0)
                }
                userPreferences.add(pref)
            }
        }
    }

    private fun updateTopic(text: String) {
        val lower = text.lowercase()
        val detected = when {
            lower.contains("coffee") || lower.contains("food") || lower.contains("dinner") || lower.contains("eat") || lower.contains("biryani") || lower.contains("dosa") -> "Food & Dining"
            lower.contains("travel") || lower.contains("airport") || lower.contains("flight") || lower.contains("trip") || lower.contains("jaipur") || lower.contains("mountain") || lower.contains("goa") -> "Travel & Exploration"
            lower.contains("job") || lower.contains("interview") || lower.contains("work") || lower.contains("project") || lower.contains("career") -> "Career & Workplace"
            lower.contains("weekend") || lower.contains("hobby") || lower.contains("music") || lower.contains("movie") || lower.contains("bored") || lower.contains("cricket") -> "Hobbies & Free Time"
            else -> null
        }

        if (detected != null && detected != currentTopic) {
            previousTopic = currentTopic
            currentTopic = detected
        }
    }

    private fun condenseMemory() {
        if (turns.size > maxRollingTurns) {
            val oldTurns = turns.take(turns.size - maxRollingTurns)
            val condensed = oldTurns.joinToString("; ") { "${it.role}: ${it.content.take(50)}" }
            summary = if (summary.isBlank()) {
                "Previous topic: $currentTopic ($condensed)"
            } else {
                "${summary.take(180)}... then discussed $currentTopic"
            }
            while (turns.size > maxRollingTurns) {
                turns.removeAt(0)
            }
        }
    }

    /**
     * Checks if a user's question can be directly answered from session memory
     * without making an external AI API request.
     */
    fun findMemoryAnswer(userInput: String): String? {
        val lower = userInput.lowercase().trim()

        // 1. Food / preference recall
        if ((lower.contains("food") || lower.contains("preference")) && (lower.contains("i liked") || lower.contains("i said") || lower.contains("i told") || lower.contains("preference") || lower.contains("what food do i like"))) {
            val foundPref = synchronized(userPreferences) {
                userPreferences.lastOrNull { it.contains("food", true) || it.contains("like", true) || it.contains("eat", true) || it.contains("dosa", true) || it.contains("biryani", true) || it.contains("paneer", true) }
            }
            if (foundPref != null) {
                val clean = foundPref.replace("User likes:", "").trim()
                return "You mentioned earlier: \"$clean\"! I remember that 😄."
            }
        }

        // 2. Name recall
        if (lower.contains("what is my name") || lower.contains("what's my name") || lower.contains("do you remember my name")) {
            val nameFact = synchronized(keyFacts) {
                keyFacts.firstOrNull { it.startsWith("User name is") }
            }
            if (nameFact != null) {
                val name = nameFact.substringAfter("User name is").trim()
                return "Your name is $name! Of course I remember 🙂."
            }
        }

        // 3. Travel / places recall
        if ((lower.contains("where did i") || lower.contains("where i visited") || lower.contains("place i told")) && (lower.contains("go") || lower.contains("visit") || lower.contains("travel"))) {
            val travelFact = synchronized(keyFacts) {
                keyFacts.lastOrNull { it.contains("went to", true) || it.contains("visited", true) || it.contains("live in", true) }
            }
            if (travelFact != null) {
                val clean = travelFact.replace("User context:", "").trim()
                return "You told me earlier that $clean! That sounds memorable."
            }
        }

        // 4. Topic / conversation memory recall
        if (lower.contains("what were we talking about") || lower.contains("what was our topic") || lower.contains("do you remember what we were talking about") || lower.contains("what we were discussing")) {
            val summaryText = if (summary.isNotBlank()) " Earlier we discussed: $summary." else ""
            return "We were talking about $currentTopic!$summaryText Let's keep exploring that 😄."
        }

        return null
    }

    fun getCompactContext(): CompactConversationContext {
        val rolling = getRollingTurns()
        val facts = getKeyFacts()
        val prefs = synchronized(userPreferences) { userPreferences.toList() }
        val tokens = estimateMemoryTokens()
        return CompactConversationContext(
            rollingTurns = rolling,
            userFacts = facts,
            userPreferences = prefs,
            currentTopic = currentTopic,
            previousTopic = previousTopic,
            summary = summary,
            estimatedTokens = tokens
        )
    }

    fun getRollingTurns(): List<AITurn> = synchronized(turns) { turns.toList() }

    fun getKeyFacts(): List<String> = synchronized(keyFacts) { keyFacts.toList() }

    fun getPreferences(): List<String> = synchronized(userPreferences) { userPreferences.toList() }

    fun getSummary(): String = summary

    fun getCurrentTopic(): String = currentTopic

    fun getPreviousTopic(): String? = previousTopic

    fun buildMemoryPrompt(baseSystemPrompt: String): String {
        return buildString {
            append(baseSystemPrompt.trim())
            if (keyFacts.isNotEmpty()) {
                append("\n[Known User Facts: ${keyFacts.joinToString("; ")}]")
            }
            if (userPreferences.isNotEmpty()) {
                append("\n[Known Preferences: ${userPreferences.joinToString("; ")}]")
            }
            if (summary.isNotBlank()) {
                append("\n[Context Summary: $summary]")
            }
            append("\n[Current Topic: $currentTopic]")
        }
    }

    fun estimateMemoryTokens(): Int {
        val promptTokens = ContextManager.estimateTokenCount(summary) +
                ContextManager.estimateTokenCount(currentTopic) +
                keyFacts.sumOf { ContextManager.estimateTokenCount(it) } +
                userPreferences.sumOf { ContextManager.estimateTokenCount(it) }
        val turnsTokens = synchronized(turns) {
            turns.sumOf { ContextManager.estimateTokenCount(it.content) }
        }
        return promptTokens + turnsTokens
    }

    fun clear() {
        turns.clear()
        keyFacts.clear()
        userPreferences.clear()
        summary = ""
        currentTopic = "General English Conversation"
        previousTopic = null
    }
}