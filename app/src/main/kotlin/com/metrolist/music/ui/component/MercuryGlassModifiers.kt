package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Authentic Obsidian Glass Design Tokens ported directly from Obsidian Music APK (ModifiersKt.java):
 * - GlassBorderBrush: White linear gradient (25% to 3% opacity)
 * - GlassBackgroundColor: Color(0xFF161822) @ 60% opacity (4279638050L)
 * - GlassTintColor: Color.White @ 5% opacity
 * - GlassFallbackColor: Color(0xFF161820) @ 85% opacity (4279638048L)
 * - Default Blur Radius: 25.dp
 * - Noise Factor: 0.08f
 */
val ObsidianGlassBorderBrush = Brush.linearGradient(
    listOf(
        Color.White.copy(alpha = 0.25f),
        Color.White.copy(alpha = 0.03f)
    )
)
val ObsidianGlassFallbackColor = Color(0xFF161820).copy(alpha = 0.85f)
val ObsidianGlassBackgroundColor = Color(0xFF161822).copy(alpha = 0.60f)
val ObsidianGlassTintColor = Color.White.copy(alpha = 0.05f)

@Composable
fun Modifier.obsidianGlassEffect(
    hazeState: HazeState? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    blurRadius: Dp = 25.dp,
): Modifier {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val backgroundColor = if (isDark) {
        Color(0xFF161822).copy(alpha = 0.60f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    }

    val tintColor = if (isDark) {
        Color.White.copy(alpha = 0.05f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.03f)
    }

    val fallbackColor = if (isDark) {
        Color(0xFF161820).copy(alpha = 0.85f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
    }

    val borderBrush = if (isDark) {
        ObsidianGlassBorderBrush
    } else {
        Brush.linearGradient(
            listOf(
                Color.Black.copy(alpha = 0.12f),
                Color.Black.copy(alpha = 0.04f)
            )
        )
    }

    val hazeStyle = remember(blurRadius, isDark) {
        HazeStyle(
            backgroundColor = backgroundColor,
            tint = HazeTint(tintColor),
            blurRadius = blurRadius,
            noiseFactor = 0.08f
        )
    }

    return this
        .clip(shape)
        .then(
            if (hazeState != null) {
                Modifier.hazeEffect(state = hazeState, style = hazeStyle)
            } else {
                Modifier.background(fallbackColor)
            }
        )
        .border(width = 1.dp, brush = borderBrush, shape = shape)
}

/**
 * Obsidian White Glass / Badge Effect (from Obsidian lightObsidianGlassEffect):
 * - backgroundColor: Color.White @ 15% opacity
 * - tint: Color.White @ 5% opacity
 * - blurRadius: 20.dp
 * - noiseFactor: 0.05f
 */
@Composable
fun Modifier.lightObsidianGlassEffect(
    hazeState: HazeState? = null,
    shape: Shape = RoundedCornerShape(24.dp),
    blurRadius: Dp = 20.dp,
): Modifier {
    val hazeStyle = remember(blurRadius) {
        HazeStyle(
            backgroundColor = Color.White.copy(alpha = 0.15f),
            tint = HazeTint(Color.White.copy(alpha = 0.05f)),
            blurRadius = blurRadius,
            noiseFactor = 0.05f
        )
    }

    return this
        .clip(shape)
        .then(
            if (hazeState != null) {
                Modifier.hazeEffect(state = hazeState, style = hazeStyle)
            } else {
                Modifier.background(Color.White.copy(alpha = 0.15f))
            }
        )
        .border(width = 1.dp, brush = ObsidianGlassBorderBrush, shape = shape)
}

val LocalHazeState = androidx.compose.runtime.compositionLocalOf<HazeState?> { null }
