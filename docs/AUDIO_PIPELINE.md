# VaniFlow — Audio & Voice Pipeline

## 1. End-to-End Voice Pipeline

The audio pipeline forms the core real-time loop of VaniFlow. It manages hardware microphone capture, voice activity classification, speech recognition, conversational dispatch, text generation, speech synthesis, and hardware audio playback.

```
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│  Microphone  │ ──> │ AudioManager │ ──> │  Silero VAD   │ ──> │  STT Engine  │
│ (16kHz PCM)  │     │(Buffer/Gain) │     │ (ONNX Model) │     │(Whisper/Sher)│
└──────────────┘     └──────────────┘     └──────────────┘     └──────┬───────┘
                                                                      │
                                                                      ▼
┌──────────────┐     ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│ Audio Output │ <── │  TTS Engine  │ <── │ Sentence     │ <── │ Conversation │
│ (AudioTrack) │     │(Piper/System)│     │ Stream Split │     │  & AI Engine │
└──────────────┘     └──────────────┘     └──────────────┘     └──────────────┘
```

---

## 2. Voice Activity Detection (VAD)

### 2.1. Silero VAD (ONNX Runtime)
- **Model Size:** ~2.1 MB ONNX binary.
- **Frame Size:** 512 samples per frame (~32ms at 16kHz sample rate, 16-bit mono PCM).
- **Processing Time:** <1.5ms per frame on modern ARM CPUs.
- **Accuracy:** High noise-immunity across diverse ambient environments (street noise, office chatter).

### 2.2. VAD State Machine & Silence Thresholds

```
                      ┌─────────────────────────┐
                      │      STATE: IDLE        │
                      └────────────┬────────────┘
                                   │ Start Listening
                                   ▼
                      ┌─────────────────────────┐
             ┌──────> │    STATE: LISTENING     │ <────────────┐
             │        └────────────┬────────────┘              │
             │                     │                           │
             │                     │ VAD Speech Prob > 0.5     │
             │                     ▼                           │
             │        ┌─────────────────────────┐              │
             │        │ USER_STARTED_SPEAKING   │              │
             │        └────────────┬────────────┘              │
             │                     │                           │
             │                     │ Streaming Audio to STT    │
             │                     ▼                           │
             │        ┌─────────────────────────┐              │
             │        │    SPEECH_RECORDING     │              │
             │        └────────────┬────────────┘              │
             │                     │                           │
             │                     │ Silence Duration > 900ms  │
             │                     ▼                           │
             │        ┌─────────────────────────┐              │
             │        │  USER_STOPPED_SPEAKING  │              │
             │        └────────────┬────────────┘              │
             │                     │                           │
             │                     │ Finalize STT & Trigger AI │
             │                     ▼                           │
             │        ┌─────────────────────────┐              │
             │        │   AI_THINKING_SPEAKING  │              │
             │        └────────────┬────────────┘              │
             │                     │                           │
             │ Speech Detected     │ Normal Turn               │
             │ (Interruption)      │ Audio Finished            │
             └─────────────────────┴───────────────────────────┘
```

- **Speech Onset:** Triggered when VAD speech probability exceeds `0.55` across 2 consecutive frames (~64ms).
- **Speech Offset (Silence Window):** Triggered when silence (prob < 0.35) persists continuously for **900ms** (adjustable in settings between 700ms–1400ms based on user proficiency).

---

## 3. Speech-to-Text (STT) Abstraction

### 3.1. The `STTEngine` Contract

```kotlin
package com.vaniflow.app.engine.stt

import kotlinx.coroutines.flow.Flow
import java.io.File

interface STTEngine {
    val isReady: Boolean
    
    /**
     * Transcribes an in-memory PCM audio buffer or audio file.
     */
    suspend fun transcribe(audioData: ShortArray): Result<STTResult>
    
    /**
     * Real-time streaming transcription for low-latency live preview.
     */
    fun transcribeStream(audioStream: Flow<ShortArray>): Flow<STTStreamResult>
    
    suspend fun release()
}

data class STTResult(
    val transcript: String,
    val confidence: Float,
    val durationMs: Long
)
```

### 3.2. STT Candidate Evaluation

| Engine | Binary Footprint | Latency (2s audio) | Indian Accent Accuracy | Android Compatibility | Verdict |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **whisper.cpp** | ~40MB (tiny.en) / ~140MB (base.en) | 250ms – 450ms | Very High | Excellent (NDK/JNI NEON) | **Primary candidate for offline** |
| **Sherpa-ONNX (Zipformer)** | ~35MB – 60MB | 180ms – 320ms | High | Excellent (ONNX Runtime mobile) | **Secondary candidate (fastest)** |
| **Vosk (Kaldi)** | ~50MB | 200ms – 350ms | Moderate | Good | Baseline fallback |
| **Mock STT** | 0MB | 50ms | N/A | Universal | Used for UI tests & dev builds |

---

## 4. Text-to-Speech (TTS) Abstraction

### 4.1. The `TTSEngine` Contract

```kotlin
package com.vaniflow.app.engine.tts

import kotlinx.coroutines.flow.Flow

interface TTSEngine {
    val isReady: Boolean
    
    /**
     * Synthesizes text and plays directly through the active audio output.
     */
    suspend fun speak(
        text: String,
        voiceId: String,
        speechRate: Float = 1.0f
    ): Result<Unit>
    
    /**
     * Synthesizes audio stream chunks for buffered playback.
     */
    fun synthesizeStream(
        text: String,
        voiceId: String
    ): Flow<AudioChunk>
    
    /**
     * Instantly stops any playing or queued audio.
     */
    suspend fun stop()
}

data class AudioChunk(
    val pcmData: ByteArray,
    val sampleRate: Int = 22050
)
```

### 4.2. TTS Candidate Evaluation

| Engine | Voice Quality | Storage Size | Latency to First Sample | Indian English Voices | Verdict |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Piper TTS (VITS)** | High Neural | ~25MB – 40MB per voice | 80ms – 150ms | Yes (en_IN models available) | **Primary neural candidate** |
| **Sherpa-ONNX (VITS)** | High Neural | ~30MB per voice | 100ms – 180ms | Yes | **Strong alternative** |
| **Android System TTS** | Variable (Device) | 0MB (pre-installed) | 20ms – 50ms | System dependent | **Default lightweight baseline** |

---

## 5. Streaming & Sentence Boundary Splitting

To minimize perceived conversational latency (Time to First Audio):
1. The AI engine streams raw token chunks.
2. A `SentenceBuffer` listens to the token stream and inspects punctuation boundaries (`.`, `?`, `!`, `\n`).
3. As soon as the first complete sentence is assembled (typically 6–12 words, ~400ms into generation), it is dispatched immediately to the `TTSEngine`.
4. While Sentence 1 is being synthesized and played on `AudioTrack`, Sentence 2 is being generated by the AI in parallel.

```
AI Generation:  [ Sentence 1 tokens... ] ──> [ Sentence 2 tokens... ]
                     │                            │
                     ▼                            ▼
TTS Playback:   [ Synthesize & Play 1 ] ────> [ Synthesize & Play 2 ]
                ^ Time to First Audio ~500ms
```

---

## 6. Real-Time Interruption Handling

If the user starts speaking while the AI is thinking or speaking:
1. `SileroVAD` detects user voice energy (probability > 0.55).
2. An `AudioInterruptionEvent` is posted to the `ConversationStateMachine`.
3. Active AI text generation coroutine job is immediately cancelled (`job.cancel()`).
4. `TTSEngine.stop()` is invoked; active `AudioTrack` playback buffer is flushed.
5. Pending sentence audio queue is cleared.
6. The state machine transitions seamlessly to `USER_STARTED_SPEAKING` to capture the user's fresh utterance.
