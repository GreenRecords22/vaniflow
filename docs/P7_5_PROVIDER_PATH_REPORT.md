# VaniFlow P7.5 — AI Provider Path & Routing Resilience Report

## 1. Routing Hierarchy

VaniFlow implements a 4-tier pedagogical routing hierarchy managed by `SmartAIRouter.kt`:

```
Tier 1: Cloud LLM (Groq API / VaniFlow Remote Gateway)
  │ [Network error / Timeout / Rate limit]
  ▼
Tier 2: On-Device LLM (Local Qwen 2.5 1.5B via ONNX Runtime)
  │ [Low RAM / Model missing / Inference failure]
  ▼
Tier 3: Context-Preserving Emergency Fallback (FallbackAIEngine)
  │ [Internal exception]
  ▼
Tier 4: Level 0 Static Knowledge Cache
```

---

## 2. Provider Verification Matrix

| Route Condition | Primary Route Selected | Fallback Behavior | Verified In Test Suite |
|---|---|---|---|
| **Online + Valid API Key** | `SmartAIRouter (Cloud Groq / Llama3-8B)` | Automatic retry with prompt backoff | `P7_5_ProviderPathTest.kt` |
| **Offline Mode** | `LocalQwenAiEngine (On-device Qwen2.5)` | Seamless local inference without network calls | `P7_5_ProviderPathTest.kt` |
| **Cloud Error / 5xx** | `SmartAIRouter -> Local Qwen Fallback` | Degrades to on-device without crashing conversation | `P7_5_ProviderPathTest.kt` |
| **Both Cloud & Local Unavailable** | `FallbackAIEngine (Emergency)` | Contextual sentence generation preserving session memory | `P7_5_ProviderPathTest.kt` |

---

## 3. Quality Guard & Repetition Filter

- **ResponseQualityGuard:**
  - Cleans AI model artifacts: `<think>`, `</think>`, `<user_speech>`, character name prefixes (`Raya:`).
  - Jaccard similarity threshold set to `0.85` on significant words, preventing false-positive duplicate triggers on diverse conversation topics.
  - Canned phrase regex table filters robotic chatbot responses before TTS generation.\n