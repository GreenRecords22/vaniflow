# VaniFlow P7 Cloud AI Architecture Specification

## 1. System Overview
VaniFlow features an **API-First, Edge-Resilient Cloud AI Architecture** designed for high-performance voice interactions and complete offline capability.

---

## 2. Multi-Tiered AI Routing Topology

```
                  ┌──────────────────────────────┐
                  │      Android Application     │
                  │ (VAD → STT → Speech Analysis)│
                  └──────────────┬───────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │      TutorDecisionEngine     │
                  │   (Deterministic Pedagogy)   │
                  └──────────────┬───────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │         SmartAIRouter        │
                  │ (Routing & Circuit Breaker)  │
                  └──────┬───────────────┬───────┘
                         │               │
       [Online & Quota OK]               │ [Offline / Quota Exceeded / Failover]
                         ▼               ▼
          ┌─────────────────────┐  ┌─────────────────────┐
          │ VaniFlow AI Gateway │  │  Local Qwen2.5-0.5B │
          │  (Node.js / Express)│  │ (On-Device GGUF JNI)│
          └──────────┬──────────┘  └──────────┬──────────┘
                     │                        │
                     ▼                        │ [Model Unavailable]
          ┌─────────────────────┐             ▼
          │    Cloud Provider   │  ┌─────────────────────┐
          │  (Groq LPU Primary /│  │  Rule-based Fallback│
          │   Gemini Secondary) │  │  (Conversational AI)│
          └──────────┬──────────┘  └──────────┬──────────┘
                     │                        │
                     └───────────┬────────────┘
                                 │
                                 ▼
                  ┌──────────────────────────────┐
                  │    Normalized Response Flow  │
                  │ (Text-to-Speech + Avatar UI) │
                  └──────────────────────────────┘
```

---

## 3. Component Responsibilities

### 3.1 Android Client Layer
- **Audio Capture & VAD**: 16 kHz PCM capture with instant barge-in detection.
- **Offline STT**: Android SpeechRecognizer producing instantaneous transcripts.
- **Tutor Decision Engine**: Determines pedagogical action (normal turn, subtle correction, retry request, struggle backoff).
- **Prompt Builder**: Formats the canonical layered prompt enclosing user speech in `<user_speech>` tags.
- **SmartAIRouter**: Evaluates provider health, network connectivity, and daily quotas (90-min fair-use).
- **No Client Secrets**: Client carries zero provider master keys; requests go via the VaniFlow AI Gateway.

### 3.2 VaniFlow AI Gateway (`server/`)
- **App Authentication**: Validates `X-VaniFlow-App-Id` and session headers.
- **Rate Limiting**: Sliding-window rate limiter per client IP/session (60 req/min).
- **Provider Dispatch**: Calls Groq (`llama-3.1-8b-instant`) or Gemini (`gemini-1.5-flash`) via server-side secrets.
- **Response Normalization**: Produces uniform `TutorAIResponse` format and SSE token streaming.

### 3.3 Fallback Hierarchy
1. **Level 1 (Primary Cloud)**: VaniFlow AI Gateway / Groq (<300ms TTFT).
2. **Level 2 (Secondary Cloud)**: Google Gemini 1.5 Flash.
3. **Level 3 (Local On-Device)**: Qwen2.5-0.5B-Instruct-GGUF via llama.cpp JNI.
4. **Level 4 (Deterministic Rule AI)**: Rich semantic `ConversationalDialogueEngine`.
