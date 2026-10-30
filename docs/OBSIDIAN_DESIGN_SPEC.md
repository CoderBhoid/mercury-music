# Mercury Music — Obsidian Design Tokens & System Specification

**Organization**: Sednium Technologies  
**Project**: Mercury Music  
**Version**: 1.0.0  

---

## 1. Color Palette Tokens

| Token Name | Hex / ARGB | CSS / Compose Value | Usage |
|---|---|---|---|
| `ObsidianBackground` | `#090A0F` | `Color(0xFF090A0F)` | App root canvas background |
| `ObsidianSurface` | `#0C0E16` | `Color(0xFF0C0E16)` | Card and panel solid substrate |
| `ObsidianGlassSurface` | `60% #0C0E16` | `Color(0x990C0E16)` | Haze frosted glass surface tint |
| `ObsidianGlassBorderHigh` | `25% #FFFFFF` | `Color(0x40FFFFFF)` | Specular highlight stroke (top/left) |
| `ObsidianGlassBorderLow` | `5% #FFFFFF` | `Color(0x0DFFFFFF)` | Specular shadow stroke (bottom/right) |
| `TextPrimary` | `#FFFFFF` | `Color(0xFFFFFFFF)` | Titles, active icons, primary labels |
| `TextSecondary` | `#8E92A4` | `Color(0xFF8E92A4)` | Subtitles, artist names, duration text |
| `TextMuted` | `#505565` | `Color(0xFF505565)` | Inactive icons, timestamps, borders |
| `AccentElectric` | `#3B82F6` | `Color(0xFF3B82F6)` | Active state indicator, scrub progress |
| `BadgeBackground` | `15% #FFFFFF` | `Color(0x26FFFFFF)` | OPUS/FLAC format pill badge substrate |

---

## 2. Spacing & Grid System

Base Unit: **4dp**

- `SpaceMicro`: `2.dp`
- `SpaceSmall`: `4.dp`
- `SpaceCompact`: `8.dp`
- `SpaceNormal`: `12.dp`
- `SpaceMedium`: `16.dp`
- `SpaceLarge`: `24.dp`
- `SpaceXLarge`: `32.dp`
- `SpaceDock`: `48.dp` (Navigation pill offset from bottom edge)

---

## 3. Shape Tokens

- **Navigation Capsule Dock**: `RoundedCornerShape(32.dp)` (Capsule pill)
- **Album Artwork Card**: `RoundedCornerShape(24.dp)`
- **Player Bottom Sheet**: `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`
- **Standard Content Cards**: `RoundedCornerShape(16.dp)`
- **Button / Action Pill**: `RoundedCornerShape(12.dp)`
- **Audio Format Badge**: `RoundedCornerShape(6.dp)`

---

## 4. Typography Hierarchy

```kotlin
val ObsidianTypography = Typography(
    displayLarge = TextStyle(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        color = Color(0xFFFFFFFF)
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
        color = Color(0xFFFFFFFF)
    ),
    titleMedium = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.15.sp,
        color = Color(0xFFFFFFFF)
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.5.sp,
        color = Color(0xFFFFFFFF)
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.25.sp,
        color = Color(0xFF8E92A4)
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        fontFamily = FontFamily.Monospace,
        color = Color(0xFFFFFFFF)
    )
)
```

---

## 5. Animation Physics & Interpolators

- **Cupertino Tab Spring**:
  `spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)`
- **Glass Sheet Elevation & Morph**:
  `tween(durationMillis = 350, easing = FastOutSlowInEasing)`
- **Dynamic Canvas Artwork Crossfade**:
  `tween(durationMillis = 400, easing = LinearOutSlowInEasing)`
