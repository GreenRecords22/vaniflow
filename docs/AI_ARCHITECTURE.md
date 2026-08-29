# VaniFlow — AI Architecture & Orchestration

## 1. Provider-Agnostic Design Philosophy

VaniFlow decouples conversation intelligence from specific AI backends. The user interface, ViewModels, and `ConversationEngine` **never communicate directly with any external vendor SDK or API**.

All generation, evaluation, and analysis requests flow through a unified, contract-driven interface: `AIEngine`.

```
                      ┌─────────────────────────────┐
                      │    ConversationEngine /     │
                      │     Use Case Consumers      │
                      └──────────────┬──────────────┘
                                     │
                                     ▼
                      ┌─────────────────────────────┐
                      │      AIEngine Interface     │
                      └──────────────┬──────────────┘
                                     │
                                     ▼
                      ┌─────────────────────────────┐
                      │        SmartAIRouter        │
                      │  (Decides Optimal Engine)   │
                      └──────┬──────────────┬───────┘
                             │              │
             ┌───────────────┴────┐   ┌─────┴────────────────┐
             │                    │   │                      │
             ▼                    ▼   ▼                      ▼
    ┌─────────────────┐  ┌──────────────────┐  ┌───────────────────┐
    │  LocalAIEngine  │  │ CloudAIEngine    │  │ FallbackAIEngine  │
    │   (llama.cpp)   │  │ (OpenRouter/etc) │  │ (Local Scripted)  │
    └─────────────────┘  └──────────────────┘  └───────────────────┘
```

---

## 2. Core Contracts & Engine Interfaces

### 2.1. The `AIEngine` Interface

```kotlin
package com.vaniflow.app.engine.ai

import kotlinx.coroutines.flow.Flow

interface AIEngine {
    val engineType: AIEngineType
    val isAvailable: Boolean

    /**
     * Streams tokens in real time as they are generated.
     */
    fun generateResponseStream(
        promptContext: AIPromptContext
    ): Flow<AIStreamChunk>

    /**
     * Synchronous / full generation for offline or non-streaming use cases.
     */
    suspend fun generateResponse(
        promptContext: AIPromptContext
    ): Result<AIResponse>

    /**
     * Evaluates a turn for grammatical mistakes and naturalness.
     */
    suspend fun evaluateTurn(
        userText: String,
        contextHistory: List<TurnSnippet>
    ): Result<TurnEvaluation>
}

enum class AIEngineType {
    LOCAL_ON_DEVICE,
    CLOUD_PROVIDER,
    DETERMINISTIC_FALLBACK,
    MOCK
}

data class AIPromptContext(
    val characterSystemPrompt: String,
    val scenarioGoal: String,
    val conversationHistory: List<TurnSnippet>,
    val userCurrentInput: String,
    val maxTokens: Int = 150,
    val temperature: Float = 0.7f
)

sealed interface AIStreamChunk {
    data class Token(val text: String) : AIStreamChunk
    data class SentenceComplete(val fullSentence: String) : AIStreamChunk
    data class Error(val throwable: Throwable) : AIStreamChunk
    object Finished : AIStreamChunk
}
```

---

## 3. Implementations

### 3.1. `LocalAIEngine` (On-Device SLM via llama.cpp / GGUF)
- Runs lightweight Small Language Models (SLMs) locally (e.g., Qwen2.5-0.5B/1.5B, Gemma-2-2B quantized).
- Completely offline, zero network latency, 100% data privacy.
- Executed on native C++ runtime (via JNI / NDK) leveraging CPU NEON, GPU (Vulkan/OpenCL), or NPU when available.

### 3.2. `CloudAIEngine` (Adapter Pattern)
- Connects to remote LLM endpoints (OpenRouter, Gemini, Groq, or OpenAI-compatible backends) via Ktor.
- Used when high-end conversational depth is desired and network connectivity is robust.
- Protected by strict client-side quota controls and timeout boundaries.

### 3.3. `FallbackAIEngine` (Deterministic Scripted Engine)
- Zero-AI rule-based fallback engine.
- Contains pre-defined conversational branches, clarification prompts, and pattern-matched responses for all 6 core scenarios.
- Guarantees that the app never crashes or becomes completely silent, even if local models fail and the device is offline.

### 3.4. `MockAIEngine`
- Test stub that emits simulated token streams with realistic delays (e.g., 20ms per token).
- Used for rapid UI prototyping, unit tests, and instrumented UI automation without native dependencies.

---

## 4. Local-First Routing & Zero-Cost Optimization

To eliminate unnecessary API costs, reduce latency, and preserve battery life:

1. **Deterministic Grammar & Spell Checks:** Basic syntax errors, article mismatches, and common spelling typos are detected using local regex and word-graph algorithms before consulting an LLM.
2. **Scenario Progress & Goal State:** Tracking whether the user completed a scenario step (e.g., "ordered coffee size" or "specified milk type") is evaluated using local slot-filling heuristics.
3. **Vocabulary Lookup & Idioms:** Word definitions and phonetics come from an embedded SQLite dictionary table.
4. **LLM Invocation Filter:** An LLM is **only** invoked when generative dialogue synthesis is genuinely required.

---

## 5. Cost Protection & Health Monitoring

The cloud engine is fortified by four defensive layers:

```
Request ──> [ QuotaManager ] ──> [ CircuitBreaker ] ──> [ ProviderHealthMonitor ] ──> Network API
                  │                       │                         │
                  ▼ (Budget Exceeded)     ▼ (Failures High)         ▼ (High Latency/Error)
             [ Reroute Local ]       [ Reroute Fallback ]      [ Reroute Local ]
```

### 5.1. `ProviderConfig` & `RequestBudget`
Each provider has strict dynamic limits defined in configuration:

```kotlin
data class ProviderConfig(
    val providerId: String,
    val dailyRequestBudget: Int,
    val dailyTokenBudget: Int,
    val timeoutMs: Long = 5000L,
    val maxRetries: Int = 2,
    val cooldownPeriodMs: Long = 60_000L,
    val priority: Int = 1,
    val enabled: Boolean = true
)
```

### 5.2. Dynamic Budget Thresholds

| Budget Consumed | Operational Strategy | Action Taken |
| :--- | :--- | :--- |
| **0% – 60%** | Normal Operation | High-fidelity cloud generation allowed for all turns. |
| **60% – 70%** | Cache Aggressive | Reuse previous response templates for routine acknowledgments; compress prompt context. |
| **70% – 75%** | Prefer Local | Route conversation turns to local SLM if device has downloaded weights. |
| **75% – 80%** | Traffic Reduction | Reduce max tokens from 150 to 60; skip non-essential grammar LLM checks. |
| **80%+ / 100%** | Hard Stop | Completely disable cloud requests for the provider; route 100% to local or fallback engines. |

### 5.3. `CircuitBreaker` State Machine
- **CLOSED:** Normal operating state.
- **OPEN:** After 3 consecutive network failures or HTTP 429 / 5xx errors, the circuit opens for `cooldownPeriodMs` (e.g., 60 seconds). All calls immediately bypass the provider without hitting the network.
- **HALF-OPEN:** After cooldown, a single probe request is attempted. Success closes the circuit; failure re-opens it.

---

## 6. Context Window & Prompt Management

To keep on-device inference fast and cloud token costs minimal:

1. **Sliding History Window:** The context history is capped at the last **6 conversation turns** (3 user + 3 assistant).
2. **Compact System Prompts:** Character prompts are compressed to <120 tokens, using structured bullet points rather than verbose narrative.
3. **Deterministic Summary Injection:** Key user facts (e.g., User's target topic) are injected into the prompt as a single-line key-value tag `[Context: User ordering oat milk latte]`.

```
┌─────────────────────────────────────────────────────────────┐
│ Character Identity (Compact Bullet Directives: ~60 tokens) │
├─────────────────────────────────────────────────────────────┤
│ Scenario Context Tag: [Scenario: CoffeeShop | Step: Order]  │
├─────────────────────────────────────────────────────────────┤
│ Recent Turns (Sliding Window: Last 6 turns, ~150 tokens)    │
├─────────────────────────────────────────────────────────────┤
│ Latest User Input: "I'd like a cappuccino with oat milk"    │
└─────────────────────────────────────────────────────────────┘
```
