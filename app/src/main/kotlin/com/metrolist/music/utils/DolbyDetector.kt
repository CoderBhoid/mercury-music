package com.metrolist.music.utils

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaCodecList
import android.media.Spatializer
import android.media.audiofx.AudioEffect
import android.os.Build
import android.util.Log
import androidx.media3.common.MimeTypes

data class DolbyHardwareReport(
    val isSupported: Boolean,
    val deviceName: String,
    val matchedFeatures: List<String>,
    val details: String,
)

/**
 * Hardware Dolby & Spatial Audio Detector ported 1:1 from Obsidian Music APK.
 * Detects hardware decoders (E-AC-3, E-AC-3 JOC Atmos, AC-4), OEM DSP modules,
 * and Android 13+ Spatializer capabilities.
 */
object DolbyDetector {
    private const val TAG = "DolbyDetector"

    fun checkHardwareSupport(context: Context): DolbyHardwareReport {
        val matchedFeatures = mutableListOf<String>()
        val deviceName = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

        // 1. Scan MediaCodecList for Dolby & Atmos hardware decoders
        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in codecList.codecInfos) {
                if (!info.isEncoder) {
                    for (type in info.supportedTypes) {
                        if (type.equals(MimeTypes.AUDIO_E_AC3, ignoreCase = true) &&
                            !matchedFeatures.contains("Dolby Digital Plus (E-AC-3) Decoder")
                        ) {
                            matchedFeatures.add("Dolby Digital Plus (E-AC-3) Decoder")
                        }
                        if (type.equals(MimeTypes.AUDIO_E_AC3_JOC, ignoreCase = true) &&
                            !matchedFeatures.contains("Dolby Atmos (E-AC-3 JOC) Decoder")
                        ) {
                            matchedFeatures.add("Dolby Atmos (E-AC-3 JOC) Decoder")
                        }
                        if (type.equals(MimeTypes.AUDIO_AC4, ignoreCase = true) &&
                            !matchedFeatures.contains("Dolby AC-4 Immersive Decoder")
                        ) {
                            matchedFeatures.add("Dolby AC-4 Immersive Decoder")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking MediaCodecList: ${e.message}")
        }

        // 2. Query system audio effects for Dolby / Atmos hardware DSP
        try {
            val effects = AudioEffect.queryEffects()
            if (effects != null) {
                for (descriptor in effects) {
                    val name = descriptor.name.orEmpty()
                    val implementor = descriptor.implementor.orEmpty()
                    if (name.contains("Dolby", ignoreCase = true) ||
                        name.contains("Atmos", ignoreCase = true) ||
                        implementor.contains("Dolby", ignoreCase = true)
                    ) {
                        val effectName = "Hardware Dolby DSP ($name)"
                        if (!matchedFeatures.contains(effectName)) {
                            matchedFeatures.add(effectName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying audio effects: ${e.message}")
        }

        // 3. Android 13+ Hardware Spatializer
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val spatializer = audioManager?.spatializer
                if (spatializer != null && spatializer.immersiveAudioLevel != 0) {
                    matchedFeatures.add("Android ${Build.VERSION.RELEASE} Hardware Spatializer")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying Spatializer: ${e.message}")
        }

        // 4. OEM Dolby System Modules (Samsung, Xiaomi, OnePlus, Motorola, etc.)
        try {
            val pm = context.packageManager
            val oemPackages = listOf(
                "com.samsung.android.soundalive" to "Samsung SoundAlive Dolby Engine",
                "com.dolby.daxappui" to "Dolby Atmos Audio Service",
                "com.dolby.daxservice" to "Dolby DAX Hardware Service",
                "com.dolby.atmos" to "Dolby Atmos System Module",
                "com.dolby.audio" to "Dolby Audio Hardware Driver",
            )
            for ((pkg, label) in oemPackages) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    if (!matchedFeatures.contains(label)) {
                        matchedFeatures.add(label)
                    }
                } catch (_: PackageManager.NameNotFoundException) {
                    // Not present on this OEM
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking OEM Dolby packages: ${e.message}")
        }

        val isSupported = matchedFeatures.isNotEmpty()
        val details = if (isSupported) {
            "Hardware Dolby Atmos and Spatial Audio capabilities active on $deviceName."
        } else {
            "Standard high-fidelity stereo output. No proprietary OEM Dolby hardware DSP detected."
        }

        return DolbyHardwareReport(
            isSupported = isSupported,
            deviceName = deviceName,
            matchedFeatures = matchedFeatures,
            details = details,
        )
    }

    fun isHardwareDolbySupported(context: Context): Boolean = checkHardwareSupport(context).isSupported
}
