# P7.3 Real Conversational AI + English Correction Rebuild Audit

## 1. Executive Summary

During manual testing of VaniFlow, critical user acceptance failures were detected:
- The conversational engine frequently returned canned, repeated phrases (*"That's interesting!"*, *"That's great!"*, *"Keep practicing!"*).
- The system ignored the actual semantic meaning of user questions and statements.
- English pedagogical correction was missing or replaced with fake/robotic praise without addressing genuine Indian learner mistakes.
- A flawed prefix-similarity check (`startsWith(past.take(20))`) falsely identified distinct responses as duplicates, collapsing varied conversations into generic fallback phrases.

**Milestone P7.3 systematically rebuilt the conversational pipeline**, eliminating canned chatbot branch tables and establishing genuine LLM intelligence backed by an authoritative pedagogical tutor constitution, strict quality guards, and deterministic grammar analysis.

---

## 2. Runtime Path Trace (Microphone to Spoken Audio)

The verified production conversational path operates as follows:

```
[Microphone Audio Stream (16kHz PCM)]
       │
       ▼
[Silero VAD / AudioEnergyDetector] ── (Speech Endpoint Detection)
       │
       ▼
[Sherpa-ONNX / Android STT] ──────── (Real-time Transcription)
       │
       ▼
[ConversationViewModel] ──────────── (State & Session Management)
       │
       ▼
[ConversationEngine.processTurn]
       │
       ├──────────────────────────────────────────────┐
       ▼                                              ▼
[EnglishCorrectionEngine]                   [LearningMemoryManager]
  - Deterministic Grammar Analysis            - Learner Profile & History
  - High-Frequency Learner Error Rules        - Mistake Tracking & Streaks
  - Tense / SVA / Preposition Rules           - Confidence & Struggle Metrics
       │                                              │
       └──────────────────────┬───────────────────────┘
                              ▼
                   [TutorDecisionEngine]
                     - Evaluates Learner State
                     - Selects Pedagogical Action:
                       • NORMAL_CONVERSATION
                       • PASSIVE_CORRECTION
                       • IMPORTANT_CORRECTION
                       • ASK_RETRY
                       • STRUGGLE_BACKOFF
                     - Emits Structured Coaching Directive
                              │
                              ▼
                     [SmartAIRouter]
                       │
       ┌───────────────┼───────────────┬──────────────────┐
       ▼               ▼               ▼                  ▼
[VaniFlow Gateway] [Groq Cloud] [Local Qwen2.5] [Emergency Fallback]
 (Primary Remote)   (Secondary)   (On-Device)   (Context-Preserving)
       │
       ▼
[ResponseQualityGuard]
  - Anti-canned phrase filtering
  - Anti-leak / system prompt protection
  - Semantic repetition detection (Jaccard similarity)
       │
       ▼
[ConversationViewModel / AvatarPresenter]
       │
       ▼
[VisemeLipSyncController & Piper TTS / Sherpa TTS]
       │
       ▼
[AudioTrack Speaker Output + Real-time Lip Animation (60 FPS)]
```

---

## 3. Root Cause Analysis of Previous Failures

### 3.1 300+ Line Keyword Matching Matrix
- **Old Behavior:** `ConversationalDialogueEngine.kt` contained hundreds of hardcoded `when { lower.contains(...) -> ... }` blocks that intercepted user turns before they reached genuine AI models.
- **Problem:** Small talk, questions, and nuanced replies were converted into hardcoded scripts instead of conversational AI generation.

### 3.2 Flawed Prefix-Match Anti-Repetition Guard
- **Old Code:**
  ```kotlin
  if (pastNorm.startsWith(candNorm.take(20))) return true
  ```
- **Flaw:** Any two responses starting with similar introductory words (e.g., *"I love traveling in..."* vs *"I love spicy Indian dishes..."*) were falsely flagged as duplicate repetitions and replaced with a single hardcoded canned phrase.
- **Fix:** Replaced with Jaccard word-set overlap (`intersection / union >= 0.85` on normalized non-trivial tokens).

### 3.3 Prompt Instructions Biased Toward Generic Fillers
- **Old Prompts:** Encouraged excessive praise and conversational cheerleading (*"Always praise the learner! Be super encouraging!"*).
- **Result:** Models repeatedly outputted *"That's fantastic!"* and ignored the user's factual questions.
- **Fix:** Redesigned `ConversationPromptBuilder` with strict response rules:
  - Directly address the semantic meaning of the user's speech.
  - Answer personal, factual, or opinion questions in character first before asking follow-ups.
  - Strictly prohibit repetitive filler phrases (*"That's interesting!"*, *"Keep practicing!"*).

---

## 4. Key Architectural Fixes

1. **VaniFlow Tutor Constitution v1.0:** Embedded immutable pedagogical rules into every runtime prompt across cloud and on-device models.
2. **Authoritative TutorDecisionEngine:** Separates pedagogical decisions (retry vs praise vs struggle backoff) from raw LLM text generation.
3. **ResponseQualityGuard:** Runtime sanitizer stripping preamble leaks, canned clichés, and prompt injection attempts.
4. **Enhanced EnglishCorrectionEngine:** Deterministic high-precision correction covering irregular past tense, prepositions, subject-verb agreement, and Indian English collocations with zero false positives on correct sentences.
5. **Failover & Resilient Cascading:** Seamless automatic failover across Gateway -> Secondary Cloud -> Local Qwen2.5 -> Contextual Emergency Fallback.
