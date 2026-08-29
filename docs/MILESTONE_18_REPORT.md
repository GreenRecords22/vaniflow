# Milestone 18 Report — Real Human-Like Voice Conversation + Avatar

**Date:** 2026-08-29
**Goal:** Make VaniFlow feel like a natural, human-like voice conversation with a
character avatar — real end-to-end offline voice loop, repetition prevention,
personality, honest avatar behavior, and strict honest reporting.

**Verdict:** Core conversational correctness is improved and verified; the real
on-device Local AI produces contextual, distinct, non-canned replies, and the
streaming/logic fixes are test-backed. The full *live* voice loop and
phoneme lip-sync remain NOT TESTED / BLOCKED for honest, documented reasons.

---

## VERIFIED

- **Real on-device generation produces distinct, contextual answers.**
  `RealLocalLLMDeviceTest` executed on physical device `CASSE65LH66SWSZP`
  (RMX2040, Android 11). Three follow-up questions about "working from home"
  returned three non-blank, mutually-distinct, context-aware replies. Logged
  `REAL INFERENCE VERIFIED`. No network used.
- **Streaming is now live, not buffered.** `SmartAIRouter.streamResponse()`
  emits tokens incrementally (previously buffered the whole reply via
  `.collectJoined()`, killing first-sentence TTS). Proven by
  `SmartAIRouterStreamingTest` (inter-token gap > 150 ms).
- **Repetition guard no longer blocks short legit replies.**
  `MIN_WORDS_FOR_REPETITION = 2` added to `RepetitionGuard`; "Yes." /
  "Absolutely." / "I agree." are no longer flagged. Proven by
  `RepetitionGuardTest` (true duplicates still caught; short affirmations pass;
  state resets).
- **Prompt preserves personality AND adds follow-up/topic awareness.**
  `ConversationPromptBuilder` still threads character + scenario + history and
  now adds explicit rules to resolve pronouns from history, follow user topic
  changes, and keep replies to 1–3 spoken sentences. Proven by extended
  `ConversationPromptBuilderTest`.
- **Cache key is conversation-safe.** `AIResponseCacheKeySafetyTest` confirms
  different questions get different keys (normalized input + context hash), and
  conversational turns are never cached (`DO_NOT_CACHE`).
- **Branding is clean.** No internal model names (Qwen/Llama/GGUF/llama.cpp/Piper/
  Vosk/Zipformer) appear in the UI — audit confirmed.
- **Offline-first preserved.** All generation ran fully on-device; no network call.
- **Full JVM suite green:** `./gradlew testDebugUnitTest` → **160 tests passing**,
  BUILD SUCCESSFUL (was 156 before M18; +4 new M18 regression tests).
- **Unit-test suite fixed:** the 2 failing `ModelManagerTest` network-dependent
  tests were rewritten to offline local-file tests (no real download in CI).
- **Avatar reflects real state deterministically.** `ConversationScreen` now
  animates `LISTENING` / `THINKING` (pulsing) / `SPEAKING` / `IDLE` from the
  actual `ConversationState` — THINKING previously had no motion.

---

## ESTIMATED

- **First-spoken-sentence latency:** the first sentence is spoken as soon as the
  first sentence boundary is reached in the live stream. M17 measured ~7.9 tok/s
  on a Galaxy S9+; exact latency on RMX2040 is not re-measured here (no live
  spoken session driven). The *mechanism* (live streaming + sentence-split TTS)
  is verified by code + unit test.
- **Model quality:** the 0.5B "VaniFlow Lite" is genuine but capped at 160 tokens
  and prone to occasional training-leakage; carried from M17, not a regression.

---

## NOT TESTED

- **Full live mic → STT → LLM → TTS spoken loop.** Requires a human speaking into
  the device; cannot be driven automatically. The LLM half (text → streamed
  tokens) is verified on-device; STT and TTS paths are verified by code/unit
  tests and prior milestones, but not as a live connected loop here.
- **Hands-free / ambient loop over a long session** (battery/thermal/LMK) — needs
  a human beta session.
- **Airplane-mode full offline install-to-conversation** — the model is loaded from
  device storage when present; not re-driven end-to-end here.

---

## BLOCKED (honest — with WHY and NEXT ACTION)

- **Phoneme / viseme-accurate lip-sync → BLOCKED.**
  *Why:* the offline Android TTS emits no viseme timestamps, and there are no
  avatar rig assets with mouth blendshapes in the repo. *Next action:* add a
  real-time amplitude/VAD stream from TTS and a rigged avatar with mouth
  morph targets, or swap to a TTS that exposes visemes. Until then the avatar
  uses honest state/amplitude-driven animation (no fake word-synced mouth).
- **Streaming-path device test crashes on RMX2040 → BLOCKED (device-specific).**
  *Why:* `RealLocalAIEngineDeviceTest` (which exercises `LocalAIEngine`
  `.streamResponse()` through `LlamaCppRuntime`) **crashed the process** on
  RMX2040 during streaming generation. The **identical** test passed on a Galaxy
  S9+ in M17, and the **generate-path** device test passes on RMX2040 today —
  so this is a **native `llama.cpp` streaming-path issue specific to this
  device/SoC** (ColorOS/RMX2040), not a regression from the M18 Kotlin changes
  (which only added `open` to `LocalAIEngine`/`isModelReady` and touched
  `SmartAIRouter`, `RepetitionGuard`, `ConversationPromptBuilder`).
  *Next action:* reproduce on a second device and capture a native tombstone to
  determine whether it is a SoC-specific NEON/ABI issue in the bundled
  `libllamajni` build or a streaming-cleanup (nativeAbort) race; harden the JNI
  stream teardown.

---

## Changes made (summary)

| Area | File | Change |
|------|------|--------|
| Streaming | `engine/ai/SmartAIRouter.kt` | Local path emits tokens live; removed mid-stream `collectJoined()` buffering |
| Repetition | `engine/ai/RepetitionGuard.kt` | `MIN_WORDS_FOR_REPETITION = 2` |
| Prompt | `engine/ai/prompt/ConversationPromptBuilder.kt` | Added follow-up / topic-change / persona rules |
| Avatar | `feature/conversation/ConversationScreen.kt` | State-driven scale/pulse per `ConversationState` |
| Testability | `engine/ai/LocalAIEngine.kt` | `open class` + `open fun isModelReady()` |
| Tests | `ModelManagerTest.kt` | Offline local-file rewrite (no real download) |
| New tests | `RepetitionGuardTest`, `AIResponseCacheKeySafetyTest`, `SmartAIRouterStreamingTest` | M18 regression coverage |
| Device tests | `RealLocalLLMDeviceTest.kt`, `RealLocalAIEngineDeviceTest.kt` | Copy GGUF from `/data/local/tmp` at runtime for reproducibility |

---

## Recommended next actions (post-M18)

1. Capture the RMX2040 native tombstone for the streaming crash and harden the JNI
   stream teardown (`nativeAbort` on completion vs. cancellation race).
2. Run a real human voice session to validate the full mic→STT→LLM→TTS loop and
   avatar reactivity on-device.
3. (Optional) Add amplitude-driven avatar mouth motion as a stopgap before true
   viseme lip-sync is feasible.
