# VaniFlow — System Architecture & Technical Design

## 1. Overview & Architectural Philosophy

VaniFlow is built using modern Android best practices:
- **Clean Architecture + MVVM** (Model-View-ViewModel)
- **Unidirectional Data Flow (UDF)** with Kotlin Coroutines & `StateFlow`
- **Declarative UI** via Jetpack Compose & Material 3
- **Dependency Injection** via Google Dagger Hilt
- **Local-First Data Storage** via Room SQLite & Preferences DataStore
- **Single-Module MVP** structured with strict package boundaries for seamless future multi-module migration.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                         Presentation Layer (UI)                          │
│        Jetpack Compose Screens • ViewModels • UI State (StateFlow)       │
└────────────────────────────────────┬─────────────────────────────────────┘
                                     │
                                     ▼
┌──────────────────────────────────────────────────────────────────────────┐
│                           Domain Layer (Core)                            │
│           Domain Models • Use Cases / Interactors • Repository Interfaces│
└───────────────────┬──────────────────────────────────┬───────────────────┘
                    │                                  │
                    ▼                                  ▼
┌──────────────────────────────────────┐  ┌────────────────────────────────┐
│          Data Layer (Storage)        │  │       Engine Layer (AI/Audio)  │
│ Room DB • DataStore • Remote Clients │  │ Audio • VAD • STT • AI • TTS   │
└──────────────────────────────────────┘  └────────────────────────────────┘
```

---

## 2. Technology Stack

| Layer / Concern | Technology Selection | Justification & Role |
| :--- | :--- | :--- |
| **Language** | Kotlin 2.1 | Modern language features, strong type-safety, robust coroutines. |
| **UI Framework** | Jetpack Compose (BOM) | Modern declarative UI, dynamic animations, composable state sharing. |
| **Design System** | Material 3 (Material You) | Lumina AI Teal color scheme, dynamic theming, accessibility tokens. |
| **Dependency Injection** | Dagger Hilt 2.5x | Compile-time safety, standard Android integration, ViewModel scoping. |
| **Local Database** | Room Database 2.6x | SQLite ORM with type-safe queries, migration support, Flow observers. |
| **Key-Value Store** | Jetpack Preferences DataStore | Asynchronous, transactional storage for app preferences and flags. |
| **Async / Streams** | Kotlin Coroutines & Flow | Asynchronous non-blocking pipelines, backpressure support, state sharing. |
| **Networking** | Ktor Client 3.x | Lightweight, multiplatform HTTP client with CIO engine for JSON/streaming. |
| **Image Loading** | Coil 3.x | Fast Compose-native asynchronous image loading and memory caching. |
| **Serialization** | `kotlinx.serialization` | Fast, reflection-free JSON encoding and decoding for APIs and navigation. |
| **Navigation** | Compose Navigation 2.8+ | Type-safe navigation with Kotlin `@Serializable` objects. |

---

## 3. Package Structure

The project employs a feature-first and layer-aware package layout under `com.vaniflow.app`:

```
com.vaniflow.app
│
├── App.kt                           # Application class (@HiltAndroidApp)
├── MainActivity.kt                  # Single Activity host for Compose Navigation
│
├── ui
│   ├── theme                        # Lumina AI Teal design tokens
│   │   ├── Color.kt
│   │   ├── Type.kt
│   │   ├── Shape.kt
│   │   └── Theme.kt
│   ├── components                   # Shared atomic Compose components
│   │   ├── AppButton.kt
│   │   ├── VoiceWaveform.kt
│   │   ├── CharacterCard.kt
│   │   ├── ScenarioCard.kt
│   │   ├── MetricTile.kt
│   │   └── BottomNavigationBar.kt
│   └── navigation                   # Type-safe routing definitions
│       ├── NavGraph.kt
│       └── Destinations.kt
│
├── feature                          # Feature screens & ViewModels
│   ├── home                         # Home dashboard & quick launcher
│   ├── practice                     # Scenario & character practice hub
│   ├── conversation                 # Live voice speaking screen
│   ├── session_summary              # Post-session evaluation & feedback
│   ├── progress                     # User metrics, streaks & analytics
│   ├── profile                      # User settings & offline models
│   ├── onboarding                   # Initial guest setup & goal selection
│   └── model_manager                # Download & manage AI models
│
├── domain
│   ├── model                        # Pure business models (no framework dependencies)
│   │   ├── Character.kt
│   │   ├── Scenario.kt
│   │   ├── ConversationTurn.kt
│   │   ├── Session.kt
│   │   ├── Feedback.kt
│   │   ├── ProgressStats.kt
│   │   └── ModelInfo.kt
│   ├── repository                   # Abstract repository interfaces
│   │   ├── CharacterRepository.kt
│   │   ├── ScenarioRepository.kt
│   │   ├── ConversationRepository.kt
│   │   ├── ProgressRepository.kt
│   │   ├── SettingsRepository.kt
│   │   └── ModelRepository.kt
│   └── usecase                      # Business logic orchestrators
│       ├── StartSessionUseCase.kt
│       ├── ProcessVoiceTurnUseCase.kt
│       ├── EvaluateTurnFeedbackUseCase.kt
│       ├── GetProgressSummaryUseCase.kt
│       └── ManageOfflineModelUseCase.kt
│
├── data
│   ├── local
│   │   ├── db                       # Room Database, Entities, DAOs
│   │   │   ├── VaniFlowDatabase.kt
│   │   │   ├── entity/
│   │   │   ├── dao/
│   │   │   └── converter/
│   │   └── datastore                # Preferences DataStore & User Preferences
│   │       ├── PreferencesDataStore.kt
│   │       └── UserPreferences.kt
│   ├── remote                       # Remote API clients, DTOs & fallback routes
│   │   ├── KtorClientProvider.kt
│   │   ├── dto/
│   │   └── CloudAIClient.kt
│   └── repository                   # Concrete repository implementations
│       ├── CharacterRepositoryImpl.kt
│       ├── ScenarioRepositoryImpl.kt
│       ├── ConversationRepositoryImpl.kt
│       ├── ProgressRepositoryImpl.kt
│       ├── SettingsRepositoryImpl.kt
│       └── ModelRepositoryImpl.kt
│
├── engine                           # Autonomous intelligence & audio subsystems
│   ├── ai                           # AI engine interface, router & providers
│   │   ├── AIEngine.kt
│   │   ├── SmartAIRouter.kt
│   │   ├── LocalAIEngine.kt
│   │   ├── CloudAIEngine.kt
│   │   └── FallbackAIEngine.kt
│   ├── conversation                 # Core conversational state machine
│   │   ├── ConversationEngine.kt
│   │   ├── ConversationState.kt
│   │   └── ConversationStateMachine.kt
│   ├── audio                        # Audio hardware, recorder & player
│   │   ├── AudioRecorder.kt
│   │   ├── AudioPlayer.kt
│   │   └── AudioManager.kt
│   ├── vad                          # Voice Activity Detection (Silero ONNX)
│   │   ├── VADEngine.kt
│   │   └── SileroVADEngine.kt
│   ├── stt                          # Speech-to-Text abstraction
│   │   ├── STTEngine.kt
│   │   ├── WhisperCppSTTEngine.kt
│   │   └── SherpaOnnxSTTEngine.kt
│   ├── tts                          # Text-to-Speech abstraction
│   │   ├── TTSEngine.kt
│   │   ├── PiperTTSEngine.kt
│   │   └── SystemTTSEngine.kt
│   ├── learning                     # Feedback & pedagogical logic
│   │   ├── SelectiveFeedbackEngine.kt
│   │   ├── GrammarEvaluator.kt
│   │   └── VocabularyExtractor.kt
│   ├── character                    # Persona prompts & system rules
│   │   └── CharacterPromptProvider.kt
│   ├── scenario                     # Scenario scripts & state trackers
│   │   └── ScenarioScriptManager.kt
│   ├── progress                     # Streak & metric calculation engine
│   │   └── ProgressCalculator.kt
│   └── model                        # Model download & verification manager
│       ├── ModelDownloader.kt
│       ├── ChecksumValidator.kt
│       └── ModelStorageManager.kt
│
├── di                               # Hilt Dependency Injection Modules
│   ├── AppModule.kt
│   ├── DatabaseModule.kt
│   ├── RepositoryModule.kt
│   ├── EngineModule.kt
│   └── UseCaseModule.kt
│
└── util                             # Helper utilities, extensions, loggers
    ├── CoroutineDispatchers.kt
    ├── Result.kt
    ├── AudioUtils.kt
    └── Logger.kt
```

---

## 4. UI Architecture & Navigation

### 4.1. MVVM + Unidirectional Data Flow (UDF)

Each feature screen follows strict UDF:
1. **UiState:** An immutable Kotlin data class describing the complete screen state.
2. **UiEvent:** One-off side effects (e.g., Navigate, ShowSnackbar) emitted via `SharedFlow` or `Channel`.
3. **UserAction:** Sealed interface representing user interactions dispatched to the `ViewModel`.

```
     ┌────────────────────────────────────────────────────────┐
     │                     Compose Screen                     │
     └─────────────┬────────────────────────────▲─────────────┘
                   │ User Action                │ UI State
                   ▼                            │ (StateFlow)
     ┌──────────────────────────────────────────┴─────────────┐
     │                       ViewModel                        │
     └─────────────┬────────────────────────────▲─────────────┘
                   │ Execute Use Case           │ Domain Model
                   ▼                            │
     ┌──────────────────────────────────────────┴─────────────┐
     │                        Use Case                        │
     └─────────────┬────────────────────────────▲─────────────┘
                   │ Query / Mutate             │ Data Stream
                   ▼                            │ (Flow)
     ┌──────────────────────────────────────────┴─────────────┐
     │                       Repository                       │
     └────────────────────────────────────────────────────────┘
```

### 4.2. Navigation Structure

Navigation is managed via Compose Navigation 2.8+ using Kotlin `@Serializable` destinations:

```kotlin
@Serializable object HomeRoute
@Serializable object PracticeRoute
@Serializable object ProgressRoute
@Serializable object ProfileRoute

@Serializable data class ConversationRoute(
    val characterId: String,
    val scenarioId: String
)

@Serializable data class SessionSummaryRoute(
    val sessionId: String
)

@Serializable object OnboardingRoute
@Serializable object ModelManagerRoute
```

#### Bottom Navigation Destinations
- **Home:** Daily speaking target, quick 5-min practice launch with active character, daily progress badge.
- **Practice:** Scenario catalog (filter by Daily Life, Travel, Work, Interview) and character selector.
- **Progress:** Weekly speaking streak, cumulative practice time, fluency trajectory, and Vocabulary Vault.
- **Profile:** Offline models management, audio sensitivity, voice speed, learning preferences.

---

## 5. Dependency Injection (Hilt) Setup

Hilt provides organized, compile-time verified modules:

- **`DatabaseModule` (`@Singleton`):** Provides Room `VaniFlowDatabase`, DAOs (`ProfileDao`, `CharacterDao`, `ScenarioDao`, `SessionDao`, `ProgressDao`, `VocabularyDao`).
- **`DataStoreModule` (`@Singleton`):** Provides `DataStore<Preferences>`.
- **`NetworkModule` (`@Singleton`):** Provides configured Ktor `HttpClient`.
- **`EngineModule` (`@Singleton`):** Provides `AudioManager`, `VADEngine`, `STTEngine`, `TTSEngine`, `AIEngine`, and `SmartAIRouter`.
- **`RepositoryModule` (`@Binds`):** Binds domain repository interfaces to data layer implementations.
- **`UseCaseModule`:** Provides reusable business use cases.

---

## 6. Multi-Module Migration Strategy (Future)

While the MVP is built as a single Gradle module (`:app`) to accelerate velocity, the codebase adheres to strict boundaries:
- Domain layer has **zero Android framework dependencies**.
- Engine implementations are hidden behind domain interfaces.
- Feature screens only communicate via UseCases and ViewModels.

When the project scales post-MVP, it can cleanly split into:
- `:core:model` (Pure Kotlin domain entities)
- `:core:database` (Room database and DAOs)
- `:core:network` (Ktor HTTP client and API adapters)
- `:core:audio` (AudioRecord, AudioTrack, VAD)
- `:core:engine-ai` (Local & cloud AI routing)
- `:core:ui` (Lumina AI Teal components and theme)
- `:feature:conversation` (Speaking screen and state machine)
- `:feature:practice` (Scenarios and characters)
- `:feature:progress` (Metrics and analytics)
- `:feature:settings` (Model downloads and preferences)
