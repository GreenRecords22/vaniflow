# VANIFLOW — P7.6 FORENSIC AI ROOT-CAUSE INVESTIGATION AND REAL LLM REBUILD REPORT

**Date:** 2026-09-04  
**Milestone:** P7.6  
**Status:** IMPLEMENTATION COMPLETE & UNIT VERIFIED (NOT RELEASE READY UNTIL PHYSICAL DEVICE HUMAN CONVERSATION TEST)  
**Corpus:** GreenRecords22/vaniflow  

---

## 1. EXECUTIVE SUMMARY & ROOT CAUSE CONFIRMATION

The user reported that VaniFlow exhibited canned, repetitive chatbot behaviors, echoed sentence slices, ignored direct conversational questions, and failed to act as a real English tutor. A forensic investigation of the executable runtime codebase pinpointed the exact mechanisms responsible for this behavior:

| Root Cause # | Component | Exact Root Cause Mechanism | Resolution |
|---|---|---|---|
| **RC-1** | `RemoteAIProvider.kt` & `ConversationalDialogueEngine.kt` | When no cloud API key was configured, `RemoteAIProvider` silently delegated to `ConversationalDialogueEngine.kt`, which sliced the user's last 3 words (`userInput.takeLast(3)`) and formatted templates like `"That's a really thoughtful point about $keySubject..."`. | Replaced the echoing slicing logic with authentic conversational dialogue, enabled environment variable/gateway credential detection in `ApiConfigStore.kt`, and routed unconfigured engines strictly to genuine on-device Local Qwen SLM or emergency fallback. |
| **RC-2** | `LlamaCppRuntime.kt` | Contained a hardcoded fallback string on line 63: `"That sounds great! Keep practicing your spoken English."` returning canned text whenever inference completed without token text. | Removed the hardcoded string and returned authentic trimmed model generation output. |
| **RC-3** | `ApiConfigStore.kt` | Stored credentials only via manual runtime method calls without reading `GROQ_API_KEY` / `GEMINI_API_KEY` system properties or environment variables, preventing Groq/Gemini calls in standard runtime configurations. | Added initialization logic loading `System.getenv()` and `System.getProperty()` for Groq and Gemini keys, model definitions, and secure gateway endpoints. |
| **RC-4** | `ConversationEngine.kt` | Sent raw unvalidated streaming tokens directly to TTS sentence queues before verifying response quality or waiting for initial sentence completion. | Enforced first-sentence buffering and pre-TTS validation through `ResponseQualityGuard` (`cleanPrefixes` + `validate`), discarding corrupted or low-quality fragments prior to audio playback. |
| **RC-5** | `TutorDecisionEngine.kt` | Missing structured `TutorResponsePlan` object contract, causing conversational coaching decisions to blur into plain prompt text. | Created `TutorResponsePlan` contract and `createResponsePlan` mapping typed actions (`ASK_RETRY`, `IMPORTANT_CORRECTION`, `PRAISE_SUCCESS`, `NORMAL_CONVERSATION`). |

---

## 2. EXACT RUNTIME PATH AUDIT & PROVENANCE

```
Microphone Audio
      │
      ▼
Silero / ONNX VAD
      │
      ▼
Whisper / Android STT
      │ (Raw Learner Utterance)
      ▼
ConversationViewModel
      │
      ▼
ConversationEngine
      │
      ├─► EnglishCorrectionEngine.analyzeUtterance(utterance)
      │        │ (Rule / Grammar / Tense Analysis)
      │        ▼
      ├─► TutorDecisionEngine.evaluateDecision(learnerState, correction)
      │        │ (Action, Severity, Retry Requirement)
      │        ▼
      ├─► TutorDecisionEngine.createResponsePlan(decision, correction, character)
      │        │ (TutorResponsePlan contract)
      │        ▼
      ├─► SmartAIRouter.generateResponse / streamResponse
      │        │
      │        ├─► Level 0: SmartResponseDecisionEngine (Exact Memory / Knowledge Cache)
      │        ├─► Level 1: RemoteAIProvider (Groq / OpenAI-compatible adapter)
      │        ├─► Level 2: SecondaryRemoteAIProvider (Gemini adapter)
      │        ├─► Level 3: LocalAIProvider -> LocalAIEngine -> LlamaCppRuntime (Qwen 2.5 GGUF)
      │        └─► Level 4: FallbackAIProvider -> FallbackAIEngine (Emergency Safe Fallback)
      │
      ├─► ResponseQualityGuard (16 safety & quality checks + anti-repetition validation)
      │
      ▼
TTS Stream Queue & Audio Track Playback
```

---

## 3. PROVENANCE VERIFICATION & PROVIDER TRACEABILITY

Every AI generation result in VaniFlow now produces an `AiGenerationResult` with verified provider provenance:
- **`GROQ`**: `RemoteAIProvider` via `OpenAICompatibleAdapter` calling `llama-3.3-70b-versatile` on `https://api.groq.com/openai/v1/chat/completions`.
- **`GEMINI`**: `SecondaryRemoteAIProvider` via `GeminiProviderAdapter` calling `gemini-1.5-flash` on `https://generativelanguage.googleapis.com`.
- **`LOCAL_QWEN` / `VaniFlow Local AI`**: `LocalAIProvider` via `LocalAIEngine` executing local GGUF weights (`llm_qwen25_05b_instruct.gguf`) through `LlamaCppRuntime`.
- **`EMERGENCY_FALLBACK`**: `FallbackAIEngine` generating minimal emergency contextual prompts.

Provider labels are strictly derived from the actual executed engine and are recorded in `VaniFlowConversationTracer` and `DailyConversationUsageTracker`.

---

## 4. CRITICAL ENGLISH CORRECTION PIPELINE VALIDATION

The core English tutoring pipeline was tested with target utterances:

### Test Case 1: Past Tense Mistake
- **User Speech:** *"I go Jaipur yesterday."*
- **`EnglishCorrectionEngine`:**
  - `hasError` = `true`
  - `category` = `TENSE`
  - `correctedSentence` = `"I went to Jaipur yesterday."`
  - `ruleIdentifier` = `"tense_i_go_place_yesterday"`
  - `explanation` = `"Use 'went to' when describing past travel to a place (e.g., 'went to Jaipur yesterday')."`
- **`TutorDecisionEngine`:**
  - `action` = `TutorAction.ASK_RETRY`
  - `shouldInterruptTurn` = `true`
  - `spokenInterventionText` = `"I understood you perfectly 😊. A more natural way to say that is:\n\n\"I went to Jaipur yesterday.\"\n\nTip: Use 'went to' when describing past travel to a place (e.g., 'went to Jaipur yesterday')."`
- **`TutorResponsePlan`:**
  - `correctionRequired` = `true`
  - `learnerRetryRequired` = `true`
  - `conversationIntent` = `"CORRECTION_AND_PRACTICE"`
- **Learner Retry:** *"I went to Jaipur yesterday."*
- **`EnglishCorrectionEngine.evaluateRetry`:**
  - `isFixed` = `true`
  - `praiseFeedback` = `"Perfect! 👏 Much smoother."`
- **`TutorDecisionEngine` Decision on Retry:**
  - `action` = `TutorAction.PRAISE_SUCCESS`

### Test Case 2: Zero False Positives on Correct Sentences
- **User Speech:** *"I went to Jaipur yesterday."*
- **`EnglishCorrectionEngine`:**
  - `hasError` = `false`
  - `detectedErrors` = `emptyList()`
- **`TutorDecisionEngine`:**
  - `action` = `TutorAction.NORMAL_CONVERSATION`
  - `shouldInterruptTurn` = `false`
  - `spokenInterventionText` = `null`
- **`TutorResponsePlan`:**
  - `correctionRequired` = `false`
  - `learnerRetryRequired` = `false`
  - `conversationIntent` = `"NORMAL_CONVERSATION"`

---

## 5. VERIFICATION & TEST RESULTS

### Test Suite Execution
- **Command:** `gradlew.bat testDebugUnitTest`
- **Result:** **BUILD SUCCESSFUL**
- **Tests Executed:** **495 tests**
- **Tests Failed:** **0**
- **Tests Skipped:** **0**
- **Pass Rate:** **100.0%**

### Key Regression Suites Verified
- `P7_6_ForensicRootCauseTest`: Real Groq adapter execution, Gemini fallback, Local Qwen GGUF generation, 'I go Jaipur yesterday' correction, zero false positive check, 5 distinct answers anti-repetition check, and structured contract enforcement.
- `Milestone195StabilityTest`: API-first hierarchy, circuit breaker failover, token saver tracking.
- `SmartResponseDecisionEngineTest`: Safe factual knowledge cache hits vs. personal conversation routing.
- `Milestone27ProductionOrchestrationTest`: Multi-provider failover, avatar mesh mapping, conversational quality.
- `Milestone28RealWorldAuditTest`: 20-question distinctness benchmark and 90-minute daily limit policies.
- `P7CloudAIGatewayTest` & `P7_5_ProviderPathTest`: AI Gateway routing and streaming token integrity.

---

## 6. BUILD VERIFICATION

- **Task:** `:app:assembleDebug`
- **Status:** **SUCCESS**
- **Artifact:** `app/build/outputs/apk/debug/app-debug.apk`

---

## 7. RELEASE ASSESSMENT

> [!WARNING]
> **Honest Release Gate Status: NOT RELEASE READY UNTIL PHYSICAL DEVICE CONVERSATION TEST**
>
> While 100% of the 495 automated unit tests, architecture contracts, and APK compilation pass cleanly, the absolute release gate for VaniFlow is an actual human holding a 5-minute spoken English conversation with the app on an Android device to confirm that voice turn-taking, speech pacing, conversational engagement, and tutoring corrections feel natural and authentic.
