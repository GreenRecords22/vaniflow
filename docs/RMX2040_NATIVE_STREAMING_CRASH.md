# RMX2040 Native Streaming Crash Investigation & Resolution

## Target Device
- **Device**: Realme RMX2040 (Realme 6i / Narzo 10)
- **SoC / Architecture**: MediaTek Helio G80 (arm64-v8a)
- **Android Version**: Android 11 (API 30)
- **RAM**: 4GB LPDDR4x
- **LLM Runtime**: llama.cpp GGUF C/C++ API via JNI bridge (`libllamajni.so`)
- **Model**: Qwen2.5-0.5B-Instruct-Q4_K_M (491 MB)

---

## 1. Crash Analysis & Root Cause

### Root Cause 1: JNI Method ID Caching Class Mismatch
In `llamajni.cpp`:
```cpp
// Static global method IDs
static jmethodID g_midOnToken = nullptr;
static jmethodID g_midOnComplete = nullptr;
static jmethodID g_midOnError = nullptr;
```
When `g_midOnToken` was initialized from the first Kotlin callback object via `env->GetObjectClass(callback)`, subsequent streaming invocations or test suites created distinct Kotlin anonymous class instances (e.g. `LlamaCppRuntime$stream$1$1` vs test callbacks).
In Android ART runtime (Android 11 arm64), passing a cached `jmethodID` of Class A to `CallVoidMethod` on an instance of Class B caused a hard JNI runtime abort:
```
JNI DETECTED ERROR IN APPLICATION: jmethodID ... not valid for class ...
```

### Root Cause 2: Missing User Input in ChatML Turn Structure
In `LlamaCppRuntime.buildMessages()`:
When `history` was empty or didn't contain the latest `userInput`, `buildMessages` only added `"system"` to `roles` without adding a `"user"` turn.
When `llama_chat_apply_template` was called, Qwen2.5 received a system prompt with no user turn and immediately was asked to complete `<|im_start|>assistant`.
Because of this, the 0.5B model saw the complex meta-instructions and began reciting prompt instructions, rules, and mock headers (`CHALLENGE Level:`, `SYSTEM MESSAGE:`, `System Response:`, `User Response:`).

### Root Cause 3: JNI Exception Propagation During Stream Cancellation
When a stream was cancelled in Kotlin coroutines, the callback channel closed and Kotlin threw a cancellation/state exception back across JNI. JNI did not call `env->ExceptionClear()`, causing subsequent JNI calls to abort with pending JNI exceptions.

---

## 2. Resolutions Applied

1. **Dynamic Per-Invocation Method Resolution in JNI**:
   Removed global `g_midOnToken`, `g_midOnComplete`, `g_midOnError` caching in `llamajni.cpp`. Method IDs are now looked up safely from the callback object for each generation call.
2. **JNI Exception Safety**:
   Added `env->ExceptionCheck()` and `env->ExceptionClear()` after all `CallVoidMethod` and `NewStringUTF` calls.
3. **ChatML User Turn Alignment**:
   `LlamaCppRuntime.buildMessages()` now guarantees that `request.userInput` is always present as the final `"user"` turn in the ChatML structure.
4. **Prompt Normalization**:
   Cleaned `ConversationPromptBuilder` to provide direct, natural spoken instructions to the model without complex meta-headers that confuse 0.5B SLMs.
5. **Output Role Tag Sanitization**:
   Added `cleanSpokenText` in `ConversationEngine` to strip any stray `"Raya:"`, `"Assistant:"`, or `"System:"` prefixes before sending text to TTS or displaying in UI bubbles.

---

## 3. Classification

**Status: FIXED**
- Verified live on physical Realme RMX2040 device (`CASSE65LH66SWSZP`).
- `RealLocalAIEngineDeviceTest` streaming test passed on device.
- `RealLocalLLMDeviceTest` 3-turn multi-turn contextual inference passed on device.
- Full streaming response confirmed without native crashes.