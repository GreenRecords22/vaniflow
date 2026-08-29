# VaniFlow — Testing Strategy & Quality Assurance

## 1. Testing Strategy & Pyramid

VaniFlow employs a multi-tiered testing strategy ensuring high reliability across asynchronous audio streams, state machine transitions, offline model persistence, and UI rendering:

```
                      ▲
                     / \
                    /   \     10% UI & End-to-End Tests
                   / UI  \    (Compose Tests, Navigation, Flows)
                  /───────\
                 /         \   20% Integration Tests
                / Integrat. \  (Pipeline, Room, DataStore, VAD)
               /─────────────\
              /               \ 70% Unit Tests
             /    Unit Tests   \(ViewModels, StateMachine, AI Router)
            /───────────────────\
```

---

## 2. Unit Testing Scope (70%)

Unit tests run on the JVM without requiring an Android emulator or device:

### 2.1. Conversation State Machine
- **State Transitions:** Verify deterministic transitions between `IDLE` -> `LISTENING` -> `USER_STARTED_SPEAKING` -> `USER_STOPPED_SPEAKING` -> `THINKING` -> `AI_SPEAKING`.
- **Interruption Behavior:** Validate that an incoming `UserSpeakingEvent` while in `AI_SPEAKING` instantly cancels active jobs and transitions to `USER_STARTED_SPEAKING`.
- **Timeout Recovery:** Ensure that unresponsive engines trigger graceful fallback transitions rather than indefinite hanging.

### 2.2. SmartAIRouter & Quota Management
- **Budget Threshold Transitions:** Verify switching behavior across 60% (cache), 70% (local prefer), 75% (traffic reduce), and 80% (hard stop).
- **Circuit Breaker:** Assert transitions from `CLOSED` to `OPEN` after 3 consecutive failures, and verify probe testing in `HALF_OPEN`.

### 2.3. ViewModels & UDF
- Assert that user actions result in expected `UiState` emissions using Turbine and Kotlin Coroutine `TestDispatcher`.
- Verify error handling and one-off `UiEvent` channels.

### 2.4. Selective Feedback Engine
- Test classification of grammatical errors into `MINOR` (ignored during flow) and `MAJOR` (included in summary).

---

## 3. Integration Testing Scope (20%)

Integration tests validate the interaction between multiple decoupled subsystems:

### 3.1. Full Conversational Pipeline (STT -> AI -> TTS)
- Feed synthetic 16kHz PCM audio buffers into `SileroVAD` -> `STTEngine` -> `ConversationEngine` -> `AIEngine` -> `TTSEngine`.
- Assert that sentence boundary token streaming triggers TTS synthesis with zero dropped audio chunks.

### 3.2. Local Storage & Room DAOs
- Use in-memory SQLite database (`Room.inMemoryDatabaseBuilder`) to test CRUD queries, cascade deletions on `SessionEntity`, and transaction rollbacks.
- Test `PreferencesDataStore` schema migrations.

### 3.3. Offline Mode Transitions
- Verify that cutting network connectivity smoothly demotes the AI route to `LocalAIEngine` or `FallbackAIEngine` without throwing unhandled exceptions.

### 3.4. Model Package Lifecycle
- Validate model download flow, pause/resume chunk integrity, SHA-256 checksum validation, and safe deletion.

---

## 4. UI & End-to-End Testing Scope (10%)

UI tests utilize `createAndroidComposeRule<MainActivity>`:
- **Onboarding Flow:** Launch as new guest -> select English level -> complete setup -> land on Home.
- **Character Selection:** Navigate from Home -> Choose Rudra -> Assert active partner badge updates.
- **Scenario Launch:** Select "Order Coffee" -> Tap Mic -> Verify animated waveform reflects speaking state.
- **Session Completion:** End session -> Assert session summary displays accurate turn count, fluency score, and mistake breakdown.

---

## 5. Performance & Latency Benchmarks

| Metric | Target SLA | Critical Threshold | Measurement Method |
| :--- | :--- | :--- | :--- |
| **Cold App Startup** | < 1.2s | < 2.0s | Android Jetpack Macrobenchmark |
| **VAD Speech Detection Latency** | < 40ms | < 80ms | Time from voice frame to speech onset event |
| **STT Transcription Time (2s audio)** | < 350ms | < 700ms | Pipeline timestamp delta |
| **Time to First Audio (TTFA)** | < 600ms | < 1200ms | Delta from user stop speaking to first TTS audio sample |
| **UI Frame Rate** | 60 / 120 fps | No jank (>16ms frames < 1%) | Compose Frame Timing Metric |
| **Peak RAM (with Local SLM loaded)** | < 1.4 GB | < 1.9 GB | Android Profiler Memory Allocation |

---

## 6. Device Test Matrix

Testing must be validated across the three core hardware tiers:

```
┌────────────────────────────────────────────────────────────────────────┐
│ LOW TIER (3GB - 4GB RAM)                                               │
│ Target: Android 10 (API 29) • MediaTek Helio G35 / Snapdragon 665     │
│ Validation: Smooth System TTS, Sherpa Tiny STT, Zero OOM crashes       │
├────────────────────────────────────────────────────────────────────────┤
│ MID TIER (6GB RAM)                                                     │
│ Target: Android 12/13 (API 31/33) • Snapdragon 778G / Dimensity 7050   │
│ Validation: Whisper Base STT, Piper Neural TTS, Qwen 0.5B Local SLM    │
├────────────────────────────────────────────────────────────────────────┤
│ HIGH TIER (8GB+ RAM)                                                   │
│ Target: Android 14+ (API 34) • Snapdragon 8 Gen 2/3 / Dimensity 9300   │
│ Validation: Whisper Small STT, Studio TTS, Qwen 1.5B/3B Local SLM      │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 7. Manual Testing & Acoustic Robustness

Automated tests must be complemented by real-world manual testing:

1. **Indian English Accent Diversity:** Test speech recognition across North Indian, South Indian, Eastern, and Western acoustic cadence variations.
2. **Ambient Noise Environments:** Test VAD speech onset accuracy in busy coffee shops, roadside traffic, and rooms with ceiling fan noise.
3. **Pacing & Hesitations:** Verify that natural speaking pauses (<900ms) do not cause premature cutoff or turn submission.

---

## 8. Disclaimer on Fluency Scoring

```
┌────────────────────────────────────────────────────────────────────────┐
│                      PEDAGOGICAL ACCURACY NOTICE                       │
│                                                                        │
│  Fluency scores (0–100) and grammar metrics generated by VaniFlow are  │
│  designed purely for user encouragement, progress tracking, and        │
│  formative self-reflection. They do NOT constitute scientific CEFR,   │
│  IELTS, or TOEFL accredited certifications.                            │
└────────────────────────────────────────────────────────────────────────┘
```
