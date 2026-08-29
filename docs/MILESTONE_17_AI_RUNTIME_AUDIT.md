# MILESTONE 17 — AI RUNTIME AUDIT

> Classification legend: **VERIFIED** (confirmed by reading code/device), **IMPLEMENTED**
> (code exists and is correct), **MISSING** (required, not present, resolvable), **BLOCKED**
> (cannot be resolved in this environment without an external dependency/asset).

## 1. Existing AI Stack — Audit

| Component | Path | Status | Notes |
|---|---|---|---|
| `LocalLLMRuntime` | `engine/ai/llm/LocalLLMRuntime.kt` | IMPLEMENTED (contract) | Clean interface: `isAvailable()`, `generate(LlmInferenceRequest): String?`, `stream(...): Flow<String>`, `release()`. Ready for a real impl. |
| `StubLocalLLMRuntime` | `engine/ai/llm/StubLocalLLMRuntime.kt` | IMPLEMENTED (no inference) | `isAvailable()==false`, `generate()`→null, `stream()`→emptyFlow. Intentionally no backend. |
| `LocalAIEngine` | `engine/ai/LocalAIEngine.kt` | IMPLEMENTED | Delegates to `LocalLLMRuntime`; `isModelReady()` = real file >1MB **and** `runtime.isAvailable()`. Returns `AIResult.Error` when not ready (honest, no fake). |
| `SmartAIRouter` | `engine/ai/SmartAIRouter.kt` | IMPLEMENTED | L1 Local AI = PRIMARY; L2 opt-in cloud; L3 context-aware fallback; L4 scenario matrix last resort. Repetition regen + context-aware cache key present. |
| `ConversationPromptBuilder` | `engine/ai/prompt/ConversationPromptBuilder.kt` | IMPLEMENTED | Builds character + personality + level + scenario + history (last 8) + user msg + learning objective + response rules. `buildRegenerationPrompt` for repeats. |
| `ContextManager` | `engine/ai/ContextManager.kt` | IMPLEMENTED | `compactHistory` (max 6 turns), `buildSystemPrompt`, `estimateTokenCount`. |
| `RepetitionGuard` | `engine/ai/RepetitionGuard.kt` | IMPLEMENTED | Jaccard ≥ 0.8 over last 6 responses; single regeneration path. |
| `AIResponseCache` | `engine/ai/cache/*` | IMPLEMENTED | Context+prompt-version keyed; `DO_NOT_CACHE` for conversational turns. |
| `ModelManager` / `DefaultModelManager` | `engine/model/*` | PARTIAL — MISSING | Catalog with VaniFlow-branded LLM metadata (`brandedName`, `vaniFlowTier`) exists. **`downloadModel()` is SIMULATED** (writes random bytes of target length, placeholder SHA-256). No real HTTP fetch, no real integrity check. Storage path = `context.filesDir/models`. |
| Device tier | `ModelInfo.kt` (`DeviceTier`), `DefaultModelManager.getRecommendedTier()` | IMPLEMENTED | Uses `ActivityManager.MemoryInfo.totalMem`: ≥7GB→HIGH, ≥4.5GB→MEDIUM, else LOW. Device here reports ~5.75 GB → MEDIUM (Core). |
| `VaniFlowModelTier` | `engine/model/VaniFlowModelTier.kt` | IMPLEMENTED | LITE/CORE/PRO branding; `fromModelId` maps internal ids → tier. No internal model names exposed. |
| Hilt wiring | `di/AppModule.kt` | MISSING (binding) | Binds `StubLocalLLMRuntime` → `LocalLLMRuntime`. Must be repointed to the real runtime. |

## 2. Runtime / Native Infrastructure — Audit

| Item | Status | Detail |
|---|---|---|
| Native inference code (llama.cpp / ONNX / etc.) | MISSING | No C/C++ source, no `externalNativeBuild`, no `CMakeLists.txt`, no `.so` in `jniLibs`. |
| NDK | MISSING | `local.properties` has `sdk.dir` only; `ANDROID_NDK_HOME` unset; no `<sdk>/ndk/*`. Must install via sdkmanager. |
| CMake | MISSING | Not on PATH; not in SDK. Must install via sdkmanager. |
| JNI wrapper | MISSING | No `native` declarations or `System.loadLibrary` for an LLM backend. |
| GGUF model file on device | MISSING / BLOCKED (asset) | No `.gguf` present. Must be obtained (download) and placed at `files/models/llm_qwen25_05b.gguf`. |
| Network for model | VERIFIED AVAILABLE | HuggingFace reachable (HTTP 200). `INTERNET` permission present. Ktor (`ktor.client.okhttp`) already a dependency. |

## 3. Device Capability — VERIFIED (physical)

```
adb -s 21387578e60d7ece
  model : SM-G965F (Samsung Galaxy S9+)
  sdk   : 29 (Android 10)
  mem   : MemTotal 5755748 kB  (~5.75 GB)
  abi   : arm64-v8a, armeabi-v7a, armeabi
```
Capable of running Qwen2.5-0.5B-Instruct (q4) comfortably; 1.5B (q4 ~1.1 GB) is feasible but tighter.

## 4. Exactly What Is Missing For Genuine Local Inference

1. **Toolchain**: NDK + CMake (installable via sdkmanager). — *resolvable*
2. **Native runtime**: a real `LocalLLMRuntime` implementation backed by llama.cpp (GGUF). — *to build/integrate*
3. **JNI bridge**: minimal C wrapper exposing model load / token stream / release. — *to write*
4. **Real model asset**: a genuine GGUF file on device at the path `LocalAIEngine` checks. — *to download*
5. **Real model download**: replace simulated `downloadModel` with actual HTTP + SHA-256 verify. — *to implement*
6. **Hilt binding**: point `bindLocalLLMRuntime` at the real impl. — *to change*

## 5. Per Rule #16 Assessment

None of the missing items are permanent environmental blockers. NDK/CMake install and model download are
user-consented, resolvable actions. **There is no blocker that forces a fake/stub implementation** — the
architecture is correct and ready. Proceeding to a real `LlamaCppLocalLLMRuntime` is the prescribed path.

## 6. Verification Evidence

- GGUF URL `HEAD`: `200`, `Content-Length: 491400032` (≈491 MB) for `qwen2.5-0.5b-instruct-q4_k_m.gguf`.
- `StubLocalLLMRuntime.isAvailable()` returns `false` (read: `StubLocalLLMRuntime.kt:23`).
- `LocalAIEngine.isModelReady()` requires `file.length() > 1_000_000L && runtime.isAvailable()` (`LocalAIEngine.kt:51-57`).
- `DefaultModelManager.downloadModel` writes `targetBytes.copyOf(toWrite)` of random data (`DefaultModelManager.kt:164-174`) — confirmed simulated.
