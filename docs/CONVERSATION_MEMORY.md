# VaniFlow Smart Conversation Memory

**Classification:** VERIFIED  

---

## 1. Memory Architecture

To prevent unbounded token growth and eliminate high latency, `ConversationMemoryManager` maintains a compact memory structure instead of transmitting 90-minute transcripts:

1. **Rolling Turns:** Last 6–8 turns (`AITurn.Role.USER` / `AITurn.Role.ASSISTANT`).
2. **Evolving Summary:** When turns exceed 16, older turns are condensed into a 2-sentence context summary.
3. **Current Topic:** Contextual category (e.g. *Food & Dining*, *Travel*, *Work*, *Hobbies*).
4. **Bounded Prompt:** Total memory footprint is strictly bounded (<500 tokens).

```
[System Identity]
      +
[Context Summary: Previous topic was Travel...]
      +
[Current Topic: Food & Dining]
      +
[Rolling 6-8 History Turns]
      +
[Current User Message]
```