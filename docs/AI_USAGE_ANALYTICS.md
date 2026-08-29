# VaniFlow AI Usage Analytics & 90-Minute Policy

**Classification:** VERIFIED  

---

## 1. Local Telemetry Metrics

`DailyConversationUsageTracker` collects privacy-safe metrics:
- `dailyMinutes`: Total speaking time practiced today.
- `totalRequests`: Number of AI interactions.
- `inputTokens` / `outputTokens`: Token consumption.
- `cacheHits` / `savedTokens`: Tokens saved via educational cache.
- `cacheHitRatePercent`: Percentage of queries resolved via cache.
- `tokensPerMinute`: Measured token throughput per speaking minute.

---

## 2. 90-Minute Daily Fair-Use Policy

- **0–80 Minutes:** Unrestricted API-First remote conversational intelligence.
- **80–90 Minutes:** Continued normal operation with background local model warm-up.
- **90+ Minutes:** If local VaniFlow SLM is installed, conversation transitions smoothly to on-device AI without abruptly terminating active turns.
- **If Local AI is Uninstalled:** Continues using available remote quota without displaying abrupt error screens.