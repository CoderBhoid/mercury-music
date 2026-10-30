package com.metrolist.music.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp


/**
 * Obsidian Apple-Style Immersive Scrubbing Slider.
 * Features an ultra-clean resting progress bar that dynamically springs
 * into an expanded tactile scrub bar with specular thumb and ambient glow on touch.
 */
@Composable
fun MercuryPlayerSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    enabled: Boolean = true,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.20f),
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(value) }
    val hapticFeedback = LocalHapticFeedback.current

    val trackSpring = spring<androidx.compose.ui.unit.Dp>(dampingRatio = 0.5f, stiffness = 200f)
    val floatSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 200f)

    val animatedTrackHeight by animateDpAsState(
        targetValue = if (isDragging) 8.dp else 4.dp,
        animationSpec = trackSpring,
        label = "trackHeight"
    )

    val animatedThumbRadius by animateDpAsState(
        targetValue = if (isDragging) 8.5.dp else 0.dp,
        animationSpec = trackSpring,
        label = "thumbRadius"
    )

    val animatedGlowAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0.75f else 0.3f,
        animationSpec = floatSpring,
        label = "glowAlpha"
    )

    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0f)
    val displayValue = if (isDragging) dragProgress else value
    val progressFraction = if (rangeSpan > 0f) {
        ((displayValue - valueRange.start) / rangeSpan).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .then(
                if (enabled) {
                    Modifier
                        .pointerInput(enabled, valueRange) {
                            detectTapGestures { offset ->
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                val newValue = valueRange.start + fraction * rangeSpan
                                onValueChange(newValue)
                                onValueChangeFinished?.invoke()
                            }
                        }
                        .pointerInput(enabled, valueRange) {
                            var lastHapticFraction = -1f
                            detectDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                    val newValue = valueRange.start + fraction * rangeSpan
                                    dragProgress = newValue
                                    lastHapticFraction = fraction
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onValueChange(newValue)
                                },
                                onDragEnd = {
                                    isDragging = false
                                    onValueChangeFinished?.invoke()
                                },
                                onDragCancel = {
                                    isDragging = false
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                    val newValue = valueRange.start + fraction * rangeSpan
                                    dragProgress = newValue
                                    if (kotlin.math.abs(fraction - lastHapticFraction) >= 0.015f) {
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        lastHapticFraction = fraction
                                    }
                                    onValueChange(newValue)
                                }
                            )
                        }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        val paint = remember {
            android.graphics.Paint().apply {
                isAntiAlias = true
            }
        }

        val animatedBlurRadius by animateDpAsState(
            targetValue = if (isDragging) 22.dp else 10.dp,
            animationSpec = trackSpring,
            label = "blurRadius"
        )

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            val width = size.width
            val centerY = size.height / 2f
            val f5 = animatedTrackHeight.toPx()
            val thumbRadiusPx = animatedThumbRadius.toPx()
            val blurRadiusPx = animatedBlurRadius.toPx().coerceAtLeast(1f)
            val glowAlpha = animatedGlowAlpha
            val f6 = 12.dp.toPx() / 2f
            val f7 = (((width - f6 - f6) * progressFraction) + f6).coerceIn(f6, width - f6)

            // Inactive track
            drawLine(
                color = inactiveColor,
                start = Offset(f6, centerY),
                end = Offset(width - f6, centerY),
                strokeWidth = f5,
                cap = StrokeCap.Round
            )

            // Active track with boosted 4-pass Bloom glow
            if (f7 > f6) {
                val nativeCanvas = drawContext.canvas.nativeCanvas

                // Pass 1: Wide Ambient Aura Bloom
                paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SCREEN)
                paint.strokeWidth = 5.0f * f5
                paint.color = activeColor.copy(alpha = glowAlpha * 0.40f).toArgb()
                paint.maskFilter = android.graphics.BlurMaskFilter(3.0f * blurRadiusPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
                nativeCanvas.drawLine(f6, centerY, f7, centerY, paint)

                // Pass 2: Vibrant Mid-Range Bloom
                paint.strokeWidth = 3.2f * f5
                paint.color = activeColor.copy(alpha = glowAlpha * 0.75f).toArgb()
                paint.maskFilter = android.graphics.BlurMaskFilter(1.6f * blurRadiusPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
                nativeCanvas.drawLine(f6, centerY, f7, centerY, paint)

                // Pass 3: Intense Core Bloom
                paint.strokeWidth = 1.8f * f5
                paint.color = activeColor.copy(alpha = if (isDragging) 1.0f else glowAlpha * 0.95f).toArgb()
                paint.maskFilter = android.graphics.BlurMaskFilter(0.6f * blurRadiusPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
                nativeCanvas.drawLine(f6, centerY, f7, centerY, paint)

                // Pass 4: Radiant Thumb Halo Bloom
                if (thumbRadiusPx > 0.5f) {
                    paint.style = android.graphics.Paint.Style.FILL
                    paint.color = activeColor.copy(alpha = glowAlpha * 0.85f).toArgb()
                    paint.maskFilter = android.graphics.BlurMaskFilter(1.8f * blurRadiusPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
                    nativeCanvas.drawCircle(f7, centerY, thumbRadiusPx * 2.6f, paint)
                }

                paint.xfermode = null
                paint.maskFilter = null

                // Crisp active line on top
                drawLine(
                    color = activeColor,
                    start = Offset(f6, centerY),
                    end = Offset(f7, centerY),
                    strokeWidth = f5,
                    cap = StrokeCap.Round
                )
            }

            // Specular thumb with drop shadow
            if (thumbRadiusPx > 0.5f) {
                val nativeCanvas = drawContext.canvas.nativeCanvas
                paint.style = android.graphics.Paint.Style.FILL
                paint.color = Color.Black.copy(alpha = 0.5f).toArgb()
                paint.maskFilter = android.graphics.BlurMaskFilter(10f, android.graphics.BlurMaskFilter.Blur.NORMAL)
                nativeCanvas.drawCircle(f7, centerY + (thumbRadiusPx * 0.35f), thumbRadiusPx * 1.3f, paint)
                paint.maskFilter = null

                // Pure specular white thumb core
                drawCircle(
                    color = Color.White,
                    radius = thumbRadiusPx,
                    center = Offset(f7, centerY)
                )
                // Specular micro-ring
                drawCircle(
                    color = Color.Black.copy(alpha = 0.12f),
                    radius = thumbRadiusPx * 0.35f,
                    center = Offset(f7, centerY)
                )
            }
        }
    }
}

/**
 * Specialized Obsidian Audio Quality & Format Badge.
 * Features tailored visual treatments for:
 * - Dolby Atmos (cosmic spatial neon gradient + pulsing ambient halo)
 * - Hi-Res FLAC (luxury amber/gold metallic badge with star icon)
 * - OPUS 48k Data Saver (emerald/mint eco badge with waveform icon)
 * - Standard OPUS/AAC (sleek obsidian glass capsule)
 */
@Composable
fun ObsidianAudioBadge(
    formatText: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val hazeState = LocalHazeState.current
    val badgeShape = RoundedCornerShape(10.dp)

    val isDolby = remember(formatText) {
        formatText.contains("Dolby", ignoreCase = true) || formatText.contains("Atmos", ignoreCase = true)
    }
    val isHiRes = remember(formatText) {
        formatText.contains("FLAC", ignoreCase = true) || formatText.contains("24", ignoreCase = true)
    }
    val isDataSaver = remember(formatText) {
        formatText.contains("48", ignoreCase = true) || formatText.contains("Saver", ignoreCase = true)
    }

    val pulseAnim = if (isDolby) {
        val infiniteTransition = rememberInfiniteTransition(label = "dolbyPulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.90f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dolbyGlow"
        )
        alpha
    } else {
        1.0f
    }

    val containerModifier = when {
        isDolby -> {
            modifier
                .clip(badgeShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF0F172A).copy(alpha = 0.85f),
                            Color(0xFF1E1B4B).copy(alpha = 0.85f),
                            Color(0xFF2E1065).copy(alpha = 0.85f)
                        )
                    )
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFF00F0FF).copy(alpha = pulseAnim),
                            Color(0xFF8B5CF6).copy(alpha = pulseAnim),
                            Color(0xFFFF007F).copy(alpha = pulseAnim)
                        )
                    ),
                    shape = badgeShape
                )
        }
        isHiRes -> {
            modifier
                .clip(badgeShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1C1917).copy(alpha = 0.85f),
                            Color(0xFF292524).copy(alpha = 0.85f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color(0xFFFFD700).copy(alpha = 0.8f),
                            Color(0xFFF59E0B).copy(alpha = 0.8f)
                        )
                    ),
                    shape = badgeShape
                )
        }
        isDataSaver -> {
            modifier
                .clip(badgeShape)
                .background(Color(0xFF064E3B).copy(alpha = 0.45f))
                .border(
                    width = 1.dp,
                    color = Color(0xFF10B981).copy(alpha = 0.7f),
                    shape = badgeShape
                )
        }
        else -> {
            modifier
                .lightObsidianGlassEffect(
                    hazeState = hazeState,
                    shape = badgeShape
                )
        }
    }

    Box(
        modifier = containerModifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Icon indicator
            when {
                isDolby -> {
                    // Dolby spatial audio indicator
                    Canvas(modifier = Modifier.size(12.dp)) {
                        val stroke = 1.5.dp.toPx()
                        val w = size.width
                        val h = size.height
                        // Left 'D'
                        drawArc(
                            color = Color(0xFF00F0FF),
                            startAngle = 90f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                        )
                        // Right 'D'
                        drawArc(
                            color = Color(0xFFFF007F),
                            startAngle = 270f,
                            sweepAngle = 180f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                        )
                    }
                }
                isHiRes -> {
                    // Gold diamond indicator
                    Canvas(modifier = Modifier.size(10.dp)) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(size.width / 2f, 0f)
                            lineTo(size.width, size.height / 2f)
                            lineTo(size.width / 2f, size.height)
                            lineTo(0f, size.height / 2f)
                            close()
                        }
                        drawPath(path = path, color = Color(0xFFFFD700))
                    }
                }
                isDataSaver -> {
                    // Emerald audio frequency wave indicator
                    Canvas(modifier = Modifier.size(11.dp)) {
                        val stroke = 1.5.dp.toPx()
                        val h = size.height
                        drawLine(Color(0xFF10B981), Offset(2.dp.toPx(), h * 0.3f), Offset(2.dp.toPx(), h * 0.7f), strokeWidth = stroke, cap = StrokeCap.Round)
                        drawLine(Color(0xFF10B981), Offset(5.5.dp.toPx(), h * 0.1f), Offset(5.5.dp.toPx(), h * 0.9f), strokeWidth = stroke, cap = StrokeCap.Round)
                        drawLine(Color(0xFF10B981), Offset(9.dp.toPx(), h * 0.35f), Offset(9.dp.toPx(), h * 0.65f), strokeWidth = stroke, cap = StrokeCap.Round)
                    }
                }
            }

            val displayText = when {
                isDolby -> if (formatText.contains("True", ignoreCase = true)) "TRUE DOLBY ATMOS" else "DOLBY ATMOS"
                isHiRes -> if (formatText.contains("24", ignoreCase = true)) "FLAC 24-BIT • 96kHz" else "FLAC LOSSLESS"
                isDataSaver -> "OPUS 48k • DATA SAVER"
                else -> formatText
            }

            val textColor = when {
                isDolby -> Color.White
                isHiRes -> Color(0xFFFFE082)
                isDataSaver -> Color(0xFF6EE7B7)
                else -> Color.White.copy(alpha = 0.95f)
            }

            Text(
                text = displayText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    letterSpacing = 0.6.sp,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}
