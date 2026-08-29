# VaniFlow — Visual Assets, Fonts & Design Tokens

## 1. Brand Identity & Logo Assets

The VaniFlow brand identity incorporates modern typography and the **Lumina AI Teal** palette, signaling precision, clarity, and supportive intelligence.

| Asset File | Format | Location | Status / Usage |
| :--- | :--- | :--- | :--- |
| **`logo.svg`** | Scalable Vector | `stitch-design/logo.svg` | Converted to Android VectorDrawable (`ic_vaniflow_logo.xml`) for crisp scaling across all DPI buckets. |
| **`logo.png`** | Raster PNG | `stitch-design/logo.png/screen.png` | Used for launcher icons (`res/mipmap/`) and social preview cards. |

---

## 2. Character Artwork & Legal Provenance

```
┌────────────────────────────────────────────────────────────────────────┐
│                        LEGAL & PROVENANCE NOTICE                       │
│                                                                        │
│  Character images were generated during rapid UI prototyping via the   │
│  Google Stitch interface and are hosted on Google's ephemeral CDN.     │
│  Redistribution and commercial copyright status is UNCLEAR.            │
└────────────────────────────────────────────────────────────────────────┘
```

### Character Asset Inventory

| Character | Current CDN URL | Provenance & Legal Status | MVP Handling | Post-MVP Replacement Plan |
| :--- | :--- | :--- | :--- | :--- |
| **Raya** | `https://lh3.googleusercontent.com/aida-public/AB6AXuCZvEuY...` | AI Generated via Google Stitch prototype (Unclear rights) | **Temporary:** Downloaded compressed local WebP asset in `res/drawable/` as offline placeholder | Commission custom character artist or generate licensed 3D/2D vector avatars |
| **Rudra** | `https://lh3.googleusercontent.com/aida-public/AB6AXuCrkWXx...` | AI Generated via Google Stitch prototype (Unclear rights) | **Temporary:** Downloaded compressed local WebP asset in `res/drawable/` as offline placeholder | Commission custom character artist or generate licensed 3D/2D vector avatars |
| **Adwaita** | `https://lh3.googleusercontent.com/aida-public/AB6AXuCcfGcL...` | AI Generated via Google Stitch prototype (Unclear rights) | **Temporary:** Downloaded compressed local WebP asset in `res/drawable/` as offline placeholder | Commission custom character artist or generate licensed 3D/2D vector avatars |
| **Shub** | `https://lh3.googleusercontent.com/aida-public/AB6AXuBsxkV8...` | AI Generated via Google Stitch prototype (Unclear rights) | **Temporary:** Downloaded compressed local WebP asset in `res/drawable/` as offline placeholder | Commission custom character artist or generate licensed 3D/2D vector avatars |

---

## 3. Typography Strategy

VaniFlow uses a dual-typeface strategy to maintain distinct visual hierarchy and optimal legibility on mobile screens:

### 3.1. Display & Headlines: **Manrope**
- **License:** SIL Open Font License 1.1 (Open Source, 100% Free for commercial use).
- **Role:** Display headers, screen titles, character names, and score callouts.
- **Weights Used:** SemiBold (600), Bold (700), ExtraBold (800).
- **Source:** Bundled locally in `res/font/` and configured via Compose `FontFamily`.

### 3.2. Body & Labels: **Inter**
- **License:** SIL Open Font License 1.1 (Open Source, 100% Free for commercial use).
- **Role:** Conversational dialogue transcripts, feedback explanations, metadata, and buttons.
- **Weights Used:** Regular (400), Medium (500), SemiBold (600).
- **Source:** Bundled locally in `res/font/`.

---

## 4. Iconography

- **Library:** Material Symbols Outlined (Google Material 3 Icons).
- **License:** Apache License 2.0 (Open Source).
- **Implementation:** Leverages `androidx.compose.material.icons:material-icons-extended` and custom SVGs in `res/drawable/`.

### Key Icons Mapping
- `record_voice_over` — Practice tab / speaking indicator
- `mic` / `mic_off` — User speech control
- `volume_up` — AI speech audio replay
- `home` — Home dashboard
- `bar_chart` — Progress & statistics
- `person` — Profile & settings
- `local_cafe` — Daily Life scenario
- `flight_takeoff` — Travel scenario
- `work` — Standup & workplace scenario
- `groups` — Social small talk scenario
- `trending_up` — Fluency trajectory

---

## 5. Design System: Lumina AI Teal

Extracted directly from `stitch-design/lumina_ai_teal/DESIGN.md`:

```kotlin
package com.vaniflow.app.ui.theme

import androidx.compose.ui.graphics.Color

// Core Brand Palette
val Primary = Color(0xFF006B5F)           // Polished Teal
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFF5AE1CC)
val OnPrimaryContainer = Color(0xFF006156)

val Secondary = Color(0xFF39665F)         // Muted Sage
val OnSecondary = Color(0xFFFFFFFF)
val SecondaryContainer = Color(0xFFBCECE2)
val OnSecondaryContainer = Color(0xFF406C65)

val Tertiary = Color(0xFF825500)          // Sunset Gold Accent
val OnTertiary = Color(0xFFFFFFFF)
val TertiaryContainer = Color(0xFFFFC169)
val OnTertiaryContainer = Color(0xFF774E00)

val Background = Color(0xFFF5FBF8)        // Neutral Off-White Canvas
val OnBackground = Color(0xFF171D1B)

val Surface = Color(0xFFF5FBF8)
val OnSurface = Color(0xFF171D1B)
val OnSurfaceVariant = Color(0xFF3C4946)
val SurfaceContainerLowest = Color(0xFFFFFFFF) // Elevated Card Canvas
val SurfaceContainer = Color(0xFFEAEFEC)
val SurfaceContainerHigh = Color(0xFFE4E9E7)

val Outline = Color(0xFF6C7A76)
val OutlineVariant = Color(0xFFBBCAC5)

val Error = Color(0xFFBA1A1A)
val ErrorContainer = Color(0xFFFFDAD6)
val OnErrorContainer = Color(0xFF93000A)
```

### Spacing & Corner Radii

```kotlin
// Spacing Tokens
val SpacingBase = 4.dp
val SpacingXs = 8.dp
val SpacingSm = 16.dp
val SpacingMd = 24.dp
val SpacingLg = 32.dp
val SpacingXl = 48.dp
val ContainerMargin = 20.dp
val Gutter = 16.dp

// Corner Radii Tokens
val RoundedSm = 4.dp
val RoundedDefault = 8.dp
val RoundedMd = 12.dp
val RoundedLg = 16.dp
val RoundedXl = 24.dp       // Standard Cards, Main Action Buttons
val RoundedPill = 9999.dp   // Voice FAB & Filter Chips
```
