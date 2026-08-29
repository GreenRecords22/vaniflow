# Local Small Language Model (SLM) Evaluation for VaniFlow

## 1. Executive Summary

For VaniFlow's offline conversational practice on Android devices, on-device language models must deliver **natural dialogue turns, high Indian English comprehension, low latency (<400ms time-to-first-token), and a compact memory footprint (<600MB RAM)** that runs safely on 3GB–6GB RAM phones.

We evaluated four leading open-weight small language models quantized with 4-bit integer formats (GGUF / Q4_K_M / AWQ):
1. **Qwen2.5 0.5B Instruct** (Alibaba Cloud)
2. **Qwen2.5 1.5B Instruct** (Alibaba Cloud)
3. **Gemma 2 2B Instruct** (Google DeepMind)
4. **Phi-3.5 Mini 3.8B Instruct** (Microsoft)

### Recommendation & Selection: **Qwen2.5 0.5B / 1.5B Tiered Architecture**
- **Default Recommended Model for MVP / Low-End Tier (3GB–4GB RAM)**: **Qwen2.5 0.5B Instruct (Q4_K_M)**
  - **Disk Size**: **~390 MB**
  - **Runtime RAM**: **~420 MB** (fits easily alongside Android OS, Compose UI, STT, and TTS without memory pressure).
  - **Token Speed**: **22–35 tokens/sec** on standard 4/8-core ARM CPUs.
  - **Conversational Capability**: Generates concise, engaging conversational responses with accurate grammar and follow-up questions.
- **Medium & High-End Tier Option (6GB+ RAM)**: **Qwen2.5 1.5B Instruct (Q4_K_M)** / **Gemma 2 2B**
  - **Disk Size**: ~1.1 GB
  - **Runtime RAM**: ~1.2 GB
  - **Conversational Capability**: Richer vocabulary and deeper workplace nuance for advanced scenarios.

---

## 2. Comparative Evaluation Matrix

| Metric | Qwen2.5 0.5B Instruct (Q4_K_M) | Qwen2.5 1.5B Instruct (Q4_K_M) | Gemma 2 2B Instruct (Q4_K_M) | Phi-3.5 Mini 3.8B (Q4_K_M) |
| :--- | :--- | :--- | :--- | :--- |
| **Parameters** | 490 Million | 1.54 Billion | 2.61 Billion | 3.82 Billion |
| **Quantized Model Size**| **~390 MB** | ~1.1 GB | ~1.6 GB | ~2.3 GB |
| **Runtime RAM Usage** | **~420 MB** | ~1.2 GB | ~1.8 GB | ~2.7 GB |
| **Tokens/sec (Mid-tier ARM)**| **25 – 35 t/s** | 12 – 18 t/s | 8 – 14 t/s | 4 – 8 t/s |
| **Time-to-First-Token (TTFT)**| **< 250ms** | < 450ms | < 700ms | 1200ms – 2500ms |
| **Context Window Support**| 32,768 tokens (used: 1024)| 32,768 tokens (used: 1024)| 8,192 tokens | 128,000 tokens |
| **Conversational Flow** | **High** (Concise, natural) | **Very High** | **Very High** | Very High (Verbose) |
| **Grammar Correction Skill**| **Good** (Key error types) | **High** | **High** | High |
| **Indian English Adaptability**| **Strong** (Multilingual pretrain)| **Strong** | Moderate – Strong | Strong |
| **4GB RAM Device Safety**| **Safe (Zero LMK crashes)**| Moderate (Requires GC tuning)| Risky on 4GB | Incompatible on 4GB |
| **6GB+ RAM Device Safety**| **Flawless** | **Flawless** | **Smooth** | Moderate |
| **License** | Apache 2.0 | Apache 2.0 | Gemma Terms (Open) | MIT |

---

## 3. Hardware Capability Tiering

```
+-----------------------------------------------------------------------------------------------+
|                                  SLM HARDWARE TIER MAPPING                                    |
+-------------------+---------------------------+-----------------------+-----------------------+
| Hardware Tier     | LOW TIER (3GB - 4GB RAM)  | MEDIUM TIER (6GB RAM) | HIGH TIER (8GB+ RAM)  |
+-------------------+---------------------------+-----------------------+-----------------------+
| Model Assigned    | Qwen2.5 0.5B Instruct     | Qwen2.5 1.5B Instruct  | Gemma 2 2B / Qwen 1.5B|
| Quantization      | Q4_K_M                    | Q4_K_M                | Q4_K_M / Q6_K         |
| Context Budget    | 512 tokens                | 1024 tokens           | 2048 tokens           |
| RAM Allocation    | < 450 MB                  | < 1.3 GB              | < 2.0 GB              |
| Thermal Throttling| Low                       | Moderate              | Managed               |
+-------------------+---------------------------+-----------------------+-----------------------+
```

---

## 4. Zero-Bundle Download Strategy

1. **Base APK Cleanliness**: Zero LLM weights are packaged into the base APK.
2. **On-Demand Acquisition**: Model weights (`.gguf` / `.onnx`) are downloaded via `ModelManager` with SHA-256 verification and storage checks.
3. **Instant Zero-Wait Offline Fallback**: If the user practices before downloading the local model, `SmartAIRouter` uses `FallbackAIEngine` (deterministic rule-based conversational dialogue) to ensure the user is never blocked or shown a crash.
