# Design Permission & Clean-Room Implementation Note

**Organization**: Sednium Technologies  
**Founder**: Ayush Pal (Founded January 20, 2024)  
**Project**: Mercury Music  
**Target Platform**: Android (Jetpack Compose, Kotlin)  
**Base Upstream**: Metrolist (Licensed under GNU General Public License v3.0)

---

## 1. Authorization & Design Style Rights
The visual design style, glassmorphic layout language, floating pill navigation dock, and Cupertino-inspired aesthetics of Mercury Music were designed based on architectural analysis of the Obsidian Music aesthetic.

- The author of the Obsidian Music visual design language has confirmed that developers are free to replicate, utilize, and adapt the visual design style and UX interaction models via clean-room reverse engineering.
- **No decompiled source code, proprietary algorithms, or copyrighted binaries have been copied, reverse-engineered into source, or embedded into the Mercury codebase.**
- Mercury Music is a 100% clean-room build implemented from scratch in idiomatic Kotlin and Jetpack Compose, built atop the open-source GPL-3.0 Metrolist audio architecture.

## 2. Upstream Open-Source Attribution & Licensing
Mercury Music complies fully with the GNU General Public License v3.0 (GPL-3.0):
- **Upstream Project**: Metrolist by Mostafa Alagamy & Contributors
- **Preceding Lineage**: OuterTune (Davide Garberi, Michael Zh) & InnerTune (Zion Huang, Malopieds)
- **License**: GNU General Public License v3.0 (GPL-3.0)
- All modifications, enhancements, UI rewrites, and new modules created by Sednium Technologies remain subject to the GPL-3.0 terms.

## 3. Clean-Room Methodology
1. **Visual & Behavioral Specification**: Only high-level UI specifications (blur radii, noise grain percentages, color palette tokens, spring physics parameters, and component hierarchy) were documented from external observation.
2. **Native Implementation**: All Compose modifiers, Haze frosted-glass shaders (`dev.chrisbanes.haze`), custom canvas artwork blur backgrounds, Apple-style scrub sliders, and format badge composables are newly written, maintainable, and type-safe.
