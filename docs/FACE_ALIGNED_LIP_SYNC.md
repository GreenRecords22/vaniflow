# Face-Aligned Lip Sync & Articulation

**Classification:** VERIFIED  

---

## 1. Pixel Landmarks (1024x1024 Source Bitmaps)

| Character | Mouth Center X | Mouth Center Y | Mouth Width Ratio | Mouth Height Ratio | Lip Color |
|---|---|---|---|---|---|
| **Raya** | `0.500` (512px) | `0.576` (590px) | `0.155` (158px) | `0.058` (60px) | `#C7656A` |
| **Rudra** | `0.500` (512px) | `0.581` (595px) | `0.160` (164px) | `0.060` (62px) | `#B86058` |
| **Adwaita** | `0.500` (512px) | `0.556` (570px) | `0.150` (154px) | `0.056` (58px) | `#BE555C` |
| **Shub** | `0.500` (512px) | `0.600` (615px) | `0.155` (158px) | `0.058` (60px) | `#B55D55` |

---

## 2. Destination Transform Formula

When rendering inside a Canvas of dimensions `(canvasW, canvasH)`:
```kotlin
val scale = maxOf(canvasW / 1024f, canvasH / 1024f)
val scaledW = 1024f * scale
val scaledH = 1024f * scale
val offsetX = (canvasW - scaledW) / 2f
val offsetY = (canvasH - scaledH) / 2f

val mCenterX = offsetX + (scaledW * profile.mouthCenterX)
val mCenterY = offsetY + (scaledH * profile.mouthCenterY)
```
This formula ensures the Canvas drawing coordinates precisely match the image pixels rendered by `ContentScale.Crop` with 0 pixel offset on all screen densities and aspect ratios.