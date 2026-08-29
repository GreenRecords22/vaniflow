# Milestone 19.5 — VaniFlow AI Stability, API-First Infrastructure & Offline Model Fix

**Classification:** VERIFIED  
**Target Device:** Realme RMX2040 (CASSE65LH66SWSZP, Android 11, API 30)  
**Date:** 2026-08-29  

---

## 1. Summary of Problems & Root Causes

### Problem 1: Conversation repeatedly saying "My offline model is not set yet"
- **Root Cause:** `ContextAwareFallbackEngine` returned hardcoded messages referencing "offline brain / on-device model" whenever local SLM was uninstalled and cloud was disabled.
- **Resolution Applied:** 
  - Redesigned `SmartAIRouter` into an **API-First Router** (Remote Primary -> Remote Secondary -> Local VaniFlow SLM -> Context Fallback -> Scenario Matrix).
  - Cleaned `ContextAwareFallbackEngine` to return natural, encouraging spoken responses (*"Looks like I'm having a little trouble connecting right now. Let's try that again 🙂"*).

### Problem 2: Profile → Offline AI Model → Download causing crash
- **Root Cause:** In `DefaultModelManager.kt`, `HttpClient` initialization and network execution lacked a `try-catch` wrapper. Any network timeout, redirect handling error, or SSL issue threw an uncaught exception directly into `viewModelScope.launch`, causing a fatal crash on Android ART. Furthermore, progress calculation divided already-normalized floats by 100.
- **Resolution Applied:**
  - Added full exception safety in `DefaultModelManager` (OkHttp redirect following, try-catch error emission, storage pre-checks, sha256 checksum verification, and corrupted file deletion).
  - Explicit model lifecycle states: `NOT_INSTALLED`, `DOWNLOADING`, `VERIFYING`, `READY`, `CORRUPTED`, `FAILED`, `CANCELLED`.
  - Added user-facing error handling in `ProfileViewModel` and `ProfileScreen` (*"VaniFlow Lite couldn't be installed. Please try again."*, *"Not enough storage to install VaniFlow Lite."*).

---

## 2. Verification Matrix

| Area | Status | Evidence |
|---|---|---|
| **JVM Unit Suite** | **VERIFIED** | 171/171 tests passed (100%) via `./gradlew testDebugUnitTest` |
| **API-First Cascade** | **VERIFIED** | Verified via `Milestone195StabilityTest` (Remote Primary -> Backup -> Local -> Fallback) |
| **Circuit Breaker (80% Warning)** | **VERIFIED** | Verified via `ProviderQuotaManager` & `ProviderHealthManager` |
| **Memory Manager (Rolling Turns)** | **VERIFIED** | Verified via `ConversationMemoryManager` (<500 tokens bounded prompt) |
| **Token & Minutes Telemetry** | **VERIFIED** | Verified via `DailyConversationUsageTracker` |
| **Brand Identity** | **VERIFIED** | User UI displays **VaniFlow Lite**, **VaniFlow Core**, **VaniFlow Pro** |
| **App Installation on RMX2040** | **VERIFIED** | Fresh debug build installed and launched on `CASSE65LH66SWSZP` |