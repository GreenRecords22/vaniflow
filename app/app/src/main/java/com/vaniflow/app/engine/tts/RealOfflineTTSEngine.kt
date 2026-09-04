package com.vaniflow.app.engine.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.vaniflow.app.domain.model.CharacterVoiceConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

import com.vaniflow.app.ui.avatar.LipSyncController
import com.vaniflow.app.ui.avatar.VisemeLipSyncController

/**
 * Production Real Offline TTS Engine with Strict Gender & Accent Filtering.
 *
 * Prioritizes Google Neural TTS engine with authentic female voices for Raya & Adwaita,
 * and masculine voices for Shub & Rudra.
 */
@Singleton
class RealOfflineTTSEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceRegistry: CharacterVoiceRegistry,
    private val lipSyncController: LipSyncController
) : TTSEngine {

    constructor(
        context: Context,
        voiceRegistry: CharacterVoiceRegistry
    ) : this(context, voiceRegistry, VisemeLipSyncController())

    private val tag = "VaniFlowTTS"

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    private val isInterrupted = AtomicBoolean(false)

    init {
        initTts()
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(context, { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                    Log.d(tag, "Google TTS initialized successfully.")
                    try {
                        tts?.language = Locale("en", "IN")
                    } catch (_: Exception) {}
                } else {
                    fallbackDefaultTts()
                }
            }, "com.google.android.tts")
        } catch (e: Exception) {
            Log.w(tag, "Google TTS init exception: ${e.message}, falling back")
            fallbackDefaultTts()
        }
    }

    private fun fallbackDefaultTts() {
        try {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                    Log.d(tag, "Default TTS initialized.")
                    try {
                        tts?.language = Locale("en", "IN")
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize default TTS: ${e.message}")
        }
    }

    private fun selectBestVoiceForCharacter(voiceConfig: CharacterVoiceConfig) {
        val allVoices = tts?.voices
        if (allVoices.isNullOrEmpty()) {
            Log.w(tag, "No TTS voices available on this device for ${voiceConfig.characterId}")
            return
        }

        val isFemale = voiceConfig.gender.equals("female", ignoreCase = true)

        // Log all available voices for debugging on-device (only once)
        if (allVoices.isNotEmpty()) {
            Log.d(tag, "Available TTS voices (${allVoices.size} total):")
            allVoices.take(20).forEach { v ->
                Log.d(tag, "  Voice: name=${v.name}, locale=${v.locale}, features=${v.features}")
            }
        }

        // Filter English voices
        val englishVoices = allVoices.filter { voice ->
            val lang = voice.locale.language.lowercase()
            val name = voice.name.lowercase()
            lang.startsWith("en") || name.startsWith("en-") || name.startsWith("en_") || name.contains("english")
        }

        Log.d(tag, "English voices found: ${englishVoices.size} for character=${voiceConfig.characterId}, wantFemale=$isFemale")

        /**
         * Gender detection — three-tier priority:
         * 1. Android Voice.getFeatures() API — most reliable (used by modern Google TTS)
         * 2. Name-based token heuristics — fallback for OEM voices without feature flags
         * 3. Pitch-only mode — last resort when device has no gender-specific voices
         */
        fun detectGenderFromFeatures(voice: Voice): String? {
            val features = voice.features ?: return null
            // Feature strings can be: "gender=female", "gender=male", "female", "male"
            for (feature in features) {
                val f = feature.lowercase().trim()
                when {
                    f.contains("gender=female") || f == "female" -> return "female"
                    f.contains("gender=male") || f == "male" -> return "male"
                }
            }
            return null
        }

        fun detectGenderFromName(voice: Voice): String? {
            val name = voice.name.lowercase()
            val femaleTokens = setOf(
                "female", "#female", "cxx", "ene", "enc", "sfg", "iob", "iom",
                "rjs", "afh", "-f-", "_f_", "#f#", "f0", "f1", "f2", "f3",
                "woman", "girl", "her"
            )
            val maleTokens = setOf(
                "male", "#male", "ahp", "end", "tpd", "iol", "-m-", "_m_",
                "#m#", "m0", "m1", "m2", "m3", "man", "boy", "him"
            )
            if (femaleTokens.any { name.contains(it) }) return "female"
            if (maleTokens.any { name.contains(it) }) return "male"
            return null
        }

        fun isTargetGender(voice: Voice, wantFemale: Boolean): Boolean {
            val featureGender = detectGenderFromFeatures(voice)
            if (featureGender != null) {
                return if (wantFemale) featureGender == "female" else featureGender == "male"
            }
            val nameGender = detectGenderFromName(voice)
            if (nameGender != null) {
                return if (wantFemale) nameGender == "female" else nameGender == "male"
            }
            // Unknown gender — cannot use for strict matching
            return false
        }

        // Try Indian locale first, then any English
        val targetVoices = englishVoices.filter { isTargetGender(it, isFemale) }
        val indianTarget = targetVoices.firstOrNull { it.locale.country.equals("IN", ignoreCase = true) }
        val bestVoice = indianTarget ?: targetVoices.firstOrNull()

        if (bestVoice != null) {
            val genderLabel = if (isFemale) "Female" else "Male"
            Log.d(tag, "✅ Selected $genderLabel voice for ${voiceConfig.characterId}: ${bestVoice.name} [${bestVoice.locale}] features=${bestVoice.features}")
            try {
                tts?.voice = bestVoice
            } catch (e: Exception) {
                Log.e(tag, "Failed to set voice: ${e.message}")
            }
        } else {
            // Last resort: use the voice with highest/lowest quality score based on gender
            // At minimum apply correct pitch to differentiate
            val fallbackVoice = if (isFemale) {
                englishVoices.firstOrNull { it.locale.country.equals("IN", ignoreCase = true) }
                    ?: englishVoices.firstOrNull()
            } else {
                englishVoices.lastOrNull { it.locale.country.equals("IN", ignoreCase = true) }
                    ?: englishVoices.lastOrNull()
            }
            val genderLabel = if (isFemale) "female" else "male"
            Log.w(tag, "⚠️ No $genderLabel English voice found for ${voiceConfig.characterId}. Install Google TTS with Indian English voice pack. Using pitch=${voiceConfig.pitch} as fallback.")
            if (fallbackVoice != null) {
                try { tts?.voice = fallbackVoice } catch (_: Exception) {}
            }
        }
    }


    override suspend fun speak(text: String, voiceId: String, speed: Float): TTSResult {
        if (text.isBlank()) {
            return TTSResult.Completed(0L)
        }

        _isSpeaking.value = true
        isInterrupted.set(false)

        val voiceConfig = voiceRegistry.voiceConfigs.values.find {
            it.voiceId.equals(voiceId, ignoreCase = true) || it.characterId.equals(voiceId, ignoreCase = true)
        } ?: voiceRegistry.getVoiceConfig(voiceId)

        // 1. Sentence-level chunking
        val sentences = SentenceSplitter.splitIntoSentences(text)
        if (sentences.isEmpty()) {
            _isSpeaking.value = false
            return TTSResult.Completed(0L)
        }

        val startTime = System.currentTimeMillis()

        // 2. Play each sentence sequentially
        for (sentence in sentences) {
            if (isInterrupted.get()) {
                _isSpeaking.value = false
                return TTSResult.Interrupted
            }

            val completed = speakSingleSentence(sentence, voiceConfig, speed)
            if (!completed || isInterrupted.get()) {
                _isSpeaking.value = false
                return TTSResult.Interrupted
            }
        }

        _isSpeaking.value = false
        val durationMs = System.currentTimeMillis() - startTime
        return TTSResult.Completed(durationMs)
    }

    @Volatile private var activeAmplitudeJob: Job? = null

    private suspend fun speakSingleSentence(sentence: String, voiceConfig: CharacterVoiceConfig, speed: Float): Boolean {
        if (isInterrupted.get()) return false

        val utteranceId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<Boolean>()

        if (!isTtsInitialized || tts == null) {
            // Simulated duration for testing environments
            lipSyncController.onSpeechStart()
            val wordCount = sentence.split("\\s+".toRegex()).size.coerceAtLeast(1)
            val fakeDuration = ((wordCount / (150f * speed)) * 60 * 1000).toLong().coerceIn(300L, 4000L)

            val step = 35L
            var elapsed = 0L
            while (elapsed < fakeDuration) {
                if (isInterrupted.get()) {
                    lipSyncController.interrupt()
                    return false
                }
                val t = elapsed / 1000.0
                val amp = (kotlin.math.sin(t * 2.0 * kotlin.math.PI * 3.8) * 0.40 + 0.50).toFloat().coerceIn(0.15f, 0.90f)
                lipSyncController.onAmplitude(amp)
                delay(step)
                elapsed += step
            }
            lipSyncController.onSpeechEnd()
            return !isInterrupted.get()
        }

        return withContext(Dispatchers.Main) {
            val ttsInstance = tts ?: return@withContext false

            // Set voice, pitch and rate dynamically per character
            selectBestVoiceForCharacter(voiceConfig)
            ttsInstance.setPitch(voiceConfig.pitch)
            ttsInstance.setSpeechRate(speed * voiceConfig.speakingRate)

            ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                    lipSyncController.onSpeechStart()
                    activeAmplitudeJob?.cancel()
                    activeAmplitudeJob = CoroutineScope(Dispatchers.Default).launch {
                        val startTime = System.currentTimeMillis()
                        val step = 35L
                        while (!deferred.isCompleted && !isInterrupted.get()) {
                            val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
                            val syllableWave = kotlin.math.sin(elapsed * 2.0 * kotlin.math.PI * 3.8) * 0.40 + 0.50
                            val modulation = kotlin.math.cos(elapsed * 2.0 * kotlin.math.PI * 1.2) * 0.15
                            val amp = (syllableWave + modulation).toFloat().coerceIn(0.15f, 0.90f)
                            lipSyncController.onAmplitude(amp)
                            delay(step)
                        }
                    }
                }

                override fun onDone(id: String?) {
                    activeAmplitudeJob?.cancel()
                    lipSyncController.onSpeechEnd()
                    if (id == utteranceId && !deferred.isCompleted) {
                        deferred.complete(true)
                    }
                }

                override fun onError(id: String?) {
                    activeAmplitudeJob?.cancel()
                    lipSyncController.onSpeechEnd()
                    if (id == utteranceId && !deferred.isCompleted) {
                        deferred.complete(false)
                    }
                }

                override fun onError(id: String?, errorCode: Int) {
                    activeAmplitudeJob?.cancel()
                    lipSyncController.onSpeechEnd()
                    if (id == utteranceId && !deferred.isCompleted) {
                        deferred.complete(false)
                    }
                }
            })

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            val queueMode = TextToSpeech.QUEUE_FLUSH
            val result = ttsInstance.speak(sentence, queueMode, params, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                activeAmplitudeJob?.cancel()
                lipSyncController.onSpeechEnd()
                deferred.complete(false)
            }

            try {
                kotlinx.coroutines.withTimeoutOrNull(7000L) {
                    deferred.await()
                } ?: false
            } catch (e: Exception) {
                activeAmplitudeJob?.cancel()
                lipSyncController.onSpeechEnd()
                false
            }
        }
    }

    override suspend fun stop() {
        isInterrupted.set(true)
        _isSpeaking.value = false
        activeAmplitudeJob?.cancel()
        lipSyncController.interrupt()
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    override suspend fun pause() {
        stop()
    }

    override suspend fun resume() {
        // No-op for TTS
    }

    fun release() {
        _isSpeaking.value = false
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isTtsInitialized = false
        } catch (_: Exception) {}
    }
}
