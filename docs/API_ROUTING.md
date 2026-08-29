# VaniFlow — Smart AI Routing & Quota Architecture

## 1. SmartAIRouter Execution Pipeline

The `SmartAIRouter` serves as the central dispatcher for all conversational and pedagogical inference. It ensures optimal responsiveness, zero cost waste, complete offline autonomy, and resilience against network instability.

```
                  ┌─────────────────────────────────────┐
                  │    Inbound Conversational Turn      │
                  └──────────────────┬──────────────────┘
                                     │
                                     ▼
                  ┌─────────────────────────────────────┐
                  │ Stage 1: Local Preprocessing        │
                  │ (Sanitization & Intent Parsing)     │
                  └──────────────────┬──────────────────┘
                                     │
                                     ▼
                  ┌─────────────────────────────────────┐
                  │ Stage 2: Deterministic Match Check  │ ── Match Found ──> Return Scripted
                  │ (Greetings / Navigation / Slot-fill)│                    Response
                  └──────────────────┬──────────────────┘
                                     │ No deterministic match
                                     ▼
                  ┌─────────────────────────────────────┐
                  │ Stage 3: Local LLM Viability Check  │
                  │ (Model Downloaded & Device Ready?)  │ ── Yes & Offline ──> LocalAIEngine
                  └──────────────────┬──────────────────┘
                                     │ Need Cloud / Online
                                     ▼
                  ┌─────────────────────────────────────┐
                  │ Stage 4: Cloud Provider & Quota Gate│
                  │ (Health, Quota, Circuit Breaker)    │ ── Circuit Closed ──> CloudAIEngine
                  └──────────────────┬──────────────────┘
                                     │ Open / Quota Exhausted
                                     ▼
                  ┌─────────────────────────────────────┐
                  │ Stage 5: Graceful Fallback Engine   │ ── Fallback ──> FallbackAIEngine
                  └─────────────────────────────────────┘
```

---

## 2. Dynamic Provider Configuration (`ProviderConfig`)

Each cloud AI provider profile is declared through a structured `ProviderConfig` object:

```kotlin
data class ProviderConfig(
    val name: String,
    val providerId: String,
    val dailyRequestBudget: Int,
    val dailyTokenBudget: Int,
    val priority: Int, // 1 = highest
    val timeoutMs: Long = 4500L,
    val maxRetries: Int = 2,
    val cooldownPeriodMs: Long = 60_000L,
    val enabled: Boolean = true,
    val healthStatus: HealthStatus = HealthStatus.HEALTHY
)

enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    CIRCUIT_OPEN
}
```

---

## 3. Dynamic Budget Thresholds

The `QuotaManager` monitors consumption in real time and automatically transitions through defensive mitigation tiers:

```
0%                  60%         70%       75%       80%              100%
┌────────────────────┬───────────┬─────────┬─────────┬─────────────────┐
│     NORMAL         │  CACHE    │ PREFER  │ TRAFFIC │ HARD STOP /     │
│   OPERATIONS       │ AGGRESSIVE│  LOCAL  │ REDUCE  │ 100% FALLBACK   │
└────────────────────┴───────────┴─────────┴─────────┴─────────────────┘
```

### Threshold Action Matrix

| Threshold | Mode | Routing Strategy & Actions |
| :--- | :--- | :--- |
| **0% – 60%** | `NORMAL` | Standard streaming cloud requests enabled for all conversation turns. |
| **60% – 70%** | `CACHE_AGGRESSIVE` | Reuse previous scenario prompt responses where applicable; compress context history. |
| **70% – 75%** | `PREFER_LOCAL` | Automatically switch conversation turns to the on-device `LocalAIEngine` if models are present. |
| **75% – 80%** | `TRAFFIC_REDUCTION` | Truncate max tokens to 60; disable optional cloud-based grammar and pronunciation deep evaluations. |
| **80%+ / 100%** | `HARD_STOP` | Fully block cloud network requests; route all calls to local SLM or deterministic `FallbackAIEngine`. |

---

## 4. Provider Health Monitoring & Circuit Breaker

### 4.1. `ProviderHealthMonitor` Metrics
- **Rolling Latency Window:** Tracks the last 20 requests (P50, P90, and P95 latency). If P95 exceeds 4000ms, the provider is marked `DEGRADED`.
- **Error Rate Tracker:** Calculates the percentage of HTTP 4xx/5xx responses.
- **Availability Heartbeat:** Re-evaluates network connectivity states on network interface changes.

### 4.2. `CircuitBreaker` State Lifecycle

```
       ┌───────────┐  3 consecutive failures   ┌──────────┐
       │  CLOSED   │ ────────────────────────>  │   OPEN   │
       │ (Passing) │                            │(Blocking)│
       └─────▲─────┘                            └────┬─────┘
             │                                       │
             │ Single Probe Success                  │ Cooldown expires (60s)
             │                                       ▼
       ┌─────┴─────┐                            ┌──────────┐
       │   HALF-   │ <───────────────────────── │  PROBING │
       │   OPEN    │     Probe Failure          └──────────┘
       └───────────┘
```

- **CLOSED:** Normal operating state. Requests pass through to cloud endpoints.
- **OPEN:** After 3 consecutive timeouts or server errors, requests fail fast locally without consuming network sockets.
- **HALF-OPEN:** Once `cooldownPeriodMs` (60s) elapses, the router allows a single probe turn. If successful, the circuit resets to `CLOSED`. If it fails, the circuit returns to `OPEN` for another cooldown cycle.

---

## 5. Handling Unmetered / Unknown Provider Quotas

When using endpoints where real-time balance APIs are unavailable:
1. **Conservative Token Counter:** Estimates tokens locally using character-to-token heuristics (~4 chars/token) and tracks cumulative daily usage.
2. **Rate Limit Circuit Trip:** An immediate HTTP 429 (Too Many Requests) or HTTP 402 (Payment Required) triggers an immediate circuit break, demoting the provider and rerouting the user to `LocalAIEngine`.
3. **Daily Reset Timer:** Reset counters daily at `00:00 UTC` without requiring an internet connection.

---

## 6. API Key Security & Environment Isolation

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CRITICAL SECURITY RULE                          │
│                                                                        │
│  NEVER embed raw API keys, tokens, or cloud secrets in the APK build,  │
│  source code, Git repositories, or client-side assets.                 │
└────────────────────────────────────────────────────────────────────────┘
```

### Architecture for Environments
- **Development & Local Testing:**
  - Developers configure keys in a local untracked `local.properties` file:
    ```properties
    VANIFLOW_DEV_API_KEY=sk-...
    VANIFLOW_BASE_URL=https://openrouter.ai/api/v1
    ```
  - Injected securely into `BuildConfig` during local debug builds only.
- **Production MVP & Release:**
  - Zero hardcoded keys in the release APK.
  - Cloud requests route through a lightweight authenticated backend proxy that securely attaches keys and enforces rate limits.
  - Or, users can optionally supply their own personal API key in Developer Settings (BYOK — Bring Your Own Key).
