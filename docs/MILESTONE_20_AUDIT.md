# Milestone 20 — Repository & Architecture Audit

**Target Milestone:** Milestone 20 — Human Character + Realistic Talking Avatar + Natural Conversational AI  
**Audit Date:** 2026-08-29  
**Target Device:** Realme RMX2040 (Android 11, API 30, 4GB RAM)  

---

## 1. Component Classification Matrix

| Component | Classification | Current State & Technical Notes |
|---|---|---|
| **API-First AI Routing** (`SmartAIRouter`) | **VERIFIED** | Multi-provider cascade (Remote Primary -> Secondary -> Local SLM -> Context Fallback -> Scenario Matrix) verified on JVM (171/171 tests) and RMX2040. |
| **Provider Health & Quotas** (`ProviderHealthManager`, `ProviderQuotaManager`) | **VERIFIED** | Circuit-breaker with 80% quota warning and auto-exhaustion switching. |
| **Conversation Memory** (`ConversationMemoryManager`) | **VERIFIED** | Rolling 6–8 turns, topic extraction, summary condensing (<500 tokens bounded prompt). |
| **Usage Tracking** (`DailyConversationUsageTracker`) | **VERIFIED** | Tracks daily minutes, tokens, cache hits, 90-minute fair-use policy. |
| **Model Persistence & Branding** (`DefaultModelManager`) | **VERIFIED** | VaniFlow Lite / Core / Pro branding; persistent storage in `files/models/`. |
| **Real STT Pipeline** (`SherpaOnnxSTTEngine`) | **VERIFIED** | Real offline STT + Android SpeechRecognizer fallback. |
| **Real TTS Pipeline** (`RealOfflineTTSEngine`) | **VERIFIED** | Google Neural TTS with Indian English female voices (Raya, Adwaita) and male voices (Rudra, Shub). |
| **Character Visual Assets** (`res/drawable/avatar_*.png`) | **VERIFIED** | High-resolution 2.5D character portrait assets (Raya, Rudra, Adwaita, Shub) present in repository. |
| **Avatar Presentation in UI** (`ConversationScreen`, `CharacterAvatar`) | **PARTIALLY IMPLEMENTED** | Currently renders a small 160dp circular image inside `CircleShape` with simple scale pulse. Lacks full upper-body immersion, breathing, head tilt, and emotion rendering. |
| **Audio ↔ Avatar Synchronization** (`LipSyncController`) | **PARTIALLY IMPLEMENTED** | `VisemeLipSyncController` has a deterministic viseme wave curve; needs real-time audio amplitude/RMS tracking and smoothing during TTS playback. |
| **Phoneme / Viseme 3D Blendshapes** | **BLOCKED** | Project contains high-res 2.5D portrait PNG assets, but no 3D mesh rigs, Live2D models, or phoneme timing API. AmplitudeVisemeProvider will be implemented; PhonemeVisemeProvider documented as BLOCKED pending 3D rig assets. |
| **Character Personalities & Prompt Directives** (`CharacterPromptBuilder`, `ConversationPromptBuilder`) | **IMPLEMENTED (NEEDS POLISH)** | Previously forced questions after every single sentence; needs natural conversational response policy, distinct character styles (Raya: warm Indian English mentor, Rudra: casual/energetic, Adwaita: sophisticated/professional, Shub: calm/analytical), and emotional metadata parsing. |
| **Interruption / Barge-in Pipeline** (`ConversationEngine`) | **VERIFIED** | Instant TTS stop, audio flush, mic restart, and state transition to LISTENING. |
| **Room Persistence & DAOs** | **VERIFIED** | Session, turn, vocabulary, progress tracking intact. |
| **Error UX** | **VERIFIED** | Zero technical model error strings exposed to user. |