# API Provider Architecture & Failover

**Classification:** VERIFIED  

---

## 1. Pluggable Provider Registry

The `ProviderRegistry` manages an ordered hierarchy of AI providers:
- `RemoteAIProvider` (Priority 1 — Primary Cloud AI)
- `SecondaryRemoteAIProvider` (Priority 2 — Backup Cloud AI)
- `LocalAIProvider` (Priority 3 — On-Device SLM)
- `FallbackAIProvider` (Priority 4 — Natural Fallback)

---

## 2. Health & Quota States

- `AVAILABLE` (0–79% usage): Normal routing.
- `QUOTA_WARNING` (80–89% usage): Triggers automatic migration to secondary provider.
- `DEGRADED` (High latency / timeouts): Circuit breaker temporarily cools down provider.
- `QUOTA_EXHAUSTED` (100% usage): Provider disabled until next daily reset.
- `ERROR` (429 Rate Limit / 3 consecutive errors): 60s circuit-breaker cooldown.