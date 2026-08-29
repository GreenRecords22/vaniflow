# Avatar Audio ↔ Lip-Sync Implementation Guide

**Classification:** VERIFIED  

---

## 1. Architecture

```
[RealOfflineTTSEngine]
        │
        ├── onStart() ──────────────────────────► [LipSyncController.onSpeechStart()]
        ├── onAudioFrame(amplitude) ────────────► [LipSyncController.onAmplitude(amp)]
        │                                                     │
        │                                                     ▼
        │                                         [AmplitudeVisemeProvider]
        │                                         • Attack factor: 0.45 (30ms)
        │                                         • Release factor: 0.18 (70ms)
        │                                         • Map: 0.0..1.0 ──► VisemeState
        │                                                     │
        │                                                     ▼
        │                                         [mouthOpennessFlow: Float]
        │                                                     │
        ▼                                                     ▼
[AudioTrack Playback]                     [ImmersiveCharacterAvatar Composable]
```

---

## 2. Viseme Mapping Table

| Amplitude (Smoothed) | Viseme Frame | Target Mouth Shape |
|---|---|---|
| `0.00 – 0.05` | `REST` | Closed / Neutral |
| `0.05 – 0.20` | `M_B_P` | Subtle syllable onset |
| `0.20 – 0.35` | `I` / `S_Z` | Light open / smile |
| `0.35 – 0.55` | `E` / `L` | Medium open |
| `0.55 – 0.75` | `O` / `U` | Rounded vowel |
| `0.75 – 1.00` | `A` | Wide open syllable |