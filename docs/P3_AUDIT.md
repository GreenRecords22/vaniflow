# VaniFlow — P3 Architecture Audit Report
**Milestone:** P3 — Adaptive AI Tutor Brain  
**Date:** September 1, 2026  
**Auditor:** Senior Android Architect & Learning-System Engineer  

---

## 1. Executive Summary & Existing Architecture Inventory

The VaniFlow codebase currently contains a comprehensive set of specialized intelligence and speech processing engines implemented across P0, P0.5, P1, P1.5, P2, and P2.1:

| Component | Location | Role & Capabilities |
|---|---|---|
| **`EnglishCorrectionEngine`** | `engine/learning/tutor/` | Deterministic grammatical analysis, Indian English pattern recognition, severity assignment (`STYLE`, `MINOR`, `IMPORTANT`, `CRITICAL`), retry suggestion generation. |
| **`CorrectionPolicyEngine`** | `engine/learning/tutor/` | Evaluates whether a spoken correction interruption should occur based on cooldowns, struggle backoff, confidence floor (<40%), and mastery thresholds (>=85). |
| **`MasteryEngine`** | `engine/learning/tutor/` | Per-concept mastery score tracking (0..100), success/failure penalties, decay over time, and practice priority sorting. |
| **`DifficultyEngine`** | `engine/learning/tutor/` | Determines 4-tier difficulty (`BEGINNER`, `INTERMEDIATE`, `UPPER_INTERMEDIATE`, `ADVANCED`) from CEFR level, confidence, and retry consistency. |
| **`LearningGoalGenerator`** | `engine/learning/tutor/` | Dynamically produces 1–3 scenario- and weakness-aligned session learning goals. |
| **`LearningMemoryManager`** | `engine/learning/tutor/` | Coordinates `LearnerProfile`, `MasteryState`, `LearningEvent`s, `VocabularyMemory`, speech analysis persistence, and coaching context generation. |
| **`SpeechQualityAnalyzer`**, **`FluencyAnalyzer`**, **`PronunciationAnalyzer`** | `engine/speech/` | Acoustic SNR/RMS health, temporal pacing (approx. WPM), non-penalizing natural pause detection (200-800ms), and pronunciation practice target candidate targeting. |
| **`SmartAIRouter`** & **`SmartResponseDecisionEngine`** | `engine/ai/` | 90-minute daily fair use tracking, token cache, memory direct answering, and multi-provider failover. |
| **`ConversationPromptBuilder`** | `engine/ai/prompt/` | Assembles character persona, scenario, history, and tutoring context into the runtime LLM prompt. |
| **`ConversationEngine`** | `engine/conversation/` | Orchestrates the hands-free voice loop, turn state machine, audio playback, retry handling, and Room database persistence. |

---

## 2. Identified Gaps & Duplication Risks

### Gaps:
1. **Lack of a Unified Central Decision Layer**: Currently, `ConversationEngine` performs scattered if-else checks for retries and corrections directly inside `processUserUtterance`. It lacks a dedicated, deterministic `TutorDecisionEngine` that inspects an aggregated `TutorLearnerState` to choose the optimal next pedagogical action.
2. **Deterministic Priority Coordination**: When multiple signals occur simultaneously (e.g., a weak concept opportunity, an active session goal, vocabulary needing recycling, and a minor error), there was no formal, unified priority hierarchy resolving them deterministically.
3. **Structured Tutor Decision Representation**: The prompt builder received flat text from `getTutoringPromptContext()`, rather than consuming a structured `TutorDecision` specifying exact coaching targets, difficulty adjustments, and conversational steering directives.

### Duplication Risks to Avoid:
- **DO NOT** create a second correction engine (use `EnglishCorrectionEngine`).
- **DO NOT** create a second mastery engine (use `MasteryEngine`).
- **DO NOT** create a second difficulty engine (use `DifficultyEngine`).
- **DO NOT** create a second learner profile (use `LearnerProfile`).
- **DO NOT** create an unnecessary new Room table (derive state from existing persisted repositories: `LearnerProfileRepository`, `ConceptMasteryRepository`, `LearningEventRepository`, `VocabularyMemoryRepository`, `SpeechAnalysisRepository`).

---

## 3. Scope of Modifications for P3

1. **New Core Components**:
   - `com.vaniflow.app.engine.learning.tutor.model.TutorLearnerState`: Snapshot model aggregating all available learner signals.
   - `com.vaniflow.app.engine.learning.tutor.model.TutorAction`: Enum representing pedagogical decisions (`NORMAL_CONVERSATION`, `PASSIVE_CORRECTION`, `IMPORTANT_CORRECTION`, `CRITICAL_CORRECTION`, `ASK_RETRY`, `GIVE_SECOND_HINT`, `PRAISE_SUCCESS`, `PRACTICE_WEAK_CONCEPT`, `REUSE_VOCABULARY`, `LOWER_DIFFICULTY`, `MAINTAIN_DIFFICULTY`, `INCREASE_DIFFICULTY`, `ENCOURAGE_LEARNER`, etc.).
   - `com.vaniflow.app.engine.learning.tutor.model.TutorDecision`: Output model containing the chosen action, reason, spoken intervention (if any), target concept, recycled vocabulary, and prompt directive.
   - `com.vaniflow.app.engine.learning.tutor.TutorDecisionEngine`: Central deterministic engine evaluating the learner state snapshot.

2. **Integration Touchpoints**:
   - `LearningMemoryManager.kt`: Add `buildLearnerState(...)` and `evaluateTutorDecision(...)`.
   - `ConversationEngine.kt`: Route utterance evaluation through `TutorDecisionEngine`, executing spoken interventions or injecting structured prompt directives.
   - `ConversationPromptBuilder.kt`: Accept structured tutoring directive from `TutorDecision`.

3. **Database Assessment**:
   - Room Database Schema v4 is fully sufficient. No schema migration is required as all necessary state is persisted across existing tables (`learner_profile`, `concept_mastery`, `learning_events`, `vocabulary_memory`, `speech_analysis`, `sessions`, `conversation_turns`).
