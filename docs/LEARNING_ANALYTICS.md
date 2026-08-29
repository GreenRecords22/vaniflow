# VaniFlow Learning Analytics & Local Data Architecture

## 1. Room Database as Single Source of Truth

All learning progress, speaking time, session scores, conversation turns, and saved vocabulary reside strictly inside the local SQLite database (`vaniflow.db`) powered by Android Room:

```
[Speaking Conversation Turn Finished]
                  |
                  v
       [ConversationEngine]
                  |
                  +----[Saves SessionEntity]-------------> [SessionDao]
                  |
                  +----[Saves ConversationTurnEntity]---> [ConversationTurnDao]
                  |
                  v
       [DefaultSessionRepository] & [DefaultProgressRepository]
                  |
                  v
[Reactive Flow Streams]
                  |
       +----------+----------+
       |                     |
       v                     v
[SessionSummaryViewModel] [ProgressViewModel] & [ProfileViewModel]
       |                     |
       v                     v
[SessionSummaryScreen]    [ProgressScreen] & [ProfileScreen]
```

---

## 2. Deterministic Streak Algorithm

A practice day is defined as a calendar date containing at least one recorded session.

### Calculation Logic
- Convert session timestamps to `LocalDate` using device timezone (`ZoneId.systemDefault()`).
- Filter distinct practice dates and sort in descending order.
- **Current Streak**:
  - If practice occurred **Today**: count consecutive days backwards ($today, today-1, today-2, \dots$).
  - If no practice occurred Today, but practice occurred **Yesterday**: the streak is preserved and counted backwards from yesterday.
  - If neither Today nor Yesterday has a recorded session: `currentStreak = 0`.
- **Longest Streak**:
  - Computes the maximum unbroken consecutive day sequence across the user's entire history.

---

## 3. Safe Progress Reset Policy

Tapping "Reset Progress" in the Danger Zone on the Profile Screen:
- Clears `sessions` table.
- Clears `conversation_turns` table.
- Clears `saved_vocabulary` table.
- Flushes `ai_response_cache` Room table.
- **NEVER deletes downloaded on-device speech (STT/TTS) or LLM model weights**, ensuring offline capabilities remain immediately ready without requiring re-download.
