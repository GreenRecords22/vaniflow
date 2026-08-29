package com.vaniflow.app.ui.avatar

import com.vaniflow.app.ui.avatar.viseme.Viseme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive test suite for Milestone 21: Actual Visible Talking AI Avatar.
 */
class Milestone21TalkingAvatarTest {

    private lateinit var lipSyncController: VisemeLipSyncController

    @Before
    fun setup() {
        lipSyncController = VisemeLipSyncController()
    }

    @Test
    fun testAllFourCharacterFacialProfilesAreConfigured() {
        val raya = CharacterFacialRegistry.getProfile("raya")
        val rudra = CharacterFacialRegistry.getProfile("rudra")
        val adwaita = CharacterFacialRegistry.getProfile("adwaita")
        val shub = CharacterFacialRegistry.getProfile("shub")

        assertEquals("raya", raya.characterId)
        assertTrue("Raya mouth center X must be ~0.5", raya.mouthCenterX in 0.45f..0.55f)
        assertTrue("Raya mouth center Y must be in lower face ~0.6", raya.mouthCenterY in 0.55f..0.68f)
        assertTrue("Raya mouth width ratio must be positive", raya.mouthWidthRatio > 0.10f)

        assertEquals("rudra", rudra.characterId)
        assertTrue("Rudra mouth center Y must be in lower face", rudra.mouthCenterY in 0.55f..0.68f)

        assertEquals("adwaita", adwaita.characterId)
        assertTrue("Adwaita mouth center Y must be in lower face", adwaita.mouthCenterY in 0.55f..0.68f)

        assertEquals("shub", shub.characterId)
        assertTrue("Shub mouth center Y must be in lower face", shub.mouthCenterY in 0.55f..0.68f)
    }

    @Test
    fun testMouthShapeMapping() {
        // Rest / Silence
        assertEquals(MouthShape.REST, mapToMouthShape(0.0f, Viseme.REST))
        assertEquals(MouthShape.REST, mapToMouthShape(0.04f, Viseme.REST))

        // Consonant bilabial closure
        assertEquals(MouthShape.CLOSED, mapToMouthShape(0.4f, Viseme.M_B_P))

        // Rounded vowel
        assertEquals(MouthShape.ROUND_O, mapToMouthShape(0.6f, Viseme.O))
        assertEquals(MouthShape.ROUND_O, mapToMouthShape(0.6f, Viseme.U))

        // Continuous Openness Progression
        assertEquals(MouthShape.SMALL_OPEN, mapToMouthShape(0.20f, Viseme.I))
        assertEquals(MouthShape.MEDIUM_OPEN, mapToMouthShape(0.50f, Viseme.E))
        assertEquals(MouthShape.WIDE_OPEN, mapToMouthShape(0.85f, Viseme.A))
    }

    @Test
    fun testLipSyncControllerSpeechCycleAndMouthShapes() {
        assertEquals(MouthShape.REST, lipSyncController.currentMouthShape())

        // 1. Speech Starts
        lipSyncController.onSpeechStart()
        assertTrue("Speech start must open mouth", lipSyncController.currentMouthOpenness() > 0.1f)
        assertEquals(MouthShape.SMALL_OPEN, lipSyncController.mouthShapeFlow.value)

        // 2. High Audio Energy Frame
        lipSyncController.onAmplitude(0.85f)
        assertTrue("Mouth openness must react to high energy", lipSyncController.mouthOpennessFlow.value > 0.3f)
        val shape = lipSyncController.mouthShapeFlow.value
        assertTrue("High amplitude must produce open or round mouth shape", shape == MouthShape.WIDE_OPEN || shape == MouthShape.MEDIUM_OPEN || shape == MouthShape.ROUND_O)

        // 3. Speech Ends
        lipSyncController.onSpeechEnd()
        assertEquals(0.0f, lipSyncController.mouthOpennessFlow.value, 0.001f)
        assertEquals(MouthShape.REST, lipSyncController.mouthShapeFlow.value)
        assertEquals(Viseme.REST, lipSyncController.currentViseme().viseme)
    }

    @Test
    fun testInstantInterruptionSnapsMouthClosed() {
        lipSyncController.onSpeechStart()
        lipSyncController.onAmplitude(0.95f)
        assertTrue(lipSyncController.mouthOpennessFlow.value > 0.2f)

        // Barge-in interruption
        lipSyncController.interrupt()
        assertEquals(0.0f, lipSyncController.mouthOpennessFlow.value, 0.001f)
        assertEquals(MouthShape.REST, lipSyncController.mouthShapeFlow.value)
        assertEquals(Viseme.REST, lipSyncController.currentViseme().viseme)
    }
}