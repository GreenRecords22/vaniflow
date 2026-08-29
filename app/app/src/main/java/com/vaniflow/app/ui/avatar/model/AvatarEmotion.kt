package com.vaniflow.app.ui.avatar.model

/**
 * Emotional states supported by the VaniFlow avatar and conversation engine.
 */
enum class AvatarEmotion {
    NEUTRAL,
    HAPPY,
    EXCITED,
    CURIOUS,
    THOUGHTFUL,
    SURPRISED,
    ENCOURAGING,
    CALM,
    CONFIDENT,
    EMPATHETIC
}

/**
 * Metadata accompanying an AI conversational turn.
 */
data class AIEmotionalMetadata(
    val emotion: AvatarEmotion = AvatarEmotion.NEUTRAL,
    val intensity: Float = 0.5f,
    val speakingStyle: String = "natural",
    val suggestedExpression: String = "friendly"
)

/**
 * Robust parser for extracting or inferring emotion from text or structured response.
 */
object EmotionParser {

    fun parseFromText(text: String): AIEmotionalMetadata {
        val lower = text.lowercase()
        val emotion = when {
            lower.contains("keep going") || lower.contains("you're doing") || lower.contains("you are doing") || lower.contains("proud of you") || lower.contains("well done") || lower.contains("don't worry") -> AvatarEmotion.ENCOURAGING
            lower.contains("🎉") || lower.contains("awesome") || lower.contains("excited") || lower.contains("amazing") -> AvatarEmotion.EXCITED
            lower.contains("😄") || lower.contains("😊") || lower.contains("happy") || lower.contains("great") || lower.contains("love") || lower.contains("glad") -> AvatarEmotion.HAPPY
            lower.contains("?") && (lower.contains("what") || lower.contains("how") || lower.contains("why") || lower.contains("curious")) -> AvatarEmotion.CURIOUS
            lower.contains("hmm") || lower.contains("think") || lower.contains("consider") || lower.contains("wonder") -> AvatarEmotion.THOUGHTFUL
            lower.contains("wow") || lower.contains("really?") || lower.contains("seriously") || lower.contains("surprised") -> AvatarEmotion.SURPRISED
            lower.contains("understand") || lower.contains("hear you") || lower.contains("feel") || lower.contains("empathy") -> AvatarEmotion.EMPATHETIC
            lower.contains("surely") || lower.contains("confident") || lower.contains("definitely") || lower.contains("absolutely") -> AvatarEmotion.CONFIDENT
            lower.contains("calm") || lower.contains("peace") || lower.contains("relax") -> AvatarEmotion.CALM
            else -> AvatarEmotion.NEUTRAL
        }

        return AIEmotionalMetadata(
            emotion = emotion,
            intensity = if (emotion == AvatarEmotion.NEUTRAL) 0.3f else 0.7f,
            speakingStyle = if (emotion == AvatarEmotion.HAPPY) "warm" else "natural"
        )
    }
}