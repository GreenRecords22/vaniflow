# LOCAL LLM RUNTIME DECISION (Milestone 17)

## Selected runtime: **llama.cpp** (native, GGUF)

| Attribute | Value |
|---|---|
| Runtime | llama.cpp (https://github.com/ggerganov/llama.cpp) |
| Version | Latest `master` (pinned commit at integration time) |
| License | MIT — compatible with the closed-source VaniFlow app |
| Model format | GGUF (Qwen2.5-0.5B / 1.5B Instruct, `q4_k_m`) |
| Target ABI | `arm64-v8a` (device is SM-G965F, ABI list confirms primary arm64-v8a) |
| Kotlin integration | JNI wrapper (`System.loadLibrary("llama")`) + thin `LlamaCppLocalLLMRuntime` |
| Streaming | Native token callback → Kotlin `Flow<String>` |
| Cancellation | Native `llama_set_abort_callback` / generation-flag checked per token |
| Expected RAM (0.5B q4) | ~600–900 MB resident incl. KV cache (device has ~5.75 GB) |
| Expected latency | **TO BE MEASURED** on device (time-to-first-token, tok/s) — not estimated here |
| Offline | Fully offline after model is on device; no network at inference time |

## Why llama.cpp

1. **Best architecture fit**: the existing `LocalLLMRuntime` contract (`generate` / `stream: Flow` / `release`) maps
   directly onto a llama.cpp session. No redesign of the router/prompt/cache layers is needed.
2. **ARM64 Android support**: first-class; builds with the NDK via its `build-android.sh` / CMake. No external
   dependencies (pure C/C++ + pthreads).
3. **GGUF support**: the project already catalogs Qwen2.5 GGUF models; llama.cpp is the reference GGUF loader.
4. **Streaming token generation**: `llama_decode` loop with a token callback — directly feeds sentence-buffered TTS.
5. **4 GB RAM viability**: Qwen2.5-0.5B q4 runs comfortably; the device has ~5.75 GB.
6. **License**: MIT — safe for a proprietary app (unlike GPL-licensed alternatives).
7. **Maintainability**: single well-known upstream, minimal JNI surface, model-agnostic.

## Rejected alternatives

| Alternative | Reason rejected |
|---|---|
| **ONNX Runtime GenAI** (`onnxruntime-genai-android`) | GGUF support on Android is limited/unreliable; requires exporting Qwen → ONNX with a specific toolchain. Adds a heavy AAR + model conversion step for no benefit over llama.cpp. |
| **MLC-LLM** (`ai.mlc:mlc-llm-android`) | Requires MLC-packed weights / model conversion; larger integration surface; GGUF not used directly. |
| **TensorFlow Lite (TFJagged / LLM) ** | No clean, small, chat-capable 0.5B GGUF story; conversion overhead; larger runtime. |
| **GPT4All / Kobold** | No clean Android AAR; not maintained for on-device Android GGUF. |
| **Pure-Kotlin/JVM transformer** | Far too slow for real-time conversation on mobile; not a production path. |

## Integration approach

- Install **NDK (r27) + CMake (3.22)** via `sdkmanager`.
- Build llama.cpp as a shared library for `arm64-v8a` (produces `libllama.so` + `libggml.so` + `libcommon.so`).
- Drop prebuilt `.so` files into `app/src/main/jniLibs/arm64-v8a/` (no `externalNativeBuild` rebuild needed in-app; the
  lib is built once with the NDK). Optionally add `externalNativeBuild` later for reproducibility.
- Write a minimal JNI layer (`llama_jni.c`) exposing: `loadModel(path)`, `generate(system, history, user)` with a
  token callback, `abort()`, `free()`.
- Implement `LlamaCppLocalLLMRuntime` (Kotlin) satisfying `LocalLLMRuntime`; bind it in `AppModule`.
- Fix `DefaultModelManager.downloadModel` to perform a real chunked HTTP download (Ktor) + SHA-256 verification, and
  place the file where `LocalAIEngine.getModelFile` expects it (`files/models/llm_qwen25_05b.gguf`).

## Risk / consent gate

- Requires installing NDK + CMake (~1 GB) and obtaining a GGUF model (~491 MB for 0.5B q4_k_m). These are
  user-consented downloads. The app itself stays offline at inference time; model download is a deliberate,
  user-initiated action (INTERNET permission already present).
- No fake/stub implementation will be shipped in the production path. If any step cannot be completed, this doc will
  be updated and the blocker reported per Rule #16 — not masked.
