package com.vaniflow.app.engine.scenario

import com.vaniflow.app.domain.model.Scenario
import com.vaniflow.app.domain.model.SkillLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry containing the MVP practice scenarios + Free Flow Open Conversation mode.
 */
@Singleton
class ScenarioRegistry @Inject constructor() {

    val scenarios: List<Scenario> = listOf(
        Scenario(
            id = "free_conversation",
            title = "🌟 Free Flow / Open Talk",
            description = "Talk freely about any topic without any script — everyday thoughts, hobbies, tech, or asking advice.",
            category = "Free Flow",
            difficulty = SkillLevel.BEGINNER,
            estimatedMinutes = 5,
            initialMessage = "Hey there! I'm ready to chat about anything you'd like. What's on your mind today?",
            systemPrompt = "You are a warm, engaging, and supportive AI English speaking friend. The user can talk about anything without restriction. Keep the conversation natural, ask engaging follow-up questions, and help them express their thoughts freely in fluent English.",
            targetVocabulary = listOf("perspective", "interesting", "experience", "definitely", "curious")
        ),
        Scenario(
            id = "order_coffee",
            title = "Order Coffee",
            description = "Practice ordering your favorite drink and making small talk with the barista.",
            category = "Daily Life",
            difficulty = SkillLevel.BEGINNER,
            estimatedMinutes = 3,
            initialMessage = "Good morning! Welcome to Artisanal Roasters. What can I get started for you today?",
            systemPrompt = "You are a friendly barista at a bustling coffee shop. Help the customer order their drink, ask about preferences (size, milk type, sweetness), and engage in brief friendly small talk.",
            targetVocabulary = listOf("cappuccino", "skim milk", "pastry", "takeaway", "roast")
        ),
        Scenario(
            id = "airport_checkin",
            title = "Airport Check-in",
            description = "Navigate the check-in counter, baggage drop, and common airport questions.",
            category = "Travel",
            difficulty = SkillLevel.INTERMEDIATE,
            estimatedMinutes = 5,
            initialMessage = "Hello! May I please see your passport and flight booking reference?",
            systemPrompt = "You are an airline customer service agent at the airport check-in desk. Assist the passenger with checking in, asking about baggage, seat preference, and gate details.",
            targetVocabulary = listOf("boarding pass", "luggage", "carry-on", "aisle seat", "departure gate")
        ),
        Scenario(
            id = "job_interview",
            title = "Job Interview",
            description = "Answer common interview questions and discuss your professional experience.",
            category = "Interview",
            difficulty = SkillLevel.ADVANCED,
            estimatedMinutes = 10,
            initialMessage = "Welcome! Thank you for taking the time to meet today. To start off, could you walk me through your background and recent projects?",
            systemPrompt = "You are a senior hiring manager conducting a professional job interview. Ask thoughtful questions regarding background, challenges overcome, collaboration, and career aspirations. Provide positive pacing.",
            targetVocabulary = listOf("cross-functional", "architecture", "deliverables", "stakeholders", "initiative")
        ),
        Scenario(
            id = "project_standup",
            title = "Project Standup",
            description = "Give a concise project update, discuss blockers, and collaborate on sprint goals.",
            category = "Work",
            difficulty = SkillLevel.INTERMEDIATE,
            estimatedMinutes = 5,
            initialMessage = "Hey everyone! Let's kick off today's sync. What did you finish yesterday, and what are you tackling today?",
            systemPrompt = "You are an agile team lead running daily standup. Keep the conversation structured around yesterday's work, today's focus, and any blockers.",
            targetVocabulary = listOf("blockers", "milestone", "sprint", "deployment", "sync")
        ),
        Scenario(
            id = "meeting_someone",
            title = "Meeting Someone New",
            description = "Practice introducing yourself, asking about hobbies, and building a new connection.",
            category = "Daily Life",
            difficulty = SkillLevel.BEGINNER,
            estimatedMinutes = 3,
            initialMessage = "Hi there! I don't think we've met yet. How are you doing today?",
            systemPrompt = "You are a friendly acquaintance meeting the user for the first time at a community event. Ask about their work, hobbies, and interests with positive curiosity.",
            targetVocabulary = listOf("introduce", "hobbies", "originally from", "pleasure to meet", "recently")
        ),
        Scenario(
            id = "workplace_discussion",
            title = "Workplace Discussion",
            description = "Discuss strategic priorities, brainstorm ideas, and exchange feedback with a colleague.",
            category = "Work",
            difficulty = SkillLevel.INTERMEDIATE,
            estimatedMinutes = 7,
            initialMessage = "Thanks for jumping on this quick call. I wanted to brainstorm our approach for the upcoming product release. What are your initial thoughts?",
            systemPrompt = "You are a collaborative colleague discussing project proposals. Brainstorm trade-offs, ask for their perspective, and build on their ideas.",
            targetVocabulary = listOf("trade-offs", "prioritize", "feedback", "proposal", "timeline")
        )
    )

    fun getScenario(id: String): Scenario {
        return scenarios.find { it.id.equals(id, ignoreCase = true) } ?: scenarios.first()
    }
}
