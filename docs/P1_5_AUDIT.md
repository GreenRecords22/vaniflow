# VaniFlow — P1.5 Speaking Module Completion Audit
# Real Speaking-Session Lifecycle & Learner Experience

**Date:** 2026-08-30  
**Repository:** GreenRecords22/vaniflow  
**Latest Git Commit:** `503d286` (Production English Tutor Intelligence & Learning Progression)  
**Status:** BASELINE VERIFIED (296/296 JVM Unit Tests Passing, 66/66 Physical Device Instrumented Tests Passing)

---

## 1. Executive Summary & Audit Objectives

The purpose of P1.5 is to take the intelligence foundation built in P1 and connect it end-to-end into a **complete, living speaking session experience**:
- Real Speaking Session Start $\rightarrow$ Conversational Turn Flow $\rightarrow$ Spoken/Passive Correction $\rightarrow$ Retry Evaluation $\rightarrow$ Deterministic Concept Mastery Update $\rightarrow$ Real Learning Event Logging $\rightarrow$ Natural Expression Extraction $\rightarrow$ Spoken Vocabulary Reuse $\rightarrow$ Real Session End $\rightarrow$ Non-Fabricated Session Summary $\rightarrow$ Room Persistence $\rightarrow$ Real Progress Dashboard.

---

## 2. Component-by-Component Audit Findings

### 2.1 Session Lifecycle (`ConversationEngine.kt`, `SessionEntity`, `SessionDao`)
- **Current State:** `ConversationEngine.startSession` generates an in-memory session ID and triggers `learningMemoryManager.startSession(scenario, currentSessionId)`. However, `SessionEntity` is only inserted into `sessionDao` when `endSession()` is invoked.
- **P1.5 Target:** Insert an active session record at session start and update it upon completion, or ensure seamless state preservation if process is backgrounded. Record `difficultyLevel` and `learningGoals` at start time.

### 2.2 Turn Learning Pipeline (`ConversationEngine.kt` $\rightarrow$ `EnglishCorrectionEngine.kt` $\rightarrow$ `CorrectionPolicyEngine.kt` $\rightarrow$ `LearningMemoryManager.kt`)
- **Current State:** Normal utterances analyze grammar/tense/agreement/preposition/article mistakes via `EnglishCorrectionEngine`. `CorrectionPolicyEngine` evaluates cooldowns (60s), struggle backoff (after 3 consecutive errors), and high-mastery suppression ($\ge 85$).
- **P1.5 Findings:**
  - `MASTERY_GAIN` / `CONCEPT_PRACTICED` events: When a user uses clean grammar in a category where they previously struggled, `onNaturalCorrectUsage` is available but needs automatic detection from clean turns.
  - Spoken correction flow works smoothly on physical device (verified with "buyed" $\rightarrow$ "bought" retry loop).

### 2.3 Progress Calculation & Pseudo-Measurement Audit (`ProgressCalculationEngine.kt`)
- **Current State:** 
  - Line 83: `val pronunciationScore = (85 + (speakingConfidence * 0.1f)).toInt().coerceIn(75, 98)`
  - Line 84: `val vocabularyScore = (78 + (userTurnsCount * 2)).coerceIn(65, 95)`
- **P1.5 Remediation:** **MUST BE REMOVED.**
  - VaniFlow does not have a native phoneme-level pronunciation acoustic model in MVP. Pronunciation should NOT be presented as a pseudo-exact percentage score (e.g. 92%). Instead, indicate conversational clarity or qualitative states based on STT confidence/word match (e.g., "Clear", "Developing", "Natural") or omit numerical deception.
  - Vocabulary score must be derived from actual expressions used/learned from `VocabularyMemory`, or expressed as "X expressions practiced".

### 2.4 CEFR Estimation Hardening (`ProgressCalculationEngine.kt`, `LearnerProfile.kt`)
- **Current State:** Level is calculated starting at 10 utterances.
- **P1.5 Remediation:** Prevent premature CEFR level jumps. Introduce an evidence threshold (minimum 15 utterances across sessions). If below threshold, display status as `"Assessing level..."` or `"Building your profile..."` rather than declaring an unearned CEFR rating.

### 2.5 Real Spoken Vocabulary Pipeline & Natural Reuse (`VocabularyMemoryRepository`, `ConversationPromptBuilder`)
- **Current State:** `VocabularyMemoryEntity` and `VocabularyMemoryDao` exist in Room DB v3. `rememberVocabulary()` exists in `LearningMemoryManager`.
- **P1.5 Enhancement:**
  - Implement deterministic extraction of useful spoken collocations/phrases (e.g. "looking forward to", "by the way", "on the other hand", "could you please", "as soon as possible") from user and AI speech turns.
  - Inject active learner expressions into `ConversationPromptBuilder` so the AI conversational partner naturally weaves context for the learner to practice them.

### 2.6 UI Integration: Session Summary & Progress Screen
- **Current State:**
  - `SessionSummaryScreen.kt` and `SessionSummaryViewModel.kt` read from `sessionRepository`.
  - `ProgressScreen.kt` and `ProgressViewModel.kt` read from `progressRepository`.
  - `HomeScreen.kt` and `HomeViewModel.kt` display streak days and total minutes.
- **P1.5 Enhancement:**
  - Update `SessionSummaryScreen` to display real evidence: duration, turns count, corrections count, successful retries, improved concepts, concepts needing practice, and newly practiced expressions.
  - Update `ProgressScreen` to display real CEFR status, confidence trend, mastered concepts count, concepts needing practice list, and spoken vocabulary count.
  - Update `HomeScreen` to feature a compact learning card (e.g. "🎯 Past tense is improving" or "2 concepts practiced today").

---

## 3. P1.5 Implementation Plan & Roadmap

1. **Clean `ProgressCalculationEngine` of all pseudo-measurements** and replace with evidence-based / qualitative metrics.
2. **Harden CEFR level estimation** with multi-session evidence thresholds.
3. **Implement practical spoken vocabulary extraction & natural prompt reuse** in `LearningMemoryManager` and `ConversationEngine`.
4. **Wire real learning event generation for natural correct usage** on clean turns.
5. **Connect `SessionSummaryViewModel` and `SessionSummaryScreen`** to display genuine session learning outcomes.
6. **Connect `ProgressViewModel` and `ProgressScreen`** to display real mastered concepts, weak concepts, and vocabulary memories from Room.
7. **Enhance `HomeScreen`** to show live improving concepts alongside streak and speaking minutes.
8. **Run full automated validation suite** (Unit tests + Physical device connected tests).
