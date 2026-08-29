# VaniFlow — Architecture Decision Records (ADRs)

## ADR Index

- **ADR-001:** Single-Module Architecture for MVP
- **ADR-002:** Dagger Hilt as Primary Dependency Injection Framework
- **ADR-003:** Provider-Agnostic AI Interface Abstraction
- **ADR-004:** Local-First, Offline-Capable Data Architecture
- **ADR-005:** Zero Login & No Monetization in MVP
- **ADR-006:** Stitch Prototype as Visual Reference Only
- **ADR-007:** Downloadable AI Models (Zero Weights in Base APK)
- **ADR-008:** Mock Engines First Development Strategy
- **ADR-009:** Explicit Conversation State Machine
- **ADR-010:** Selective & Non-Intrusive Error Correction

---

### ADR-001: Single-Module Architecture for MVP
- **Status:** Accepted
- **Context:** While multi-module Android projects provide strict encapsulation, they increase Gradle configuration overhead, slow down early prototyping velocity, and introduce build script complexity.
- **Decision:** Build the entire MVP within a single `:app` module while enforcing strict Clean Architecture package layering (`domain`, `data`, `engine`, `feature`, `ui`).
- **Consequences:** Faster build times and agile refactoring in early stages. Clean package boundaries ensure trivial migration to multi-module once the architecture stabilizes post-MVP.

---

### ADR-002: Dagger Hilt as Primary Dependency Injection Framework
- **Status:** Accepted
- **Context:** Need a robust, compile-time verified dependency injection framework that seamlessly integrates with Jetpack Compose, ViewModels, and Room.
- **Decision:** Use Google Dagger Hilt 2.5x.
- **Consequences:** Eliminates boilerplate service-locator patterns, provides compile-time verification of dependency graphs, and offers standard Android lifecycle bindings out of the box.

---

### ADR-003: Provider-Agnostic AI Interface Abstraction
- **Status:** Accepted
- **Context:** Direct coupling between UI screens/ViewModels and specific AI APIs (OpenAI, Gemini, OpenRouter) creates vendor lock-in and complicates local offline routing.
- **Decision:** All AI generation and evaluation must route through the `AIEngine` contract managed by `SmartAIRouter`. No UI component may reference a concrete provider.
- **Consequences:** New cloud vendors or local C++ inference engines (llama.cpp) can be plugged in without changing a single line of UI or ViewModel code.

---

### ADR-004: Local-First, Offline-Capable Data Architecture
- **Status:** Accepted
- **Context:** Learners frequently face network drops or high data costs. Sending all audio and personal conversation logs to cloud servers introduces privacy concerns and network latency.
- **Decision:** All profiles, turn histories, vocabulary vault items, and progress statistics are stored locally in SQLite (Room) and DataStore. Core dialogue and speech processing must function offline.
- **Consequences:** Full privacy for the learner, instantaneous app responsiveness, zero hosting costs for local storage, and uninterrupted learning during offline commutes.

---

### ADR-005: Zero Login & No Monetization in MVP
- **Status:** Accepted
- **Context:** Registration walls and subscription paywalls introduce major drop-off friction during initial user onboarding and product validation.
- **Decision:** Auto-generate a `GuestProfileEntity` on initial launch. Do not include authentication screens, cloud login, or payment gateways in the MVP.
- **Consequences:** Maximum conversion from install to first spoken sentence (<15 seconds). Focuses team bandwidth entirely on conversational quality and audio pipeline stability.

---

### ADR-006: Stitch Prototype as Visual Reference Only
- **Status:** Accepted
- **Context:** Google Stitch generated HTML/Tailwind web prototypes for design exploration.
- **Decision:** Stitch designs serve solely as visual references. All UI must be authored natively using Jetpack Compose and Material 3 design tokens (`Lumina AI Teal`). Never copy HTML/web code.
- **Consequences:** Clean, idiomatic Android UI with 60/120fps fluid Compose animations, native accessibility, and proper lifecycle management.

---

### ADR-007: Downloadable AI Models (Zero Weights in Base APK)
- **Status:** Accepted
- **Context:** Neural model weights for STT, TTS, and SLM range from 40MB to 1.5GB. Bundling weights into the APK would create an unacceptably large download.
- **Decision:** Base APK contains only core code and lightweight Silero VAD (~30MB total APK size). Neural models are downloaded on-demand post-install via `ModelManager`.
- **Consequences:** Rapid Google Play Store installs. Users only download model packages compatible with their specific device RAM and storage tier.

---

### ADR-008: Mock Engines First Development Strategy
- **Status:** Accepted
- **Context:** Integrating native NDK libraries (llama.cpp, Whisper C++) early can block UI and business logic development if compilation or JNI bindings face delays.
- **Decision:** Build complete mock implementations (`MockAIEngine`, `MockSTTEngine`, `MockTTSEngine`) first to test state machines, UI flows, and navigation.
- **Consequences:** Team can build and test 100% of the user experience and state machine transitions before integrating native binaries.

---

### ADR-009: Explicit Conversation State Machine
- **Status:** Accepted
- **Context:** Managing conversational voice state across asynchronous VAD events, user interruptions, STT streaming, and TTS synthesis using scattered boolean flags leads to race conditions and inconsistent UI states.
- **Decision:** Implement a centralized `ConversationStateMachine` with mutually exclusive sealed states: `IDLE`, `LISTENING`, `USER_STARTED_SPEAKING`, `SPEECH_RECORDING`, `USER_STOPPED_SPEAKING`, `THINKING`, `AI_SPEAKING`, `INTERRUPTED`.
- **Consequences:** Deterministic state transitions, zero audio overlap bugs, and simplified UI state mapping.

---

### ADR-010: Selective & Non-Intrusive Error Correction
- **Status:** Accepted
- **Context:** Constant linguistic corrections during live speech destroy conversational confidence and disrupt cognitive fluency for language learners.
- **Decision:** Never interrupt live spoken dialogue for minor grammatical errors. Minor slips are ignored during conversation; significant errors are flagged non-intrusively and compiled into the post-session summary.
- **Consequences:** Users feel comfortable speaking freely and building natural conversational cadence without fear of immediate criticism.
