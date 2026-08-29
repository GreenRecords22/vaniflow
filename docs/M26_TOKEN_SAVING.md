# Milestone 26 — Smart Token Saver & Privacy-Safe Caching

**Classification:** VERIFIED  

---

## 1. Decision Flowchart

```
[User Input]
     │
     ├── 1. Sensitive Data Check (Passwords / PINs / Cards / OTPs) ──► Flag DO_NOT_CACHE
     │
     ├── 2. Direct Session Memory Query ─────────────────────────────► MEMORY_ONLY (0 API)
     │
     ├── 3. Educational Definition Query ────────────────────────────► CACHE_EXACT (0 API)
     │
     ├── 4. Stable Factual Knowledge Cache ──────────────────────────► CACHE_EXACT (0 API)
     │
     └── 5. Dynamic Contextual Conversation ─────────────────────────► REMOTE_AI_REQUIRED
```

---

## 2. Cache Exclusion Rules

- Personal / emotional dynamic turns are strictly excluded from caching.
- Passwords, OTPs, Aadhaar, and credit card numbers are never cached.