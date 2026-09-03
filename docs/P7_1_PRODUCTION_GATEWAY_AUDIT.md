# VaniFlow P7.1 Production Cloud Gateway Audit

## 1. Executive Summary
This document provides the security, architecture, and network configuration audit for the **VaniFlow Production Cloud AI Gateway** and Android client integration at HEAD `c67a932`.

---

## 2. Production Security & Architecture Audit

### 2.1 Zero Client-Side Provider Secrets
- **Audit Findings**:
  - Scanning tracked code, resources, and compiled binaries confirms **zero** Groq (`gsk_`), Gemini (`AIza`), or OpenAI (`sk-`) secrets in the Android APK/AAB.
  - All upstream LLM authentication occurs solely within the backend server environment (`server/.env`).
- **Status**: **VERIFIED**

### 2.2 Endpoint Separation (Dev vs. Production)
- **Android `BuildConfig.GATEWAY_URL`**:
  - `debug` build type: `http://10.0.2.2:8080/v1/chat` (allows local emulator & LAN testing).
  - `release` build type & `defaultConfig`: `https://gateway.vaniflow.com/v1/chat` (enforces HTTPS production gateway).
  - Runtime override via `ApiConfigStore.setGatewayConfig(endpoint)` supported for custom staging environments.
- **Status**: **VERIFIED**

### 2.3 Server-Side Hardening & Protections
- **HTTPS Enforcement**: Production redirects non-HTTPS requests to `https://`.
- **Sliding-Window Rate Limiting**: Max 60 requests/minute per client IP/session with `Retry-After: 60` HTTP 429 response.
- **Payload Validation**: Max body size 128 KB; `userInput` capped at 2,000 chars; `systemPrompt` capped at 6,000 chars; history capped at 6 turns.
- **Provider Timeout Protection**: 10,000 ms timeout via `AbortController` preventing stalled client threads (returns HTTP 504).
- **Sanitized Responses**: `<think>...</think>` internal reasoning tags stripped from model outputs before delivering to client.
- **Status**: **VERIFIED**

### 2.4 Groq Model Configuration Audit
- **Exact Upstream Model Identifier**: `groq/compound-mini` (utilizing Groq's high-throughput compound routing backed by `llama-3.3-70b-versatile` on Groq LPUs).
- **Fallback Models Supported**: `qwen/qwen3.6-27b`, `gemini-1.5-flash`, `gpt-4o-mini`.
- **Status**: **VERIFIED**

---

## 3. Resilience & Multi-Level Fallback

```
ONLINE (Primary Cloud Path):
User Input → STT → TutorDecisionEngine → SmartAIRouter → VaniFlow AI Gateway (HTTPS) → Groq (compound-mini) → TTS → Avatar

FAILOVER / OFFLINE (Edge Path):
Cloud Gateway Offline / No Network → Local Qwen2.5-0.5B-GGUF (llama.cpp JNI) → TTS → Avatar

MODEL UNLOADED (Safety Path):
Local Model Unavailable → ConversationalDialogueEngine (Semantic Rule AI) → TTS → Avatar
```
- **Status**: **VERIFIED**
