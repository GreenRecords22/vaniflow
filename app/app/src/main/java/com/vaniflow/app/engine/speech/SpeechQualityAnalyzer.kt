package com.vaniflow.app.engine.speech

import com.vaniflow.app.engine.speech.model.SpeechAudioSegment
import com.vaniflow.app.engine.speech.model.SpeechQualityResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Assesses the acoustic health, signal-to-noise ratio (SNR), clipping, and noise floor
 * to determine whether an audio segment has sufficient quality for speech learning intelligence.
 */
@Singleton
class SpeechQualityAnalyzer @Inject constructor(
    private val featureExtractor: SpeechFeatureExtractor
) {

    fun analyze(audio: SpeechAudioSegment): SpeechQualityResult {
        if (audio.samples.isEmpty() || audio.durationMs < 200L) {
            return SpeechQualityResult(
                snrDb = 0f,
                noiseFloorDbfs = SpeechFeatureExtractor.MIN_DBFS,
                clippingCount = 0,
                clippingRatio = 0f,
                rmsEnergyDbfs = SpeechFeatureExtractor.MIN_DBFS,
                isSignalUsable = false,
                qualitySummary = "Insufficient audio duration"
            )
        }

        val features = featureExtractor.extractAcousticFeatures(audio)

        val isSevereClipping = features.clippingRatio > 0.05f // >5% clipped samples
        val isExtremelyNoisy = features.snrDb < 4.0f // Very low SNR
        val isAlmostSilent = features.meanRmsDbfs < -55.0f

        val isSignalUsable = !isSevereClipping && !isExtremelyNoisy && !isAlmostSilent && features.voicedDurationMs >= 150L

        val summary = when {
            isSevereClipping -> "Audio distorted due to microphone clipping"
            isExtremelyNoisy -> "High ambient background noise"
            isAlmostSilent -> "Audio volume too low"
            features.snrDb >= 15.0f -> "Clean acoustic signal"
            else -> "Acceptable acoustic signal"
        }

        return SpeechQualityResult(
            snrDb = features.snrDb,
            noiseFloorDbfs = features.estimatedNoiseFloorDbfs,
            clippingCount = features.clippingCount,
            clippingRatio = features.clippingRatio,
            rmsEnergyDbfs = features.meanRmsDbfs,
            isSignalUsable = isSignalUsable,
            qualitySummary = summary
        )
    }
}
