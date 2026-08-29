# Talking Avatar Implementation Guide

**Classification:** VERIFIED  

---

## 1. Architecture

```
[RealOfflineTTSEngine]
        │
        ├── speakSingleSentence() (Active TTS Playback)
        │         │
        │         └── Coroutine Loop (every 35ms) ──► lipSyncController.onAmplitude(amp)
        │
        ▼
[LipSyncController]
        │
        ├── AmplitudeVisemeProvider (Attack: 30ms, Release: 70ms)
        ├── mapToMouthShape(openness, viseme)
        │
        ▼
[TalkingAvatarRenderer (Compose Canvas)]
        │
        ├── Base Portrait (avatar_raya.png)
        ├── Sinusoidal Breathing + Head Tilt
        ├── Eye-Blink Loop (every 3.8s)
        ├── Dynamic Oral Cavity + Teeth Highlight + Lip Contour Overlay
        └── Bottom Vignette Fade
```

---

## 2. Character Facial Profiles

| Character | Mouth X | Mouth Y | Mouth Width Ratio | Mouth Height Ratio | Lip Color |
|---|---|---|---|---|---|
| **Raya** | `0.500` | `0.612` | `0.170` | `0.082` | `#C7656A` |
| **Rudra** | `0.500` | `0.618` | `0.175` | `0.080` | `#B86058` |
| **Adwaita** | `0.500` | `0.608` | `0.162` | `0.078` | `#BE555C` |
| **Shub** | `0.500` | `0.605` | `0.165` | `0.080` | `#B55D55` |