# VaniFlow API Security & Credential Protection

**Classification:** MANDATORY SECURITY POLICY  
**Date:** 2026-08-29  

---

## 1. Zero API Keys Policy in Client APK

1. **No Hardcoded Strings**: No API keys, secret tokens, or Bearer auth credentials exist in `.kt`, `.xml`, `BuildConfig`, or resources.
2. **Runtime Configuration Store**: `ApiConfigStore` manages credentials in volatile memory.
3. **Recommended Production Architecture**:

```
[Android Client (VaniFlow App)]
             │ (JWT / Session Token)
             ▼
[VaniFlow Secure API Gateway] (Reverse Proxy)
             │ (Injects Server-Side Secret Keys)
             ├──────────────────────────┬──────────────────────────┐
             ▼                          ▼                          ▼
       [Groq API]                  [Gemini API]              [OpenRouter]
```

---

## 2. Privacy-Safe Telemetry

Production logs must NEVER output Authorization headers, API keys, or raw sensitive user inputs (e.g. credit cards or passwords).