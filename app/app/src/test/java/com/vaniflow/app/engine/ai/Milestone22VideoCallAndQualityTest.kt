package com.vaniflow.app.engine.ai

import com.vaniflow.app.domain.model.SkillLevel
import com.vaniflow.app.ui.avatar.CharacterFacialRegistry
import com.vaniflow.app.ui.avatar.MouthShape
import com.vaniflow.app.ui.avatar.VisemeLipSyncController
import com.vaniflow.app.ui.avatar.mapToMouthShape
import com.vaniflow.app.ui.avatar.viseme.Viseme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive regression tests for Milestone 22:
 * 1. Conversational diversity across different questions
 * 2. Emotional and topic-specific reflections
 * 3. Face-aligned mouth coordinates
 * 4. Video-call state management
 */
class Milestone22VideoCallAndQualityTest {

    private lateinit var dialogueEngine: ConversationalDialogueEngine
    private lateinit var lipSyncController: VisemeLipSyncController

    @Before
    fun setup() {
        dialogueEngine = ConversationalDialogueEngine()
        lipSyncController = VisemeLipSyncController()
    }

    @Test
    fun testFiveBenchmarkQuestionsProduceFiveDistinctAnswers() {
        val q1 = "What is your favorite food?"
        val q2 = "Do you like travelling?"
        val q3 = "What do you usually do on weekends?"
        val q4 = "Tell me something interesting about India."
        val q5 = "I'm feeling tired today."

        val a1 = dialogueEngine.generateResponse("raya", "Scenario", SkillLevel.BEGINNER, emptyList(), q1)
        val a2 = dialogueEngine.generateResponse("raya", "Scenario", SkillLevel.BEGINNER, emptyList(), q2)
        val a3 = dialogueEngine.generateResponse("raya", "Scenario", SkillLevel.BEGINNER, emptyList(), q3)
        val a4 = dialogueEngine.generateResponse("raya", "Scenario", SkillLevel.BEGINNER, emptyList(), q4)
        val a5 = dialogueEngine.generateResponse("raya", "Scenario", SkillLevel.BEGINNER, emptyList(), q5)

        // All 5 answers must be non-empty and completely distinct
        val set = setOf(a1, a2, a3, a4, a5)
        assertEquals("All 5 questions must produce 5 unique contextual responses", 5, set.size)

        // Verify specific contextual keywords in responses
        assertTrue("Food answer must mention cuisine/food", a1.contains("dosa") || a1.contains("food") || a1.contains("paneer"))
        assertTrue("Travel answer must mention travel/places", a2.contains("Himachal") || a2.contains("Rajasthan") || a2.contains("traveled"))
        assertTrue("Weekend answer must mention weekend activities", a3.contains("weekend") || a3.contains("music") || a3.contains("baking"))
        assertTrue("India answer must mention India trivia", a4.contains("Hikkim") || a4.contains("post office") || a4.contains("clouds") || a4.contains("English"))
        assertTrue("Tired answer must show empathy and rest", a5.contains("tired") || a5.contains("rest") || a5.contains("energy"))
    }

    @Test
    fun testFacialProfileMouthCenterYIsAccuratelyPositionedOnFaceNotNeck() {
        val raya = CharacterFacialRegistry.getProfile("raya")
        val rudra = CharacterFacialRegistry.getProfile("rudra")
        val adwaita = CharacterFacialRegistry.getProfile("adwaita")
        val shub = CharacterFacialRegistry.getProfile("shub")

        // In 1024x1024 image, lips are at ~570-600px (0.55 - 0.60), NOT at neck (> 0.70)
        assertTrue("Raya mouth Y center must be on face (~0.576)", raya.mouthCenterY in 0.55f..0.60f)
        assertTrue("Rudra mouth Y center must be on face (~0.581)", rudra.mouthCenterY in 0.55f..0.60f)
        assertTrue("Adwaita mouth Y center must be on face (~0.556)", adwaita.mouthCenterY in 0.54f..0.59f)
        assertTrue("Shub mouth Y center must be on face (~0.600)", shub.mouthCenterY in 0.57f..0.62f)
    }

    @Test
    fun testMathematicalDestinationRectCropCalculation() {
        // Test wide canvas (e.g. 400x300)
        val canvasW = 400f
        val canvasH = 300f
        val imgW = 1024f
        val imgH = 1024f

        val scale = maxOf(canvasW / imgW, canvasH / imgH)
        val scaledW = imgW * scale
        val scaledH = imgH * scale
        val offsetX = (canvasW - scaledW) / 2f
        val offsetY = (canvasH - scaledH) / 2f

        val raya = CharacterFacialRegistry.getProfile("raya")
        val mCenterX = offsetX + (scaledW * raya.mouthCenterX)
        val mCenterY = offsetY + (scaledH * raya.mouthCenterY)

        assertTrue("Mouth center X must be centered horizontally", mCenterX in 190f..210f)
        assertTrue("Mouth center Y must be properly mapped inside canvas", mCenterY in 120f..200f)
    }

    @Test
    fun testMouthShapesAndInstantInterruption() {
        lipSyncController.onSpeechStart()
        assertEquals(MouthShape.SMALL_OPEN, lipSyncController.mouthShapeFlow.value)

        lipSyncController.onAmplitude(0.9f)
        val openShape = lipSyncController.mouthShapeFlow.value
        assertTrue(openShape == MouthShape.WIDE_OPEN || openShape == MouthShape.MEDIUM_OPEN || openShape == MouthShape.ROUND_O)

        // Instant interruption snap
        lipSyncController.interrupt()
        assertEquals(MouthShape.REST, lipSyncController.mouthShapeFlow.value)
        assertEquals(0.0f, lipSyncController.mouthOpennessFlow.value, 0.001f)
    }
}