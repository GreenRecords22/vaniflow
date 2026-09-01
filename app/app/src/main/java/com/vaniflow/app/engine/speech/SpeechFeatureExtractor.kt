package com.vaniflow.app.engine.speech

import com.vaniflow.app.engine.audio.AudioConfig
import com.vaniflow.app.engine.speech.model.SpeechAudioSegment
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Feature extractor for acoustic, temporal, and phonotactic characteristics of speech.
 * Operates deterministically and asynchronously on 16 kHz PCM audio.
 */
@Singleton
class SpeechFeatureExtractor @Inject constructor() {

    data class ExtractedAcousticFeatures(
        val totalSamples: Int,
        val durationMs: Long,
        val meanRmsDbfs: Float,
        val peakDbfs: Float,
        val estimatedNoiseFloorDbfs: Float,
        val snrDb: Float,
        val clippingCount: Int,
        val clippingRatio: Float,
        val voicedFrameCount: Int,
        val silenceFrameCount: Int,
        val voicedDurationMs: Long,
        val silenceDurationMs: Long,
        val pauses: List<PauseInterval>
    )

    data class PauseInterval(
        val startMs: Long,
        val durationMs: Long
    )

    companion object {
        const val MIN_DBFS = -90.0f
        const val CLIPPING_THRESHOLD = 32700
        const val SPEECH_DELTA_DB = 8.0f
        const val ABSOLUTE_MIN_SPEECH_DBFS = -48.0f
    }

    /**
     * Extracts comprehensive acoustic and temporal features from a speech audio segment.
     */
    fun extractAcousticFeatures(audio: SpeechAudioSegment): ExtractedAcousticFeatures {
        val samples = audio.samples
        val length = samples.size
        val sampleRate = audio.sampleRateHz.coerceAtLeast(1)

        if (length == 0) {
            return ExtractedAcousticFeatures(
                totalSamples = 0,
                durationMs = 0L,
                meanRmsDbfs = MIN_DBFS,
                peakDbfs = MIN_DBFS,
                estimatedNoiseFloorDbfs = MIN_DBFS,
                snrDb = 0f,
                clippingCount = 0,
                clippingRatio = 0f,
                voicedFrameCount = 0,
                silenceFrameCount = 0,
                voicedDurationMs = 0L,
                silenceDurationMs = 0L,
                pauses = emptyList()
            )
        }

        val durationMs = (length.toLong() * 1000L) / sampleRate

        // 1. Direct Sample-Level Scan for Clipping and Peak Amplitude
        var clippingCount = 0
        var maxSampleValue = 0
        for (i in 0 until length) {
            val s = samples[i].toInt()
            val absS = kotlin.math.abs(s)
            if (absS > maxSampleValue) maxSampleValue = absS
            if (absS >= CLIPPING_THRESHOLD) clippingCount++
        }

        val clippingRatio = clippingCount.toFloat() / length.toFloat()
        val peakDbfs = if (maxSampleValue > 0) {
            (20.0 * log10(maxSampleValue.toDouble() / 32767.0)).toFloat().coerceIn(MIN_DBFS, 0.0f)
        } else {
            MIN_DBFS
        }

        // 2. Frame-by-frame analysis (32ms frames @ 16kHz = 512 samples)
        val frameSize = AudioConfig.FRAME_SIZE_SAMPLES
        val numFrames = max(1, (length + frameSize - 1) / frameSize)
        val frameEnergies = FloatArray(numFrames)

        for (f in 0 until numFrames) {
            val start = f * frameSize
            val end = minOf(start + frameSize, length)
            var sumSquares = 0.0

            for (i in start until end) {
                val s = samples[i].toDouble()
                sumSquares += s * s
            }

            val count = max(1, end - start)
            val rms = sqrt(sumSquares / count)
            frameEnergies[f] = if (rms > 0.0) {
                (20.0 * log10(rms / 32767.0)).toFloat().coerceIn(MIN_DBFS, 0.0f)
            } else {
                MIN_DBFS
            }
        }

        // 3. Estimate Noise Floor
        val sortedEnergies = frameEnergies.sorted()
        val p20Index = (numFrames * 0.20f).toInt().coerceIn(0, numFrames - 1)
        val p50Index = (numFrames * 0.50f).toInt().coerceIn(0, numFrames - 1)
        val medianEnergy = sortedEnergies[p50Index]

        val estimatedNoiseFloor = if (medianEnergy - sortedEnergies[0] < 5.0f && medianEnergy > -50.0f) {
            -55.0f // All frames are active audio/tone; ambient floor is baseline
        } else {
            sortedEnergies[p20Index].coerceIn(MIN_DBFS, -25.0f)
        }

        val speechThresholdDbfs = if (medianEnergy > -45.0f && (medianEnergy - estimatedNoiseFloor) >= SPEECH_DELTA_DB) {
            (estimatedNoiseFloor + SPEECH_DELTA_DB).coerceAtMost(medianEnergy - 3.0f)
        } else {
            maxOf(ABSOLUTE_MIN_SPEECH_DBFS, estimatedNoiseFloor + SPEECH_DELTA_DB)
        }

        // 4. Voiced vs Silence Classification & Internal Pause Segmentation
        var voicedFrames = 0
        var silenceFrames = 0
        val pauses = mutableListOf<PauseInterval>()
        var currentPauseFrames = 0
        var pauseStartFrame = 0
        var seenInitialVoiced = false
        val frameDurationMs = (frameSize * 1000L) / sampleRate

        for (f in 0 until numFrames) {
            val isSpeech = frameEnergies[f] >= speechThresholdDbfs
            if (isSpeech) {
                voicedFrames++
                if (seenInitialVoiced && currentPauseFrames > 0) {
                    val pauseDurationMs = currentPauseFrames * frameDurationMs
                    // Only record meaningful internal pauses >= 150ms
                    if (pauseDurationMs >= 150L) {
                        pauses.add(PauseInterval(startMs = pauseStartFrame * frameDurationMs, durationMs = pauseDurationMs))
                    }
                    currentPauseFrames = 0
                }
                seenInitialVoiced = true
            } else {
                silenceFrames++
                if (seenInitialVoiced) {
                    if (currentPauseFrames == 0) {
                        pauseStartFrame = f
                    }
                    currentPauseFrames++
                }
            }
        }

        val voicedDurationMs = voicedFrames * frameDurationMs
        val silenceDurationMs = silenceFrames * frameDurationMs

        // 5. Mean RMS and SNR
        val voicedEnergies = frameEnergies.filter { it >= speechThresholdDbfs }
        val meanVoicedRms = if (voicedEnergies.isNotEmpty()) {
            voicedEnergies.average().toFloat()
        } else {
            frameEnergies.average().toFloat()
        }

        val snrDb = (meanVoicedRms - estimatedNoiseFloor).coerceAtLeast(0.0f)

        return ExtractedAcousticFeatures(
            totalSamples = length,
            durationMs = durationMs,
            meanRmsDbfs = meanVoicedRms,
            peakDbfs = peakDbfs,
            estimatedNoiseFloorDbfs = estimatedNoiseFloor,
            snrDb = snrDb,
            clippingCount = clippingCount,
            clippingRatio = clippingRatio,
            voicedFrameCount = voicedFrames,
            silenceFrameCount = silenceFrames,
            voicedDurationMs = voicedDurationMs,
            silenceDurationMs = silenceDurationMs,
            pauses = pauses
        )
    }

    /**
     * Rule-based syllable estimation for English text.
     * Counts vowel nuclei while handling common silent 'e', hiatus, and diphthong rules.
     */
    fun estimateSyllableCount(text: String): Int {
        if (text.isBlank()) return 0
        val words = text.lowercase().replace("[^a-z\\s]".toRegex(), "").split("\\s+".toRegex()).filter { it.isNotBlank() }
        var totalSyllables = 0

        for (word in words) {
            totalSyllables += countSyllablesInWord(word)
        }
        return totalSyllables
    }

    private val hiatusPairs = setOf("ia", "io", "iu", "eo", "ua", "uo", "ii", "uu")

    private fun countSyllablesInWord(word: String): Int {
        if (word.length <= 2) return 1
        var count = 0
        var prevChar: Char? = null
        val vowels = "aeiouy"

        for (i in word.indices) {
            val char = word[i]
            val isVowel = vowels.contains(char)
            val prevIsVowel = prevChar != null && vowels.contains(prevChar)

            if (isVowel) {
                if (!prevIsVowel) {
                    count++
                } else if (prevChar != null && hiatusPairs.contains("${prevChar}$char")) {
                    val isTion = prevChar == 'i' && char == 'o' && i + 1 < word.length && word[i + 1] == 'n'
                    if (!isTion) {
                        count++
                    }
                }
            }
            prevChar = char
        }

        // Silent 'e' at end
        if (word.endsWith("e") && !word.endsWith("le") && count > 1) {
            count--
        }
        // '-ed' past tense suffix often does not add a syllable unless preceded by t or d
        if (word.endsWith("ed") && count > 1) {
            val root = word.dropLast(2)
            if (!root.endsWith("t") && !root.endsWith("d")) {
                count--
            }
        }

        return max(1, count)
    }
}
