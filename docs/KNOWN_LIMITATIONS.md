# VaniFlow Known Limitations & Post-MVP Roadmap

## 1. Known Limitations in MVP Release Candidate

1. **Hardware Validation (partially done)**:
    - `VERIFIED` on physical S9+ (SM-G965F, API 29): cold launch (release 797 ms / debug 2,860 ms), memory footprint (~172 MB PSS home), 145 JVM + 61 on-device instrumented tests.
    - `NOT TESTED`: thermal dissipation over a 10-min+ session, battery consumption rate, and LMK low-RAM pressure behavior. These require a human beta session (see `REAL_CONVERSATION_QA.md`).
2. **Historical Trend Visualizations**:
   - Progress bar charts display the active calendar week (Monday–Sunday); multi-month historical graphing is scheduled for post-MVP cloud sync / multi-device milestones.
3. **Release APK Unsigned**:
   - `app-release-unsigned.apk` is compiled with full R8 optimization, resource shrinking, and ProGuard rules, but requires developer/organization keystore signing before Google Play Store publishing.

## 2. Addressed in Milestone 14 (Production Stability Hardening)

The following previously-soft areas were hardened and are no longer limitations:

- **Voice error UX**: STT/AI/TTS failures now surface friendly, non-technical messages; raw exceptions/paths are never shown to the user (see `ConversationErrorMessages`).
- **Silent STT failure**: hands-free mode no longer swallows STT errors — a friendly "I didn't catch that" message is shown and persists until a successful transcript arrives.
- **Generation cancellation**: in-flight AI/TTS generation is cancelled on new turn, interruption, session end, and `ViewModel.onCleared()` via `ConversationEngine.cancelAll()`.
- **Permission permanent denial**: handled with a settings deep-link dialog (`requiresPermissionSettings`).
- **Model storage pre-check**: downloads are blocked with a friendly message when free storage is insufficient (`ModelManager.hasSufficientStorage`).

### Residual low-risk items (by design)
- `ConversationState` is a single source of truth but UI transitions are guarded at the call sites rather than via a strict centralized state-machine reducer. Mid-speak illegal transitions are prevented by `activeGenerationJob` cancellation, not a formal event validator. This is accepted for MVP; a formal reducer is a post-MVP improvement.
- Greeting re-plays from `IDLE` on a fresh session; resuming a backgrounded in-progress session continues from current engine state (no forced reset, no transcript loss within the same process).

## 3. Addressed in Milestone 15 (Real Conversation QA)

- **Session summary data-loss**: `onEndSession` now uses the engine's real session id (`getCurrentSessionId()`), so saved `SessionSummary` records are correctly retrievable (were previously unreachable via a synthetic id).
- **Empty AI turn artifact**: the engine builds AI history *before* appending the placeholder AI turn and prunes trailing empty AI turns on start, interruption, and empty AI responses (no dangling empty bubbles).
- **Recognizer re-arm loop**: silent/error STT re-arms are now capped (`maxSilentReArms = 15`) to prevent battery/thermal runaway on a persistently failing recognizer.

### Residual limitations identified in M15 (honest, not yet fixed)
- **`LocalAIEngine` is stub-gated**: behind a real model file > 1 MB (`isModelReady`). On devices without the bundled model it falls through to `FallbackAIEngine` (offline rule-based). Bundling/loading a real `.gguf` model is out of scope for this beta and documented as a beta limitation.
- **TTS is serial, not overlapping**: `RealOfflineTTSEngine.speak` awaits each sentence on the Main dispatcher, so concurrent/overlapping TTS is not supported (by design; acceptable for turn-based practice).
- **Live audio/STT/TTS not automated**: real microphone capture, speech recognition, and TTS playback cannot be validated in CI; they require a human session (`REAL_CONVERSATION_QA.md`).

## 4. Addressed in Milestone 16 (Real Conversational AI + Avatar)

The root cause of repeated/canned responses was fixed structurally: the old keyword `generateLocalInference`
in `LocalAIEngine` was **deleted** and replaced with a `LocalLLMRuntime` abstraction; `SmartAIRouter`
was re-prioritized so Local AI is the primary engine (not the deterministic matrix); conversation
history now flows into the prompt (`ConversationPromptBuilder`); repetition is prevented by
`RepetitionGuard`; and the cache key is corrected so unrelated questions can never return a stored
answer. Model tiers are now VaniFlow-branded in the UI. See `MILESTONE_16_REAL_AI.md` and
`CONVERSATIONAL_AI_ARCHITECTURE.md`.

### Residual limitations / BLOCKED items in M16 (honest, not yet fixed)
- **Deterministic scenario matrix still exists as Level 4 last-resort** (`FallbackAIEngine`): it can
  only trigger when Local AI, Cloud, and the context-aware fallback all fail. It is no longer the
  primary engine and cannot override successful Local AI.
- **Hyper-realistic avatar rigs → BLOCKED**: no rig assets in repo. The state-driven avatar
  (`AvatarController`/`CharacterAvatar`) IS wired into `ConversationScreen` (ring color + pulse
  follow `ConversationState`), but remains a flat drawable, not a 3D rig.
- **True phoneme-level lip-sync → BLOCKED**: `VisemeLipSyncController` is a deterministic stand-in;
  real sync needs rig assets + a real-time amplitude stream from TTS (offline TTS is synchronous today).
- **TTS is serial** (carried from M15): unaffected by M16.
- **Live audio/STT/TTS not automated** (carried from M15).

## 5. Addressed in Milestone 17 (Real On-Device Local AI)

The single BLOCKED seam from M16 — a real `LocalLLMRuntime` + model — is now **implemented and
verified on a physical device** (see `MILESTONE_17_REAL_LOCAL_AI.md`):

- **`LlamaCppRuntime`** (llama.cpp + Qwen2.5-0.5B GGUF) is the production `LocalLLMRuntime`, wired
  via Hilt. Real token generation; no stub/keyword matrix in the production path.
- **Real model download** streams via Ktor + SHA-256 verify (simulated download removed).
- **On-device tests pass** (`RealLocalLLMDeviceTest`, `RealLocalAIEngineDeviceTest`) proving genuine,
  contextual, distinct generation.

### Residual limitations after M17 (honest)
- **0.5B "VaniFlow Lite" quality is limited**: genuine but prone to role-play/training-leakage and is
  capped at 160 tokens. The 1.5B "VaniFlow Core" tier gives materially better responses on capable
  devices. This is a model-capability tradeoff, not a code defect.
- **Phoneme lip-sync still BLOCKED** (no rig assets) — carried from M16.
- **Live mic → STT → LLM → TTS conversation not automated**; the LLM half is validated by the
  instrumented tests, but a full spoken session needs a human (see `REAL_CONVERSATION_QA.md`).
- Running both device tests in one instrumentation process crashes (shared native model state after
  `release()`); run them separately.

## 6. Addressed in Milestone 18 (Real Human-Like Voice Conversation + Avatar)

Conversational realism and avatar honesty were improved without touching the
verified on-device architecture:

- **Live streaming fix**: `SmartAIRouter.streamResponse()` no longer buffers the
  whole Local AI reply (the old `.collectJoined()` killed first-sentence TTS).
  Tokens now stream live so TTS can begin on the first sentence.
- **Repetition fix**: `RepetitionGuard` now has `MIN_WORDS_FOR_REPETITION = 2`, so
  short legitimate replies ("Yes." / "Absolutely." / "I agree.") are no longer
  wrongly flagged as repetition.
- **Prompt follow-up awareness**: `ConversationPromptBuilder` adds explicit rules
  to resolve pronouns from history, follow the user's topic change, and keep
  replies to 1–3 spoken sentences (personality/scenario context preserved).
- **Avatar honesty**: `ConversationScreen` animates per real `ConversationState`
  (LISTENING / THINKING-pulse / SPEAKING / IDLE). No fake word-synced mouth.

### Residual limitations after M18 (honest)
- **Phoneme/viseme lip-sync → still BLOCKED** (no rig assets; offline TTS emits no
  visemes). Carried from M16; avatar uses state/amplitude-driven motion only.
- **Streaming-path device test CRASHES on RMX2040** (device-specific native
  `llama.cpp` issue, not a Kotlin regression). The generate-path device test
  passes on the same phone, and the streaming test passed on a Galaxy S9+ in M17.
  Tracked for native tombstone capture + JNI teardown hardening.
- **Full live mic → STT → LLM → TTS loop NOT TESTED** automatically (needs a
  human); LLM half verified on-device (see `REAL_CONVERSATION_QA_M18.md`).
- JVM suite: 160 passing. Device proof: `RealLocalLLMDeviceTest` PASS on RMX2040;
  `RealLocalAIEngineDeviceTest` CRASH on RMX2040 (see above).

## 7. Addressed in Milestone 19 (Real Voice Beta + Native Streaming Stability)

The M18 streaming-path native crash on RMX2040 is **FIXED**:

- **Root cause:** `llama_token_to_piece()` returns raw UTF-8 bytes; a multi-byte
  character can be split across two token pieces, and the streaming path called
  `NewStringUTF` on the incomplete sequence → `SIGABRT` ("JNI DETECTED ERROR: not
  valid Modified UTF-8"). The non-streaming `generate()` path returned the full
  buffer at the end (complete UTF-8) so it never triggered — which is why only the
  streaming test crashed, and only on outputs that split a multibyte char
  (non-deterministic due to sampling).
- **Fix:** buffer raw piece bytes and emit only complete UTF-8 characters
  (`utf8_first_char_len` helper); final string built solely from complete chars.
- **Build note:** the `cpp/` source was **not** wired into Gradle (`no
  externalNativeBuild`), so the shipped `libllamajni.so` was a prebuilt in
  `jniLibs/arm64-v8a`. The fix was recompiled manually with NDK r27
  (`-static-libstdc++`, linked against the prebuilt llama libs) and the prebuilt
  `.so` replaced. **Follow-up:** reference `cpp/CMakeLists.txt` from `build.gradle`
  via `externalNativeBuild` so C++ edits compile automatically.
- **Verified on device:** `RealLocalAIEngineDeviceTest` now **passes on RMX2040**;
  streamed output containing emoji (`🌟`, `😊`) — the exact bytes that previously
  aborted — confirmed in logcat with `ENGINE STREAM VERIFIED`.

### Residual limitations after M19 (honest)
- **Live human voice loop (mic→STT→LLM→TTS) NOT TESTED** — requires a person
  speaking; see `REAL_VOICE_BETA_M19.md`. The LLM half is verified on-device.
- **Phoneme/viseme lip-sync still BLOCKED** (no rig/viseme API) — carried forward.
- **Gradle/ADB harness flakiness** on this machine: repeated device-test runs hit
  locked `utp.0.log` / "Failed to install split APK" errors (environment, not code).
  Tests pass when run cleanly.
- **Release APK unsigned** (carried from MVP) — needs keystore signing for Play.
