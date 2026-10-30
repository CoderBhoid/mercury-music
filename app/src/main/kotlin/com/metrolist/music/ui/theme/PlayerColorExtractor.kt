/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

object PlayerColorExtractor {
    fun extractGradientColors(
        palette: Palette,
        fallbackColor: Int,
    ): List<Color> {
        val ranked = palette.rankedColors(2, fallbackColor)
        val dominant = palette.getDominantColor(fallbackColor)
        val vibrant = palette.getVibrantColor(dominant)
        val darkVibrant = palette.getDarkVibrantColor(palette.getDarkMutedColor(android.graphics.Color.DKGRAY))

        val topColor = Color(if (ranked.isNotEmpty()) ranked[0] else vibrant)
        val middleColor = if (ranked.size > 1) Color(ranked[1]) else Color(darkVibrant)
        val bottomColor = Color.Black

        return listOf(topColor, middleColor, bottomColor)
    }
}
