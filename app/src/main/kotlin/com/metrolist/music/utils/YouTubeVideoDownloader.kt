/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.metrolist.innertube.YouTube
import com.metrolist.innertubex.models.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer

object YouTubeVideoDownloader {

    data class VideoFormatOption(
        val itag: Int,
        val qualityLabel: String,
        val width: Int?,
        val height: Int?,
        val fps: Int?,
        val videoUrl: String,
        val videoMimeType: String,
        val audioUrl: String?,
        val audioMimeType: String?,
        val isAdaptive: Boolean,
        val approximateSizeBytes: Long,
    )

    data class YouTubeVideoInfo(
        val videoId: String,
        val title: String,
        val author: String,
        val durationSeconds: Long,
        val thumbnailUrl: String,
        val formats: List<VideoFormatOption>,
    )

    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val shortMatch = Regex("(?:youtu\\.be/|youtube\\.com/(?:shorts/|embed/|v/))([a-zA-Z0-9_-]{11})").find(trimmed)
        if (shortMatch != null) return shortMatch.groupValues[1]

        val watchMatch = Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(trimmed)
        if (watchMatch != null) return watchMatch.groupValues[1]

        if (trimmed.length == 11 && trimmed.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
            return trimmed
        }

        return null
    }

    suspend fun fetchVideoDetails(videoId: String): Result<YouTubeVideoInfo> = withContext(Dispatchers.IO) {
        runCatching {
            var playerResponse = YouTube.player(videoId = videoId, client = YouTubeClient.WEB).getOrNull()

            if (playerResponse?.streamingData?.formats.isNullOrEmpty() &&
                playerResponse?.streamingData?.adaptiveFormats.isNullOrEmpty()
            ) {
                playerResponse = YouTube.player(videoId = videoId, client = YouTubeClient.ANDROID).getOrNull()
            }

            val streamingData = playerResponse?.streamingData
                ?: throw IllegalStateException("Could not retrieve streaming data for video")

            val videoDetails = playerResponse.videoDetails
                ?: throw IllegalStateException("Could not retrieve video details")

            val durationSeconds = videoDetails.lengthSeconds.toLongOrNull() ?: 0L
            val title = videoDetails.title ?: "YouTube Video"
            val author = videoDetails.author ?: "Unknown Artist"
            val thumbnail = videoDetails.thumbnail.thumbnails.lastOrNull()?.url.orEmpty()

            // Find best audio stream for muxing with adaptive video
            val audioFormats = streamingData.adaptiveFormats.filter { it.isAudio && !it.url.isNullOrBlank() }
            val bestAudio = audioFormats.maxByOrNull { it.bitrate }

            val formatOptions = mutableListOf<VideoFormatOption>()

            // Progressive formats (contain both video and audio, e.g. 720p/360p)
            streamingData.formats?.forEach { format ->
                if (!format.isAudio && !format.url.isNullOrBlank()) {
                    val label = format.qualityLabel ?: "${format.height ?: 360}p"
                    val size = format.contentLength ?: ((format.bitrate.toLong() * durationSeconds) / 8L)
                    formatOptions.add(
                        VideoFormatOption(
                            itag = format.itag,
                            qualityLabel = label,
                            width = format.width,
                            height = format.height,
                            fps = format.fps,
                            videoUrl = format.url!!,
                            videoMimeType = format.mimeType,
                            audioUrl = null,
                            audioMimeType = null,
                            isAdaptive = false,
                            approximateSizeBytes = size,
                        )
                    )
                }
            }

            // Adaptive formats (up to 4K / 2160p, 1440p, 1080p, etc.)
            if (bestAudio?.url != null) {
                val adaptiveVideo = streamingData.adaptiveFormats
                    .filter { !it.isAudio && !it.url.isNullOrBlank() }
                    .groupBy { it.qualityLabel ?: "${it.height ?: 0}p" }

                adaptiveVideo.forEach { (label, group) ->
                    val bestInGroup = group.maxByOrNull { it.bitrate } ?: return@forEach
                    val videoSize = bestInGroup.contentLength ?: ((bestInGroup.bitrate.toLong() * durationSeconds) / 8L)
                    val audioSize = bestAudio.contentLength ?: ((bestAudio.bitrate.toLong() * durationSeconds) / 8L)

                    // Avoid duplicate labels if progressive already has identical resolution
                    val existing = formatOptions.find { it.qualityLabel == label }
                    if (existing == null || (bestInGroup.height ?: 0) > 720) {
                        if (existing != null) formatOptions.remove(existing)
                        formatOptions.add(
                            VideoFormatOption(
                                itag = bestInGroup.itag,
                                qualityLabel = label,
                                width = bestInGroup.width,
                                height = bestInGroup.height,
                                fps = bestInGroup.fps,
                                videoUrl = bestInGroup.url!!,
                                videoMimeType = bestInGroup.mimeType,
                                audioUrl = bestAudio.url,
                                audioMimeType = bestAudio.mimeType,
                                isAdaptive = true,
                                approximateSizeBytes = videoSize + audioSize,
                            )
                        )
                    }
                }
            }

            // Sort resolutions descending (4K -> 1440p -> 1080p -> 720p -> 480p -> 360p)
            val sortedFormats = formatOptions.sortedWith(
                compareByDescending<VideoFormatOption> { it.height ?: 0 }
                    .thenByDescending { it.fps ?: 30 }
            )

            if (sortedFormats.isEmpty()) {
                throw IllegalStateException("No downloadable formats available for this video")
            }

            YouTubeVideoInfo(
                videoId = videoId,
                title = title,
                author = author,
                durationSeconds = durationSeconds,
                thumbnailUrl = thumbnail,
                formats = sortedFormats,
            )
        }
    }

    private fun downloadStreamToFile(
        urlStr: String,
        destFile: File,
        startProgress: Float,
        endProgress: Float,
        onProgress: (Float) -> Unit,
    ) {
        val url = URL(urlStr)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        conn.connect()

        val contentLength = conn.contentLength.takeIf { it > 0 } ?: 1
        var totalBytesRead = 0L

        conn.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(32768)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    val p = startProgress + (totalBytesRead.toFloat() / contentLength) * (endProgress - startProgress)
                    onProgress(p.coerceIn(startProgress, endProgress))
                }
                output.flush()
            }
        }
    }

    private fun muxVideoAndAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        onProgress: (Float) -> Unit,
    ) {
        val videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
        val audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var videoTrackIndex = -1
        var audioTrackIndex = -1

        for (i in 0 until videoExtractor.trackCount) {
            val format = videoExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("video/")) {
                videoExtractor.selectTrack(i)
                videoTrackIndex = muxer.addTrack(format)
                break
            }
        }

        for (i in 0 until audioExtractor.trackCount) {
            val format = audioExtractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                audioExtractor.selectTrack(i)
                audioTrackIndex = muxer.addTrack(format)
                break
            }
        }

        muxer.start()

        val bufferSize = 1024 * 1024
        val dstBuf = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        // Write video track
        if (videoTrackIndex != -1) {
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = videoExtractor.readSampleData(dstBuf, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = videoExtractor.sampleTime
                bufferInfo.flags = videoExtractor.sampleFlags
                muxer.writeSampleData(videoTrackIndex, dstBuf, bufferInfo)
                videoExtractor.advance()
            }
        }
        onProgress(0.95f)

        // Write audio track
        if (audioTrackIndex != -1) {
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = audioExtractor.readSampleData(dstBuf, 0)
                if (bufferInfo.size < 0) break
                bufferInfo.presentationTimeUs = audioExtractor.sampleTime
                bufferInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(audioTrackIndex, dstBuf, bufferInfo)
                audioExtractor.advance()
            }
        }

        videoExtractor.release()
        audioExtractor.release()

        muxer.stop()
        muxer.release()
        onProgress(0.98f)
    }

    suspend fun downloadVideo(
        context: Context,
        videoInfo: YouTubeVideoInfo,
        selectedFormat: VideoFormatOption,
        onProgress: (Float) -> Unit,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            onProgress(0.02f)

            val safeTitle = videoInfo.title.replace("[/\\\\:*?\"<>|]".toRegex(), "_").trim()
            val fileName = "$safeTitle.mp4"
            val tempDir = context.cacheDir

            val finalFile: File

            if (selectedFormat.isAdaptive && selectedFormat.audioUrl != null) {
                // Adaptive: download video stream (0% - 50%), audio stream (50% - 85%), mux (85% - 98%)
                val tempVideoFile = File(tempDir, "temp_v_${System.currentTimeMillis()}.mp4")
                val tempAudioFile = File(tempDir, "temp_a_${System.currentTimeMillis()}.mp4")
                val muxedFile = File(tempDir, "muxed_${System.currentTimeMillis()}.mp4")

                try {
                    downloadStreamToFile(selectedFormat.videoUrl, tempVideoFile, 0.05f, 0.55f, onProgress)
                    downloadStreamToFile(selectedFormat.audioUrl, tempAudioFile, 0.55f, 0.85f, onProgress)
                    muxVideoAndAudio(tempVideoFile, tempAudioFile, muxedFile, onProgress)
                    finalFile = muxedFile
                } finally {
                    tempVideoFile.delete()
                    tempAudioFile.delete()
                }
            } else {
                // Progressive: single download (0% - 95%)
                val progressiveFile = File(tempDir, "prog_${System.currentTimeMillis()}.mp4")
                downloadStreamToFile(selectedFormat.videoUrl, progressiveFile, 0.05f, 0.95f, onProgress)
                finalFile = progressiveFile
            }

            // Publish to MediaStore Downloads / Mercury folder
            val contentResolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/Mercury")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            }

            val targetUri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("Failed to create MediaStore entry for video")

            contentResolver.openOutputStream(targetUri)?.use { outStream ->
                finalFile.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
                outStream.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                contentResolver.update(targetUri, contentValues, null, null)
            }

            finalFile.delete()
            onProgress(1.0f)
            targetUri
        }
    }
}
