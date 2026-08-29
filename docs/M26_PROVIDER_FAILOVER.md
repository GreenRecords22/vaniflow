# Milestone 26 — Multi-Provider Failover & Quota Management

**Classification:** VERIFIED  

---

## 1. Provider Routing Cascade

1. **LEVEL 0**: Local Safe Knowledge Cache & Session Memory (Zero API Cost).
2. **LEVEL 1**: Primary Remote Cloud AI (Groq / OpenRouter / Gateway).
3. **LEVEL 2**: Secondary Remote Cloud AI (Google Gemini 1.5 Flash).
4. **LEVEL 3**: Additional healthy cloud provider (if configured).
5. **LEVEL 4**: Local VaniFlow On-Device SLM (Offline / Post-90min Fair Use).
6. **LEVEL 5**: Natural Conversational Fallback (Friendly degradation, zero error codes).

---

## 2. Real Circuit-Breaker Failover Rules

- **HTTP 429 (Rate Limit)**: Instantly trips circuit breaker, sets 60s cooldown, routes traffic to Secondary.
- **Connection / Read Timeout**: Increments failure count; trips circuit breaker after 3 consecutive failures.
- **80% Quota Warning**: Automatically marks provider `QUOTA_WARNING` and prefers the next healthy provider.
- **100% Quota Exhaustion**: Disables provider until daily reset.