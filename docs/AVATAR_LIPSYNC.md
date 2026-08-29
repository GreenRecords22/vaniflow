# Avatar & Lip-Sync Architecture (Milestone 16)

## Overview

The avatar layer is fully decoupled from AI logic. It consumes a single source of truth —
`ConversationState` — mapped to `AvatarState`, and is ready to drive real rigs. Hyper-realistic
avatar assets and true phoneme-level lip-sync are **BLOCKED** (no rigs in repo; offline TTS does
not currently expose a real-time amplitude stream).

## Components

| File | Responsibility |
|---|---|
| `ui/avatar/AvatarState.kt` | Enum `IDLE / LISTENING / THINKING / SPEAKING / INTERRUPTED / ERROR` + `ConversationState.toAvatarState()` |
| `ui/avatar/AvatarController.kt` | `StateFlow<AvatarState>` the UI observes |
| `ui/avatar/LipSyncController.kt` | `LipSyncController` interface + `VisemeLipSyncController` (amplitude/viseme → mouth openness) |
| `ui/avatar/CharacterAvatarConfig.kt` | `CharacterAvatarRegistry`: per-character asset/pose config |
| `ui/components/CharacterAvatar.kt` | State-driven ring color + pulse; honors `avatarState` |

## State Mapping

`ConversationState` → `AvatarState`:
- `IDLE` → IDLE
- `LISTENING` → LISTENING (ring cyan, gentle pulse)
- `AI_THINKING` → THINKING
- `AI_SPEAKING` → SPEAKING (ring green, pulse; lip-sync active)
- `INTERRUPTED` → INTERRUPTED (brief), then LISTENING
- `ERROR` → ERROR

## Lip-Sync (VisemeLipSyncController)

`LipSyncController` exposes:
- `onSpeechStart()` / `onSpeechEnd()`
- `onAmplitude(value: Float)` — real amplitude feed (when available) drives mouth openness
- deterministic fallback viseme curve when no amplitude is present

`CharacterAvatar` reads `mouthOpenness` (0–1) from the controller while `state == SPEAKING`.
On interruption, mouth snaps shut and state moves to `INTERRUPTED`/`LISTENING`.

## Integration Status

- `AvatarController` + `LipSyncController` are **not yet wired into `ConversationScreen`**; the
  `CharacterAvatar` `avatarState` parameter is added but call sites must pass the live state.
- Real hyper-realistic rigs: **BLOCKED** (assets not in repo).
- True phoneme sync against a real rig: **BLOCKED**.

## What is needed to finish

1. Add real character rig assets (models/sprites) to `CharacterAvatarRegistry`.
2. Wire `AvatarController` to `ConversationViewModel`'s `conversationState` and pass `avatarState`
   to `CharacterAvatar` in `ConversationScreen`.
3. Expose a real amplitude stream from `RealOfflineTTSEngine` and feed `LipSyncController.onAmplitude`.
