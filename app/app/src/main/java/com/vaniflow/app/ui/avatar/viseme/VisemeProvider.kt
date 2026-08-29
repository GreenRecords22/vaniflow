package com.vaniflow.app.ui.avatar.viseme

/**
 * Standard facial visemes for speech animation.
 */
enum class Viseme {
    REST,     // Neutral / mouth closed
    A,        // "ah" (father)
    E,        // "eh" (bed)
    I,        // "ee" (see)
    O,        // "oh" (go)
    U,        // "oo" (too)
    M_B_P,    // Closed bilabial consonants
    F_V,      // Labiodental fricatives
    TH,       // Dental consonants
    L,        // Alveolar lateral
    S_Z,      // Sibilants
    SH_CH     // Palato-alveolar fricatives
}

/**
 * Current viseme state with normalized openness/intensity (0.0 to 1.0).
 */
data class VisemeState(
    val viseme: Viseme = Viseme.REST,
    val intensity: Float = 0.0f
)

/**
 * Viseme provider abstraction.
 */
interface VisemeProvider {
    fun getCurrentViseme(): VisemeState
    fun onAudioFrame(amplitude: Float)
    fun onSpeechStart()
    fun onSpeechEnd()
}

/**
 * Amplitude-driven Viseme Provider.
 * Converts real-time audio energy into speech visemes and smoothed mouth openness.
 */
class AmplitudeVisemeProvider : VisemeProvider {
    @Volatile private var isSpeaking = false
    @Volatile private var smoothedIntensity = 0.0f

    // Attack (30ms) & Release (70ms) smoothing factors
    private val attackFactor = 0.45f
    private val releaseFactor = 0.18f

    override fun onSpeechStart() {
        isSpeaking = true
        smoothedIntensity = 0.15f
    }

    override fun onSpeechEnd() {
        isSpeaking = false
        smoothedIntensity = 0.0f
    }

    override fun onAudioFrame(amplitude: Float) {
        if (!isSpeaking) {
            smoothedIntensity = 0.0f
            return
        }

        val clamped = amplitude.coerceIn(0f, 1f)
        smoothedIntensity = if (clamped > smoothedIntensity) {
            smoothedIntensity + (clamped - smoothedIntensity) * attackFactor
        } else {
            smoothedIntensity + (clamped - smoothedIntensity) * releaseFactor
        }
    }

    override fun getCurrentViseme(): VisemeState {
        if (!isSpeaking || smoothedIntensity < 0.05f) {
            return VisemeState(Viseme.REST, 0.0f)
        }

        val viseme = when {
            smoothedIntensity > 0.75f -> Viseme.A
            smoothedIntensity > 0.55f -> Viseme.O
            smoothedIntensity > 0.35f -> Viseme.E
            smoothedIntensity > 0.20f -> Viseme.I
            else -> Viseme.M_B_P
        }

        return VisemeState(viseme, smoothedIntensity)
    }
}

/**
 * Phoneme-level Viseme Provider.
 *
 * NOTE: True phoneme-level synchronization requires a compatible 3D character rig /
 * Live2D blendshapes and a phoneme timing API from the TTS engine.
 * CLASSIFICATION: BLOCKED pending 3D character mesh rig assets.
 */
class PhonemeVisemeProvider : VisemeProvider {
    override fun getCurrentViseme(): VisemeState = VisemeState(Viseme.REST, 0.0f)
    override fun onAudioFrame(amplitude: Float) {}
    override fun onSpeechStart() {}
    override fun onSpeechEnd() {}
}