# Obsidian Music v2.5.1 Reverse-Engineering Specification

**Target Application**: Obsidian Music v2.5.1  
**Methodology**: Visual & Behavioral Clean-Room Reverse Engineering  
**Implementation Target**: Mercury Music (Sednium Technologies)

---

## 1. Visual Hierarchy & Layer Stack
The Obsidian visual architecture is structured in 4 distinct compositional z-layers:

```
Layer 4: Floating Cupertino Navigation Pill & Overlay Controls (Topmost)
Layer 3: Player Bottom Sheet / Content Cards (Frosted Glass Surfaces)
Layer 2: Content Surface & Typography (High-contrast monochromatic text)
Layer 1: Dynamic Canvas Artwork Background (Substrate)
```

---

## 2. Frosted Glass (Haze) Specifications

### A. Core Blur & Noise Parameters
- **Blur Radius**: `25.dp` Gaussian blur.
- **Noise Factor**: `0.08f` (8% monochromatic grain applied to eliminate 8-bit color banding on dark OLED displays).
- **Surface Color & Alpha**: `#0C0E16` at 60% opacity (`Color(0x990C0E16)`).
- **Light Bleed / Progressive Gradient**: Top-to-bottom alpha ramp from `0x99` (60%) to `0xB3` (70%) on bottom sheets.

### B. Specular Bevel Border
Glass surfaces feature a 1dp directional linear gradient stroke that simulates physical glass refraction under ambient lighting:
- **Stroke Width**: `1.dp`
- **Brush**: `Brush.linearGradient(colors = listOf(Color(0x40FFFFFF), Color(0x05FFFFFF), Color(0x1AFFFFFF)), start = Offset(0f, 0f), end = Offset.Infinite)`
- **Highlight Direction**: Upper-left (40% white) to lower-right (5% white).

---

## 3. Dynamic Canvas Artwork Background

### A. Substrate Rendering
- Behind both the player and the scrollable views, the current track's album artwork is decoded and rendered to a background canvas.
- **Blur Pipeline**: Dual-pass blur with effective radius of `90.dp` to `120.dp`.
- **Saturation / Brightness Tuning**: Saturation scaled to `1.25f`, contrast scaled to `0.90f`, dark overlay tint of `#090A0F` at 45% alpha to guarantee WCAG 2.1 AAA text readability.
- **Track Transition**: 400ms crossfade tween between consecutive album art substrates.

---

## 4. Apple-Style Scrub Slider

### A. Geometry & Touch States
- **Resting Track Height**: `4.dp` with `CircleShape` corners.
- **Active / Pressed Track Height**: Expands smoothly to `8.dp` on touch down via `animateDpAsState(tween(150))`.
- **Resting Thumb**: Hidden or minimal `6.dp` circular pip.
- **Active Thumb**: Expands to `10.dp` high-contrast pill with specular glow.
- **Scrubbing Physics**: Velocity dampening when dragging vertically away from the seek bar (1x at 0dp delta, 0.5x at 50dp delta, 0.25x at 100dp delta).

---

## 5. Audio Format Badges (Pills)

### A. Codec & Sample Rate Indicator
In player and detail views, format pills provide stream fidelity feedback (OPUS, FLAC, AAC, Hi-Res 24-bit/96kHz):
- **Shape**: `RoundedCornerShape(6.dp)`.
- **Padding**: Horizontal `6.dp`, Vertical `2.dp`.
- **Background**: `Color(0x26FFFFFF)` (15% white).
- **Border**: `0.5.dp` solid `Color(0x33FFFFFF)` (20% white).
- **Typography**: `11.sp`, Font Weight `FontWeight.SemiBold`, letter spacing `0.5.sp`, Monospace or Sans-Serif Caps.
