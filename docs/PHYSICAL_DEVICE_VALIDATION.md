# Physical Device Performance & Validation Matrix

## 1. Hardware Testing Status

> [!NOTE]
> As of Milestone 8, no physical Android device or emulator is attached via ADB (`adb devices` list empty). All latency, memory, and CPU metrics are therefore explicitly labeled as **ESTIMATED (Based on ARM profiling & architectural benchmarks)** vs **TESTED (Automated CI/CD unit & integration suites)**.

---

## 2. Benchmark & Latency Targets

| Metric | Target Specification | Tested vs Estimated | Architectural Implementation |
| :--- | :--- | :--- | :--- |
| **VAD Speech Detection** | < 40ms | **Tested (Unit)** | Energy RMS dBFS calculation per 32ms PCM frame |
| **STT Time-to-First-Partial**| < 120ms | **Estimated** | Sherpa-ONNX streaming Zipformer chunk decoding |
| **STT Final Sentence Latency**| < 100ms post-hangover | **Estimated** | VAD silence timeout trigger (900ms) |
| **Local Cache Lookup** | < 15ms | **Tested (Unit)** | Indexed Room SQLite query on SHA-256 key |
| **AI Time-to-First-Token (TTFT)**| < 250ms | **Estimated** | Qwen2.5 0.5B Instruct Q4_K_M on 4/8-core ARM CPU |
| **TTS First Sentence Audio** | < 180ms | **Estimated** | SentenceSplitter early chunk synthesis |
| **End-to-End Latency** | < 600ms total | **Estimated** | Streaming token pipeline |
| **User Interruption Response** | < 40ms | **Tested (Unit)** | AudioTrack flush + Atomic cancellation |
| **Peak Runtime RAM** | < 650 MB | **Estimated** | SLM (~420MB) + STT (~50MB) + TTS (~35MB) + App |
| **Thermal / LMK Safety** | Zero crashes on 4GB RAM | **Tested (Tier logic)**| `ModelManager` hardware RAM tier enforcement |

---

## 3. Physical Device Validation Checklist

When deploying to a physical Android device, execute the following protocol:
- [ ] Connect device running Android 10+ (API 29+) with 4GB/6GB RAM.
- [ ] Verify runtime microphone permission prompt and rationale dialog.
- [ ] Record speaking latency for:
  - Daily Life (Coffee shop scenario)
  - Job Interview scenario
- [ ] Validate immediate audio cessation upon user interruption mid-sentence.
- [ ] Run a 10-minute continuous practice session and measure battery drain & thermal throttling.
- [ ] Verify Room database persistence by navigating to Progress and Profile screens.
