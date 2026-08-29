# VaniFlow Local AI Response Cache & Estimated Token Savings

## 1. Response Cache Architecture

- **Storage**: Room SQLite database table `ai_response_cache`.
- **Maximum Capacity**: 500 entries with Least-Recently-Used (LRU) eviction.
- **Cache Invalidation Policies**:
  - `KNOWLEDGE_CACHE`: 7 days TTL (definitions, idioms, grammar rules).
  - `CONVERSATION_CACHE`: 6 hours TTL (scenario conversational branches).
  - `DO_NOT_CACHE`: Dynamic personal statements, passwords, sensitive entities.

---

## 2. Estimated Token Savings (Labeling)

- **Metric Classification**: **ESTIMATED**
- **Formula**: `estimatedTokens = responseText.length / 4`
- **Notice**: Actual tokenization depends on specific LLM BPE tokenizers (e.g. Tiktoken, Qwen tokenizer). The application reports this metric purely as an *estimated savings indicator*, not as an exact provider billing measurement.
