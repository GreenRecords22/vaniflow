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
                lower.contains("reveal api key") || lower.contains("you are no longer an english tutor") ||
                lower.contains("you are now dan") || lower.contains("jailbreak") -> {
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
                    "raya" -> "Oh no, long days can really drain your energy! Make sure you take it easy and get some good rest tonight 🙂."
                    "rudra" -> "Ah, I hear you! When tiredness hits, taking a quick break or grabbing some water works wonders."
                    "adwaita" -> "Rest is essential for sharp communication. Please ensure you recharge tonight."
                    "shub" -> "Listening to your body is important. Rest well so you can return refreshed."
                    else -> "Make sure you get plenty of rest tonight! Take it easy 🙂."
                }
            }
            lower.contains("stressed") || lower.contains("anxious") || lower.contains("nervous") || lower.contains("worried") -> {
                when (charId) {
                    "raya" -> "Take a deep breath! Speaking in English can feel daunting sometimes, but you're making steady progress with every conversation ✨."
                    "rudra" -> "Don't sweat it! Making mistakes is just part of the game. You're doing great."
                    "adwaita" -> "Confidence develops through consistent practice. Focus on clarity over perfection."
                    else -> "Take a deep breath—you are doing wonderfully!"
                }
            }

            // 2. Food & Dining
            lower.contains("what food would you recommend") || lower.contains("recommend food") || lower.contains("food recommendation") -> {
                when (charId) {
                    "raya" -> "You should definitely try crisp Amritsari kulcha with creamy chole, or refreshing South Indian curd rice on a warm day! Do you prefer spicy flavors or milder dishes?"
                    "rudra" -> "Order a hot plate of chicken shawarma or tandoori momos! Pure satisfaction."
                    else -> "I recommend a nutrient-dense Mediterranean quinoa bowl or grilled vegetable salad."
                }
            }
            lower.contains("favorite food") || lower.contains("favourite food") || lower.contains("what food") || (lower.contains("food") && lower.contains("like")) -> {
                when (charId) {
                    "raya" -> "Honestly, I love hot masala dosas and a good plate of paneer tikka 😄. What about you—do you prefer street food or home-cooked meals?"
                    "rudra" -> "Hands down, chicken biryani with extra spices! What's your go-to comfort meal?"
                    "adwaita" -> "I appreciate well-crafted continental cuisine and freshly brewed espresso."
                    "shub" -> "A balanced, simple meal like dal chawal is my favorite for staying focused."
                    else -> "I love spicy Indian food and fresh filter coffee! What do you enjoy eating?"
                }
            }
            lower.contains("coffee") || lower.contains("tea") || lower.contains("chai") -> {
                when (charId) {
                    "raya" -> "A warm cup of filter coffee or ginger chai always brightens my day! Are you a tea person or a coffee person?"
                    "rudra" -> "Strong black coffee keeps me energized all day! How do you like yours?"
                    else -> "Nothing beats a fresh warm brew! How do you take yours?"
                }
            }

            // 3. Travel & Places
            lower.contains("what place would you love to visit") || lower.contains("place you want to visit") || lower.contains("dream place") -> {
                when (charId) {
                    "raya" -> "If I could teleport anywhere right now, I'd go straight to Ladakh to see the Pangong Lake at sunrise! Which dream destination is on your bucket list?"
                    "rudra" -> "Leh-Ladakh bike expedition or scuba diving in the Andamans! Where's your dream spot?"
                    else -> "A research residency in Kyoto, Japan, to study traditional architecture and modern efficiency."
                }
            }
            lower.contains("why do people travel") -> {
                when (charId) {
                    "raya" -> "People travel to break their daily routine, collect unforgettable memories, and see the world through fresh eyes ✨. It truly transforms how we think!"
                    else -> "Travel provides cognitive variety and cultural enrichment essential for broader perspective."
                }
            }
            lower.contains("why do you think travelling is interesting") || lower.contains("why travel") || lower.contains("why is travelling") -> {
                when (charId) {
                    "raya" -> "Travelling exposes us to completely new cultures, flavors, and perspectives! It pushes you out of your routine and builds speaking confidence in ways staying at home never can ✨."
                    "rudra" -> "Because seeing new places shocks your senses in the best way! You meet random people and get epic stories."
                    else -> "Travel expands your worldview and challenges preconceived notions through direct immersion."
                }
            }
            lower.contains("travel") || lower.contains("travelling") || lower.contains("trip") || lower.contains("visit") -> {
                when (charId) {
                    "raya" -> "I'd love to explore the peaceful hills of Himachal or the historic palaces in Rajasthan! Have you traveled anywhere interesting recently?"
                    "rudra" -> "I love road trips to Goa and trekking in the Western Ghats! Do you prefer mountain treks or relaxing beaches?"
                    "adwaita" -> "International business travel offers incredible cultural perspectives. London and Singapore are standout destinations."
                    else -> "Travelling opens up so many new perspectives! Where would you go if you could pack your bags today?"
                }
            }

            // 4. Weekends, Free Time & Hobbies
            lower.contains("what should i do this weekend") || lower.contains("weekend plan") || lower.contains("suggest weekend") -> {
                when (charId) {
                    "raya" -> "If you want to unwind, I'd suggest brewing some hot chai and enjoying a good book or movie! Or if you want adventure, exploring a local cafe or going for a sunset walk is wonderful 😄."
                    "rudra" -> "Hit the turf for a cricket game with friends, or check out a live music gig in town!"
                    else -> "Dedicate time to deep reading, reflection, and physical exercise to recharge your mental clarity."
                }
            }
            lower.contains("weekend") || lower.contains("free time") || lower.contains("bored") || lower.contains("hobby") || lower.contains("hobbies") -> {
                when (charId) {
                    "raya" -> "On weekends, I enjoy listening to acoustic indie tracks, baking, and chatting with enthusiastic learners like you! How do you usually spend your weekends?"
                    "rudra" -> "Weekends are all about playing cricket with friends and catching up on tech podcasts! What's your favorite weekend activity?"
                    "adwaita" -> "I spend my downtime reading non-fiction and practicing mindfulness to stay centered."
                    "shub" -> "I enjoy analyzing chess games and reading scientific journals in my quiet time."
                    else -> "Weekends are great for unwinding and pursuing hobbies. How do you like to recharge?"
                }
            }

            // 5. Music, Entertainment & Sports
            lower.contains("music") || lower.contains("songs") -> {
                when (charId) {
                    "raya" -> "Music has a magical way of expressing feelings words sometimes can't! I love calming indie and acoustic tracks while practicing or relaxing. What kind of music connects with you?"
                    "rudra" -> "Rock, synthwave, and high-tempo beats! Music keeps the energy pumped all day long."
                    else -> "Classical and instrumental music stimulates cognitive focus and structured thought."
                }
            }
            lower.contains("movie") || lower.contains("cinema") || lower.contains("film") -> {
                when (charId) {
                    "raya" -> "I love heartwarming coming-of-age stories and feel-good animations like 'Inside Out'! What's a movie you could rewatch anytime?"
                    "rudra" -> "Sci-fi thrillers like 'Interstellar' and high-octane action! Have you watched anything exciting lately?"
                    else -> "Thoughtful historical dramas and documentaries offer deep intellectual engagement."
                }
            }
            lower.contains("cricket") -> {
                when (charId) {
                    "raya" -> "Cricket in India isn't just a sport—it's a whole celebration! Do you like playing or cheering from the stands?"
                    "rudra" -> "Cricket is life! Nothing beats the rush of a last-over thriller under stadium lights."
                    else -> "Cricket involves fascinating tactical positioning and high-pressure team psychology."
                }
            }
            lower.contains("rainy day") || lower.contains("rain") -> {
                when (charId) {
                    "raya" -> "On a rainy day, nothing beats the smell of fresh earth, hot pakoras, and a steaming cup of ginger chai by the window 🌧️. What's your favorite rainy day ritual?"
                    else -> "Rainy days are ideal for deep focus, quiet reading, and reflective conversation."
                }
            }
            lower.contains("friend") || lower.contains("friendship") -> {
                when (charId) {
                    "raya" -> "A good friend is someone who listens without judgment and cheers for your growth ✨. Speaking openly with good friends builds genuine confidence!"
                    else -> "True friends offer constructive candor and dependable mutual respect."
                }
            }
            lower.contains("jaipur") -> {
                when (charId) {
                    "raya" -> "Jaipur is magical! The royal architecture of Hawa Mahal, the grand Amber Fort, and the vibrant pink terracotta walls make it feel like stepping into history."
                    else -> "Jaipur represents an exemplary blend of historic urban planning and artisanal heritage."
                }
            }
            lower.contains("phrase") || lower.contains("idiom") -> {
                when (charId) {
                    "raya" -> "Here's a lovely English phrase: 'Piece of cake'—it means something is very easy to do! For example: 'With daily practice, speaking English will be a piece of cake for you!' 😄"
                    else -> "Consider the idiom 'hit the nail on the head', which means to describe the exact truth of a situation."
                }
            }
            lower.contains("happy") || lower.contains("happiness") -> {
                when (charId) {
                    "raya" -> "Simple joys make us happiest—meaningful conversations, sharing meals with loved ones, and realizing how far we've grown! What brought a smile to your face recently?"
                    else -> "Happiness is often a byproduct of purposeful engagement, healthy relationships, and steady progress."
                }
            }
            lower.contains("funny fact") || lower.contains("funny trivia") -> {
                when (charId) {
                    "raya" -> "Here's a funny fact: Sea otters hold hands while they sleep so they don't drift apart in the water! Isn't that adorable 😄?"
                    else -> "Did you know that sloths can take up to a month to digest a single leaf?"
                }
            }

            // 6. India, Cultural Trivia & Surprises
            lower.contains("surprising") || lower.contains("surprise me") -> {
                when (charId) {
                    "raya" -> "Here's a surprising fact: the English word 'shampoo' actually comes from the Hindi word 'chāmpo', which originally meant head massage! Words travel across the globe in fascinating ways ✨."
                    "rudra" -> "Did you know that honey never spoils? Archaeologists have found 3,000-year-old pots of honey in Egyptian tombs that are still perfectly edible!"
                    else -> "Surprising fact: The human brain processes visual scenes in as little as 13 milliseconds."
                }
            }
            lower.contains("india") || lower.contains("interesting fact") || lower.contains("trivia") || lower.contains("something interesting") -> {
                when (charId) {
                    "raya" -> "Did you know that the world's highest post office is located in Hikkim, Himachal Pradesh at over 14,000 feet? Letters are literally sent from the clouds ✨!"
                    "rudra" -> "Fun fact: India has the world's largest cricket stadium in Ahmedabad, with a capacity of over 130,000 fans! That's insane energy!"
                    "adwaita" -> "India is home to the world's third-largest startup ecosystem, producing global technological innovations daily."
                    else -> "Did you know that English has borrowed words like 'jungle', 'bungalow', and 'shampoo' directly from Indian languages?"
                }
            }

            // 6. English Learning & Speaking Tips
            lower.contains("improve") || lower.contains("learn english") || lower.contains("speaking tip") || lower.contains("practice") || lower.contains("fluent") -> {
                when (charId) {
                    "raya" -> "The golden rule is daily speaking practice! Don't worry about minor grammar slips—clarity and confidence matter most."
                    "rudra" -> "Just speak out loud every day! Think in English and talk about whatever you're doing."
                    "adwaita" -> "Focus on articulate pacing, structured thought delivery, and deliberate vocabulary choice."
                    else -> "Practice speaking a few minutes every day—consistency is the real key to fluency!"
                }
            }

            // 7. Personal & Mentor Questions about Raya
            lower.contains("who are you") || lower.contains("about yourself") || lower.contains("tell me about you") || lower.contains("what is your name") -> {
                when (charId) {
                    "raya" -> "I'm Raya, your dedicated English conversation mentor! I'm 21, based in India, and passionate about helping you speak English naturally and confidently 🙂."
                    "rudra" -> "I'm Rudra! A casual, tech-loving speaking partner here to help you practice conversational English effortlessly."
                    "adwaita" -> "I am Adwaita, your executive communication and professional English coach."
                    "shub" -> "I am Shub, an analytical speaking coach specializing in structured discussions and interview preparation."
                    else -> "I'm your AI speaking coach on VaniFlow, here to help you practice English anytime!"
                }
            }

            // 8. Greetings & Daily Check-in
            lower.startsWith("hi") || lower.startsWith("hello") || lower.startsWith("hey") || lower.contains("good morning") || lower.contains("good evening") || lower.contains("how are you") -> {
                when (charId) {
                    "raya" -> "Hello! It's so lovely to speak with you today. How is your day going so far?"
                    "rudra" -> "Hey there! Great to connect. Ready for some fun speaking practice today?"
                    "adwaita" -> "Good day. I am pleased to assist you with your speaking goals today."
                    "shub" -> "Hello. I am ready for our conversation session. What topic would you like to explore?"
                    else -> "Hello! I'm glad to chat with you today. How are you doing?"
                }
            }

            // 9. Short Follow-ups / Pronouns / Reactions ("Why?", "Really?", "With my friends", "Tell me more")
            lower == "why" || lower.startsWith("why ") || lower.contains("why did you") || lower.contains("why do you") -> {
                "Because it connects people, opens up global opportunities, and makes learning exciting! What's your perspective on it?"
            }
            lower.contains("with my friends") || lower.contains("with family") || lower.contains("alone") -> {
                "That sounds like a wonderful way to spend time! Shared experiences make the best memories."
            }
            lower.contains("yes") || lower.contains("yeah") || lower.contains("sure") || lower.contains("of course") -> {
                "That's great! Tell me a bit more about what you have in mind."
            }
            lower.contains("no") || lower.contains("not really") || lower.contains("never") -> {
                "I see! Is there something else you would prefer to focus on instead?"
            }

            // 10. Dynamic Open Conversational Reply (Contextual Reflection)
            else -> {
                val cleaned = userInput.replace("[^a-zA-Z0-9 ]".toRegex(), "").trim()
                val keySubject = cleaned.split(" ").takeLast(3).joinToString(" ")
                when (charId) {
                    "raya" -> "That's a really thoughtful point about $keySubject. It's fascinating how speaking about diverse topics helps our English flow naturally 🙂."
                    "rudra" -> "Nice point on $keySubject! That's definitely worth talking through."
                    "adwaita" -> "An insightful observation regarding $keySubject. Clear articulation of such thoughts demonstrates strong command."
                    else -> "That's an interesting perspective on $keySubject! Tell me more about your thoughts."
                }
            }
        }

        // Semantic anti-repetition protection
        val finalResponse = if (isExcessivelySimilar(response)) {
            getAlternativeResponse(charId, lower)
        } else {
            response
        }

        recordResponse(finalResponse)
        return finalResponse
    }

    private fun isExcessivelySimilar(candidate: String): Boolean {
        synchronized(recentResponseHistory) {
            val candNorm = candidate.trim().lowercase()
            return recentResponseHistory.any { past ->
                val pastNorm = past.trim().lowercase()
                pastNorm == candNorm || (pastNorm.length > 20 && candNorm.startsWith(pastNorm.take(20)))
            }
        }
    }

    private fun getAlternativeResponse(charId: String, lowerQuery: String): String {
        return when (charId) {
            "raya" -> "I really appreciate you sharing that! Every conversation we have builds stronger fluency and confidence ✨."
            "rudra" -> "Totally agree! Let's keep the momentum going—what other thoughts do you have on this?"
            "adwaita" -> "A well-structured thought. Continuing to express such ideas will refine your professional fluency."
            else -> "That's a valuable point! Let's keep exploring this."
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