# VaniFlow — P2 Speech Intelligence & Pronunciation Coaching Architecture

## 1. Overview
The P2 Speech Intelligence engine introduces real, acoustic-backed speech processing, fluency analysis, and pronunciation coaching to VaniFlow without ever fabricating phoneme scores or percentages when acoustic evidence is absent.

## 2. Core Architecture Components

### 2.1 Acoustic & Temporal Feature Extraction (`SpeechFeatureExtractor`)
- **Sample Scanning**: Direct 16-bit PCM amplitude and clipping inspection (`abs(s) >= 32700`).
- **Frame-by-Frame RMS Energy**: 32 ms windows (512 samples @ 16 kHz), computing logarithmic dBFS in `[-90.0, 0.0] dBFS`.
- **Dynamic Noise Floor & SNR**: Tracks baseline background noise floor using low-percentile energy distributions, calculating signal-to-noise ratio ($SNR = RMS_{voiced} - NoiseFloor$).
- **Voiced vs Silence Segmentation**: Classifies voiced speech frames vs internal pauses.
- **Phonotactic Syllable Estimator**: Counts vowel nuclei while accounting for silent 'e', '-ed' regular past tense endings, hiatus vowel pairs (`ia`, `iu`, `eo`, `ua`), and diphthong suffixes (`tion`, `sion`).

### 2.2 Speech Signal Health (`SpeechQualityAnalyzer`)
- Evaluates SNR, clipping ratio, and voiced duration.
- Classifies signal as usable vs degraded (microphone clipping, high ambient noise, or whisper volume).

### 2.3 Evidence-Based Fluency Analysis (`FluencyAnalyzer`)
- **Non-Penalization of Natural Thinking Pauses**: Pauses between 200 ms and 800 ms are natural conversational thinking pauses and are never flagged as hesitations.
- **Hesitation Classification**: Distinguishes `NORMAL_PAUSE` (200-800 ms), `HESITATION` (800-1500 ms), `LONG_HESITATION` (>1500 ms), and `REPEATED_HESITATION`.
- **Speaking Rate (WPM & SPS)**: Measures words-per-minute (100-160 WPM normal range) and syllables-per-second.

### 2.4 Truthful Pronunciation Coaching (`PronunciationAnalyzer`)
- **Anti-Fabrication Principle**: When speech duration < 600 ms, SNR is low, or acoustic phoneme evidence is not present, returns `NOT_ENOUGH_DATA` with `phonemeEvidenceAvailable = false` and quantitative score 0.
- **Target Sound Detection**: Detects key Indian English pronunciation target areas:
  1. Unvoiced "th" (/θ/ in "think", "thanks", "thirty") vs "s"/"t"
  2. "v" vs "w" distinction ("very", "world", "water")
  3. Past tense regular "-ed" endings ("walked", "asked", "watched")
  4. Smooth consonant clusters ("str", "spr")
- **Actionable Micro-Tips**: Provides concrete articulatory instructions (e.g. *"Place your tongue gently between your teeth for 'think' rather than 'sink'"*).

### 2.5 Persistence & Database Schema (Room DB v4)
- **Table**: `speech_analysis`
- **Fields**: `id`, `turnId`, `sessionId`, `audioDurationMs`, `voicedDurationMs`, `pauseCount`, `totalPauseDurationMs`, `wordsPerMinute`, `qualitativeFluency`, `qualitativePronunciation`, `hesitationType`, `snrDb`, `hasPhonemeEvidence`, `practicedSound`, `timestampEpochMs`.
- **Migration**: `MIGRATION_3_4` registered in `VaniFlowDatabase.kt`.
- **Repository**: `SpeechAnalysisRepository` & `DefaultSpeechAnalysisRepository`.
