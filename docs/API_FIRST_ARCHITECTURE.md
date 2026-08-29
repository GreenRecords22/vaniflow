# VaniFlow API-First Architecture

**Classification:** VERIFIED  

---

## 1. Overview & Hierarchy

VaniFlow employs an **API-First Architecture** where cloud/remote intelligence provides the primary conversational reasoning, while local on-device SLMs serve as offline fallbacks when network connectivity is lost or daily API quotas are reached.

```
                  [User Input]
                       │
                       ▼
              [SmartAIRouter]
                       │
      ┌────────────────┴────────────────┐
      ▼ (Educational query?)            ▼ (Conversation turn)
[Dict Rule Engine]             [Multi-Provider Cascade]
                                        │
             ┌──────────────────────────┴──────────────────────────┐
             ▼ (Priority 1)                                        ▼ (Priority 2)
   [RemoteAIProvider]                                   [SecondaryRemoteAIProvider]
   • High conversational fluency                        • Automatic failover
   • 4000ms short timeout                               • 5000ms timeout
             │ (Offline / Rate limited?)                           │ (Offline / Rate limited?)
             └──────────────────────────┬──────────────────────────┘
                                        ▼ (Priority 3)
                              [LocalAIProvider]
                              • VaniFlow Lite / Core (GGUF SLM)
                              • 100% Offline capability
                                        │ (Model not installed?)
                                        ▼ (Priority 4)
                           [FallbackAIProvider]
                           • Natural graceful degradation
                           • Context-aware encouragement
```

---

## 2. Provider Interface Contract

Every AI engine implements `AIProvider`:
```kotlin
interface AIProvider {
    val providerId: String
    val providerName: String
    val priority: Int
    val config: ProviderConfig

    fun isAvailable(): Boolean
    fun getHealthState(): ProviderHealthState
    suspend fun generateResponse(systemPrompt: String, history: List<AITurn>, userInput: String): AIResult
    fun streamResponse(systemPrompt: String, history: List<AITurn>, userInput: String): Flow<String>
    fun recordSuccess(latencyMs: Long, tokensGenerated: Int)
    fun recordFailure(isRateLimit: Boolean = false)
}
```

---

## 3. Security Boundary

- **Zero API Keys in APK:** The Android application communicates exclusively with a secure proxy/backend endpoint.
- **Header Isolation:** The backend manages third-party authentication tokens.