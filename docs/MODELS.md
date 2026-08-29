# VaniFlow — Model Strategy & Management

## 1. Zero-Bundle Download Strategy

To keep the initial APK lightweight (~25–35 MB), fast to install, and accessible across Google Play Store bandwidth constraints, **heavy AI model weights are never bundled directly inside the APK**.

Instead, model binaries are managed as downloadable assets managed through a dedicated `ModelManager`. Users can choose to download offline packs tailored to their device hardware and available storage.

```
┌────────────────────────────────────────────────────────────┐
│                    Lean Base APK (~30MB)                   │
│   App Code • UI Assets • Silero VAD ONNX Runtime Base      │
└─────────────────────────────┬──────────────────────────────┘
                              │
             Post-Install On-Demand Downloads
                              │
      ┌───────────────────────┼───────────────────────┐
      ▼                       ▼                       ▼
┌──────────────┐       ┌──────────────┐       ┌──────────────┐
│  STT Models  │       │  TTS Voices  │       │ Local LLM/SLM│
│  (35 - 140MB)│       │  (25 - 50MB) │       │ (350M - 1.8G)│
└──────────────┘       └──────────────┘       └──────────────┘
```

---

## 2. ModelManager Responsibilities

The `ModelManager` subsystem orchestrates the complete lifecycle of on-device neural assets:

```kotlin
interface ModelManager {
    fun getAvailableModels(): Flow<List<ModelPackage>>
    fun getDownloadedModels(): Flow<List<ModelPackage>>
    
    suspend fun downloadModel(modelId: String): Flow<DownloadProgress>
    suspend fun pauseDownload(modelId: String)
    suspend fun resumeDownload(modelId: String)
    suspend fun cancelDownload(modelId: String)
    
    suspend fun verifyIntegrity(modelId: String): Boolean
    suspend fun deleteModel(modelId: String): Result<Unit>
    suspend fun validateStorageAvailable(requiredBytes: Long): Boolean
}
```

### Core Responsibilities
1. **Catalog Manifest Resolution:** Fetches a signed JSON manifest describing available model versions, download URLs, file sizes, and expected SHA-256 checksums.
2. **Resumable Chunk Downloads:** Downloads model binaries in chunks using Ktor with `Range` header support so downloads can pause and resume seamlessly across network shifts.
3. **Integrity & Checksum Verification:** Calculates SHA-256 hash upon completion before moving the file from `cacheDir` to `filesDir/models/`.
4. **Storage Space Validation:** Verifies that the device has at least `1.5x` the model size in free disk space before initiating download.
5. **Corruption Recovery:** If a model fails to load into memory or its checksum fails, the corrupted file is quarantined and purged, and the user is prompted to re-download.
6. **Version & Update Management:** Checks for updated model quantization revisions without breaking existing user data.

---

## 3. Device Capability Tiers

The app inspects available device RAM, SoC core count, and Android API level to recommend the optimal model profile:

```
┌───────────────────────────────────────────────────────────────────────────┐
│                          Device Capability Tiers                          │
├─────────────────┬───────────────────┬─────────────────────────────────────┤
│ Tier            │ Specs             │ Recommended Model Configuration     │
├─────────────────┼───────────────────┼─────────────────────────────────────┤
│ LOW             │ 3GB - 4GB RAM     │ • Silero VAD (ONNX)                 │
│                 │ Entry 4/8-core    │ • Sherpa-ONNX Tiny STT (~35MB)      │
│                 │                   │ • Android System TTS                │
│                 │                   │ • Cloud AI / Rule Fallback (No SLM) │
├─────────────────┼───────────────────┼─────────────────────────────────────┤
│ MEDIUM          │ 6GB RAM           │ • Silero VAD (ONNX)                 │
│                 │ Mid Snapdragon/   │ • Whisper.cpp Base STT (~75MB)      │
│                 │ Dimensity         │ • Piper TTS Neural Voice (~30MB)    │
│                 │                   │ • Qwen2.5-0.5B (Q4_K_M ~350MB)      │
├─────────────────┼───────────────────┼─────────────────────────────────────┤
│ HIGH            │ 8GB+ RAM          │ • Silero VAD (ONNX)                 │
│                 │ Flagship SoC with │ • Whisper.cpp Small STT (~140MB)    │
│                 │ NPU/GPU Accel     │ • Piper Studio Quality Voice (~45MB)│
│                 │                   │ • Qwen2.5-1.5B/3B or Gemma-2-2B-Q4  │
└─────────────────┴───────────────────┴─────────────────────────────────────┘
```

---

## 4. Model Types & Candidate Evaluation

### 4.1. Large / Small Language Models (LLM / SLM)

Candidates under empirical benchmarking for local inference:

| Model Candidate | Quantization | File Size | Target RAM | Tokens/Sec (Mid SoC) | Quality / Instruction Following |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Qwen2.5-0.5B-Instruct** | Q4_K_M | ~390 MB | ~650 MB | 24–30 tok/s | Good for short structured dialogue |
| **Qwen2.5-1.5B-Instruct** | Q4_K_M | ~980 MB | ~1.3 GB | 14–18 tok/s | Excellent coherence & personality |
| **Gemma-2-2B-IT** | Q4_K_M | ~1.4 GB | ~1.9 GB | 10–14 tok/s | High quality, strict adherence |
| **Phi-3.5-mini-Instruct** | Q4_K_M | ~2.1 GB | ~2.7 GB | 6–9 tok/s | Very rich, requires high-end tier |

### 4.2. Speech Recognition (STT)

| Model Candidate | Model Architecture | Size | Language Support | WER (Indian Accents) |
| :--- | :--- | :--- | :--- | :--- |
| **whisper.cpp tiny.en** | Transformer Encoder-Decoder | ~39 MB | English (Optimized) | ~11.2% |
| **whisper.cpp base.en** | Transformer Encoder-Decoder | ~142 MB | English | ~8.4% |
| **Sherpa-ONNX Zipformer** | Conformer / Transducer | ~42 MB | Multi-accent English | ~8.9% |

### 4.3. Speech Synthesis (TTS)

| Model Candidate | Technology | Voice Footprint | Real-time Factor (RTF) | Quality (MOS 1-5) |
| :--- | :--- | :--- | :--- | :--- |
| **Piper TTS (en_US / en_IN)** | VITS Neural | ~30 MB | 0.15 (Fast) | 4.3 |
| **Sherpa-ONNX VITS** | VITS Neural | ~35 MB | 0.18 (Fast) | 4.2 |
| **Android TextToSpeech** | Platform OEM | 0 MB | Instant | 3.5 |

---

## 5. Evaluation & Selection Criteria

No model is hardcoded or finalized without passing the following empirical threshold gates on real physical hardware:

1. **Peak RAM Consumption:** Must not exceed 40% of physical device RAM to prevent Android OS Out-Of-Memory (OOM) kills.
2. **Tokens Per Second:** Must achieve **>= 15 tokens/second** for streaming conversation to feel natural and responsive.
3. **Cold Load Time:** Model must initialize from disk into memory in **< 1.8 seconds**.
4. **License Compliance:** Must possess clear commercial and redistribution authorization (e.g., Apache 2.0, MIT, or compatible open model terms).
5. **Decoupled Architecture:** The `ConversationEngine` references models purely via abstract identifiers (`model_id`), allowing instant swapping without modifying business logic.
