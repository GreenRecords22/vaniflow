# VaniFlow Release Candidate QA Report

## 1. Character Profiles & Personalities Verified

1. **Raya**: Friendly & Patient mentor, Beginner difficulty, warm Indian English voice (`tts_piper_raya_warm`), gentle correction style.
2. **Rudra**: Casual & Energetic partner, Intermediate difficulty, natural Indian English conversational flow.
3. **Adwaita**: Professional & Confident coach, Advanced difficulty, nuanced executive workplace feedback.
4. **Shub**: Professional & Calm interviewer, Advanced difficulty, structured analytical speaking partner.

---

## 2. Real-World Scenarios Verified

1. **Order Coffee**: Daily Life, Beginner, 3 minutes.
2. **Airport Check-in**: Travel, Intermediate, 5 minutes.
3. **Job Interview**: Interview, Advanced, 10 minutes.
4. **Project Standup**: Work, Intermediate, 5 minutes.
5. **Meeting Someone**: Daily Life, Beginner, 3 minutes.
6. **Workplace Discussion**: Work, Intermediate, 7 minutes.

---

## 3. End-to-End Pipeline & Resilience Matrix

- **Audio Capture**: 16kHz 16-bit Mono PCM.
- **Voice Activity Detection**: Energy VAD with adaptive noise floor and 900ms silence threshold.
- **Sentence Chunking**: `SentenceSplitter` generates low-latency speech bursts.
- **Instant Interruption**: Sub-40ms voice onset interruption with zero stale token emissions.
- **Learning Analytics**: Room database single source of truth; deterministic timezone-aware streak algorithm; multidimensional session scoring (Fluency, Grammar, Pronunciation, Vocabulary).

---

## 4. Milestone 14 Production Stability Hardening (added 2026-08-29)

- **Voice Error Resilience**: STT / AI / TTS failures surface friendly, non-technical messages; raw exceptions and file paths are never exposed to the user (`ConversationErrorMessages`, `ConversationEngine.errorMessage`).
- **Silent STT Failure Fixed**: hands-free mode previously swallowed STT errors with no feedback; now shows "I didn't catch that. Tap the mic and try again." and re-arms listening.
- **Generation Cancellation**: `ConversationEngine.cancelAll()` cancels in-flight AI generation/TTS on new turn, user interruption, session end, and `ViewModel.onCleared()`. Stale updates are guarded via `activeGenerationJob`.
- **Permission Permanent Denial**: handled with a system-settings deep-link dialog (`requiresPermissionSettings`).
- **Model Storage Pre-Check**: `ModelManager.hasSufficientStorage` blocks downloads with a friendly message when free storage is insufficient (wired in `ProfileViewModel.downloadModel`).
- **Regression Coverage**: +17 JVM unit tests (`ConversationEngineTest`, `ConversationViewModelTest`, `ProfileViewModelTest`). Full suite: **141 JVM tests** + **61 on-device instrumented tests (SM-G965F)** all passing.
- **Privacy**: audit completed (`docs/PRIVACY_BETA_AUDIT.md`) — zero telemetry, no network by default, no PII exposure; release signing key git-ignored.

---

## 5. Milestone 15 Real Conversation QA (added 2026-08-29)

- **Device Validation**: full pipeline re-verified on physical **Samsung Galaxy S9+ (SM-G965F, Android 10 / API 29)** via 61 on-device instrumented tests — all passing. JVM unit suite: **145 tests** passing (incl. new M15 regressions).
- **Session Summary Data-Loss Fixed**: `ConversationViewModel.onEndSession` now uses `conversationEngine.getCurrentSessionId()` (was a synthetic timestamp id that made saved summaries unreachable). Regression: `onEndSession passes the engine's real session id`.
- **Empty AI Turn Fixed**: `ConversationEngine.processUserUtterance` builds AI history *before* the placeholder AI turn and prunes trailing empty AI turns on start, interruption, and empty AI responses. Regressions: `empty AI response does not leave a dangling empty AI turn`, `interruption prunes the empty placeholder AI turn`.
- **Recognizer Re-Arm Cap**: `ConversationViewModel` caps consecutive silent/error STT re-arms (`maxSilentReArms = 15`) to prevent battery/thermal runaway on a persistently failing recognizer; resets on a successful final transcript, mic tap, or permission grant.
- **Performance (VERIFIED on device)**: cold launch debug 2,860 ms (`am start -W` COLD); memory ~172 MB PSS at home; release AAB 6.34 MB / APK 4.62 MB. Battery/thermal over a long session: **NOT TESTED** (needs human beta).
- **Offline-First Integrity (VERIFIED)**: `grep` for `https?://|Retrofit|OkHttp|HttpClient|fetch(|URL(` across `src` → no matches. No network client, no cloud SDK. Secret scan → no matches. `keystore.properties` + `vaniflow-release.keystore` git-ignored.
- **Security**: no hardcoded secrets; keystore handled locally; offline-only by design.
- **Beta Gate**: GO for internal beta (see `docs/MILESTONE_15_BETA_READINESS.md`). Public release blocked until human sessions complete `docs/REAL_CONVERSATION_QA.md` and `LocalAIEngine` is either wired to a real bundled model or formally de-scoped.

---

## 6. Milestone 16 Real Conversational AI + Avatar (added 2026-08-29)

- **Root cause fixed (VERIFIED by audit)**: the repeated/canned-response bug was caused by (a) `LocalAIEngine`
  having no real backend and (b) its `generateLocalInference` being a hardcoded keyword matrix, so the
  deterministic `FallbackAIEngine` became the primary engine and ignored conversation history. The keyword
  matrix was deleted; `LocalAIEngine` now delegates to a `LocalLLMRuntime` abstraction.
- **Routing (VERIFIED via tests)**: `SmartAIRouter` priority is Local AI (primary) → Optional Cloud →
  Context-Aware Fallback → Scenario Matrix (last resort only). The scenario matrix can no longer override
  a successful Local AI response.
- **Context & Repetition (VERIFIED)**: `ConversationPromptBuilder` injects character, scenario, and real
  history (rolling window). `RepetitionGuard` (Jaccard ≥ 0.8) regenerates on repeats, then degrades to the
  honest context-aware fallback.
- **Cache (VERIFIED)**: key now includes characterId + scenarioId + normalized input + contextHash +
  promptVersion; conversation turns are `DO_NOT_CACHE`, so unrelated questions never return a stored answer.
- **Model tiers (VERIFIED)**: UI now shows **VaniFlow Lite / Core / Pro** branding (no internal model ids
  exposed). Mapping in `docs/VANIFLOW_MODEL_TIERS.md`.
- **Avatar / Lip-Sync (VISUALS WIRED, rig assets BLOCKED)**: `AvatarState`, `AvatarController`,
  `LipSyncController` (`VisemeLipSyncController`), and `CharacterAvatarConfig` provide state-driven
  listening/thinking/speaking visuals that are **wired into `ConversationScreen`** (ring color + pulse
  follow `ConversationState`). Hyper-realistic rigs and true phoneme sync are BLOCKED (no assets;
  offline TTS exposes no real-time amplitude today). See `docs/AVATAR_LIPSYNC.md`.
- **Tests (VERIFIED)**: JVM unit suite **156 tests PASS** (was 145; +11 M16: `RepetitionGuardTest`,
  `ConversationPromptBuilderTest`, `ConversationDiversityTest`, updated `SmartAIRouterTest`, updated
  `Milestone13`). On-device instrumented suite unchanged at 61 passing.
- **Performance (MEASURED on device)**: see `MILESTONE_17_REAL_LOCAL_AI.md`. On a Galaxy S9+ (CPU, 4
  threads): ~7.6–8.0 tok/s, ~1.5–2.5 s prompt processing, KV cache 24 MB. No cold-launch/memory
  regression vs M15.
- **Genuine AI status (VERIFIED REAL — not faked)**: `LlamaCppRuntime` (llama.cpp + Qwen2.5-0.5B GGUF)
  is the production `LocalLLMRuntime`, wired via Hilt. Real on-device token generation is verified by two
  on-device instrumented tests (`RealLocalLLMDeviceTest`, `RealLocalAIEngineDeviceTest`). The 0.5B Lite
  tier quality is limited (model-capability tradeoff), not a fake. See `docs/MILESTONE_17_REAL_LOCAL_AI.md`.
- **Go / No-Go**: **GO** for the "genuine on-device AI" claim. The real runtime + model file are present and
  verified on a physical device. Remaining honest caveats: Lite-tier answer quality, phoneme lip-sync still
  BLOCKED (no rig assets), and a full live mic→STT→LLM→TTS session needs a human beta.
