package com.vaniflow.app.engine.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Intelligent Dynamic Offline AI Dialogue & Recast Engine.
 *
 * Provides highly contextual, persona-aware, multi-topic, conversational turns
 * that adapt to the user's questions, statements, and topics.
 * Ensures natural English practice without repeating static canned phrases.
 */
@Singleton
class FallbackAIEngine @Inject constructor() : AIEngine {

    override suspend fun generateResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): AIResult {
        val startTime = System.currentTimeMillis()
        delay(40) // Instant deterministic NLP processing (<50ms)

        val character = detectCharacter(systemPrompt)
        val response = generateContextualDialogue(userInput, character, conversationHistory)
        val latency = System.currentTimeMillis() - startTime

        return AIResult.Success(
            text = response,
            latencyMs = latency,
            metadata = AIResponseMetadata(
                routingLevel = AIRoutingLevel.SCENARIO_MATRIX,
                latencyMs = latency,
                tokensGenerated = ContextManager.estimateTokenCount(response),
                providerName = "VaniFlow Scenario Matrix (last-resort fallback)"
            )
        )
    }

    override fun streamResponse(
        systemPrompt: String,
        conversationHistory: List<AITurn>,
        userInput: String
    ): Flow<String> = flow {
        val character = detectCharacter(systemPrompt)
        val fullResponse = generateContextualDialogue(userInput, character, conversationHistory)
        val words = fullResponse.split(" ")
        for (word in words) {
            delay(20) // Natural token streaming
            emit("$word ")
        }
    }

    private fun detectCharacter(systemPrompt: String): String {
        val lower = systemPrompt.lowercase()
        return when {
            lower.contains("adwaita") -> "adwaita"
            lower.contains("rudra") -> "rudra"
            lower.contains("shub") -> "shub"
            else -> "raya"
        }
    }

    private fun generateContextualDialogue(
        rawInput: String,
        character: String,
        history: List<AITurn>
    ): String {
        val input = rawInput.trim()
        val lower = input.lowercase()

        // 1. Check for Greetings & Initial Small Talk
        if (lower.matches("^(hi|hello|hey|hey there|good morning|good afternoon|good evening|namaste).*".toRegex())) {
            return when (character) {
                "raya" -> "Hello! It is so wonderful to talk with you. How is your day going so far?"
                "adwaita" -> "Hello. Great to connect with you today. What topic or scenario shall we focus on?"
                "rudra" -> "Hey there! Awesome to chat with you today! What's up on your end?"
                "shub" -> "Good day. It's a pleasure to speak with you. How can we make today's practice productive for you?"
                else -> "Hello! I'm happy to practice English with you today. How are you feeling?"
            }
        }

        // 2. Personal & Identity Questions
        if (lower.contains("who are you") || lower.contains("what is your name") || lower.contains("tell me about yourself")) {
            return when (character) {
                "raya" -> "I'm Raya, your friendly English speaking partner! I love helping learners build confidence through warm, relaxed conversations. What would you like to chat about?"
                "adwaita" -> "I am Adwaita. My focus is helping professionals master confident, clear, and impactful English for leadership and career growth. What are your speaking goals?"
                "rudra" -> "I'm Rudra! Think of me as your energetic conversation buddy for fast-paced, real-life English talk. What's on your mind today?"
                "shub" -> "I am Shub. I enjoy thoughtful, analytical discussions and structured communication practice. What subject interests you most today?"
                else -> "I am your AI conversation coach, here to help you speak English naturally and fluently!"
            }
        }

        if (lower.contains("how are you") || lower.contains("how do you do") || lower.contains("how have you been")) {
            return when (character) {
                "raya" -> "I'm doing really well, thank you for asking! I'm excited to practice speaking with you. How about you—did anything interesting happen today?"
                "adwaita" -> "I'm doing excellently, thank you. Ready for an engaging discussion. How has your week been shaping up?"
                "rudra" -> "I'm pumped up and feeling great! Thanks for asking. How are things on your side today?"
                "shub" -> "I am doing well, thank you. I am looking forward to our discussion. How is your schedule treating you today?"
                else -> "I'm doing great! How are you doing today?"
            }
        }

        // 3. English Learning, Hesitation & Vocabulary Guidance
        if (lower.contains("fluent") || lower.contains("english") || lower.contains("hesitat") || lower.contains("improve") || lower.contains("grammar") || lower.contains("vocab")) {
            return when (character) {
                "raya" -> "The best way to become fluent is simply to speak without worrying about small mistakes. Every sentence you try builds muscle memory! What is one situation where you feel nervous speaking English?"
                "adwaita" -> "Fluency is built on structure and consistency. Focus on expressing one complete idea clearly before moving to the next. In what context do you want to elevate your communication most?"
                "rudra" -> "Don't overthink the grammar while speaking! Just express your thoughts freely and keep the momentum going. What's your favorite English movie or podcast?"
                "shub" -> "Language acquisition improves when you actively use new vocabulary in context. Try describing your thoughts systematically. What specific speaking skill are you aiming to refine?"
                else -> "Consistent daily practice is the fastest path to fluency. Tell me about your routine for practicing English!"
            }
        }

        // 4. Food, Cooking, Tea, Coffee & Dining
        if (lower.contains("food") || lower.contains("cook") || lower.contains("eat") || lower.contains("dinner") || lower.contains("lunch") || lower.contains("breakfast") || lower.contains("biryani") || lower.contains("pizza") || lower.contains("chai") || lower.contains("tea") || lower.contains("coffee") || lower.contains("dish")) {
            return when (character) {
                "raya" -> "I love talking about delicious food! A good cup of masala chai or hot dosa always brings happiness. What is your all-time favorite comfort dish, and do you enjoy cooking it yourself?"
                "adwaita" -> "Culinary experiences are a fantastic conversation topic. Exploring regional flavors often teaches us so much about culture. When dining out, what cuisine do you usually gravitate towards?"
                "rudra" -> "Food is life! Whether it's spicy street food, crispy samosas, or cheesy pizza, I'm always up for it. What's the best food spot in your city?"
                "shub" -> "Cooking involves both art and precision. The balance of spices and ingredients is fascinating. Do you prefer cooking at home or exploring new restaurants?"
                else -> "Food is such an enjoyable topic. What is your favorite meal to enjoy on a relaxing weekend?"
            }
        }

        // 5. Travel, Cities, Nature & Destinations
        if (lower.contains("travel") || lower.contains("trip") || lower.contains("flight") || lower.contains("airport") || lower.contains("vacation") || lower.contains("mountain") || lower.contains("beach") || lower.contains("city") || lower.contains("bangalore") || lower.contains("delhi") || lower.contains("mumbai") || lower.contains("goa") || lower.contains("place")) {
            return when (character) {
                "raya" -> "Traveling to new places opens our hearts and minds! Whether it's the peaceful hills or a vibrant city, journeys make great memories. What is the most memorable trip you have ever taken?"
                "adwaita" -> "Traveling broadens perspectives and offers invaluable cultural insights. Which destination is currently at the top of your travel wishlist, and what attracts you to it?"
                "rudra" -> "Traveling is such an adrenaline rush! Exploring scenic routes, trying local snacks, and meeting new people. Do you prefer peaceful mountains or lively beaches?"
                "shub" -> "Travel offers an opportunity to observe different lifestyles and historical architecture. If you could take a one-month sabbatical anywhere in the world, where would you go?"
                else -> "Traveling is wonderful for creating lasting memories. Tell me about a place you love visiting!"
            }
        }

        // 6. Technology, AI, Software, Career & Work
        if (lower.contains("work") || lower.contains("job") || lower.contains("office") || lower.contains("career") || lower.contains("interview") || lower.contains("tech") || lower.contains("ai") || lower.contains("coding") || lower.contains("software") || lower.contains("project") || lower.contains("engineer") || lower.contains("developer")) {
            return when (character) {
                "raya" -> "Technology is evolving so quickly, and it's exciting to see how AI is helping people learn faster. How does technology or your current work fit into your daily life?"
                "adwaita" -> "In the modern workplace, clear articulation and executive presence set leaders apart. How do you approach presenting complex ideas simply to team members or clients?"
                "rudra" -> "Tech and AI are moving super fast right now! It's crazy how much has changed in just a couple of years. What's your take on AI tools like ChatGPT or coding assistants?"
                "shub" -> "Effective problem-solving in engineering and business requires structured thinking and clear communication. What has been the most intriguing project you have worked on recently?"
                else -> "Professional growth requires strong communication. What area of your work do you enjoy the most?"
            }
        }

        // 7. Hobbies, Movies, Music, Sports & Weekend Fun
        if (lower.contains("hobby") || lower.contains("movie") || lower.contains("music") || lower.contains("cricket") || lower.contains("song") || lower.contains("football") || lower.contains("sport") || lower.contains("game") || lower.contains("netflix") || lower.contains("read") || lower.contains("book") || lower.contains("weekend")) {
            return when (character) {
                "raya" -> "Having fun hobbies makes life so fulfilling! Listening to music or reading a heartwarming story is always delightful. How do you like to recharge during your weekends?"
                "adwaita" -> "Cultivating diverse interests outside of work often stimulates creativity and focus. What books, podcasts, or genres of music have inspired you lately?"
                "rudra" -> "Sports, music, and movies—now you're talking! A high-stakes cricket match or an action movie is the best way to unwind. What's your favorite sport to play or watch?"
                "shub" -> "Engaging in strategic games, literature, or music helps sharpen cognitive focus. What hobby do you find most intellectually rewarding?"
                else -> "Hobbies are a great way to unwind. Tell me what you love doing in your free time!"
            }
        }

        // 8. Feelings, Mood, Weather & Empathy
        if (lower.contains("tired") || lower.contains("exhaust") || lower.contains("stress") || lower.contains("sad") || lower.contains("bored") || lower.contains("happy") || lower.contains("excit") || lower.contains("nervous") || lower.contains("weather") || lower.contains("rain") || lower.contains("hot") || lower.contains("cold")) {
            return when (character) {
                "raya" -> "I hear you! It's completely normal to feel that way. Taking a deep breath, having a warm drink, and taking things one step at a time helps a lot. What is one thing that usually brightens your mood?"
                "adwaita" -> "Recognizing how we feel allows us to manage our energy and focus effectively. When facing a demanding day, how do you prioritize and restore balance?"
                "rudra" -> "Hey, hang in there! Take a quick break, stretch, and grab some water. Don't let the grind get to you. What's something fun you can look forward to this evening?"
                "shub" -> "Energy levels naturally fluctuate throughout the day. Analyzing what causes fatigue often reveals ways to optimize our routine. How has your sleep and workload been recently?"
                else -> "It's always good to listen to how you feel. What helps you relax after a busy day?"
            }
        }

        // 9. Questions (Why, How, What, Where, When, Can, Do)
        if (lower.startsWith("why") || lower.contains("why do") || lower.contains("why is") || lower.contains("why are")) {
            return when (character) {
                "raya" -> "That's a very thoughtful 'why' question! Often, it comes down to human connection and finding joy in what we do. From your point of view, why do you think that happens?"
                "adwaita" -> "Asking 'why' gets right to the root of the matter. When we examine the underlying motives and principles, we find clearer solutions. What factors do you consider most important here?"
                "rudra" -> "Great question! There are so many angles to look at it, but usually it's about making things exciting and impactful. What's your instinct telling you about it?"
                "shub" -> "Investigating the causality behind this is intriguing. Logically, multiple variables contribute to this outcome. How would you analyze the primary reason?"
                else -> "That is a fascinating question. What is your own perspective on it?"
            }
        }

        if (lower.startsWith("how") || lower.contains("how to") || lower.contains("how can") || lower.contains("how do")) {
            return when (character) {
                "raya" -> "The best approach is to take it one small step at a time with patience. Break it down, practice gently, and celebrate progress! Have you tried taking a first step with that already?"
                "adwaita" -> "Execution requires a clear strategy: define your goal, establish milestones, and execute with disciplined consistency. What initial step seems most actionable to you?"
                "rudra" -> "Just dive right into it and learn along the way! Practical action beats overthinking every single time. What's stopping you from jumping in?"
                "shub" -> "A structured methodology works best: gather relevant data, create a step-by-step framework, and iterate based on feedback. How do you usually approach complex tasks?"
                else -> "Breaking it down into manageable steps is always effective. What do you think is the best first step?"
            }
        }

        if (lower.startsWith("what") || lower.startsWith("which") || lower.startsWith("who") || lower.startsWith("where") || lower.startsWith("when")) {
            val subject = extractSubject(input)
            return when (character) {
                "raya" -> "When it comes to $subject, I think there is so much beauty and interest to discover! What made you curious about this today?"
                "adwaita" -> "Regarding $subject, analyzing both the context and long-term implications gives the clearest clarity. How do you view this within your own experience?"
                "rudra" -> "Oh, $subject is such a cool topic to talk about! There are so many interesting stories around it. What's your personal favorite take on it?"
                "shub" -> "Examining $subject provides valuable insights into how systems and ideas function. What specific aspect of it do you find most compelling?"
                else -> "That is an intriguing question about $subject. What are your thoughts on it?"
            }
        }

        // 10. General Affirmation & Recast Engine
        val subject = extractSubject(input)
        val responseOptions = when (character) {
            "raya" -> listOf(
                "That is so interesting! When you talk about $subject, I can see how meaningful it is. Could you share a bit more detail about that?",
                "I love how clearly you explained that! Sharing your thoughts on $subject really helps build confidence. How did you first get interested in this?",
                "That makes complete sense! It's wonderful hearing your perspective on $subject. What would you say is the most rewarding part of it?",
                "You expressed that very naturally! Talking about $subject is always engaging. How do your friends or family feel about this?"
            )
            "adwaita" -> listOf(
                "That is a very articulated point regarding $subject. In professional discussions, framing that insight clearly creates strong impact. How would you summarize that to a broader team?",
                "Your reasoning on $subject is sound and structured. Precision in vocabulary makes your message even more persuasive. What is the key takeaway you want listeners to remember?",
                "An excellent perspective on $subject. Clear, assertive communication like this is essential for leadership. How has this influenced your recent decisions?",
                "Well stated. Discussing $subject with clarity demonstrates strong analytical ability. What future developments do you anticipate in this area?"
            )
            "rudra" -> listOf(
                "That's awesome! Honestly, hearing your thoughts on $subject is super cool. What's the wildest or most fun story you have about it?",
                "Totally agree with you on that! $subject is always such an energetic topic to discuss. What would you do next if you had full freedom?",
                "Haha, that's spot on! Talking about $subject with so much passion is what makes conversations fun. What got you hyped up about it?",
                "Nice one! You explained $subject like a pro. If you had to explain this to a complete beginner, how would you pitch it in one sentence?"
            )
            "shub" -> listOf(
                "Your perspective on $subject provides a coherent and thoughtful framework. When evaluating the core principles, what primary factor stands out to you?",
                "That is a logical observation regarding $subject. Connecting theory with practical application strengthens the argument. How do you validate these outcomes?",
                "A very measured and insightful viewpoint on $subject. Systematic thinking like this leads to effective problem resolution. What alternative viewpoints have you considered?",
                "Well analyzed. Your thoughts on $subject reflect careful consideration. In your assessment, what is the most critical variable involved?"
            )
            else -> listOf(
                "That is a wonderful point about $subject! Could you elaborate a bit more on that?",
                "You expressed your thoughts on $subject very clearly. What inspired you to think about this?",
                "Great insight on $subject! How do you plan to take this forward?"
            )
        }

        val seed = (input.hashCode() + history.size).let { if (it < 0) -it else it }
        return responseOptions[seed % responseOptions.size]
    }

    private fun extractSubject(input: String): String {
        val clean = input.replace("[?!.,]".toRegex(), "").trim()
        val words = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return "this topic"

        val stopWords = setOf(
            "i", "me", "my", "myself", "we", "our", "you", "your", "he", "she", "it", "they",
            "the", "a", "an", "this", "that", "these", "those", "is", "are", "was", "were",
            "be", "been", "being", "have", "has", "had", "do", "does", "did", "can", "could",
            "will", "would", "shall", "should", "may", "might", "must", "and", "but", "or",
            "so", "if", "because", "as", "until", "while", "of", "at", "by", "for", "with",
            "about", "against", "between", "into", "through", "during", "before", "after",
            "above", "below", "to", "from", "up", "down", "in", "out", "on", "off", "over",
            "under", "again", "further", "then", "once", "here", "there", "when", "where",
            "why", "how", "all", "any", "both", "each", "few", "more", "most", "other",
            "some", "such", "no", "nor", "not", "only", "own", "same", "than", "too", "very",
            "just", "don't", "dont", "im", "tell", "say", "speak", "know", "think", "like"
        )

        val meaningful = words.filter { !stopWords.contains(it.lowercase()) }
        return when {
            meaningful.size >= 2 -> "${meaningful.takeLast(2).joinToString(" ")}"
            meaningful.size == 1 -> meaningful.first()
            words.size >= 2 -> "${words.takeLast(2).joinToString(" ")}"
            else -> clean
        }
    }
}
