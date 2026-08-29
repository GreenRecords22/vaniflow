# Milestone 16 — Real Conversational AI + Human-Like Avatar Experience

**Status:** ARCHITECTURE COMPLETE · GENUINE LOCAL INFERENCE **BLOCKED** (no usable native
backend / model in this build)
**Date:** 2026-08-29
**Hardware:** Samsung Galaxy S9+ (SM-G965F, Android 10 / API 29)
**JVM tests:** 156 PASS (incl. new M16 regression + diversity + repetition-guard + prompt tests)

---

## 1. Root Cause of Repeated Responses (VERIFIED by code audit)

The reported bug — *"5 different questions return essentially the same stored sentence"* — has
two compounding root causes, both **confirmed by reading the actual implementation** (not assumed):

1. **No real local inference backend exists.** `LocalAIEngine.isModelReady()` requires a real
   model file (>1 MB) **AND** a loaded runtime. The repository contains no native LLM runtime
   (no llama.cpp / ONNX Runtime dependency) and no real model file. So `isModelReady()` returns
   `false` and `SmartAIRouter` skips the Local AI (Level 1) entirely.
2. **`LocalAIEngine.generateLocalInference()` was itself a hardcoded keyword matrix.** Even if a
   model file were present, the old code returned canned sentences from a `when` on keywords
   (`contains("coffee")`, `contains("interview")`, …). This is the deterministic dialogue matrix
   the user explicitly forbade.
3. **The deterministic fallback (`FallbackAIEngine`) was the PRIMARY engine.** With Local AI
   skipped, `SmartAIRouter` fell through to `FallbackAIEngine`, which is a large keyword/`when`
   tree producing the same templated replies for broad question classes — and it **ignored
   conversation history entirely** (only `history.size` was used for a seed), so context like
   "there" → Jaipur was never resolved.

Net effect: the app was, in practice, a "voice app with predefined responses" — exactly the
failure mode M16 set out to fix.

## 2. Local AI Implementation Status (BLOCKED — not faked)

Per the M16 directive, when a real local inference backend is missing we must **STOP and report
it**, and build the correct abstraction instead of faking success. That is what was done:

- New `LocalLLMRuntime` interface (`engine/ai/llm/LocalLLMRuntime.kt`) is the single contract a
  real on-device model must satisfy (`isAvailable()`, `generate()`, `stream()`, `release()`).
- `StubLocalLLMRuntime` ships as the default. It **honestly returns `isAvailable() == false`** and
  `null`/empty streams. It does NOT implement inference and does NOT emit canned text.
- `LocalAIEngine` now delegates to the runtime. When unavailable it returns
  `AIResult.Error(...)` instead of a fake answer. The old keyword `generateLocalInference` was
  **deleted**.
- **Integration point:** dropping in a `LlamaCppLocalLLMRuntime` or `OnnxLocalLLMRuntime` that
  satisfies `LocalLLMRuntime` (plus a real `.gguf`/`.onnx` model file) enables genuine dynamic
  conversation with **zero further changes** to routing, prompt building, cache, or UI.

**Status: REAL LOCAL INFERENCE BACKEND MISSING → BLOCKED.** No fabricated AI was substituted.

## 3. Model Runtime Status

- Model metadata for `llm_qwen25_05b_instruct` and `llm_qwen25_15b_instruct` exists and is mapped
  to VaniFlow product tiers (see `VANIFLOW_MODEL_TIERS.md`), but **no model file is bundled and
  no runtime can load one**. Until a runtime + model are added, the model download UI prepares for
  a real model but the engine cannot generate from it.

## 4. VaniFlow Model Tier Mapping (VERIFIED — branding only)

| Internal Model ID | VaniFlow Brand | RAM | Storage | Quality | User-facing? |
|---|---|---|---|---|---|
| `llm_qwen25_05b_instruct` | **VaniFlow Lite** | 2 GB | ~390 MB | Everyday | ✅ shown |
| `llm_qwen25_15b_instruct` | **VaniFlow Core** | 4 GB | ~1.1 GB | Balanced/Recommended | ✅ shown |
| (future 3B/7B) | **VaniFlow Pro** | 8 GB | ~2.2 GB | Premium | ✅ shown |

Technical model names (Qwen, GGUF ids) are **never shown in the UI**; the model card now displays
only the VaniFlow brand + tagline + size. Internal ids remain in code/config only.

## 5. Character Intelligence Implementation

`CharacterRegistry` already defines four distinct `systemPrompt`s (Raya / Rudra / Adwaita / Shub).
`ConversationPromptBuilder` now injects the character identity, personality, user level, and
scenario into every request, and `FallbackAIEngine`/the context-aware fallback still differentiate
tone per character. **With a real model these produce genuinely different personalities** because
the prompt carries the persona. (Character-specific canned replies are only in the last-resort
scenario matrix, not the primary path.)

## 6. Context Memory Implementation

`ConversationPromptBuilder.buildRuntimePrompt` assembles `CONVERSATION HISTORY` (last 8 turns),
`LATEST USER MESSAGE`, and `SCENARIO`. `ContextManager.compactHistory` keeps a rolling window (last
6 turns) to bound token budget. The engine now passes real `history` (not just `history.size`) into
the prompt, so pronoun resolution ("there" → Jaipur) is possible for a real model.

## 7. Repetition Prevention (VERIFIED)

`RepetitionGuard` (Phase 7) tracks recent assistant responses, computes lexical Jaccard similarity,
and flags repeats at ≥ 0.8. `SmartAIRouter` regenerates once with a stronger instruction on repeat;
if still repetitive, it falls back to the context-aware message instead of emitting the duplicate.
Unit-tested in `RepetitionGuardTest` and `ConversationDiversityTest`.

## 8. Cache Correction (VERIFIED)

`AIResponseCache` key now includes `characterId`, `scenarioId`, normalized `userInput`,
`contextHash` (history hash), and `promptVersion`. Conversational turns are `DO_NOT_CACHE`, so the
cache can never return an unrelated stored response for a different question. Only knowledge
definitions ("what does X mean") are cached, keyed precisely.

## 9. Avatar Implementation (ARCHITECTURE COMPLETE · assets BLOCKED)

- `AvatarState` enum (IDLE/LISTENING/THINKING/SPEAKING/INTERRUPTED/ERROR) mirrors
  `ConversationState`; `ConversationState.toAvatarState()` maps them.
- `AvatarController` exposes a clean `StateFlow<AvatarState>` the UI observes.
- `CharacterAvatarConfig`/`CharacterAvatarRegistry` map each character to its own asset
  (Raya/Rudra/Adwaita/Shub).
- `CharacterAvatar` now visibly reacts to state (ring color + subtle pulse while listening/speaking).
- **Hyper-realistic avatar rigs are NOT present in this repo → BLOCKED.** The avatar layer is
  independent of AI logic and ready to drive real rigs.

## 10. Lip-Sync Implementation (ARCHITECTURE COMPLETE · true viseme BLOCKED)

`LipSyncController` interface + `VisemeLipSyncController` provide amplitude/viseme-based mouth
openness: mouth opens while speaking (deterministic viseme curve), snaps shut on speech end, and
supports real amplitude feed via `onAmplitude()`. **True phoneme-level sync against real rigs is
BLOCKED** (no rig + offline TTS does not currently expose a real-time amplitude stream). This is an
abstraction, not random mouth animation.

## 11. TTS / Avatar Synchronization

`ConversationEngine` already drives state `THINKING → AI_SPEAKING → LISTENING` and cancels TTS/state
on interruption. The avatar state follows this same `ConversationState`, so TTS playback, avatar
speaking state, and (when wired to real amplitude) lip sync all derive from one source. On
interruption, state → `INTERRUPTED` → `LISTENING` immediately stops the avatar speech state.

## 12–14. Tests / JVM / Instrumented

- **JVM:** 156 tests PASS (was 145; +11 M16: `RepetitionGuardTest`, `ConversationPromptBuilderTest`,
  `ConversationDiversityTest`, updated `SmartAIRouterTest`, plus updated `Milestone13`).
- **Key new regression:** `ConversationDiversityTest` feeds 5 and 10 unrelated questions through the
  real router with a dynamic runtime and asserts responses are **not identical**; it also verifies
  contextual follow-up ("What did I see there?" → resolves Jaipur).
- **Instrumented on-device:** not re-run this milestone (no behavior change to the on-device harness;
  genuine conversation requires a real model, which is BLOCKED). Prior 61/61 still valid.

## 15. Physical Device Conversation Tests — **BLOCKED**

Per M16 Phase 20, the 10-question diversity + avatar/lip-sync tests require a real local model.
**Cannot be marked VERIFIED** because no inference backend exists. With the stub runtime, the app
now honestly returns the context-aware fallback message rather than fake canned dialogue.

## 16–17. 10-Question Diversity & Performance

- Diversity (with dynamic runtime): **VERIFIED by unit test** (`ConversationDiversityTest`).
- Performance: no regression expected; cold launch / memory unchanged from M15. Real-model latency
  is **ESTIMATED** (Phase 4 numbers in `PERFORMANCE_BASELINE.md`).

## 18. Remaining Limitations (honest)

- **Genuine local AI conversation is BLOCKED** until a native inference runtime + real model are
  added. The architecture is fully wired for it.
- Avatar hyper-realistic assets and true phoneme lip-sync are BLOCKED (no rigs).
- Battery/thermal over a long real session: NOT TESTED.
- Deterministic scenario matrix exists only as Level 4 last-resort fallback (never overrides
  successful Local AI).

## Go / No-Go

**NO-GO for "real AI" claim.** The product is now architecturally correct (no fake keyword AI in the
primary path, honest fallback, branded tiers, repetition guard, context-aware prompting). To ship
genuine conversational AI, the single required deliverable is a `LocalLLMRuntime` implementation
plus a real on-device model file — everything else is already in place.
