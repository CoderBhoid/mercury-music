package com.metrolist.music.ui.component

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas

/**
 * Obsidian Signature AGSL Kawarp Shader.
 * 1:1 port from Obsidian Music APK (KawarpBackgroundKt.java).
 * Applies Simplex Noise domain-warping, 2.5x saturation, Rec.709 CSS filtering,
 * and seamless texture crossfading on Android 13+ devices.
 */
private const val KAWARP_FUSED_AGSL = """
uniform float2 uResolution;
uniform float uTime;
uniform float uBlend;
uniform float uIntensity;
uniform float uSaturation;
uniform float uDithering;
uniform float uScale;
uniform shader texCur;
uniform shader texNext;

const float BLUR_SIZE = 128.0;

float3 mod289v3(float3 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
float2 mod289v2(float2 x) { return x - floor(x * (1.0 / 289.0)) * 289.0; }
float3 permute(float3 x) { return mod289v3(((x * 34.0) + 1.0) * x); }

float snoise(float2 v) {
    const float4 C = float4(0.211324865405187, 0.366025403784439,
                            -0.577350269189626, 0.024390243902439);
    float2 i  = floor(v + dot(v, C.yy));
    float2 x0 = v - i + dot(i, C.xx);
    float2 i1 = (x0.x > x0.y) ? float2(1.0, 0.0) : float2(0.0, 1.0);
    float4 x12 = x0.xyxy + C.xxzz;
    x12.xy -= i1;
    i = mod289v2(i);
    float3 p = permute(permute(i.y + float3(0.0, i1.y, 1.0)) + i.x + float3(0.0, i1.x, 1.0));
    float3 m = max(0.5 - float3(dot(x0, x0), dot(x12.xy, x12.xy), dot(x12.zw, x12.zw)), 0.0);
    m = m * m; m = m * m;
    float3 x = 2.0 * fract(p * C.www) - 1.0;
    float3 h = abs(x) - 0.5;
    float3 ox = floor(x + 0.5);
    float3 a0 = x - ox;
    m *= 1.79284291400159 - 0.85373472095314 * (a0 * a0 + h * h);
    float3 g;
    g.x = a0.x * x0.x + h.x * x0.y;
    g.y = a0.y * x12.x + h.y * x12.y;
    g.z = a0.z * x12.z + h.z * x12.w;
    return 130.0 * dot(m, g);
}

float hash(float3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

half4 main(float2 fragCoord) {
    float2 vTexCoord = fragCoord / uResolution;

    float2 uv = clamp((vTexCoord - 0.5) / uScale + 0.5, 0.0, 1.0);

    // Ground-truth Obsidian Kawarp domain-warp: slower, majestic liquid stretch
    float t = uTime * 0.05;
    float2 center = uv - 0.5;
    float centerWeight = 1.0 - smoothstep(0.0, 0.7, length(center));
    float n1 = snoise(uv * 0.35 + float2(t, t * 0.7));
    float n2 = snoise(uv * 0.35 + float2(-t * 0.8, t * 0.5) + float2(50.0, 50.0));
    float n3 = snoise(uv * 0.9 + float2(t * 1.2, -t) + float2(100.0, 0.0));
    float n4 = snoise(uv * 0.9 + float2(-t, t * 1.1) + float2(0.0, 100.0));
    float2 warp = float2(n1 * 0.65 + n3 * 0.35, n2 * 0.65 + n4 * 0.35) * centerWeight;
    float2 warpedUV = clamp(uv + warp * uIntensity, 0.0, 1.0);

    float2 texPos = warpedUV * BLUR_SIZE;
    half4 c1 = texCur.eval(texPos);
    half4 c2 = texNext.eval(texPos);
    half4 color = mix(c1, c2, half(uBlend));

    float2 vc = vTexCoord - 0.5;
    float vignette = 1.0 - dot(vc, vc) * 0.3;
    color.rgb *= half(vignette);
    half gray = dot(color.rgb, half3(0.299, 0.587, 0.114));
    color.rgb = mix(half3(gray), color.rgb, half(uSaturation));
    float2 pixelPos = floor(vTexCoord * uResolution);
    float noise = hash(float3(pixelPos, floor(uTime * 60.0)));
    color.rgb += half3(half((noise - 0.5) * uDithering));
    color.rgb = clamp(color.rgb, half3(0.0), half3(1.0));

    // Ground-truth Obsidian CSS filter pass: saturate(2.5) brightness(0.65)
    half cssGray = dot(color.rgb, half3(0.213, 0.715, 0.072));
    color.rgb = mix(half3(cssGray), color.rgb, half(2.5));
    color.rgb = clamp(color.rgb, half3(0.0), half3(1.0));
    color.rgb *= half(0.65);

    return color;
}
"""

/**
 * Authentic Obsidian Dynamic Background Renderer for all Android platforms.
 * Scales artwork, clips to circular bounds, applies high-saturation ColorMatrix (2.5x),
 * and rotates orbital canvas circles with smooth crossfading.
 */
class DynamicBackgroundRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clipPath = Path()
    private val dstRect = RectF()
    private var currentSourceId: String? = null
    var blurredTexture: Bitmap? = null
        private set
    var previousTexture: Bitmap? = null
        private set

    init {
        val satMatrix = ColorMatrix().apply { setSaturation(2.5f) }
        val brightMatrix = ColorMatrix(
            floatArrayOf(
                0.65f, 0f, 0f, 0f, 0f,
                0f, 0.65f, 0f, 0f, 0f,
                0f, 0f, 0.65f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        brightMatrix.preConcat(satMatrix)
        paint.colorFilter = ColorMatrixColorFilter(brightMatrix)
    }

    fun setImage(source: Bitmap?, sourceId: String, blurIntensity: Int = 60) {
        if (sourceId == currentSourceId && blurredTexture != null) return
        currentSourceId = sourceId

        try {
            val baseBitmap = source ?: createProceduralFallbackBitmap()
            val scaled = Bitmap.createScaledBitmap(baseBitmap, 128, 128, true)
            val circular = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(circular)
            val path = Path()
            val center = 128f / 2f
            path.addCircle(center, center, center, Path.Direction.CW)
            canvas.clipPath(path)
            canvas.drawBitmap(scaled, 0f, 0f, null)

            val blurRadius = (((blurIntensity * 45 / 100f).toInt().coerceAtLeast(1) * 128) / 640f).toInt().coerceAtLeast(1)
            val pad = (blurRadius * 3 * 1.5f).toInt()
            val paddedSize = pad + 128
            val paddedBitmap = Bitmap.createBitmap(paddedSize, paddedSize, Bitmap.Config.ARGB_8888)
            val padHalf = pad / 2f
            Canvas(paddedBitmap).drawBitmap(circular, padHalf, padHalf, null)

            val blurred = StackBlur.blur(paddedBitmap, blurRadius, 3)
            val scaledTexture = Bitmap.createScaledBitmap(blurred, 128, 128, true)
            previousTexture = blurredTexture
            blurredTexture = scaledTexture

            if (blurred != scaledTexture) blurred.recycle()
            paddedBitmap.recycle()
            if (scaled != baseBitmap) scaled.recycle()
            if (source == null) baseBitmap.recycle()
            circular.recycle()
        } catch (_: Exception) {
            // Guard against OOM
        }
    }

    private fun createProceduralFallbackBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
        val cvs = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(
            0f, 0f, 128f, 128f,
            intArrayOf(0xFF7B1FA2.toInt(), 0xFF00B0FF.toInt(), 0xFFFF4081.toInt(), 0xFF651FFF.toInt()),
            null,
            android.graphics.Shader.TileMode.CLAMP
        )
        cvs.drawRect(0f, 0f, 128f, 128f, p)
        return bmp
    }

    fun draw(
        nativeCanvas: Canvas,
        width: Float,
        height: Float,
        rotationAngle: Float,
        transitionProgress: Float,
        beatScale: Float = 1.0f
    ) {
        val cur = blurredTexture
        val prev = previousTexture
        if (cur == null && prev == null) return

        val maxDim = maxOf(width, height) * beatScale
        val isLandscape = width > height
        val halfW = width / 2f
        val halfH = height / 2f

        if (transitionProgress < 1f && prev != null) {
            drawCircles(nativeCanvas, halfW, halfH, maxDim, rotationAngle, isLandscape, height, width, prev, 1f - transitionProgress)
        }
        if (cur != null) {
            drawCircles(nativeCanvas, halfW, halfH, maxDim, rotationAngle, isLandscape, height, width, cur, transitionProgress)
        }
    }

    private fun drawCircles(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        maxDim: Float,
        angle: Float,
        isLandscape: Boolean,
        h: Float,
        w: Float,
        texture: Bitmap,
        progress: Float
    ) {
        // Ground-truth Obsidian circle placement: 4 broad smooth orbital circles
        drawCircle(canvas, texture, cx, cy, maxDim * 1.5f, Math.toDegrees((-0.25f * angle).toDouble()).toFloat(), progress * 1.0f)
        drawCircle(canvas, texture, cx, cy, maxDim * (if (isLandscape) 1.0f else 0.75f), Math.toDegrees((0.5f * angle).toDouble()).toFloat(), progress * 0.75f)
        val halfProg = progress * 0.5f
        drawCircle(canvas, texture, 0f, h, maxDim * 0.75f, Math.toDegrees((1.0f * angle).toDouble()).toFloat(), halfProg)
        drawCircle(canvas, texture, w, 0f, maxDim * (if (isLandscape) 0.65f else 0.5f), Math.toDegrees((-0.75f * angle).toDouble()).toFloat(), halfProg)
    }

    private fun drawCircle(canvas: Canvas, texture: Bitmap, cx: Float, cy: Float, radius: Float, rotationDegrees: Float, alpha: Float) {
        canvas.save()
        clipPath.reset()
        clipPath.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clipPath)
        canvas.rotate(rotationDegrees, cx, cy)
        dstRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(texture, null, dstRect, paint)
        canvas.restore()
    }

    fun pruneSettledTexture() {
        val prev = previousTexture
        if (prev != null && prev != blurredTexture) {
            prev.recycle()
            previousTexture = null
        }
    }

    fun hasTexture(): Boolean = blurredTexture != null

    fun release() {
        blurredTexture?.recycle()
        blurredTexture = null
        previousTexture?.recycle()
        previousTexture = null
    }
}

@Composable
fun DynamicBackgroundView(
    artworkBitmap: Bitmap?,
    modifier: Modifier = Modifier,
    blurIntensity: Int = 60,
    animate: Boolean = true,
) {
    val renderer = remember {
        DynamicBackgroundRenderer().apply {
            setImage(artworkBitmap, artworkBitmap?.hashCode()?.toString() ?: "fallback_procedural", blurIntensity)
        }
    }
    val transitionAnim = remember { Animatable(1f) }

    DisposableEffect(renderer) {
        onDispose {
            renderer.release()
        }
    }

    LaunchedEffect(artworkBitmap) {
        val source = artworkBitmap
        val sourceId = source?.hashCode()?.toString() ?: "fallback_procedural"
        withContext(Dispatchers.Default) {
            renderer.setImage(source, sourceId, blurIntensity)
        }
        transitionAnim.snapTo(0f)
        transitionAnim.animateTo(1f, tween(1000, easing = LinearEasing))
        renderer.pruneSettledTexture()
    }

    // Audio-reactive beat-synchronized animation loop
    var accumulatedTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (animate) {
            var lastTimeNanos = withFrameNanos { it }
            while (isActive) {
                withFrameNanos { frameTimeNanos ->
                    val dt = ((frameTimeNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    lastTimeNanos = frameTimeNanos
                    val beat = com.metrolist.music.playback.audio.AudioBeatTracker.beatEnergy
                    // Base speed is relaxed and smooth; surges up to 3.2x dynamically on drum kicks / bass drops
                    val speedMultiplier = 1.0f + beat * 2.2f
                    val delta = dt * 8.0f * speedMultiplier
                    accumulatedTime += delta
                }
            }
        }
    }

    // Check AGSL RuntimeShader availability on Android 13+ (API 33+)
    val agslShader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                RuntimeShader(KAWARP_FUSED_AGSL)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    val shaderPaint = remember {
        Paint().apply {
            isAntiAlias = true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ComposeCanvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Deep obsidian backdrop base
            drawRect(color = Color(0xFF07080C))

            val curTex = renderer.blurredTexture
            val prevTex = renderer.previousTexture
            val orbitalAngle = (accumulatedTime * 0.012f) % (2f * Math.PI.toFloat())
            val beatEnergy = com.metrolist.music.playback.audio.AudioBeatTracker.beatEnergy
            val beatScale = 1.0f + beatEnergy * 0.06f

            if (agslShader != null && curTex != null) {
                // High-performance AGSL Simplex Noise Domain-Warping Canvas
                try {
                    val shaderCur = BitmapShader(curTex, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    val shaderNext = if (prevTex != null) {
                        BitmapShader(prevTex, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                    } else {
                        shaderCur
                    }

                    agslShader.setFloatUniform("uResolution", width, height)
                    agslShader.setFloatUniform("uTime", accumulatedTime)
                    agslShader.setFloatUniform("uBlend", 1f - transitionAnim.value)
                    // Dynamic warp intensity pulses subtly with beat transients
                    agslShader.setFloatUniform("uIntensity", 1.35f + beatEnergy * 0.35f)
                    agslShader.setFloatUniform("uSaturation", 1.8f)
                    agslShader.setFloatUniform("uDithering", 0.008f)
                    agslShader.setFloatUniform("uScale", 1.0f)
                    agslShader.setInputShader("texCur", shaderCur)
                    agslShader.setInputShader("texNext", shaderNext)

                    shaderPaint.shader = agslShader
                    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, width, height, shaderPaint)
                } catch (_: Exception) {
                    // Fallback to orbital canvas if AGSL fails
                    renderer.draw(
                        drawContext.canvas.nativeCanvas,
                        width,
                        height,
                        if (animate) orbitalAngle else 0f,
                        transitionAnim.value,
                        beatScale
                    )
                }
            } else if (renderer.hasTexture()) {
                renderer.draw(
                    drawContext.canvas.nativeCanvas,
                    width,
                    height,
                    if (animate) orbitalAngle else 0f,
                    transitionAnim.value,
                    beatScale
                )
            }

            // Subtle, light bottom dissolve so controls remain legible without dimming the vibrant canvas
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0xFF07080C).copy(alpha = 0.15f),
                        Color(0xFF07080C).copy(alpha = 0.35f)
                    )
                )
            )
        }
    }
}
