# Real Avatar Asset Requirements for Next-Gen Releases

**Classification:** ASSET SPECIFICATION  

---

## 1. Context

VaniFlow V1 uses Native 2D Bitmap Mesh deformation (`drawBitmapMesh`) on high-resolution 1024x1024 portraits (`avatar_raya.png`).  
To achieve multi-axis 3D head rotation and independent phonetic teeth/tongue visemes in future major releases (V2), the following asset specifications are established:

---

## 2. Target Asset Formats

### Option A: Multi-Layered Photorealistic PSD / PNG Stack (Recommended for 2D)
- **Base Layer:** Head, hair, neck, and torso with oral cavity hollowed out (transparent mouth hole).
- **Mouth Background Layer:** Inner oral cavity depth gradient + throat shadow.
- **Teeth Layers:** Upper dental arch + lower dental arch (separate alpha PNGs).
- **Tongue Layer:** Articulated tongue sprite.
- **Lip Layers:** Contoured upper lip mask + lower lip mask for independent deformation.

### Option B: Live2D Cubism Model (SDK 4.0 / 5.0)
- Parameterized blend weights: `ParamMouthOpenY`, `ParamMouthForm`, `ParamEyeLOpen`, `ParamEyeROpen`, `ParamAngleX`, `ParamAngleY`.
- Lightweight runtime texture atlas ($2048 \times 2048$).

### Option C: 3D glTF / GLB Rigged Model (Filament / Sceneview)
- ARKit 52 facial blendshape standards (`jawOpen`, `mouthPucker`, `mouthFunnel`, `mouthSmileLeft`, `mouthSmileRight`).
- Target budget: $< 25,000$ polygons, single PBR material, $< 15\text{MB}$ file size.