/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.Context
import android.os.Environment
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSong
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.time.LocalDateTime
import kotlin.math.absoluteValue

object AutoLibraryScanner {

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (char in line) {
            when {
                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result.map { it.trim().trim('"') }
    }

    /**
     * Scans storage for previously exported playlists (.m3u, .csv) from Mercury or Metrolist
     * and automatically restores them into Room database if not already present.
     */
    suspend fun scanAndRestorePlaylists(
        context: Context,
        database: MusicDatabase
    ): Int = withContext(Dispatchers.IO) {
        var restoredCount = 0
        runCatching {
            val candidateDirs = listOfNotNull(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "MercuryExports"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "MetrolistExports"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Mercury"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Metrolist"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Mercury"),
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Playlists"),
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { File(it, "MercuryExports") },
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { File(it, "MetrolistExports") }
            ).filter { it.exists() && it.isDirectory }

            val playlistFiles = mutableListOf<File>()
            for (dir in candidateDirs) {
                dir.listFiles { file ->
                    file.isFile && (
                        file.name.endsWith(".m3u", ignoreCase = true) ||
                        file.name.endsWith(".m3u8", ignoreCase = true) ||
                        file.name.endsWith(".csv", ignoreCase = true)
                    )
                }?.let { playlistFiles.addAll(it) }
            }

            if (playlistFiles.isEmpty()) {
                return@runCatching 0
            }

            // Existing playlist names in database
            val existingNames = database.playlistEntitiesByNameAsc()
                .map { it.name.trim().lowercase() }
                .toSet()

            for (file in playlistFiles) {
                val playlistName = file.nameWithoutExtension.trim()
                if (playlistName.isBlank() || existingNames.contains(playlistName.lowercase())) {
                    continue
                }

                val songsToAdd = mutableListOf<String>()

                if (file.name.endsWith(".m3u", ignoreCase = true) || file.name.endsWith(".m3u8", ignoreCase = true)) {
                    val lines = file.readLines()
                    var i = 0
                    while (i < lines.size) {
                        val line = lines[i].trim()
                        if (line.startsWith("#EXTINF:")) {
                            val info = line.removePrefix("#EXTINF:")
                            val commaIndex = info.indexOf(',')
                            val durationStr = if (commaIndex != -1) info.substring(0, commaIndex).trim() else "-1"
                            val titleArtist = if (commaIndex != -1) info.substring(commaIndex + 1).trim() else info
                            val duration = durationStr.toIntOrNull() ?: -1

                            val (artistName, songTitle) = if (titleArtist.contains(" - ")) {
                                val parts = titleArtist.split(" - ", limit = 2)
                                parts[0].trim() to parts[1].trim()
                            } else {
                                "Unknown Artist" to titleArtist
                            }

                            // Next non-empty, non-comment line is the link or path
                            var linkLine = ""
                            i++
                            while (i < lines.size) {
                                val next = lines[i].trim()
                                if (next.isNotEmpty() && !next.startsWith("#")) {
                                    linkLine = next
                                    break
                                }
                                i++
                            }

                            val songId = when {
                                linkLine.contains("watch?v=") -> linkLine.substringAfter("watch?v=").substringBefore("&")
                                linkLine.startsWith("https://youtu.be/") -> linkLine.substringAfter("youtu.be/").substringBefore("?")
                                linkLine.length == 11 && linkLine.matches(Regex("[a-zA-Z0-9_-]{11}")) -> linkLine
                                linkLine.isNotEmpty() -> "local_${linkLine.hashCode().absoluteValue}"
                                else -> "local_${(artistName + songTitle).hashCode().absoluteValue}"
                            }

                            val mediaMetadata = MediaMetadata(
                                id = songId,
                                title = songTitle,
                                artists = listOf(MediaMetadata.Artist(id = null, name = artistName)),
                                duration = duration,
                            )
                            database.insert(mediaMetadata)
                            songsToAdd.add(songId)
                        }
                        i++
                    }
                } else if (file.name.endsWith(".csv", ignoreCase = true)) {
                    val lines = file.readLines()
                    if (lines.isNotEmpty()) {
                        val hasHeader = lines.first().contains("Title", ignoreCase = true)
                        val startIdx = if (hasHeader) 1 else 0
                        for (idx in startIdx until lines.size) {
                            val parts = parseCsvLine(lines[idx])
                            if (parts.size >= 2) {
                                val title = parts[0]
                                val artist = parts.getOrNull(1) ?: "Unknown Artist"
                                val videoId = parts.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
                                val songId = videoId ?: "local_${(title + artist).hashCode().absoluteValue}"
                                val mediaMetadata = MediaMetadata(
                                    id = songId,
                                    title = title,
                                    artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
                                    duration = -1,
                                )
                                database.insert(mediaMetadata)
                                songsToAdd.add(songId)
                            }
                        }
                    }
                }

                if (songsToAdd.isNotEmpty()) {
                    val newPlaylist = PlaylistEntity(
                        name = playlistName,
                        bookmarkedAt = LocalDateTime.now(),
                        isEditable = true,
                    )
                    database.insert(newPlaylist)
                    val insertedPlaylist = database.playlistBlocking(newPlaylist.id)
                    if (insertedPlaylist != null) {
                        database.addSongsToPlaylist(
                            playlist = insertedPlaylist,
                            songs = songsToAdd.map { it to null }
                        )
                        restoredCount++
                        Timber.d("AutoLibraryScanner: Restored playlist '%s' with %d songs", playlistName, songsToAdd.size)
                    }
                }
            }
            restoredCount
        }.getOrDefault(0)
    }

    /**
     * Mirror a modified or created local playlist to public storage as an M3U file
     * to ensure it survives app uninstalls.
     */
    fun mirrorPlaylistToPublicStorage(
        context: Context,
        playlistName: String,
        songs: List<PlaylistSong>
    ) {
        if (playlistName.isBlank() || songs.isEmpty()) return
        try {
            val exportResult = PlaylistExporter.exportPlaylistAsM3U(context, playlistName, songs)
            val file = exportResult.getOrNull()
            if (file != null) {
                saveToPublicDocuments(context, file, "audio/x-mpegurl", "MercuryExports")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to mirror playlist %s", playlistName)
        }
    }
}
