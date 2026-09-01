# VaniFlow — P2.1 Speech Integrity Audit

## 1. Executive Summary
This audit inspects the speech intelligence, fluency, and pronunciation coaching layer in VaniFlow to guarantee zero false claims of phoneme-level measurement.

## 2. Distinction of Evidence Layers

VaniFlow strictly decouples and distinguishes five layers of evidence:

| Evidence Layer | Signal Source | Evidence Type | Permitted Claim |
|---|---|---|---|
| **1. Audio Quality Evidence** | PCM Samples (16 kHz) | SNR, RMS dBFS, clipping ratio, silence | "Audio quality is usable / clear" |
| **2. Temporal / Fluency Evidence** | VAD Frame Timings & Syllables | Voiced duration, internal pauses, WPM | "Approx. 115 WPM conversational pacing" |
| **3. Transcript Evidence** | Offline STT Engine | Word strings, token timestamps | "Utterance text transcribed" |
| **4. Practice Target Candidates** | Keyword Patterns (/θ/, /v/, -ed) | Articulatory Focus Rules | "Pronunciation practice candidate: /θ/" |
| **5. Acoustic Phoneme Evidence** | Per-Phoneme Acoustic Aligner | Phoneme Log-Likelihood / CTM | **`phonemeEvidenceAvailable = false`** |

---

## 3. What Constitutes & Does NOT Constitute Phoneme Evidence

### What is NOT Phoneme Evidence:
- **Good Audio Quality (High SNR / RMS)**: Clean acoustic signals only prove microphone health, not phoneme correctness.
- **STT Confidence**: Speech-to-text recognition accuracy only reflects language model transcript likelihood, not articulatory fidelity.
- **Transcript Keyword Matching**: Recognizing words like "think" or "very" only identifies practice opportunities, never a learner mispronunciation.
- **Words Per Minute (WPM)**: Speaking rate reflects pacing and temporal flow, not acoustic phoneme articulation.

### What Constitutes Actual Phoneme Evidence (Future Roadmap):
- An explicit acoustic phoneme alignment model (e.g. Kaldi ASR / forced aligner / Wav2Vec-CTM) computing frame-level posterior acoustic phoneme likelihoods against target reference phonemes.
- Until such a model is integrated, `phonemeEvidenceAvailable` strictly evaluates to `false`.

---

## 4. Audit Findings & Implemented Hardening Fixes

1. **`PronunciationAnalyzer.kt`**:
   - `phonemeEvidenceAvailable` is strictly hard-coded to `false`.
   - Replaced keyword "pattern detection" claims with **Pronunciation Practice Candidates / Focus Targets**.
   - Qualitative ratings reflect **Audio / Utterance Clarity** (`Natural Clarity`, `Clear Speech`, `Developing Clarity`, `Not Enough Pronunciation Evidence`).

2. **`LearningMemoryManager.kt`**:
   - Practice targets are recorded without assigning artificial error penalties or negative confidence impacts.

3. **`ProgressCalculationEngine.kt` & `DefaultProgressRepository.kt`**:
   - Guaranteed zero arbitrary or manufactured pronunciation percentages.
   - Outputs truthful state: `"Not enough pronunciation evidence"` or `"Practice Target: [Sound]"`.

4. **UI Layer (`SessionSummaryScreen.kt` & `ProgressScreen.kt`)**:
   - Labeled WPM as `"Approx. Speaking Rate"`.
   - Labeled targets as `"Pronunciation Focus Candidates"`.
