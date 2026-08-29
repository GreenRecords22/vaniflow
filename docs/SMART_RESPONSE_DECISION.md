# Smart Response Decision Engine

**Classification:** VERIFIED  

---

## 1. Decision Flowchart

```
[User Utterance]
       │
       ├── 1. Sensitive Data Check (Credentials / Cards / OTPs) ──► Mark DO_NOT_CACHE
       │
       ├── 2. Direct Session Memory Query ────────────────────────► Return MEMORY_ONLY (0 API Cost)
       │
       ├── 3. Educational / Definitional Query ───────────────────► Return CACHE_EXACT (0 API Cost)
       │
       ├── 4. Stable Factual Knowledge Cache ─────────────────────► Return CACHE_EXACT (0 API Cost)
       │
       ├── 5. 90-Minute Daily Fair-Use Check ─────────────────────► Route to LOCAL_AI_REQUIRED
       │
       └── 6. Dynamic Contextual Conversation ────────────────────► Route to REMOTE_AI_REQUIRED (Primary / Secondary)
```

---

## 2. Decision Types

- `CACHE_EXACT`: Exact match for safe factual or dictionary definitions.
- `CACHE_SEMANTIC`: Semantic variation of safe general knowledge.
- `MEMORY_ONLY`: User query answered directly from extracted session facts.
- `REMOTE_AI_REQUIRED`: Complex conversational reasoning requiring remote API.
- `LOCAL_AI_REQUIRED`: Local SLM execution for offline / fair-use scenarios.
- `FALLBACK_REQUIRED`: Natural conversational fallback when all providers are unavailable.