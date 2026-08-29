# Provider Failover & Circuit Breaker Guide

**Classification:** VERIFIED  

---

## 1. Automated Failover Triggers

| Event | Action Taken | Recovery Behavior |
|---|---|---|
| **HTTP 429 (Rate Limit)** | Instantly marks provider `ERROR`, routes to Secondary | 60s cooldown, automatic retry |
| **Timeout (>4000ms)** | Increments failure count; trips at 3 consecutive | 60s cooldown circuit breaker |
| **80% Daily Quota Warning** | Routes new requests to Secondary provider | Resets at 00:00 UTC daily |
| **100% Quota Exhaustion** | Disables provider; routes to Local SLM / Fallback | Resets at 00:00 UTC daily |
| **Network Loss / Offline** | Routes directly to Local VaniFlow SLM | Automatic resume on reconnection |