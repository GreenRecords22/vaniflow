# Milestone 20 Limitations & Future Rig Roadmap

**Classification:** VERIFIED  

---

## 1. Current Verified Capabilities

- Full-screen upper-body character presentation.
- Real-time amplitude-driven mouth/face modulation during speech.
- 10 distinct emotional states parsed from conversational turns.
- Memory continuity with facts extraction and pronoun resolution.
- Instant barge-in audio flush and avatar reset.

---

## 2. Known Limitations & Blockers

1. **3D Mesh Blendshapes:**
   - The current build uses 2.5D portrait assets. True 3D phoneme blendshape deformation requires 3D rigged models (.glb/.gltf) or Live2D models.
   - *Status:* **BLOCKED — requires 3D blendshape rig assets.**
2. **TTS Phoneme Timestamp API:**
   - Android system TTS does not expose sub-word phoneme event markers over standard IPC.
   - *Status:* **BLOCKED by platform TTS engine API; amplitude viseme smoothing utilized.**