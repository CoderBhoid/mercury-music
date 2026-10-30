package com.metrolist.music.ui.component

import android.graphics.Bitmap

/**
 * High-performance pure-Kotlin StackBlur implementation ported directly from Obsidian Music APK.
 * Operates efficiently over pixel buffers for fast rendering of blurred ambient textures.
 */
object StackBlur {
    fun blur(source: Bitmap, radius: Int, iterations: Int = 3): Bitmap {
        if (radius <= 0) {
            return source.copy(Bitmap.Config.ARGB_8888, true)
        }

        val width = source.getWidth()
        val height = source.getHeight()
        val numPixels = width * height
        val pixelsIn = IntArray(numPixels)
        val resultBitmap = source.copy(Bitmap.Config.ARGB_8888, true)
        resultBitmap.getPixels(pixelsIn, 0, width, 0, 0, width, height)

        val pixelsOut = IntArray(numPixels)
        var currentIn = pixelsIn
        var currentOut = pixelsOut

        for (it in 0 until iterations) {
            // Horizontal pass
            for (y in 0 until height) {
                val rowOffset = y * width
                val windowSize = (radius * 2) + 1
                var sumA = 0L
                var sumR = 0L
                var sumG = 0L
                var sumB = 0L

                for (dx in -radius..radius) {
                    val px = currentIn[rowOffset + dx.coerceIn(0, width - 1)]
                    sumA += (px ushr 24) and 255
                    sumR += (px shr 16) and 255
                    sumG += (px shr 8) and 255
                    sumB += px and 255
                }

                for (x in 0 until width) {
                    val a = (sumA / windowSize).toInt().coerceIn(0, 255)
                    val r = (sumR / windowSize).toInt().coerceIn(0, 255)
                    val g = (sumG / windowSize).toInt().coerceIn(0, 255)
                    val b = (sumB / windowSize).toInt().coerceIn(0, 255)
                    currentOut[rowOffset + x] = (a shl 24) or (r shl 16) or (g shl 8) or b

                    val leftPx = currentIn[rowOffset + (x - radius).coerceIn(0, width - 1)]
                    val rightPx = currentIn[rowOffset + (x + radius + 1).coerceIn(0, width - 1)]

                    sumA = sumA - ((leftPx ushr 24) and 255) + ((rightPx ushr 24) and 255)
                    sumR = sumR - ((leftPx shr 16) and 255) + ((rightPx shr 16) and 255)
                    sumG = sumG - ((leftPx shr 8) and 255) + ((rightPx shr 8) and 255)
                    sumB = sumB - (leftPx and 255) + (rightPx and 255)
                }
            }

            // Vertical pass
            for (x in 0 until width) {
                val windowSize = (radius * 2) + 1
                var sumA = 0L
                var sumR = 0L
                var sumG = 0L
                var sumB = 0L

                for (dy in -radius..radius) {
                    val px = currentOut[dy.coerceIn(0, height - 1) * width + x]
                    sumA += (px ushr 24) and 255
                    sumR += (px shr 16) and 255
                    sumG += (px shr 8) and 255
                    sumB += px and 255
                }

                for (y in 0 until height) {
                    val a = (sumA / windowSize).toInt().coerceIn(0, 255)
                    val r = (sumR / windowSize).toInt().coerceIn(0, 255)
                    val g = (sumG / windowSize).toInt().coerceIn(0, 255)
                    val b = (sumB / windowSize).toInt().coerceIn(0, 255)
                    currentIn[y * width + x] = (a shl 24) or (r shl 16) or (g shl 8) or b

                    val topPx = currentOut[(y - radius).coerceIn(0, height - 1) * width + x]
                    val bottomPx = currentOut[(y + radius + 1).coerceIn(0, height - 1) * width + x]

                    sumA = sumA - ((topPx ushr 24) and 255) + ((bottomPx ushr 24) and 255)
                    sumR = sumR - ((topPx shr 16) and 255) + ((bottomPx shr 16) and 255)
                    sumG = sumG - ((topPx shr 8) and 255) + ((bottomPx shr 8) and 255)
                    sumB = sumB - (topPx and 255) + (bottomPx and 255)
                }
            }
        }

        resultBitmap.setPixels(currentIn, 0, width, 0, 0, width, height)
        return resultBitmap
    }
}
