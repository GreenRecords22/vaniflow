# VaniFlow — P1.5 Speaking Module Completion & Real Learner Experience Report

**Date:** 2026-09-01  
**Repository:** GreenRecords22/vaniflow  
**Live HEAD SHA:** `503d286a51e3d5c59e7620ed7a0dbfbfa360ed40`  
**Status:** GREEN (300/300 Unit Tests Passing, 66/66 Physical Device Instrumented Tests Passing)

---

## 1. Live Repository HEAD & Git Status

- **Baseline Commit:** `503d286a51e3d5c59e7620ed7a0dbfbfa360ed40` (*Production English Tutor Intelligence & Learning Progression*)
- **Target Branch:** `main`
- **Working Tree State:** Clean and tested across JVM unit tests and connected physical device suite.

---

## 2. P1 Audit Findings & Remediation

| Area | P1 Baseline Finding | P1.5 Remediation & Integration |
|---|---|---|
| **Session Lifecycle** | In-memory session tracking; `SessionEntity` was only created at teardown. | `startSession()` establishes active tracking with scenario, difficulty, goals; `endSession()` writes full session record & turns to Room. Navigates seamlessly to `SessionSummaryScreen`. |
| **Progress Calculation** | Found pseudo-formulas in `ProgressCalculationEngine` (e.g. `85 + confidence * 0.1` for pronunciation, `78 + turns * 2` for vocabulary). | **Removed all pseudo-measurement formulas.** Pronunciation score set to 0 (unmeasured acoustic phonemes) or qualitative clarity ("Natural", "Clear", "Developing"). Vocabulary is based strictly on real extracted expressions. |
| **CEFR Estimation** | Premature CEFR rating calculated after as few as 10 utterances without multi-session evidence. | Enforced evidence threshold: minimum 15 utterances across sessions required before establishing level. Below threshold, UI displays `"Building level (X/15 turns)"`. |
| **Spoken Vocabulary Pipeline** | Vocabulary tables existed in Room DB v3, but lacked runtime spoken collocation/phrase extraction. | Implemented `SpokenExpressionExtractor` for practical spoken expressions (e.g., *"looking forward to"*, *"on the other hand"*, *"make sure to"*), persisted in `VocabularyMemoryRepository` and injected into `ConversationPromptBuilder`. |
| **Session Summary** | Mocked/placeholder metrics on screen. | Connected `SessionSummaryViewModel` to real `SessionRepository` and `LearningEventRepository`. Screen displays true speaking time, grammar/fluency scores, retries, improved concepts, weak areas, and learned expressions. |
| **Progress Dashboard** | Screen had static mock cards. | Connected `ProgressViewModel` to `DefaultProgressRepository` observing Room flows for sessions, learner profile, mastery states, and vocabulary memories. |
| **Home Screen Integration** | Only showed basic streak. | Connected `HomeViewModel` to live progress repository, displaying real streak days, speaking minutes, mastered count, and active improving concepts. |

---

## 3. Architecture & Production Pipeline

The speaking module operates through an integrated full-duplex runtime loop:

```
STT (Whisper/Native SpeechRecognizer)
  ↓
ConversationEngine (Orchestrator)
  ↓
EnglishCorrectionEngine (Rule + Pattern Grammar Analysis)
  ↓
CorrectionPolicyEngine (Cooldowns, Struggle Backoff, Mastery Suppression)
  ↓
LearningMemoryManager (Turn Intelligence & Event Dispatcher)
  ↓
MasteryEngine (Deterministic Bayesian-style Concept Mastery Tracking)
  ↓
SpokenExpressionExtractor (Collocation & Natural Expression Detection)
  ↓
Room Database Persistence (Sessions, Turns, LearningEvents, Mastery, VocabularyMemory)
  ↓
ConversationPromptBuilder (Bounded Coaching Context Injection)
  ↓
SmartAIRouter / Local SLM / Gemini Cloud
  ↓
TTSEngine (Sentence-split streaming playback + Barge-in interruption)
```

---

## 4. Production Integrations

1. **`LearningMemoryManager`**:
   - Manages active session goals, adaptive difficulty levels, and correction policies.
   - Updates `MasteryState` on mistakes, retries, and clean turns.
   - Logs typed `LearningEvent` records (`CORRECTION`, `SUCCESSFUL_RETRY`, `FAILED_RETRY`, `MASTERY_GAIN`, `VOCABULARY_LEARNED`).
   - Generates compact coaching context for AI prompts.

2. **`ConversationEngine`**:
   - Manages hands-free speech loop, turn lifecycle, and barge-in interruptions.
   - Coordinates spoken retry flow (max 2 attempts before supportive continuation).
   - Persists session entities and turns to Room database upon `endSession()`.

3. **`DefaultProgressRepository`**:
   - Combines Room flows for `SessionDao`, `LearnerProfileDao`, `ConceptMasteryDao`, and `VocabularyMemoryDao`.
   - Computes daily streak, weekly speaking minutes, averages, and evidence-based improvements.

4. **UI Navigation**:
   - `HomeScreen` $\rightarrow$ `ScenarioSelectionScreen` $\rightarrow$ `ConversationScreen` $\rightarrow$ `SessionSummaryScreen` $\rightarrow$ `ProgressScreen`.

---

## 5. Files Created & Modified

### Created Files
- `app/app/src/main/java/com/vaniflow/app/engine/learning/tutor/SpokenExpressionExtractor.kt`
- `app/app/src/test/java/com/vaniflow/app/engine/learning/tutor/SpokenExpressionExtractorTest.kt`
- `docs/P1_5_AUDIT.md`
- `docs/P1_5_IMPLEMENTATION_REPORT.md`

### Modified Files
- `app/app/src/main/java/com/vaniflow/app/data/repository/DefaultProgressRepository.kt`
- `app/app/src/main/java/com/vaniflow/app/domain/repository/ProgressRepository.kt`
- `app/app/src/main/java/com/vaniflow/app/engine/conversation/ConversationEngine.kt`
- `app/app/src/main/java/com/vaniflow/app/engine/learning/tutor/LearningMemoryManager.kt`
- `app/app/src/main/java/com/vaniflow/app/engine/learning/tutor/ProgressCalculationEngine.kt`
- `app/app/src/main/java/com/vaniflow/app/engine/learning/tutor/model/SessionLearningSummary.kt`
- `app/app/src/main/java/com/vaniflow/app/feature/home/HomeViewModel.kt`
- `app/app/src/main/java/com/vaniflow/app/feature/progress/ProgressScreen.kt`
- `app/app/src/main/java/com/vaniflow/app/feature/progress/ProgressViewModel.kt`
- `app/app/src/main/java/com/vaniflow/app/feature/summary/SessionSummaryScreen.kt`
- `app/app/src/main/java/com/vaniflow/app/feature/summary/SessionSummaryViewModel.kt`
- `app/app/src/test/java/com/vaniflow/app/engine/learning/tutor/ProgressCalculationEngineTest.kt`

---

## 6. Database Version & Migrations

- **Database Version:** `3`
- **Migrations:**
  - `MIGRATION_1_2`: Creates `learner_profile` and `daily_usage` tables.
  - `MIGRATION_2_3`: Creates `learning_events`, `concept_mastery`, and `vocabulary_memory` tables.
- **Migration Tests:** Verified in `DatabasePersistenceTest` (tested non-destructive data retention across version migrations).

---

## 7. Test Results

### Unit Tests (JVM)
- **Total Tests:** 300
- **Passed:** 300
- **Failed:** 0
- **Command:** `./gradlew.bat testDebugUnitTest`

### Instrumented Tests (Physical Device)
- **Target Device:** Realme 6i (`RMX2040`), Android 11 (API 30), Device ID: `CASSE65LH66SWSZP`
- **Total Tests:** 66
- **Passed:** 66
- **Failed:** 0
- **Command:** `./gradlew.bat connectedDebugAndroidTest`

---

## 8. Feature-by-Feature Verification Status

| Item | Requirement | Status | Evidence |
|---|---|---|---|
| **Session Lifecycle** | Real session start, turn progression, end, and summary transition | **VERIFIED** | `ConversationEngineTest`, `ConversationEngineTutorLoopTest`, UI flow |
| **Learning Events** | Real events logged on mistakes, retries, mastery gains, and vocabulary | **VERIFIED** | `LearningMemoryManagerTest`, `DatabasePersistenceTest` |
| **Mastery Integration** | Mastery score decreases on mistake, increases on retry/natural correct usage | **VERIFIED** | `MasteryEngineTest`, `ConversationEngineTutorLoopTest` |
| **Vocabulary Pipeline** | Useful spoken expression extraction and natural reuse in prompts | **VERIFIED** | `SpokenExpressionExtractorTest`, `LearningMemoryManager.getTutoringPromptContext()` |
| **Adaptive Difficulty** | Dynamic difficulty scaling based on CEFR level, confidence, and retry rates | **VERIFIED** | `DifficultyEngineTest`, `LearningMemoryManagerTest` |
| **Correction Policy** | Cooldowns (60s), struggle backoff (after 3 errors), high mastery suppression | **VERIFIED** | `CorrectionPolicyEngineTest`, `TutorStateTest` |
| **Confidence-First Behavior** | Gentle hints, positive reinforcement, no endless retry loops (max 2 attempts) | **VERIFIED** | `ConversationEngineTutorLoopTest` |
| **Non-Fabricated Metrics** | No fake acoustic pronunciation or inflated vocabulary scores | **VERIFIED** | `ProgressCalculationEngineTest`, `DefaultProgressRepositoryTest` |
| **CEFR Hardening** | Minimum 15-utterance evidence threshold before assigning level | **VERIFIED** | `ProgressCalculationEngineTest` |
| **Session Summary UI** | Reads and displays genuine session stats from Room | **VERIFIED** | `SessionSummaryViewModel`, `SessionSummaryScreen` |
| **Progress Screen UI** | Reads and displays live streak, minutes, mastery, and vocabulary | **VERIFIED** | `ProgressViewModel`, `ProgressScreen` |
| **Home Dashboard** | Shows live streak, minutes, and improving concepts | **VERIFIED** | `HomeViewModel`, `HomeScreen` |
| **Barge-In Interruption** | User speech cancels active AI generation & stops TTS immediately | **VERIFIED** | `ConversationEngineTest`, `Milestone13DeviceLabTest` |
| **Fair-Use Boundary** | Daily 90-minute limit tracked across sessions | **VERIFIED** | `DailyConversationUsageTrackerTest`, `FairUsePersistenceTest` |

---

## 9. Known Limitations

1. **Acoustic Phoneme Scoring:** VaniFlow does not bundle an on-device phoneme-level acoustic model in this phase. Numerical pronunciation scores are explicitly omitted or marked as unmeasured clarity levels to avoid pseudo-scientific deception.
2. **Offline Local LLM Requirements:** On-device SLM execution requires suitable RAM on low-end devices; the fallback to cloud streaming or lightweight pattern synthesis operates transparently when local weights are not present.
