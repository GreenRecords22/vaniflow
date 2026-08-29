# VaniFlow — Data Architecture & Schema

## 1. Local-First Storage Architecture

VaniFlow adopts a **strict local-first data architecture**. All user profiles, conversation histories, mistake logs, vocabulary vault items, and progress statistics are stored on-device in SQLite via **Room Database**, alongside preferences in **Preferences DataStore**.

```
┌─────────────────────────────────────────────────────────────┐
│                      Repository Layer                       │
│    (Domain Interfaces Returning Kotlin Flow<DomainModel>)   │
└──────────────┬──────────────────────────────┬───────────────┘
               │                              │
               ▼                              ▼
┌──────────────────────────────┐┌─────────────────────────────┐
│     Room Database (SQLite)   ││    Preferences DataStore    │
│  - GuestProfile              ││  - selectedCharacterId      │
│  - Character & Scenario      ││  - targetDailyMinutes       │
│  - Session & ConversationTurn││  - speechRate               │
│  - Score, Mistake, Vocabulary││  - themeMode                │
│  - Progress & ModelInfo      ││  - hasCompletedOnboarding   │
└──────────────────────────────┘└─────────────────────────────┘
```

---

## 2. Room Database Entities (Version 1)

### 2.1. `GuestProfileEntity`
Auto-created on the initial app launch. Ensures zero friction without requiring registration.

```kotlin
@Entity(tableName = "guest_profiles")
data class GuestProfileEntity(
    @PrimaryKey val id: String,                 // UUID e.g. "guest-user-default"
    val displayName: String,                    // e.g. "Learner"
    val avatarUrl: String?,                     // Optional local asset path
    val englishLevel: String,                   // "BEGINNER", "INTERMEDIATE", "ADVANCED"
    val targetDailyMinutes: Int,                // e.g. 5
    val createdAt: Long,                        // Epoch millis
    val lastActiveAt: Long                      // Epoch millis
)
```

### 2.2. `CharacterEntity`
Defines available AI conversation partners.

```kotlin
@Entity(tableName = "characters")
data class CharacterEntity(
    @PrimaryKey val id: String,                 // "raya", "rudra", "adwaita", "shub"
    val name: String,                           // "Raya"
    val personalityTitle: String,               // "Friendly • Patient"
    val levelCategory: String,                  // "BEGINNER"
    val avatarLocalPath: String,                // "avatars/raya.webp"
    val voiceId: String,                        // "piper_en_in_raya"
    val systemPrompt: String,                   // Structured prompt directives
    val temperature: Float,                     // 0.7f
    val isDefault: Boolean                      // true for Raya
)
```

### 2.3. `ScenarioEntity`
Pre-packaged conversational situations.

```kotlin
@Entity(tableName = "scenarios")
data class ScenarioEntity(
    @PrimaryKey val id: String,                 // "order_coffee", "job_interview"
    val title: String,                          // "Order Coffee"
    val category: String,                       // "DAILY_LIFE", "TRAVEL", "WORK", "INTERVIEW"
    val difficulty: String,                     // "BEGINNER", "INTERMEDIATE", "ADVANCED"
    val estimatedMinutes: Int,                  // 3
    val iconName: String,                       // "local_cafe"
    val description: String,                    // "Practice ordering your favorite drink..."
    val initialPrompt: String,                  // "Hi there! Welcome to The Daily Roast..."
    val contextConstraints: String              // JSON array of scenario goal steps
)
```

### 2.4. `SessionEntity`
Records every speaking interaction session.

```kotlin
@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = CharacterEntity::class,
            parentColumns = ["id"],
            childColumns = ["characterId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ScenarioEntity::class,
            parentColumns = ["id"],
            childColumns = ["scenarioId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [Index("characterId"), Index("scenarioId"), Index("startedAt")]
)
data class SessionEntity(
    @PrimaryKey val id: String,                 // UUID
    val characterId: String,
    val scenarioId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val totalDurationSeconds: Int,
    val userSpeakingSeconds: Int,
    val turnCount: Int,
    val overallFluencyScore: Int,               // 0 - 100
    val completionStatus: String                // "COMPLETED", "ABANDONED", "INTERRUPTED"
)
```

### 2.5. `ConversationTurnEntity`
Stores individual conversational utterances within a session.

```kotlin
@Entity(
    tableName = "conversation_turns",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("turnIndex")]
)
data class ConversationTurnEntity(
    @PrimaryKey val id: String,                 // UUID
    val sessionId: String,
    val turnIndex: Int,                         // 0, 1, 2...
    val speaker: String,                        // "USER", "AI"
    val transcript: String,                     // Recognized or generated text
    val audioDurationMs: Long,
    val latencyMs: Long,                        // Response latency in ms
    val timestamp: Long
)
```

### 2.6. `ScoreEntity`
Stores multidimensional speaking evaluations for a turn or session.

```kotlin
@Entity(
    tableName = "scores",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ScoreEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val turnId: String?,
    val grammarScore: Int,                      // 0 - 100
    val vocabularyScore: Int,                   // 0 - 100
    val pronunciationScore: Int,                // 0 - 100
    val fluencyScore: Int,                      // 0 - 100
    val recordedAt: Long
)
```

### 2.7. `VocabularyEntity` (Vocabulary Vault)
Captures newly encountered or practiced words and idioms.

```kotlin
@Entity(tableName = "vocabulary", indices = [Index("word", unique = true)])
data class VocabularyEntity(
    @PrimaryKey val id: String,                 // UUID
    val word: String,                           // e.g. "artisan"
    val phonetic: String,                       // "/ˈɑːrtɪzən/"
    val partOfSpeech: String,                   // "noun / adjective"
    val definition: String,                     // "Made in a traditional or non-mechanized way"
    val exampleSentence: String,                // "They sell artisan bread and fresh pastries."
    val contextualUsage: String?,               // Context where user heard it
    val masteryLevel: Int,                      // 0 (New) to 5 (Mastered)
    val lastReviewedAt: Long
)
```

### 2.8. `MistakeEntity`
Stores actionable grammar and phrasing feedback items.

```kotlin
@Entity(
    tableName = "mistakes",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class MistakeEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val turnId: String?,
    val originalText: String,                   // "I have ordered it yesterday"
    val correctedText: String,                  // "I ordered it yesterday"
    val mistakeType: String,                    // "TENSE", "ARTICLE", "PREPOSITION", "VOCAB"
    val explanation: String,                    // "Use simple past for completed past actions."
    val severity: String                        // "MINOR", "MAJOR"
)
```

### 2.9. `ProgressEntity`
Daily aggregated progress for streak calculations.

```kotlin
@Entity(tableName = "daily_progress", indices = [Index("dateString", unique = true)])
data class ProgressEntity(
    @PrimaryKey val id: String,                 // UUID
    val dateString: String,                     // "2026-08-28"
    val totalPracticeSeconds: Int,
    val completedSessions: Int,
    val wordsLearned: Int,
    val streakCount: Int
)
```

### 2.10. `ModelInfoEntity`
Tracks downloaded on-device model weights.

```kotlin
@Entity(tableName = "downloaded_models")
data class ModelInfoEntity(
    @PrimaryKey val id: String,                 // "whisper_base_en"
    val modelType: String,                      // "STT", "TTS", "LLM", "VAD"
    val name: String,                           // "Whisper Base English"
    val version: String,                        // "1.0.0"
    val fileSizeBytes: Long,
    val localFilePath: String,
    val sha256Checksum: String,
    val isDownloaded: Boolean,
    val isCompatible: Boolean
)
```

---

## 3. Preferences DataStore Schema

Lightweight, high-frequency app settings are persisted in `DataStore<Preferences>`:

| Key | Type | Default Value | Description |
| :--- | :--- | :--- | :--- |
| `pref_selected_character_id` | String | `"raya"` | Currently active AI conversation partner |
| `pref_user_english_level` | String | `"BEGINNER"` | Current self-declared proficiency level |
| `pref_voice_speed` | Float | `1.0f` | TTS speech playback rate (0.8x – 1.2x) |
| `pref_vad_silence_ms` | Long | `900L` | Silence window duration before turn submission |
| `pref_dark_theme` | Boolean | `false` | UI theme preference |
| `pref_has_completed_onboarding` | Boolean | `false` | Initial welcome flow completion flag |
| `pref_active_engine_tier` | String | `"AUTO"` | Preferred AI execution tier (Auto, Local, Cloud) |

---

## 4. Cloud Sync Extensibility (Post-MVP)

The data architecture is explicitly decoupled to enable cloud synchronization in future releases:
- Room DAOs return domain entities via clean repository interfaces (`ConversationRepository`, `ProgressRepository`).
- All entities utilize **UUID strings** rather than auto-incrementing integer primary keys to prevent cloud synchronization ID collisions.
- Every mutable record includes `createdAt` and `updatedAt` / `lastActiveAt` timestamps for standard Last-Write-Wins (LWW) conflict resolution.
