/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Global audio beat & envelope tracking state.
 * [beatEnergy] ranges from 0.0f (silence/calm) to 1.0f (peak drum kick/bass transient).
 */
object AudioBeatTracker {
    @Volatile
    var beatEnergy: Float = 0f
        internal set

    fun reset() {
        beatEnergy = 0f
    }
}

/**
 * Zero-permission, real-time PCM envelope and beat transient detector for ExoPlayer.
 * Inserts directly into [DefaultAudioProcessorChain], reading decoded audio PCM buffers
 * before hardware output without microphone access.
 */
@UnstableApi
class BeatDetectorAudioProcessor : BaseAudioProcessor() {
    private var encoding = C.ENCODING_INVALID
    private var channelCount = 0

    @Volatile
    private var smoothedEnergy = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        channelCount = inputAudioFormat.channelCount
        if (encoding != C.ENCODING_PCM_16BIT &&
            encoding != C.ENCODING_PCM_24BIT &&
            encoding != C.ENCODING_PCM_32BIT &&
            encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val remaining = inputBuffer.remaining()
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)

        val bytesPerFrame = channelCount * when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            else -> 4
        }

        if (bytesPerFrame > 0 && channelCount > 0) {
            val totalFrames = remaining / bytesPerFrame
            val step = maxOf(1, totalFrames / 64)
            var sumSquare = 0.0
            var count = 0
            val basePos = inputBuffer.position()

            var frame = 0
            while (frame < totalFrames) {
                val samplePos = basePos + frame * bytesPerFrame
                if (samplePos + 2 <= inputBuffer.limit()) {
                    val rawSample = when (encoding) {
                        C.ENCODING_PCM_16BIT -> abs(inputBuffer.getShort(samplePos).toInt()) / 32768.0
                        C.ENCODING_PCM_FLOAT -> abs(inputBuffer.getFloat(samplePos).toDouble()).coerceIn(0.0, 1.0)
                        else -> abs(inputBuffer.getShort(samplePos).toInt()) / 32768.0
                    }
                    sumSquare += rawSample * rawSample
                    count++
                }
                frame += step
            }

            val rms = if (count > 0) sqrt(sumSquare / count).toFloat().coerceIn(0f, 1f) else 0f

            // Fast attack on beat impact (80% weight), exponential release (~180ms decay)
            smoothedEnergy = if (rms > smoothedEnergy) {
                smoothedEnergy * 0.15f + rms * 0.85f
            } else {
                smoothedEnergy * 0.88f
            }

            AudioBeatTracker.beatEnergy = (smoothedEnergy * 1.6f).coerceIn(0f, 1f)
        }

        replaceOutputBuffer(remaining).apply {
            put(inputBuffer)
            flip()
        }
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        smoothedEnergy = 0f
        AudioBeatTracker.reset()
    }

    override fun onReset() {
        smoothedEnergy = 0f
        AudioBeatTracker.reset()
    }
}
