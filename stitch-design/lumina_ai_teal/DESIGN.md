---
name: Lumina AI Teal
colors:
  surface: '#f5fbf8'
  surface-dim: '#d6dbd9'
  surface-bright: '#f5fbf8'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#eff5f2'
  surface-container: '#eaefec'
  surface-container-high: '#e4e9e7'
  surface-container-highest: '#dee4e1'
  on-surface: '#171d1b'
  on-surface-variant: '#3c4946'
  inverse-surface: '#2c3230'
  inverse-on-surface: '#ecf2ef'
  outline: '#6c7a76'
  outline-variant: '#bbcac5'
  surface-tint: '#006b5f'
  primary: '#006b5f'
  on-primary: '#ffffff'
  primary-container: '#5ae1cc'
  on-primary-container: '#006156'
  inverse-primary: '#53dbc7'
  secondary: '#39665f'
  on-secondary: '#ffffff'
  secondary-container: '#bcece2'
  on-secondary-container: '#406c65'
  tertiary: '#825500'
  on-tertiary: '#ffffff'
  tertiary-container: '#ffc169'
  on-tertiary-container: '#774e00'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#74f8e3'
  primary-fixed-dim: '#53dbc7'
  on-primary-fixed: '#00201c'
  on-primary-fixed-variant: '#005047'
  secondary-fixed: '#bcece2'
  secondary-fixed-dim: '#a1d0c6'
  on-secondary-fixed: '#00201c'
  on-secondary-fixed-variant: '#204e47'
  tertiary-fixed: '#ffddb3'
  tertiary-fixed-dim: '#f9bb64'
  on-tertiary-fixed: '#291800'
  on-tertiary-fixed-variant: '#633f00'
  background: '#f5fbf8'
  on-background: '#171d1b'
  surface-variant: '#dee4e1'
typography:
  display-lg:
    fontFamily: Manrope
    fontSize: 48px
    fontWeight: '800'
    lineHeight: 56px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Manrope
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Manrope
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
  headline-md:
    fontFamily: Manrope
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  xs: 8px
  sm: 16px
  md: 24px
  lg: 32px
  xl: 48px
  container-margin: 20px
  gutter: 16px
---

## Brand & Style

The design system is engineered for a premium, high-efficacy AI language learning experience. The personality is **intelligent, encouraging, and sophisticated**. It targets ambitious professionals and students who require a tool that feels more like a private tutor than a game.

The aesthetic follows a **Modern Corporate** direction with a focus on **Tactile Softness**. It utilizes a "soft-depth" approach where generous whitespace and high-quality typography provide clarity, while subtle elevation and 24px radii create an approachable, high-end physical feel. The interface should feel calm and focused, minimizing cognitive load during intensive speaking practice.

## Colors

The palette is anchored by **Polished Teal**, signaling precision, clarity, and modern intelligence. **Muted Sage** is used for secondary interactive elements and progress tracking, providing a professional and grounded accompaniment to the primary brand color. 

**Sunset Gold** is used for tertiary accents, such as highlighting correct answers or special achievements. The background uses a **Neutral Grey Tint** to reduce eye strain compared to pure white, maintaining a premium, "paper-like" quality. Text uses a dark neutral for high legibility, with a softer variant for metadata and secondary labels.

## Typography

This design system employs a dual-font strategy. **Manrope** is used for all headlines and display text to provide a modern, geometric, and intelligent character. **Inter** is used for body text and functional labels due to its exceptional legibility and neutral systematic feel.

For mobile, headlines scale down to ensure they remain within the viewport without excessive wrapping. Paragraphs utilize generous line-height (1.5x minimum) to ensure long-form educational content remains digestible. Use `semibold` (600) for interactive labels to distinguish them from static body text.

## Layout & Spacing

The layout follows a **Fluid Grid** model with a 4px baseline rhythm. For mobile devices, use a 4-column grid with 20px side margins and 16px gutters. For desktop, the content is centered within a 12-column max-width container of 1140px.

Spacing is used to create clear groupings: 
- Use `xl` (48px) for separating major sections (e.g., Header to Content).
- Use `md` (24px) for spacing between unrelated cards or components.
- Use `sm` (16px) for internal padding within cards and layout blocks.
- Elements should be vertically stacked with ample white space to emphasize the "Premium" feel.

## Elevation & Depth

This design system uses **Ambient Shadows** to create a sense of tactile layering without clutter. Shadows are extremely soft, utilizing the Primary Polished Teal color as a low-opacity tint to ensure they feel integrated into the UI.

- **Level 1 (Resting Cards):** 0px 4px 20px rgba(9, 177, 158, 0.05).
- **Level 2 (Hover/Active):** 0px 8px 30px rgba(9, 177, 158, 0.08).
- **Level 3 (Modals/Overlays):** 0px 12px 40px rgba(0, 0, 0, 0.1).

Surfaces use a hierarchy of color: The main canvas is a light neutral tint (`#f6faf9`), while elevated cards use a pure `#FFFFFF` background to "pop" against the off-white base.

## Shapes

The shape language is defined by **large, friendly radii**. 
- **Standard Components:** Use a 24px corner radius (`rounded-xl` equivalent) for cards, large buttons, and input fields.
- **Small Components:** Use 8px (`rounded-md`) for chips, checkboxes, and small utility buttons.
- **Voice Interaction:** Elements related to the AI persona or voice input may use **Circular/Pill** shapes to suggest fluidity and organic conversation.

## Components

### Buttons
- **Primary:** 24px radius, Polished Teal background, white text. Always include a leading icon for core actions. Size should be 56px in height for high touch-target accessibility.
- **Secondary:** 24px radius, Muted Sage at 10% opacity with Polished Teal text.
- **Voice Button:** A floating action button (FAB) that is purely circular, featuring a subtle pulsing animation when active.

### Cards
- White background (`#FFFFFF`), Level 1 elevation, and 24px corner radius. Cards should feature a 1px border of a light neutral outline to maintain definition on white backgrounds.

### Inputs & Form Elements
- **Fields:** 56px height, 24px radius, light surface background with a 1px border. Focus state uses a 2px Polished Teal border.
- **Checkboxes:** 8px radius, using Polished Teal for the "checked" state.

### Navigation
- **Bottom Navigation (Mobile):** A fixed bar with a backdrop blur (Glassmorphism) effect. Icons are 24px, using Polished Teal for the active state and lower opacity neutral for inactive states.

### Voice Interaction States
- **Idle:** A steady, thin circular ring.
- **Listening:** A multi-layered animated pulse using Polished Teal and Sunset Gold gradients.
- **Processing:** A continuous rotating gradient around the perimeter of the voice icon.