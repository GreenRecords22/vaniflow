# Smart Memory Architecture

**Classification:** VERIFIED  

---

## 1. Compact Conversation Context

```kotlin
data class CompactConversationContext(
    val rollingTurns: List<AITurn>,       // 6–8 recent turns
    val userFacts: List<String>,          // E.g. "User name is Rahul"
    val userPreferences: List<String>,    // E.g. "User likes: masala dosa"
    val currentTopic: String,             // E.g. "Food & Dining"
    val previousTopic: String?,           // E.g. "Travel & Exploration"
    val summary: String,                  // Evolving conversation summary
    val estimatedTokens: Int              // Strictly bounded (<500 tokens)
)
```

---

## 2. Natural Memory Recall

When user facts or preferences are referenced, the dialogue system weaves them into natural conversational responses without technical phrases like *"According to your stored memory..."*.