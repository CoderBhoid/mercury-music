# Architectural & Design Decisions (ADR)

**Organization**: Sednium Technologies  
**Project**: Mercury Music  
**Status**: Accepted  

---

## ADR 001: Forking Metrolist instead of OuterTune

### Context
When selecting the foundational open-source architecture for Mercury Music, both **Metrolist** and **OuterTune** were evaluated as modern YouTube Music clients for Android.

### Decision
Sednium Technologies selected **Metrolist** as the upstream fork baseline.

### Rationale
1. **Modern Jetpack Compose & Material 3 Alignment**: Metrolist is built entirely around modern Jetpack Compose (1.12+) and Material 3 (`androidx.compose.material3:1.5.0-alpha28`), making UI replacement with Cupertino glassmorphism significantly cleaner than refactoring older XML or hybrid views.
2. **InnerTubeX & YouTube API Maturity**: Metrolist maintains active integration with `innertubex`, supporting client-side PoToken generation, cipher deobfuscation (`zemer-cipher`), and resilient streaming endpoints.
3. **Advanced Feature Foundation**: Metrolist already incorporates robust background caching, synchronized lyrics (via LrcLib and KuGou), local Room database persistence, and multi-format audio playback pipelines.
4. **Maintenance & Architecture**: Metrolist provides a clean separation between `:app` and `:innertube` modules, simplifying custom design system injection without destabilizing audio pipelines.

---

## ADR 002: Package Identity & Rebranding (`com.sednium.mercury`)

### Context
To prevent collisions with upstream Metrolist installations, ensure independent Google Cast / Android Auto integration, and establish Sednium brand ownership, a dedicated application identity is required.

### Decision
- **Application ID**: `com.sednium.mercury`
- **Application Name**: `Mercury Music`
- **FileProvider Authority**: `${applicationId}.provider` (with backwards compatibility for `${applicationId}.FileProvider`)
- **Internal Namespace**: `com.metrolist.music` retained for internal Kotlin package structure during Phase 1 to maintain spotless git history and zero import churn.

---

## ADR 003: Frosted Glass Engine via Haze 1.5.3

### Context
Obsidian-style glassmorphism requires high-performance, real-time background blurring with surface tinting, specular borders, and noise grain under heavy scroll/playback loads without dropping frames below 60/120 FPS.

### Decision
Adopt Chris Banes' **Haze 1.5.3** (`dev.chrisbanes.haze:haze` and `dev.chrisbanes.haze:haze-materials`).

### Rationale
1. **RenderEffect & Android 12+ Acceleration**: Haze natively leverages Android 12+ (API 31+) hardware-accelerated `RenderEffect` for zero-overhead GPU Gaussian blurs, while providing efficient fallbacks on API 26-30.
2. **Compose Integration**: Haze provides seamless `Modifier.hazeSource()` and `Modifier.hazeEffect()` primitives that integrate directly with nested Compose layout hierarchies (e.g., player sheet over dynamic artwork background).
3. **Materials Module**: `haze-materials` provides pre-tuned blur/noise/specular tint shaders matching Apple Cupertino material standards.

---

## ADR 004: Cupertino Navigation Pill Styling

### Context
Standard Material 3 bottom navigation bars occupy excessive vertical screen real estate and do not provide an immersive audio-first feel.

### Decision
Implement a floating capsule/pill navigation dock styled after Cupertino iOS design principles:
- **Shape**: 32dp capsule / pill geometry (`RoundedCornerShape(32.dp)`).
- **Background**: Frosted glass surface using Haze (`25.dp` blur, 60% alpha `#0C0E16`, 0.08f noise factor).
- **Border**: 1dp linear specular highlight gradient simulating beveled physical glass.
- **Physics**: Interactive spring physics (`Spring.DampingRatioLowBouncy`, `Spring.StiffnessMediumLow`) for tab indicator transitions.
