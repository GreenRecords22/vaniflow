# VaniFlow End-to-End Real-Time Conversation Architecture

## 1. Complete Conversational Pipeline

The diagram below illustrates the complete verified vertical slice operating natively on Android:

```
[User Speaks into Android Microphone]
                    |
                    v
    [AndroidAudioRecordManager] (16kHz, 16-bit Mono PCM streaming)
                    |
                    v
          [EnergyVADEngine] (RMS dBFS + 900ms silence hangover)
                    |
                    +----[Voice Energy Detected]---> UI: USER_SPEAKING
                    |                                (Pulsing ring, Waveform)
                    v
      [RealOfflineSTTEngine] (Sherpa-ONNX / Vosk Indian English)
                    |
                    +----[Partial Tokens]----------> UI: Live Partial Transcript
                    |
                    v (VAD SPEECH_ENDED)
         [Final Utterance Text]
                    |
                    v
          [ConversationEngine]
                    |
        +-----------+-----------+
        |                       |
        v                       v
[FeedbackEngine]         [SmartAIRouter]
(Grammar & Phrasing)            |
        |                       +-----> [Level 0: Deterministic Rules]
        |                       |
        |                       +-----> [Persistent AIResponseCache] (Room DB)
        |                       |
        |                       +-----> [Level 1: Local Qwen2.5 SLM]
        |                       |
        |                       +-----> [Level 2: Optional Cloud Adapter]
        |                       |
        |                       +-----> [Level 3: Offline Dialogue Matrix]
        |                                       |
        +---------------------------------------+
                    |
                    v
            [AI Token Stream]
                    |
                    +----[Live Word Stream]--------> UI: AI Speech Bubble
                    |
                    v
           [SentenceSplitter] (Punctuation chunks: [. ! ? \n])
                    |
                    v
        [CancellableAudioQueue]
                    |
                    v
        [RealOfflineTTSEngine] (Piper Neural Voice / Android Native TTS)
                    |
                    v
         [Audio Heard by User]
```

---

## 2. Instant Interruption Protocol

When user speaks or taps the microphone during AI speech:
1. `EnergyVADEngine` detects voice onset (`VADState.SPEECH_STARTED`).
2. `ConversationViewModel` immediately calls `conversationEngine.interrupt()`.
3. `RealOfflineTTSEngine.stop()` terminates audio track playback in **< 40ms**.
4. Incomplete sentence buffers and pending token streams are drained immediately.
5. Conversation state shifts smoothly to `USER_SPEAKING` with zero audio clipping or overlapping speech.
