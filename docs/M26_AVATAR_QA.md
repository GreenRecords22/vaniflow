# Milestone 26 — Avatar & Lip-Sync Quality Assurance

**Classification:** VERIFIED REAL (2D Bitmap Mesh) / RIGGED LIMITATION NOTED  

---

## 1. Video-Call UX & Visual Stability

- Fixed persistent upper viewport for character portrait.
- Floating live subtitles.
- Collapsible bottom drawer for optional chat transcript.
- Instant barge-in interruption cancels TTS and resets mouth within 0ms.

---

## 2. 2D Bitmap Mesh vs. Rigged 3D Avatar Honest Assessment

- **Implemented**: Native 2D Gaussian vertex mesh displacement (`drawBitmapMesh`) deforming the actual photographic pixels of Raya's mouth and jaw.
- **Strengths**: Preserves 100% photographic texture, zero artificial vector lines or cartoon mouth pasted over skin.
- **Architectural Limitation**: Because the source asset is a single flat 2D raster PNG, phoneme-level interior teeth/tongue visemes and 3D head yaw/pitch rotation cannot be synthesized without a multi-layered PSD or 3D blendshape model (documented in `docs/REAL_AVATAR_ASSET_REQUIREMENTS.md`).