# VaniFlow Performance Baseline & Metric Classification

## 1. Metric Classification Matrix

Every performance metric in VaniFlow is categorized into three strict tiers:

- **`VERIFIED`**: Measured directly on host build/test environment (e.g. Unit tests, APK binary size, compile success, static secret scan).
- **`ESTIMATED`**: Algorithmic calculations or mathematical models (e.g. token counts, estimated token-to-character ratio, target latencies on mid-range ARM CPU).
- **`NOT TESTED`**: Physical hardware metrics that require live Android hardware attached via ADB.

---

## 2. Baseline Metrics Summary

| Area | Metric | Value | Classification |
| :--- | :--- | :--- | :--- |
| **Packaging** | Release AAB Size (R8 Optimized, signed) | **6.34 MB** | **`VERIFIED`** |
| **Packaging** | Release APK Size (R8 Optimized, signed) | **4.62 MB** | **`VERIFIED`** |
| **Packaging** | Debug APK Size | **23.79 MB** | **`VERIFIED`** |
| **Test Quality** | JVM Unit Test Suite | **145 / 145 Tests Passing** | **`VERIFIED`** |
| **Test Quality** | Instrumented Test Suite (on-device) | **61 / 61 Tests Passing (SM-G965F)** | **`VERIFIED`** |
| **Security** | Hardcoded Secrets in APK | **0 Secrets Detected** | **`VERIFIED`** |
| **Hardware** | Cold Startup Time — Release (signed) | **797 ms** (SM-G965F, API 29) | **`VERIFIED`** |
| **Hardware** | Cold Startup Time — Debug (measured) | **2,860 ms** (SM-G965F, `am start -W` COLD) | **`VERIFIED`** |
| **Hardware** | Memory Footprint — Home (debug) | **~172 MB PSS** (SM-G965F) | **`VERIFIED`** |
| **Speech STT** | First Partial Streaming Latency | ~120ms – 250ms (Zipformer 20M) | **`ESTIMATED`** |
| **Speech STT** | Final Sentence Transcription | ~200ms – 400ms | **`ESTIMATED`** |
| **AI Generation**| Time To First Token (TTFT) | ~300ms – 600ms (Qwen2.5 0.5B GGUF) | **`ESTIMATED`** |
| **AI Generation**| Local Generation Throughput | ~15 – 25 tokens/sec | **`ESTIMATED`** |
| **Speech TTS** | Time To First Audio (TTFA) | ~150ms – 280ms (Piper ONNX) | **`ESTIMATED`** |
| **Interruption** | Speech Onset Interruption Latency | < 40ms | **`ESTIMATED`** |
| **Hardware** | Thermal Dissipation (10-min Session)| ARM CPU Thermal Throttling | **`NOT TESTED`** |
| **Hardware** | Battery Consumption Rate | mAh / conversation minute | **`NOT TESTED`** |
| **Hardware** | Real-Time Memory Under Android LMK | Low-RAM Pressure Behavior | **`NOT TESTED`** |
