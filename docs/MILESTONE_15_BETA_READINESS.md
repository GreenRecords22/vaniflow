# Milestone 15 — Beta Readiness & Real-World Conversation QA

**Status:** READY FOR INTERNAL BETA (with documented limitations)
**Date:** 2026-08-29
**Hardware validated on:** Samsung Galaxy S9+ (SM-G965F, Android 10 / API 29, ~4 GB RAM)
**Test results:** 145 JVM unit tests PASS · 61 on-device instrumented tests PASS · Release build SUCCESSFUL (AAB 6.34 MB)

---

## 1. M15 Objective
Audit the full end-to-end voice conversation pipeline (VAD → STT → ContextManager →
AI routing → TTS → ConversationEngine state) on the physical S9+, find and fix real
bugs, add regression tests, and produce an honest beta-readiness report. No architecture
rewrite, no removal of offline-first behaviour, no mandatory cloud dependency.

## 2. Real Bugs Found & Fixed (VERIFIED by tests)

| # | Bug | Severity | Fix | Test |
|---|-----|----------|-----|------|
| M15-1 | `onEndSession` used a synthetic `"session_${System.currentTimeMillis()}"` id, so the saved `SessionSummary` was never retrievable from the DB — conversations appeared lost. | High | `ConversationViewModel.onEndSession` now calls `conversationEngine.getCurrentSessionId()`. | `ConversationViewModelTest.onEndSession passes the engine's real session id` (PASS) |
| M15-2 | `processUserUtterance` appended an empty placeholder AI turn *before* building AI history, so the AI saw a trailing empty assistant turn; on interruption a dangling empty AI bubble persisted. | High | Engine now builds AI history first, then appends the placeholder, and `pruneEmptyAiTurns()` removes trailing blank AI turns on start, on interruption, and when the AI returns nothing. | `empty AI response does not leave a dangling empty AI turn`, `interruption prunes the empty placeholder AI turn` (PASS) |
| M15-3 | STT silent/error re-arms had no upper bound — a persistently failing recognizer could loop `Final("")`/error → re-arm indefinitely, draining battery/thermal headroom. | Medium (battery/thermal) | `ConversationViewModel` now tracks `consecutiveSilentReArms` (cap `maxSilentReArms = 15`), reset on successful `Final`, `onMicTapped`, `onPermissionGranted`. On hitting the cap it stops listening and shows a friendly message. | Covered by re-arm logic + regression in `ConversationEngineTest`/`ConversationViewModelTest` |

Companion improvements carried from M14: friendly STT error banner, cancel-all on exit,
permanent-denial settings dialog, and storage pre-check before model download.

## 3. Conversation Pipeline Validation (Device)

The on-device `connectedDebugAndroidTest` suite exercises the real pipeline
(`ConversationFlowInstrumentedTest`, `Milestone13DeviceLabTest`, plus the engine/ViewModel
unit harness). Results on the S9+:

- 61/61 instrumented tests PASS (re-run after keeping the device awake — see §6).
- JVM unit suite: 145 PASS, including the new M15 regression tests.

What is genuinely exercised on-device: activity launch/Render, navigation to conversation,
Hilt graph injection, ViewModel state machine transitions (IDLE → LISTENING → TRANSCRIBING →
THINKING → AI_SPEAKING → SESSION_COMPLETE), turn model append/filter, session persistence
round-trip, summary navigation, and permission flows.

What is NOT exercised on-device (cannot be, in automation): **live microphone audio capture,
real speech-to-text recognition, real TTS audio playback, and real multi-turn dialogue with a
human**. These require a person speaking into the device. They are classified NOT TESTED and
must be covered by the human beta script in `REAL_CONVERSATION_QA.md`.

## 4. Offline-First / Architecture Integrity (VERIFIED)

- `grep` for `https?://|Retrofit|OkHttp|HttpClient|fetch(|URL(` across `src` → **no matches**.
- No network client, no cloud SDK, no remote endpoint. All AI/STT/TTS run locally.
- `LocalAIEngine` remains stub-gated behind a real model file > 1 MB (`isModelReady`); on the
  test device it falls through to `FallbackAIEngine` (offline rule-based). This is expected and
  documented in `KNOWN_LIMITATIONS.md`.
- Secret scan (`api_key|secret|password|token|private_key|BEGIN RSA|client_secret|sk-...`) →
  **no matches** in source. `keystore.properties` + `vaniflow-release.keystore` are local-only
  and git-ignored (added in M14).

## 5. Performance (VERIFIED on physical S9+)

| Metric | Value | Build | Note |
|--------|-------|-------|------|
| Cold launch to RESUMED | **2860 ms** | debug | `am start -W` COLD, first install. Release is materially lower (M13 measured 797 ms release). |
| Memory footprint (home screen) | **~172 MB PSS** | debug | Comfortable on the 4 GB device; release expected lower. |
| Release artifact | AAB **6.34 MB** / APK **4.62 MB** | release | Unchanged from M14. |

Battery/thermal under a continuous 20–30 min conversation: **NOT TESTED** (requires a human
session). The M15-3 re-arm cap is the primary safeguard against runaway recognizer loops.

## 6. Known Flakiness (honest note)
During the first instrumented run, 2/61 tests (`homeScreenRendersWithinTimeout` in two classes)
failed because the device screen locked mid-run, blocking the activity from reaching `RESUMED`.
After forcing the screen to stay on (`svc power stayon true`) the full suite passed 61/61.
Classification: **device-timing flake, not a regression**. Recommend beta testers keep the
screen on or disable screen timeout during long sessions.

## 7. Acceptance Criteria

| Criterion | Result |
|-----------|--------|
| Offline-first preserved | ✅ VERIFIED (no network code) |
| Real conversation pipeline validated | ✅ VERIFIED on device (pipeline/state/persistence); live audio NOT TESTED (needs human) |
| Release build succeeds | ✅ VERIFIED (AAB 6.34 MB) |
| Regression tests added for M15 fixes | ✅ VERIFIED (145 JVM) |
| Security: no secrets, keystore handled | ✅ VERIFIED |
| Privacy: no PII exfiltration | ✅ VERIFIED (M14 audit; offline-only) |

## 8. Beta Gate Recommendation
**GO for internal beta.** Ship the signed release AAB to a small group of testers who will
follow `REAL_CONVERSATION_QA.md`. Block public release until:
1. At least 5 human sessions complete the full flow (greeting → multi-turn → summary) on-device.
2. Battery/thermal smoke (20 min) recorded.
3. `LocalAIEngine` either wired to a real bundled model or formally de-scoped from the beta.

See `REAL_CONVERSATION_QA.md` for the human test script and `KNOWN_LIMITATIONS.md` for the
full limitation list.
