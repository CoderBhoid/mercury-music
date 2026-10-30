/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.utils.completed
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.util.regex.Pattern

object PlaylistLinkImporter {

    data class ImportResult(
        val playlistTitle: String,
        val importedCount: Int,
        val totalCount: Int,
    )

    fun extractYouTubePlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        if (trimmed.startsWith("PL") || trimmed.startsWith("VL") || trimmed.startsWith("OLAK5uy_") || trimmed.startsWith("RDCLAK5uy_")) {
            return if (trimmed.startsWith("VL")) trimmed.substring(2) else trimmed
        }

        val listMatch = Regex("[?&]list=([a-zA-Z0-9_-]+)").find(trimmed)
        if (listMatch != null) {
            val id = listMatch.groupValues[1]
            return if (id.startsWith("VL")) id.substring(2) else id
        }

        val playlistMatch = Regex("playlist/([a-zA-Z0-9_-]+)").find(trimmed)
        if (playlistMatch != null) {
            val id = playlistMatch.groupValues[1]
            return if (id.startsWith("VL")) id.substring(2) else id
        }

        return null
    }

    fun extractSpotifyPlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val match = Regex("playlist/([a-zA-Z0-9]+)").find(trimmed)
        if (match != null) {
            val id = match.groupValues[1]
            if (id.length >= 15) return id
        }

        if (trimmed.length in 20..30 && trimmed.matches(Regex("[a-zA-Z0-9]+"))) {
            return trimmed
        }

        return null
    }

    suspend fun importYouTubePlaylist(
        urlOrId: String,
        database: MusicDatabase,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val playlistId = extractYouTubePlaylistId(urlOrId)
                ?: throw IllegalArgumentException("Could not extract a valid YouTube playlist ID from link")

            Timber.d("Importing YouTube playlist: $playlistId")
            val playlistPage = YouTube.playlist(playlistId).completed().getOrThrow()
            val songs = playlistPage.songs
            val title = playlistPage.playlist?.title ?: "Imported YouTube Playlist"
            val thumbnail = playlistPage.playlist?.thumbnail

            if (songs.isEmpty()) {
                throw IllegalStateException("Playlist is empty or private")
            }

            val playlistEntity = PlaylistEntity(
                name = title,
                browseId = playlistId,
                thumbnailUrl = thumbnail,
                isEditable = true,
                bookmarkedAt = LocalDateTime.now(),
                remoteSongCount = songs.size,
            )

            database.withTransaction {
                database.insert(playlistEntity)
                songs.forEach { songItem ->
                    database.insert(songItem.toMediaMetadata())
                }
                val localPlaylist = database.playlistBlocking(playlistEntity.id)
                if (localPlaylist != null) {
                    database.addSongsToPlaylist(
                        playlist = localPlaylist,
                        songs = songs.map { it.id to it.setVideoId },
                    )
                }
            }

            ImportResult(
                playlistTitle = title,
                importedCount = songs.size,
                totalCount = songs.size,
            )
        }
    }

    suspend fun importSpotifyPlaylist(
        urlOrId: String,
        database: MusicDatabase,
        onProgress: ((current: Int, total: Int) -> Unit)? = null,
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val playlistId = extractSpotifyPlaylistId(urlOrId)
                ?: throw IllegalArgumentException("Could not extract a valid Spotify playlist ID from link")

            Timber.d("Importing Spotify playlist: $playlistId")
            val conn = (URL("https://open.spotify.com/embed/playlist/$playlistId").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                connectTimeout = 15000
                readTimeout = 15000
            }

            if (conn.responseCode != 200) {
                throw IllegalStateException("Failed to load Spotify playlist (HTTP ${conn.responseCode})")
            }

            val html = conn.inputStream.bufferedReader().use { it.readText() }

            val nextDataPattern = Pattern.compile("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>")
            val matcher = nextDataPattern.matcher(html)
            if (!matcher.find()) {
                throw IllegalStateException("Could not parse Spotify playlist structure")
            }

            val json = JSONObject(matcher.group(1))
            val entity = json.optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONObject("state")
                ?.optJSONObject("data")
                ?.optJSONObject("entity")
                ?: throw IllegalStateException("Could not find Spotify playlist entity")

            val title = entity.optString("name", "Imported Spotify Playlist")
            val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
            val coverUrl = coverSources?.optJSONObject(0)?.optString("url")

            val trackList = entity.optJSONArray("trackList") ?: org.json.JSONArray()
            val totalTracks = trackList.length()
            if (totalTracks == 0) {
                throw IllegalStateException("Spotify playlist contains no tracks")
            }

            val playlistEntity = PlaylistEntity(
                name = title,
                thumbnailUrl = coverUrl,
                isEditable = true,
                bookmarkedAt = LocalDateTime.now(),
                remoteSongCount = totalTracks,
            )

            database.insert(playlistEntity)

            val importedSongIds = mutableListOf<String>()

            for (i in 0 until totalTracks) {
                val trackObj = trackList.optJSONObject(i) ?: continue
                val trackTitle = trackObj.optString("title", "")
                val trackArtist = trackObj.optString("subtitle", "")
                if (trackTitle.isBlank()) continue

                onProgress?.invoke(i + 1, totalTracks)

                try {
                    val searchQuery = "$trackTitle $trackArtist"
                    val searchResult = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    val matchingSong = searchResult?.items?.filterIsInstance<SongItem>()?.firstOrNull()
                    if (matchingSong != null) {
                        database.insert(matchingSong.toMediaMetadata())
                        importedSongIds.add(matchingSong.id)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to resolve track $trackTitle - $trackArtist on YouTube Music")
                }
            }

            if (importedSongIds.isNotEmpty()) {
                val localPlaylist = database.playlistBlocking(playlistEntity.id)
                if (localPlaylist != null) {
                    database.addSongsToPlaylist(
                        playlist = localPlaylist,
                        songs = importedSongIds.map { it to null },
                    )
                }
            }

            ImportResult(
                playlistTitle = title,
                importedCount = importedSongIds.size,
                totalCount = totalTracks,
            )
        }
    }
}
