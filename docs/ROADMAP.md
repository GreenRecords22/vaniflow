# VaniFlow — Product Roadmap & Execution Plan

## 1. Roadmap Philosophy

VaniFlow follows a **milestone-driven execution framework**. Quality, low-latency audio stability, and conversational naturalness supersede arbitrary calendar deadlines. Each milestone delivers a verifiable, testable increment.

```
┌────────────────────────────────────────────────────────────────────────┐
│  MVP Phase (Milestones 1 – 13)                                         │
│  Foundation ──> Audio Pipeline ──> Engines ──> Core UI ──> Hardening   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│  Post-MVP Growth & Advanced Intelligence (Phases 1 – 10)               │
│  Pronunciation • Multiplatform KMP • Custom Voices • CEFR Analytics   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. MVP Milestones (Milestones 1 – 13)

### Milestone 1: Project Scaffolding, Architecture & Theme Tokens
- Setup Android Studio Gradle build with Kotlin 2.1, Jetpack Compose BOM, Material 3, and Dagger Hilt.
- Implement `Lumina AI Teal` theme tokens (`Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`).
- Configure Navigation 2.8+ type-safe destination routes.

### Milestone 2: Local Data Layer
- Implement `VaniFlowDatabase` Room schema (v1) with DAOs for profiles, characters, scenarios, sessions, turns, scores, vocabulary, mistakes, progress, and model records.
- Setup `PreferencesDataStore` for user preferences and settings.
- Implement repository interfaces and concrete data layer implementations.

### Milestone 3: Audio Hardware & Silero VAD Integration
- Build low-level `AudioRecorder` utilizing `AudioRecord` (16kHz 16-bit mono PCM).
- Integrate `Silero VAD` (ONNX Runtime mobile) for fast speech onset and silence offset detection.
- Build `AudioPlayer` utilizing low-latency `AudioTrack`.

### Milestone 4: Speech-to-Text & Text-to-Speech Engines
- Implement `STTEngine` interface and bind mock engine; prepare bindings for `whisper.cpp` / `Sherpa-ONNX`.
- Implement `TTSEngine` interface and bind `SystemTTSEngine` (Android platform TTS) and `MockTTSEngine`.
- Validate audio cancellation and interruption hooks.

### Milestone 5: AI Engine Interface & SmartAIRouter
- Build unified `AIEngine` abstraction with Flow-based token streaming.
- Implement `SmartAIRouter`, `ProviderConfig`, `QuotaManager`, and `CircuitBreaker`.
- Implement `FallbackAIEngine` with deterministic rule-based dialogue scripts.

### Milestone 6: Conversation State Machine & Pipeline Integration
- Implement `ConversationStateMachine` with explicit state transitions (`IDLE`, `LISTENING`, `USER_SPEAKING`, `THINKING`, `AI_SPEAKING`, `INTERRUPTED`).
- Connect VAD -> STT -> ConversationEngine -> AI -> Sentence Splitter -> TTS -> AudioTrack.

### Milestone 7: Shared Atomic UI Component Library
- Implement reusable UI widgets: animated `VoiceWaveform`, `CharacterCard`, `ScenarioCard`, `MetricTile`, and custom `BottomNavigationBar`.
- Extract button styles and chip filters.

### Milestone 8: Core Flow: Home Dashboard & Onboarding
- Build `OnboardingScreen` for rapid goal selection without mandatory login.
- Build `HomeScreen` with 5-minute quick speaking launcher, daily progress tile, and active character display.

### Milestone 9: Core Flow: Character & Scenario Selection
- Build `CharacterSelectionScreen` showcasing the 4 AI personalities (Raya, Rudra, Adwaita, Shub).
- Build `ScenarioSelectionScreen` showcasing the 6 structured practice scenarios.

### Milestone 10: Core Flow: Live Speaking Screen
- Build `ConversationScreen` with real-time audio visualization, live speech transcript, instant interruption feedback, and turn timer.

### Milestone 11: Core Flow: Session Summary & Selective Feedback
- Build `SessionSummaryScreen` with overall fluency score, turn-by-turn replay, grammar improvement cards, and new vocabulary drawer.

### Milestone 12: Core Flow: Progress Dashboard & Vocabulary Vault
- Build `ProgressScreen` with weekly practice streaks, cumulative speaking minutes, and interactive `VocabularyVault`.

### Milestone 13: Model Manager, Offline Validation & Hardening
- Build `ModelManagerScreen` for on-demand model downloading, checksum validation, and storage monitoring.
- Perform end-to-end integration tests and memory leak profiling.

---

## 3. Post-MVP Growth Phases

```
┌───────────┐    ┌───────────┐    ┌───────────┐    ┌───────────┐    ┌───────────┐
│  Phase 1  │───>│  Phase 2  │───>│  Phase 3  │───>│  Phase 4  │───>│  Phase 5  │
│  Adaptive │    │  Phoneme  │    │ Personal  │    │ Dynamic   │    │ 3D Avatar │
│ Conversat.│    │ Pronunc.  │    │ AI Coach  │    │ Scenarios │    │ Visuals   │
└───────────┘    └───────────┘    └───────────┘    └───────────┘    └───────────┘
```

- **Phase 1: Advanced Conversational Flow:** Filler word detection ("um", "uh", "like"), pacing analysis, and dynamic conversational tone adjustments.
- **Phase 2: Phoneme-Level Pronunciation Scoring:** Acoustic phoneme alignment to identify specific syllable mispronunciations common in Indian English accents.
- **Phase 3: Personalized AI Coach:** Long-term memory of recurring user mistakes and auto-tailored practice suggestions.
- **Phase 4: Dynamic Branching Scenarios:** Multi-path roleplay scenarios and user-created custom scenario prompts.
- **Phase 5: Realistic AI Character Expressions:** Subtle facial animations and emotional state visualization during speech.
- **Phase 6: Multi-Voice Ecosystem:** Expanded voice packs including British, American, and diverse Indian regional English accents.
- **Phase 7: Optional Cloud Sync & Backup:** Encrypted cross-device backup for users who opt to create an account.
- **Phase 8: Advanced CEFR Analytics:** Formal mapping of user fluency trajectory to international CEFR benchmarks (A1 through C2).
- **Phase 9: Premium Experience & Offline Model Bundles:** Pro-tier high-accuracy on-device SLMs with NPU acceleration.
- **Phase 10: Multiplatform Expansion:** Port business logic, domain layers, and UI to iOS and Desktop via **Kotlin Multiplatform (KMP)** and Compose Multiplatform.
