# VaniFlow — P1 Implementation Audit
# Production English Tutor Intelligence & Learning Progression

**Date:** 2026-08-30
**Corpus/Repo:** GreenRecords22/vaniflow
**Status:** BASELINE VERIFIED (279/279 JVM Unit Tests Passing, 63/63 Physical Device Connected Tests Passing)

---

## 1. Executive Summary & Baseline State

VaniFlow has completed P0 and P0.5 production hardening:
- **Core Loop:** Real English Speaking Tutor loop (`ConversationEngine` -> `EnglishCorrectionEngine` -> spoken correction -> retry loop -> `WAITING_FOR_RETRY` -> retry evaluation -> praise/correction -> mastery update -> persistence -> adaptive prompt context).
- **Hardening:** Thread-safe asynchronous Room load race prevention (`ensureLoaded()` in `LearningMemoryManager` and `DailyConversationUsageTracker`), snapshot serialization (`copyProfile()`), exact 89:59 vs 90:00 boundary enforcement, fair-use cloud AI blocking, Room migration `MIGRATION_1_2`, and barge-in recovery.
- **Physical Device:** Verified on connected hardware (`Realme RMX2040 / CASSE65LH66SWSZP`, Android 11).

This audit establishes the exact architectural state of the repository before introducing **P1: Production English Tutor Intelligence & Learning Progression**.

---

## 2. Architecture & File Inventory

### 2.1 Core Tutor Engines (`com.vaniflow.app.engine`)
- `engine/conversation/ConversationEngine.kt`: Central state machine (`ConversationState`, `TutorState`), active retry management, turn orchestration, prompt dispatch.
- `engine/learning/tutor/EnglishCorrectionEngine.kt`: Rule-based grammar/tense/agreement/collocation/preposition/article mistake detector, retry evaluator (`RetryEvaluation`), natural phrasing suggestions.
- `engine/learning/tutor/LearningMemoryManager.kt`: Bridges `LearnerProfile` with `LearnerProfileRepository`, computes compact tutoring context, updates mistakes & retry counts.
- `engine/learning/tutor/LearnerProfile.kt`: Data model containing `EstimatedLevel`, `speakingConfidenceScore`, `totalUtterances`, `correctionsDelivered`, `successfulRetries`, `commonMistakes`, `masteredConcepts`, `conceptsNeedingPractice`, `recentCorrections`.
- `engine/ai/prompt/ConversationPromptBuilder.kt`: Structured prompt generator combining system persona, character personality, user level, scenario, conversation history, learning objective, and `tutoringContext`.
- `engine/ai/SmartAIRouter.kt` & `engine/ai/routing/SmartResponseDecisionEngine.kt`: Multi-provider router with deterministic rules, knowledge cache, fair-use blocking (90-min limit), local SLM cascade, and fallback contextual engine.

### 2.2 Persistence & Database (`com.vaniflow.app.data`)
- **Database Version:** Version 2 (`VaniFlowDatabase.kt`).
- **Existing Tables:**
  1. `sessions` (`SessionEntity`)
  2. `conversation_turns` (`ConversationTurnEntity`)
  3. `saved_vocabulary` (`SavedVocabularyEntity`)
  4. `guest_profile` (`GuestProfileEntity`)
  5. `ai_cache` (`AICacheEntity`)
  6. `learner_profile` (`LearnerProfileEntity` — added in v2)
  7. `daily_usage` (`DailyUsageEntity` — added in v2)
- **Repositories:**
  - `DefaultLearnerProfileRepository.kt` (`LearnerProfileRepository`)
  - `DefaultDailyUsageRepository.kt` (`DailyUsageRepository`)
  - `DefaultProgressRepository.kt` (`ProgressRepository`)
  - `DefaultSessionRepository.kt` (`SessionRepository`)
  - `DefaultVocabularyRepository.kt` (`VocabularyRepository`)

### 2.3 UI & Presentation Layer (`com.vaniflow.app.feature`)
- `feature/conversation/ConversationViewModel.kt` & `ConversationScreen.kt`: Real-time voice interaction, animated teal avatar, viseme lip-sync, live waveform, speech-to-text turns, spoken correction cards.
- `feature/progress/ProgressScreen.kt` & `ProgressViewModel.kt`: Weekly streak, speaking minutes, fluency stats, coach recommendations.
- `feature/summary/SessionSummaryScreen.kt` & `SessionSummaryViewModel.kt`: Post-session score breakdown (fluency, grammar, pronunciation, vocabulary), strengths, focus areas.

---

## 3. P1 Gap Analysis & Required Enhancements

| Area | Current State (P0.5) | Required Target (P1) |
|---|---|---|
| **Learning Events** | In-memory `recentCorrections` list in profile | Structured `LearningEvent` entity/model with timestamps, concept, category, severity, original/corrected form, retry outcome, session ID, and confidence delta |
| **Concept Mastery** | Set of string IDs (`masteredConcepts`) | Measurable `MasteryState` with score (0-100), attempt counts, consecutive successes/failures, last practiced time, and decay logic |
| **Adaptive Difficulty** | Fixed character skill level (`SkillLevel`) | Dynamic `DifficultyLevel` (`BEGINNER`, `INTERMEDIATE`, `UPPER_INTERMEDIATE`, `ADVANCED`) responsive to recent accuracy, confidence, and retry rate |
| **Session Learning Goals** | Generic default prompt string | Dynamically generated `LearningGoal`s (1–3 per session) based on weak concepts and scenario context |
| **Correction Intelligence** | Direct severity threshold (`>= IMPORTANT`) | Intelligent correction decision policy with concept cooldowns, struggle backoff, and mastery suppression |
| **Vocabulary Memory** | Basic word saving in Room | Context-aware spoken expression memory (`VocabularyMemory`) with familiarity tracking and prompt reuse |
| **CEFR Progression** | Static enum (`EstimatedLevel`) | Defensible multi-signal CEFR estimation model based on accumulated accuracy, vocabulary breadth, and fluency |
| **Database Schema** | Database v2 | Database v3 migration `MIGRATION_2_3` adding tables for `learning_events`, `concept_mastery`, and expanded vocabulary/goal fields without deleting P0 data |
| **UI Integration** | Progress & Summary using partial session aggregates | Full binding of real P1 mastery progression, learning goals, CEFR estimate, and session summary breakdown |

---

## 4. Plan vs Source Alignment & Rules
1. **Source Code is Truth:** All new classes and schema will build directly onto existing DAOs, Repositories, and Engines.
2. **Offline-First:** All P1 calculations (Mastery, Goals, Difficulty, CEFR, Progress) must execute deterministically and locally without mandatory network or cloud dependencies.
3. **Fair-Use Protection:** 90-minute daily limit tracking and blocking remain 100% enforced.
4. **Non-Destructive Migration:** Room schema version moves from 2 to 3 with `MIGRATION_2_3`.
