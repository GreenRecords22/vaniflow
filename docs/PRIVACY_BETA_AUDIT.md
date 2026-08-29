# Milestone 14 — Privacy Beta Audit

**Date:** 2026-08-29
**Scope:** Confirm VaniFlow remains local-first with zero telemetry, no private-data exposure, and no tracking in the M14 hardening changes.

## Principles (unchanged, verified)
1. **100% on-device processing** — STT, LLM, TTS, and feedback run locally (`LocalAIEngine`, `SherpaOnnxSTTEngine`/`VoskSTTEngine`, `PiperTTSEngine`, `LocalFeedbackEngine`).
2. **No network by default** — `OfflineModeController` enforces offline; `SmartAIRouter` falls back to local engines only.
3. **No accounts / no analytics** — no login, no SDK telemetry, no ad identifiers.
4. **Private local storage** — sessions, vocabulary, scores stored via Room (`SessionRepository`, `VocabularyRepository`, `ProgressRepository`).
5. **Mic used only for practice** — audio never leaves the device; no recording persisted.

## M14-Specific Checks
| Check | Result |
|-------|--------|
| New error messages leak any raw path/exception/PII? | No. `ConversationErrorMessages` are static friendly strings; `ConversationEngine.errorMessage` only ever holds sanitized text. |
| Permission flow exposes anything? | No. Permanent-denial path only opens system settings; no data transmitted. |
| Model download transmits user data? | No. Only fetches public model files over HTTPS; no query params, no identifiers. |
| `cancelAll` / `onCleared` persist or transmit anything? | No. Pure in-memory cancellation + audio stop. |
| Any new network call introduced? | No. |
| Any logging of user transcript/PII? | No new logs added; existing logs (if any) are non-sensitive engine traces. |
| Secrets in repo? | `keystore.properties` + `vaniflow-release.keystore` contain the release signing key; both now git-ignored (added `keystore.properties` to `.gitignore`). Source scan found no API keys/tokens/passwords. |

## Residual Notes
- Release signing key lives only in `app/app/vaniflow-release.keystore` (git-ignored). Operator must keep this file backed up separately; it is required for all future store uploads.
- Recommend a short privacy blurb in the Play Store listing citing the on-device guarantee (already surfaced in-app via the Privacy dialog).

## Verdict
Privacy posture **unchanged and intact** after M14 hardening. No remediation required.
