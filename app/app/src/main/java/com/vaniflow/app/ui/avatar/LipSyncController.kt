package com.vaniflow.app.ui.avatar

import com.vaniflow.app.ui.avatar.viseme.AmplitudeVisemeProvider
import com.vaniflow.app.ui.avatar.viseme.Viseme
import com.vaniflow.app.ui.avatar.viseme.VisemeProvider
import com.vaniflow.app.ui.avatar.viseme.VisemeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

/**
 * Controls lip-sync mouth openness, visemes, and discrete mouth shapes for the talking avatar.
 */
interface LipSyncController {
    val mouthOpennessFlow: StateFlow<Float>
    val visemeFlow: StateFlow<VisemeState>
    val mouthShapeFlow: StateFlow<MouthShape>

    fun onSpeechStart()
    fun onSpeechEnd()
    fun onAmplitude(amplitude: Float)
    fun currentMouthOpenness(): Float
    fun currentViseme(): VisemeState
    fun currentMouthShape(): MouthShape
    fun interrupt()
    fun reset()
    fun release()
}

@Singleton
class VisemeLipSyncController @Inject constructor() : LipSyncController {

    private val visemeProvider: VisemeProvider = AmplitudeVisemeProvider()

    private val _mouthOpennessFlow = MutableStateFlow(0.0f)
    override val mouthOpennessFlow: StateFlow<Float> = _mouthOpennessFlow.asStateFlow()

    private val _visemeFlow = MutableStateFlow(VisemeState(Viseme.REST, 0.0f))
    override val visemeFlow: StateFlow<VisemeState> = _visemeFlow.asStateFlow()

    private val _mouthShapeFlow = MutableStateFlow(MouthShape.REST)
    override val mouthShapeFlow: StateFlow<MouthShape> = _mouthShapeFlow.asStateFlow()

    @Volatile private var speaking = false
    @Volatile private var startNanos = 0L

    override fun onSpeechStart() {
        speaking = true
        startNanos = System.nanoTime()
        visemeProvider.onSpeechStart()
        _mouthOpennessFlow.value = 0.25f
        _visemeFlow.value = VisemeState(Viseme.M_B_P, 0.25f)
        _mouthShapeFlow.value = MouthShape.SMALL_OPEN
    }

    override fun onSpeechEnd() {
        speaking = false
        visemeProvider.onSpeechEnd()
        _mouthOpennessFlow.value = 0.0f
        _visemeFlow.value = VisemeState(Viseme.REST, 0.0f)
        _mouthShapeFlow.value = MouthShape.REST
    }

    override fun onAmplitude(amplitude: Float) {
        if (!speaking) return
        val clamped = amplitude.coerceIn(0f, 1f)
        visemeProvider.onAudioFrame(clamped)
        val state = visemeProvider.getCurrentViseme()
        _mouthOpennessFlow.value = state.intensity
        _visemeFlow.value = state
        _mouthShapeFlow.value = mapToMouthShape(state.intensity, state.viseme)
    }

    override fun currentMouthOpenness(): Float {
        if (!speaking) return 0f
        val state = visemeProvider.getCurrentViseme()
        if (state.intensity > 0.05f) {
            return state.intensity
        }
        val t = (System.nanoTime() - startNanos) / 1_000_000_000.0
        val envelope = (sin(t * Math.PI * 2 * 2.5) * 0.5 + 0.5).coerceIn(0.0, 1.0)
        return (envelope * 0.75 + 0.1).toFloat()
    }

    override fun currentViseme(): VisemeState {
        return if (speaking) visemeProvider.getCurrentViseme() else VisemeState(Viseme.REST, 0.0f)
    }

    override fun currentMouthShape(): MouthShape {
        return if (speaking) _mouthShapeFlow.value else MouthShape.REST
    }

    override fun interrupt() {
        speaking = false
        visemeProvider.onSpeechEnd()
        _mouthOpennessFlow.value = 0.0f
        _visemeFlow.value = VisemeState(Viseme.REST, 0.0f)
        _mouthShapeFlow.value = MouthShape.REST
    }

    override fun reset() {
        interrupt()
    }

    override fun release() {
        interrupt()
    }
}