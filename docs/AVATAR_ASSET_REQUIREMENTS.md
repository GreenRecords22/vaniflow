# Avatar Asset Requirements & Implementation Boundaries

**Classification:** VERIFIED  

---

## 1. Available Assets vs Rig Requirements

### Available Assets (Verified in Repository)
- High-resolution 2.5D portrait PNG assets:
  - `res/drawable/avatar_raya.png` (1.22 MB)
  - `res/drawable/avatar_rudra.png` (1.14 MB)
  - `res/drawable/avatar_adwaita.png` (857 KB)
  - `res/drawable/avatar_shub.png` (1.04 MB)

### Assets Required for Level C/D 3D Blendshapes / Live2D (BLOCKED)
- 3D character mesh (.glb / .gltf) with ARKit blendshapes (e.g. `jawOpen`, `mouthPucker`, `mouthFunnel`, `browInnerUp`).
- Ready Player Me / Live2D runtime models with individual layered viseme masks (mouth, eyes, eyebrows, head).
- Real-time phoneme timestamp alignment stream from TTS engine.

---

## 2. Implementation Level Decision

- **Implemented Level:** **Level B (2.5D Layered Presentation + Amplitude Visemes)**
  - Full-screen upper-body character composition in `ConversationScreen`.
  - Continuous sinusoidal breathing translation and scale.
  - Dynamic state postures (listening lean, thoughtful head tilt, speaking modulation).
  - Real-time amplitude-driven mouth openness tracking with attack/release smoothing.
- **PhonemeVisemeProvider:** Documented as **BLOCKED pending 3D character blendshape rig assets**.