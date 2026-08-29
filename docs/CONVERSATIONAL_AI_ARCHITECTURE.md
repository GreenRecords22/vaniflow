# Conversational AI Architecture (Milestone 16)

This document describes the production-shaped conversational AI pipeline after M16. It is intentionally
free of a fake keyword matrix in the primary path.

## Routing Hierarchy (SmartAIRouter)

| Level | Engine | Role | When used |
|---|---|---|---|
| 0 | Deterministic educational rules | Definitions / vocabulary ("what does X mean") | Only for explicit knowledge lookups |
| **1** | **VaniFlow Local AI (`LocalAIEngine`)** | **PRIMARY conversational engine** | When a real model + runtime are ready |
| 2 | Optional Cloud Adapter | User-enabled only | If Level 1 unavailable AND cloud opted-in |
| 3 | Context-Aware Fallback | Honest degradation (no canned tree) | Level 1 & 2 unavailable |
| 4 | Deterministic Scenario Matrix (`FallbackAIEngine`) | Final last-resort | Only if everything else fails |

The scenario matrix (Level 4) **can never override a successful Local AI response**.

## Request Flow

```
STT final transcript
  → ConversationEngine.processUserUtterance
      • builds structured prompt (ConversationPromptBuilder)
      • records active context (characterId, scenarioId) on router
  → AIEngine.streamResponse(systemPrompt, history, userInput)
      → SmartAIRouter
         1. Level 0 deterministic (knowledge only)
         2. knowledge cache (keyed by char+scenario+normalized input+contextHash+promptVersion)
         3. Level 1 Local AI (real runtime)  ← PRIMARY
            • on repeat → regenerate once with stronger instruction
            • still repeat → Level 3
         4. Level 2 optional cloud (opt-in)
         5. Level 3 context-aware fallback
         6. Level 4 scenario matrix (last resort)
  → TTS (sentence streaming) + Avatar state + LipSync
```

## Prompt Structure (ConversationPromptBuilder)

Every Local AI request carries:
- `SYSTEM` identity
- `CHARACTER PERSONALITY`
- `USER LEVEL`
- `SCENARIO`
- `CONVERSATION HISTORY` (rolling window, last 8 turns)
- `LEARNING OBJECTIVE`
- `LATEST USER MESSAGE`
- `RESPONSE RULES` (answer the real question, no canned reuse, stay in character, natural follow-up)

## Local Inference Abstraction (the integration seam)

```kotlin
interface LocalLLMRuntime {
    fun isAvailable(): Boolean
    fun generate(request: LlmInferenceRequest): String?
    fun stream(request: LlmInferenceRequest): Flow<String>
    fun release()
}
```

`StubLocalLLMRuntime` is the shipped default and reports `isAvailable() == false`. A real
`LlamaCppLocalLLMRuntime` / `OnnxLocalLLMRuntime` implementing this interface (plus a real model
file) is the **only** change required to enable genuine AI conversation.

## Repetition Guard

`RepetitionGuard` tracks recent assistant responses and flags repeats by lexical Jaccard (≥ 0.8).
Router regenerates once, then degrades to the context-aware fallback. Conversation turns are
`DO_NOT_CACHE`, so the cache can never return an unrelated response for a different question.

## Verification

- `SmartAIRouterTest` — routing levels, cache, cloud fallback.
- `ConversationDiversityTest` — 5 & 10 unrelated questions produce distinct responses with a dynamic
  runtime; contextual follow-up ("there" → Jaipur) resolves.
- `RepetitionGuardTest` — identical / near-identical / distinct detection.
- `ConversationPromptBuilderTest` — all required prompt sections present.
