# Avatar Asset Guide & Pipeline

**Classification:** VERIFIED  

---

## 1. Current 2D Asset Pipeline

- **Source Asset:** 2D high-resolution portrait PNGs stored in `res/drawable/` (`avatar_raya.png`, `avatar_rudra.png`, `avatar_adwaita.png`, `avatar_shub.png`).
- **Rendering Mechanism:** `TalkingAvatarRenderer` uses facial anchor calibration to composite real-time oral cavity depth, lip curvature, teeth highlights, and blink frames over the base portrait in hardware-accelerated Compose Canvas.

---

## 2. Future 3D / Live2D Asset Requirements (BLOCKED)

- For full 3D skeletal mesh deformation, glTF / GLB 3D rigged models with ARKit blendshape keys (`jawOpen`, `mouthPucker`, `mouthFunnel`) are required.
- *Status:* Currently **BLOCKED** pending 3D blendshape rig assets; the 2D layered canvas system provides the production-ready talking avatar for V1.