# VaniFlow Model Tiers (Milestone 16)

Internal model identifiers are **never shown to users**. The app surfaces only the VaniFlow brand
tier. This file documents the mapping and the recommended hardware tiers.

## Tiers

| Tier | Brand name | Internal model id(s) | RAM | Storage | Quality | Recommended device |
|---|---|---|---|---|---|---|
| LITE | **VaniFlow Lite** | `llm_qwen25_05b_instruct` | ~2 GB | ~390 MB | Everyday conversations | S9 / mid-range |
| CORE | **VaniFlow Core** | `llm_qwen25_15b_instruct` | ~4 GB | ~1.1 GB | Balanced (recommended) | S9+ and up |
| PRO | **VaniFlow Pro** | (future 3B/7B) | ~8 GB | ~2.2 GB | Premium / long context | Flagship |

## Implementation

- `engine/model/VaniFlowModelTier.kt` — enum with `brandedName`, `tagline`, `ramRequirementMb`,
  `storageRequirementMb`, `qualityTier`, `recommendedDeviceTier`, and `fromModelId`.
- `domain/model/ModelInfo.kt` (`ModelMetadata`) gained `brandedName` (default = name) and
  `vaniFlowTier` (default null). `DefaultModelManager` sets these for LLM entries.
- `feature/profile/ProfileScreen.kt` (`ModelCard`) displays `brandedName` + tier tagline + size.
- `ProfileViewModel` storage/error messages use `brandedName`.

## Notes

- No model file is bundled and no runtime can load one in this build → genuine AI is **BLOCKED**.
- The tier metadata is accurate for sizing/UX; the "recommended" tagline is shown but does not yet
  gate download (no runtime to enforce it).
