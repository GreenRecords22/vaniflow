# VaniFlow Offline Mode & Degradation Hierarchy

## 1. Degradation Matrix

VaniFlow automatically determines the optimal on-device conversational pipeline based on local asset availability:

| Mode | Audio Pipeline | AI Inference | Feedback Engine |
| :--- | :--- | :--- | :--- |
| **`FULL_OFFLINE_AI`** | 16kHz PCM $\rightarrow$ Zipformer STT | Local Qwen2.5 GGUF | Local Indian English Rules + CEFR Vocab |
| **`STT_TTS_ONLY`** | 16kHz PCM $\rightarrow$ Zipformer STT | Local Scenario Matrix | Local Indian English Rules + CEFR Vocab |
| **`FALLBACK_ONLY`** | Android Audio $\rightarrow$ Mock STT | Conversational Matrix | Local Indian English Rules + CEFR Vocab |
| **`ONLINE_OPTIONAL`** | 16kHz PCM $\rightarrow$ Zipformer STT | Optional Cloud / Local Fallback | Local Indian English Rules + CEFR Vocab |

---

## 2. Airplane Mode Guarantees

- Zero network socket requests initiated when in offline modes.
- STT acoustic decoding operates strictly on local tensors.
- TTS voice synthesis outputs directly to AudioTrack with zero cloud round-trips.
- User receives friendly state notices (e.g. "Offline AI isn't downloaded yet. Tap to download") rather than technical error codes.
