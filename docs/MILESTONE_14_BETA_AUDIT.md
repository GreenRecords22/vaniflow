# Milestone 14 — Real-World Beta Hardening & Production Stability

**Status:** Phase 1 (Audit) complete, Phases 2–8 in progress.
**Device:** Samsung Galaxy S9+ SM-G965F (Android 10 / API 29, ~4GB RAM).
**Date:** 2026-08-29
**Rules:** No architecture rewrite, no feature removal, offline-first preserved, no fabricated device results, no analytics/tracking, no private data exposure, existing tests retained.

---

## Phase 1 — Read-Only Audit

Audited the following sources as requested by the M14 spec:

- `engine/conversation/ConversationEngine.kt`
- `feature/conversation/ConversationViewModel.kt`
- `feature/conversation/ConversationScreen.kt`
- `engine/stt/STTEngine.kt` / `STTResult`
- `engine/tts/TTSEngine.kt` / `TTSResult`
- `engine/audio/CancellableAudioQueue.kt`
- `engine/model/ModelManager.kt` / `DefaultModelManager.kt`
- `domain/model/Models.kt` (`ConversationState`, `ConversationTurn`)
- `engine/ai/AIEngine.kt` / `LocalAIEngine` / `FallbackAIEngine`
- `engine/feedback/FeedbackEngine.kt`
- `MainActivity.kt` (permission wiring)

### Findings

| # | Area | Severity | Finding | Resolution |
|---|------|----------|---------|------------|
| F1 | Voice error UX | High | `STTResult.Error` in hands-free mode silently re-armed listening and cleared the error, so the user received **zero feedback** on STT failure. | FIXED — friendly `STT_FAILED` message now shown and persists through re-arm until a successful transcript arrives. |
| F2 | Error leakage | High | Raw exception text (`cause.message`, `result.message`) was surfaced directly to UI in STT/AI paths. | FIXED — added `ConversationErrorMessages` constants; `ConversationEngine.errorMessage` only ever holds friendly strings; all STT/AI/TTS failure paths sanitized. |
| F3 | Cancellation | High | No cancellation of in-flight AI generation / TTS when a new turn started, session ended, or user interrupted. Stale updates and overlapping audio were possible. | FIXED — `ConversationEngine` now tracks `activeGenerationJob` (via `coroutineContext[Job]`), guards stale updates, and exposes `cancelAll()` called from `startSession`, `processUserUtterance`, `endSession`, `interrupt`, and `onUserStartedSpeaking`. |
| F4 | Permissions | Medium | Permanent denial (no rationale available) was treated the same as temporary denial; no path to system settings. | FIXED — `onPermissionPermanentlyDenied()` sets `requiresPermissionSettings`; `ConversationScreen` shows a dialog that opens `ACTION_APPLICATION_DETAILS_SETTINGS`. |
| F5 | Lifecycle | Medium | `ConversationViewModel.onCleared()` did not cancel engine jobs / audio. | FIXED — `onCleared()` calls `conversationEngine.cancelAll()`. |
| F6 | Model download | Medium | `ProfileViewModel.downloadModel` had no storage pre-check; a low-storage device would start a doomed download. | FIXED — `ModelManager.hasSufficientStorage` added to the interface and `DefaultModelManager`; pre-check surfaces a friendly `modelError` before download starts. |
| F7 | State machine | Low | Transition guards existed but were not centrally enforced; mid-speak illegal transitions possible from UI. | PARTIAL — transition guards + cancellation hardening added; remaining low-risk transitions documented in `KNOWN_LIMITATIONS.md`. |
| F8 | Regression tests | High | No unit tests covered the above resilience behaviors. | FIXED — added `ConversationEngineTest` (9 tests) and `ConversationViewModelTest` (5 tests). 138 JVM tests pass. |

### Not changed (by design)
- Architecture of `SmartAIRouter`, `LocalAIEngine`/`FallbackAIEngine`, `Room` repositories, navigation graph.
- Offline-first model loading path.
- Existing instrumentation tests (all 61 retained and still green — see M13 report).

---

## Next Phases (planned)
- Phase 6: ModelManager UX storage pre-check — **done** (see F6).
- Phase 7: Session recovery audit (resume → greeting replay).
- Phase 8: Privacy audit + `PRIVACY_BETA_AUDIT.md`.
- Phase 9: Accessibility polish.
- Phases 10–12: Character/scenario regression + additional tests.
- Phase 11: Performance re-measure on device.
- Phase 13: Device regression (requires unlocked S9+).
- Phase 14: Secret scan.
- Phase 15: `clean` / `test` / `assembleRelease`.
- Phase 16: Final report + doc updates (`KNOWN_LIMITATIONS.md`, `PERFORMANCE_BASELINE.md`, `RELEASE_QA.md`, `task.md`).
