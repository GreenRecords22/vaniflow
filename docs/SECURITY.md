# VaniFlow Security Architecture & Audit Report

## 1. Deep Secrets Scan Results

A full recursive scan was performed across all files in the project (`*.kt`, `*.xml`, `*.gradle.kts`, `*.properties`, `*.json`).

- **Hardcoded API Keys Found**: `0`
- **Bearer Tokens Found**: `0`
- **Embedded Passwords Found**: `0`
- **Private Keys / Service Accounts Found**: `0`
- **Cleartext Traffic Permitted**: `false` (`android:usesCleartextTraffic="false"`)

---

## 2. Client-Side Security Realities & Protections

1. **What is Protected on Device**:
   - Audio processing, speech recognition, and language synthesis run entirely on-device in sandboxed private storage (`context.filesDir`).
   - SQLite database is stored in application private storage (`/data/data/com.vaniflow.app/databases/`).
   - Zero telemetry, analytics SDKs, or background trackers exist in the codebase.

2. **What Cannot Be Protected in Client APK**:
   - Long-lived cloud provider master secrets (OpenAI, Anthropic, Gemini API keys) cannot be safely stored in a client Android APK, as reverse-engineering tools (JADX, APKTool, Frida) can extract any embedded credential regardless of obfuscation.
   - Therefore, cloud integration is **OFF by default** and designed to communicate only via user-provided development keys or a secure backend proxy server in future milestones.
