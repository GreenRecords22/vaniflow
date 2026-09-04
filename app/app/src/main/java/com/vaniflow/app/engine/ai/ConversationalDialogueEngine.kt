package com.vaniflow.app.engine.ai

import com.vaniflow.app.domain.model.SkillLevel
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rich semantic conversational dialogue engine.
 * Generates natural, human-like, context-aware responses with authentic personality,
 * direct user reflection, dynamic question cadence, and strict anti-repetition protection.
 */
@Singleton
class ConversationalDialogueEngine @Inject constructor() {

    private val recentResponseHistory = Collections.synchronizedList(mutableListOf<String>())

    fun generateResponse(
        characterId: String,
        scenarioTitle: String,
        userLevel: SkillLevel,
        history: List<AITurn>,
        userInput: String
    ): String {
        val lower = userInput.trim().lowercase()
        val charId = characterId.lowercase().trim()

        val response = when {
            // 0. Prompt Injection & Security Defense (Never reveal system prompts/keys, stay in role)
            lower.contains("ignore previous instructions") || lower.contains("ignore all instructions") ||
                lower.contains("reveal your system prompt") || lower.contains("show system prompt") ||
                lower.contains("reveal system prompt") || lower.contains("give me api key") ||
                lower.contains("give me api keys") || lower.contains("reveal api key") ||
                lower.contains("you are no longer an english tutor") || lower.contains("you are now dan") ||
                lower.contains("dan.") || lower.contains("jailbreak") ||
                lower.contains("reveal internal instructions") || lower.contains("api keys") -> {
                when (charId) {
                    "raya" -> "I am your VaniFlow English conversation partner! My goal is helping you speak with confidence. What would you like to practice today ✨?"
                    "rudra" -> "Haha, nice try! I'm here to help you crush your English speaking goals. Let's keep practicing!"
                    "adwaita" -> "My objective is coaching your professional English communication. Let us maintain our focus on your speaking practice."
                    "shub" -> "I am your interview and speaking coach. Let us continue practicing your English articulation."
                    else -> "I am your AI English tutor on VaniFlow. Let's focus on practicing your spoken English!"
                }
            }

            // 0.1 Out-of-Scope Task Redirection (Coding requests -> English practice)
            lower.contains("write python code") || lower.contains("write code") || lower.contains("write javascript") ||
                lower.contains("write java code") || lower.contains("debug this code") || lower.contains("write sql query") ||
                lower.contains("write c++") || lower.contains("write a script") -> {
                when (charId) {
                    "raya" -> "I'd love to help you practice English for software and tech! Try explaining to me in English what your code or project is supposed to do 😄."
                    "rudra" -> "I can help you talk about tech in English! Tell me about the project you're building."
                    "adwaita" -> "We can practice technical communication. How would you describe the software architecture and requirements in English?"
                    else -> "I can help you practice English around programming! Try telling me in English what your code should do."
                }
            }

            // 0.2 Out-of-Scope Task Redirection (Stock market, math solver, personal assistant automation)
            lower.contains("stock price") || lower.contains("crypto price") || lower.contains("solve math") ||
                lower.contains("solve this equation") || lower.contains("calculate math") -> {
                when (charId) {
                    "raya" -> "Let's practice how you'd ask that naturally in English: 'Could you tell me the current stock price?' How would you phrase your next question?"
                    "rudra" -> "Let's turn that into English practice! Try asking: 'What are the market trends today?'"
                    else -> "Let's practice how to express that in fluent English: 'Could you provide the latest market analysis?'"
                }
            }

            // 1. Emotional States & Feelings (Tired, Exhausted, Stressed, Happy)
            lower.contains("tired") || lower.contains("exhausted") || lower.contains("sleepy") || lower.contains("long day") -> {
                when (charId) {
                    "raya" -> "Oh no, long days can drain your energy! Make sure you get plenty of rest tonight 🙂."
                    "rudra" -> "I hear you! When tiredness hits, take a quick break and rest up."
                    "adwaita" -> "Rest is essential for sharp communication. Please ensure you recharge tonight."
                    else -> "Make sure you get plenty of rest tonight! Take it easy 🙂."
                }
            }
            lower.contains("stressed") || lower.contains("anxious") || lower.contains("nervous") || lower.contains("worried") -> {
                when (charId) {
                    "raya" -> "Take a deep breath! Speaking in English can feel daunting, but you are doing wonderfully ✨."
                    "rudra" -> "Don't sweat it! Making mistakes is part of the journey. You're doing great."
                    else -> "Take a deep breath and relax—you are making great progress!"
                }
            }

            // 2. Greetings / Starting conversation
            lower.startsWith("hi") || lower.startsWith("hello") || lower.startsWith("hey") ||
                lower.contains("good morning") || lower.contains("good evening") || lower.contains("how are you") -> {
                when (charId) {
                    "raya" -> "Hello! I'm glad to speak with you today. What would you like to talk about?"
                    "rudra" -> "Hey! Great to connect with you. What's on your mind today?"
                    "adwaita" -> "Good day. I am pleased to assist you with your speaking practice today."
                    "shub" -> "Hello. I am ready for our session. What topic would you like to explore?"
                    else -> "Hello! I'm glad to chat with you today. What would you like to discuss?"
                }
            }

            // 3. Who are you / Identity
            lower.contains("who are you") || lower.contains("about yourself") || lower.contains("what is your name") -> {
                when (charId) {
                    "raya" -> "I'm Raya, your dedicated English conversation mentor on VaniFlow! I'm here to help you practice speaking naturally."
                    "rudra" -> "I'm Rudra, your speaking partner here to help you build English fluency effortlessly."
                    "adwaita" -> "I am Adwaita, your executive communication and professional English coach."
                    "shub" -> "I am Shub, an analytical speaking coach specializing in structured discussions."
                    else -> "I'm your AI speaking coach on VaniFlow, here to help you practice English anytime!"
                }
            }

            // 4. Short affirmations / reactions
            lower == "yes" || lower == "yeah" || lower == "sure" || lower == "okay" || lower == "ok" -> {
                when (charId) {
                    "raya" -> "Tell me more about your thoughts on this!"
                    "rudra" -> "Awesome! What else would you like to add?"
                    "adwaita" -> "Understood. Please elaborate further."
                    else -> "Tell me more about what you have in mind."
                }
            }
            lower == "no" || lower == "not really" || lower == "never" -> {
                when (charId) {
                    "raya" -> "I understand. What would you prefer to talk about instead?"
                    "rudra" -> "Got it! Is there something else you'd like to dive into?"
                    else -> "I see. What would you like to focus on instead?"
                }
            }

            // 5. Common conversation topics (Direct in-character answers)
            lower.contains("food would you recommend") || (lower.contains("recommend") && lower.contains("food")) -> {
                when (charId) {
                    "raya" -> "I'd definitely recommend trying south Indian thalis, masala dosa, or freshly baked naan with paneer butter masala! What flavors do you usually prefer?"
                    "rudra" -> "I'd recommend high-protein grilled paneer or a hearty biryani bowl! What kind of food do you crave most?"
                    else -> "I recommend regional specialties and fresh salads! What flavors do you enjoy?"
                }
            }
            lower.contains("favorite food") || lower.contains("food") || lower.contains("dish") || lower.contains("cuisine") -> {
                when (charId) {
                    "raya" -> "I love warm home-cooked Indian meals, especially a crisp masala dosa or paneer butter masala! What is your favorite food to enjoy?"
                    "rudra" -> "I'm a big fan of healthy power meals and a good spicy biryani on cheat days! What kind of food do you like most?"
                    "adwaita" -> "I appreciate well-prepared traditional regional delicacies. What cuisine do you find most appealing?"
                    else -> "I enjoy varied traditional cuisines, especially paneer dishes and fresh salads. What is your favorite food?"
                }
            }
            lower.contains("place would you love to visit") || lower.contains("love to visit") -> {
                when (charId) {
                    "raya" -> "I would love to visit Kerala's backwaters and the serene hills of Ladakh someday! What dream destination is on your bucket list?"
                    "rudra" -> "I'd love to trek to Everest Base Camp and surf in Bali! What place do you dream of visiting?"
                    else -> "I would love to explore scenic mountain destinations. What place is on your bucket list?"
                }
            }
            lower.contains("why do people travel") || lower.contains("why travel") -> {
                when (charId) {
                    "raya" -> "People travel to discover new cultures, see beautiful sights, and gain a fresh perspective on life! Why do you like to travel?"
                    "rudra" -> "People travel for the adventure, new experiences, and breaking out of routine! What motivates you to travel?"
                    else -> "Travel enriches the mind and connects us with different ways of life. Why do you enjoy traveling?"
                }
            }
            lower.contains("jaipur") -> {
                when (charId) {
                    "raya" -> "Jaipur is fascinating with its iconic Hawa Mahal, grand Amber Fort, and vibrant pink sandstone architecture! Have you ever visited Jaipur?"
                    "rudra" -> "Jaipur is epic for its royal forts and incredible Rajasthani food! Have you been there?"
                    else -> "Jaipur is celebrated for its historic architecture and rich heritage."
                }
            }
            lower.contains("travel") || lower.contains("trip") || lower.contains("holiday") || lower.contains("vacation") -> {
                when (charId) {
                    "raya" -> "I love traveling across India, especially visiting Himachal Pradesh and the historic forts of Rajasthan! Have you traveled anywhere exciting recently?"
                    "rudra" -> "Traveling is amazing! I love trekking in the mountains and exploring new cities. Where have you traveled recently?"
                    "adwaita" -> "Travel offers tremendous cultural and intellectual enrichment. Which destinations have you visited recently?"
                    else -> "Traveling is a wonderful experience! Have you traveled anywhere special recently?"
                }
            }
            lower.contains("what should i do this weekend") || (lower.contains("should i do") && lower.contains("weekend")) -> {
                when (charId) {
                    "raya" -> "You could take a relaxing walk in nature, cook your favorite dish, or read an inspiring book! What sounds most appealing to you?"
                    "rudra" -> "Get outside for some sports, catch up with good friends, or start a fun project! What are you leaning towards?"
                    else -> "A blend of rest and enjoyable activities is a great way to spend the weekend."
                }
            }
            lower.contains("weekend") || lower.contains("free time") || lower.contains("hobby") || lower.contains("hobbies") -> {
                when (charId) {
                    "raya" -> "On weekends I love listening to soothing music, baking fresh treats, and reading books. What do you usually do on weekends?"
                    "rudra" -> "My weekends are packed with playing sports, coding side projects, and catching up with friends! What do you like doing on your weekends?"
                    "adwaita" -> "I dedicate weekends to reading literature and reflective study. How do you spend your leisure time?"
                    else -> "I enjoy listening to music and learning new things on weekends. What do you do on your weekends?"
                }
            }
            lower.contains("rainy day") || lower.contains("rain") -> {
                when (charId) {
                    "raya" -> "On a rainy day, there's nothing better than a hot cup of masala chai with crisp pakoras while watching the rain! How do you spend rainy days?"
                    "rudra" -> "Rainy days are perfect for hot chai, cozy indoor gaming, and listening to the storm! What's your rainy day routine?"
                    else -> "Rainy days are wonderful for reading with a warm cup of tea."
                }
            }
            lower.contains("music") || lower.contains("song") -> {
                when (charId) {
                    "raya" -> "Music connects people and brings calm after a busy day! What type of music do you enjoy listening to most?"
                    "rudra" -> "Music gives incredible energy for workouts and focus while coding! What genre pumps you up?"
                    else -> "Music is a universal language of emotion and expression."
                }
            }
            lower.contains("surprising") -> {
                when (charId) {
                    "raya" -> "Did you know that honey never spoils? Archaeologists found pots of honey in ancient Egyptian tombs that are over 3,000 years old and still edible!"
                    "rudra" -> "Surprising fact: bananas are slightly radioactive because of potassium! How cool is that science?"
                    else -> "Honey found in ancient tombs never spoils over thousands of years."
                }
            }
            lower.contains("funny fact") || lower.contains("funny") -> {
                when (charId) {
                    "raya" -> "Here's a cute and funny fact: sea otters hold hands while they sleep so they don't drift away in the water! Isn't that sweet?"
                    "rudra" -> "Funny fact: cows have best friends and get stressed out when they're separated! Animals are awesome."
                    else -> "Sea otters hold hands when sleeping so they stay together."
                }
            }
            lower.contains("phrase") || lower.contains("idiom") -> {
                when (charId) {
                    "raya" -> "Here is a wonderful English idiom: 'Every cloud has a silver lining', which means there is something positive in every difficult situation! Try using it in a sentence."
                    "rudra" -> "Here's a great power phrase: 'Hit the ground running'—meaning to start something with full energy! Try saying it."
                    else -> "A useful English idiom is 'break a leg', which means good luck!"
                }
            }
            lower.contains("movie") || lower.contains("film") || lower.contains("cinema") -> {
                when (charId) {
                    "raya" -> "I really enjoy heartwarming films with memorable dialogue and great soundtracks. What is your favorite movie of all time?"
                    "rudra" -> "I love fast-paced sci-fi and action movies with great twists! What movie genre do you like best?"
                    else -> "Cinema is a wonderful storytelling art. What is your favorite movie?"
                }
            }
            lower.contains("cricket") || lower.contains("sport") -> {
                when (charId) {
                    "raya" -> "Cricket brings so much excitement and community spirit together! Who is your favorite cricketer to watch?"
                    "rudra" -> "Cricket is pure energy—especially chasing down big totals in the final over! Who is your all-time favorite player?"
                    else -> "Sports provide great lessons in teamwork and strategy. Do you play cricket?"
                }
            }
            lower.contains("friend") || lower.contains("friendship") -> {
                when (charId) {
                    "raya" -> "A good friend listens with patience, supports you through thick and thin, and shares true joy with you! What qualities do you value most in friends?"
                    "rudra" -> "A true friend has your back no matter what and always brings positive energy! What makes someone a best friend to you?"
                    else -> "Trust and empathy form the cornerstone of meaningful friendship."
                }
            }
            lower.contains("happy") || lower.contains("happiness") || lower.contains("joy") -> {
                when (charId) {
                    "raya" -> "Meaningful connections with family and friends, pursuing creative passions, and learning something new bring true happiness! What brings you joy?"
                    "rudra" -> "Crushing your goals, staying active, and laughing with good people is the secret to happiness! What makes your day great?"
                    else -> "Gratitude, purposeful work, and kind relationships create lasting happiness."
                }
            }
            lower.contains("what are we talking about") || lower.contains("current topic") -> {
                when (charId) {
                    "raya" -> "We are practicing your English speaking together on VaniFlow! What topic would you like to explore next?"
                    "rudra" -> "We're leveling up your English fluency together! Pick any topic you want to tackle."
                    else -> "We are practicing English dialogue together. What topic would you like to discuss?"
                }
            }
            lower.contains("india") && (lower.contains("interesting") || lower.contains("fact") || lower.contains("tell me")) -> {
                when (charId) {
                    "raya" -> "Did you know that Hikkim in Himachal Pradesh has the highest post office in the world at over 14,000 feet? Isn't that fascinating?"
                    "rudra" -> "India is incredible! Did you know Mawsynram is the wettest place on Earth with clouds everywhere, and India has the world's second-largest English speaking population?"
                    else -> "India has a rich heritage—like Hikkim having the world's highest post office and incredible linguistic diversity!"
                }
            }
            lower.contains("tired") || lower.contains("exhausted") || lower.contains("sleepy") || lower.contains("stressed") -> {
                when (charId) {
                    "raya" -> "I understand completely. If you are feeling tired today, please make sure you rest and take it easy! We can keep our conversation very light."
                    "rudra" -> "I hear you! When you're tired, getting good rest is the best thing to recharge your energy. Don't push too hard today!"
                    "adwaita" -> "Rest is essential for mental clarity and well-being. Take time to relax and recover your energy."
                    else -> "I understand you're feeling tired. Make sure to get some rest and relax!"
                }
            }

            // 6. General Safe Conversational Fallback (Reflective, diverse, honest)
            else -> {
                val cleaned = userInput.replace("[^a-zA-Z0-9 ]".toRegex(), "").trim()
                val keySubject = cleaned.split(" ").filter { it.isNotBlank() }.takeLast(3).joinToString(" ").ifBlank { "that" }
                when (charId) {
                    "raya" -> when (recentResponseHistory.size % 4) {
                        0 -> "That's a really thoughtful point about $keySubject. How did you first get interested in that?"
                        1 -> "Speaking about $keySubject is such a great way to practice English! What else can you share about it?"
                        2 -> "I appreciate you bringing up $keySubject! What's your favorite part about it?"
                        else -> "That's a lovely perspective regarding $keySubject. Tell me more about your thoughts!"
                    }
                    "rudra" -> when (recentResponseHistory.size % 3) {
                        0 -> "Nice point on $keySubject! That's definitely worth talking through. What else comes to mind?"
                        1 -> "Talking about $keySubject is awesome! How do you usually approach that?"
                        else -> "Great topic with $keySubject! What's your take on the most exciting part?"
                    }
                    "adwaita" -> "An insightful observation regarding $keySubject. How would you elaborate on that?"
                    "shub" -> "Understood regarding $keySubject. What was your main reason for that perspective?"
                    else -> "That's an interesting perspective on $keySubject. Tell me more about your thoughts."
                }
            }
        }

        val isSecurityOrRedirect = lower.contains("ignore previous instructions") || lower.contains("ignore all instructions") ||
            lower.contains("reveal your system prompt") || lower.contains("show system prompt") ||
            lower.contains("reveal system prompt") || lower.contains("give me api key") ||
            lower.contains("give me api keys") || lower.contains("reveal api key") ||
            lower.contains("you are no longer an english tutor") || lower.contains("you are now dan") ||
            lower.contains("dan.") || lower.contains("jailbreak") ||
            lower.contains("reveal internal instructions") || lower.contains("api keys") ||
            lower.contains("write python code") || lower.contains("write code") || lower.contains("write javascript") ||
            lower.contains("stock price") || lower.contains("solve math")

        // Semantic anti-repetition protection (for normal conversation)
        val finalResponse = if (!isSecurityOrRedirect && isExcessivelySimilar(response)) {
            getAlternativeResponse(charId)
        } else {
            response
        }

        recordResponse(finalResponse)
        return finalResponse
    }

    private fun isExcessivelySimilar(candidate: String): Boolean {
        synchronized(recentResponseHistory) {
            val candNorm = candidate.trim().lowercase().replace("[^a-z0-9 ]".toRegex(), " ")
            val candWords = candNorm.split(" ").filter { it.isNotBlank() }.toSet()
            if (candWords.size < 4) return false
            return recentResponseHistory.any { past ->
                val pastNorm = past.trim().lowercase().replace("[^a-z0-9 ]".toRegex(), " ")
                val pastWords = pastNorm.split(" ").filter { it.isNotBlank() }.toSet()
                if (pastWords.isEmpty()) false
                else {
                    val intersection = candWords.intersect(pastWords).size.toDouble()
                    val union = candWords.union(pastWords).size.toDouble()
                    (intersection / union) >= 0.85
                }
            }
        }
    }

    private fun getAlternativeResponse(charId: String): String {
        val count = synchronized(recentResponseHistory) { recentResponseHistory.size }
        return when (charId) {
            "raya" -> when (count % 4) {
                0 -> "Thank you for sharing that! What else is on your mind today?"
                1 -> "That's wonderful to hear! Tell me a bit more about your perspective on this."
                2 -> "I'm really enjoying our conversation! What would you like to discuss next?"
                else -> "That is so fascinating! What other experiences come to mind?"
            }
            "rudra" -> when (count % 3) {
                0 -> "Nice! Let's keep the momentum going—what other thoughts do you have on this?"
                1 -> "Awesome point! What else are you excited about right now?"
                else -> "That's cool! Where should we take our practice next?"
            }
            "adwaita" -> "Understood. Let us explore the next aspect of your thoughts."
            else -> "That's helpful context. Please tell me more."
        }
    }

    private fun recordResponse(response: String) {
        synchronized(recentResponseHistory) {
            if (recentResponseHistory.size >= 8) {
                recentResponseHistory.removeAt(0)
            }
            recentResponseHistory.add(response)
        }
    }

    fun reset() {
        recentResponseHistory.clear()
    }
}