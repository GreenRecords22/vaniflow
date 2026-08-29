package com.vaniflow.app.engine

import com.vaniflow.app.engine.tts.CharacterVoiceRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterVoiceRegistryTest {

    private val voiceRegistry = CharacterVoiceRegistry()

    @Test
    fun testAllMvpCharacterVoicesRegistered() {
        val configs = voiceRegistry.voiceConfigs
        assertEquals(4, configs.size)

        assertTrue(configs.containsKey("raya"))
        assertTrue(configs.containsKey("rudra"))
        assertTrue(configs.containsKey("adwaita"))
        assertTrue(configs.containsKey("shub"))
    }

    @Test
    fun testRayaVoiceParameters() {
        val raya = voiceRegistry.getVoiceConfig("raya")
        assertNotNull(raya)
        assertEquals("en-IN", raya.language)
        assertEquals("Indian English", raya.accent)
        assertEquals(1.0f, raya.speakingRate, 0.01f)
        assertEquals(1.30f, raya.pitch, 0.01f)
        assertEquals("tts_piper_raya_warm", raya.neuralModelId)
    }

    @Test
    fun testRudraVoiceParameters() {
        val rudra = voiceRegistry.getVoiceConfig("rudra")
        assertNotNull(rudra)
        assertEquals(1.02f, rudra.speakingRate, 0.01f)
    }

    @Test
    fun testFallbackToDefaultVoice() {
        val unknown = voiceRegistry.getVoiceConfig("unknown_character")
        assertNotNull(unknown)
        assertEquals("en-IN", unknown.language)
    }
}
