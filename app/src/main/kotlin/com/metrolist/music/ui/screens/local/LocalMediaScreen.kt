/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.local

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.LocalHazeState
import com.metrolist.music.ui.component.obsidianGlassEffect
import com.metrolist.music.utils.makeTimeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LocalAudioTrack(
    val id: Long,
    val contentUri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val folderName: String,
    val path: String,
) {
    fun toMediaItem(): MediaItem {
        val artworkUri = if (albumId > 0) {
            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
        } else null

        val meta = MediaMetadata(
            id = "local_$id",
            title = title,
            artists = listOf(MediaMetadata.Artist(id = null, name = artist)),
            duration = (durationMs / 1000).toInt(),
            thumbnailUrl = artworkUri?.toString(),
            album = MediaMetadata.Album(id = "", title = album),
        )

        return MediaItem.Builder()
            .setMediaId("local_$id")
            .setUri(contentUri)
            .setTag(meta)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .setDisplayTitle(title)
                    .setSubtitle(artist)
                    .setArtworkUri(artworkUri)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }
}

enum class LocalTab {
    ALL,
    FOLDERS,
    CACHED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMediaScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val database = LocalDatabase.current
    val downloadUtil = LocalDownloadUtil.current
    val playerCache = downloadUtil.playerCache
    val hazeState = LocalHazeState.current

    val currentPlayingMediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }

    // Permission handling
    val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, requiredPermission) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    // Tab state
    var selectedTab by remember { mutableStateOf(LocalTab.ALL) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Data lists
    var localTracks by remember { mutableStateOf<List<LocalAudioTrack>>(emptyList()) }
    var cachedSongsList by remember { mutableStateOf<List<Song>>(emptyList()) }
    var isLoadingLocal by remember { mutableStateOf(false) }

    // Export progress state
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var exportTotal by remember { mutableIntStateOf(0) }

    // Load cached songs from database & verify in playerCache
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val dbSongs = database.cachePlaylistSongs().first()
            val validCached = dbSongs.filter { song ->
                val length = song.format?.contentLength ?: playerCache.getContentMetadata(song.id).let {
                    androidx.media3.datasource.cache.ContentMetadata.getContentLength(it)
                }
                length > 0L && playerCache.isCached(song.id, 0, length)
            }
            withContext(Dispatchers.Main) {
                cachedSongsList = validCached
            }
        }
    }

    // Load MediaStore audio files if permission granted
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            isLoadingLocal = true
            withContext(Dispatchers.IO) {
                com.metrolist.music.utils.AutoLibraryScanner.scanAndRestorePlaylists(context, database)
                val fetched = queryDeviceAudio(context)
                withContext(Dispatchers.Main) {
                    localTracks = fetched
                    isLoadingLocal = false
                }
            }
        }
    }

    // Export Folder Picker Launcher
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null && cachedSongsList.isNotEmpty()) {
            coroutineScope.launch {
                isExporting = true
                exportTotal = cachedSongsList.size
                exportProgress = 0

                val successCount = withContext(Dispatchers.IO) {
                    exportCachedSongsToDirectory(
                        context = context,
                        targetDirUri = treeUri,
                        playerCache = playerCache,
                        songs = cachedSongsList,
                        onProgress = { current ->
                            exportProgress = current
                        }
                    )
                }

                isExporting = false
                Toast.makeText(
                    context,
                    context.getString(R.string.export_cache_success, successCount),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // Back handler for Folder detail
    if (selectedFolder != null) {
        BackHandler {
            selectedFolder = null
        }
    }

    // Filtered lists
    val filteredLocalTracks = remember(localTracks, searchQuery) {
        if (searchQuery.isBlank()) localTracks
        else localTracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    val filteredCachedSongs = remember(cachedSongsList, searchQuery) {
        if (searchQuery.isBlank()) cachedSongsList
        else cachedSongsList.filter {
            it.song.title.contains(searchQuery, ignoreCase = true) ||
            it.artists.any { artist -> artist.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    val folderGroups = remember(localTracks) {
        localTracks.groupBy { it.folderName }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
        ) {
            // Folder Back Banner when inside a folder
            if (selectedFolder != null) {
                item(key = "folder_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { selectedFolder = null },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column {
                            Text(
                                text = selectedFolder!!,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (selectedFolder == "Mercury Cache") "${cachedSongsList.size} cached songs"
                                else "${folderGroups[selectedFolder]?.size ?: 0} songs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                // Top Action Row (Song count + Export MP3 button, without duplicate "Local Music" title)
                item(key = "top_action_row") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${localTracks.size + cachedSongsList.size} tracks available",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (cachedSongsList.isNotEmpty()) {
                            Surface(
                                onClick = { exportLauncher.launch(null) },
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier.clip(RoundedCornerShape(20.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.download),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Export MP3",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Export Progress Bar
            item(key = "export_progress") {
                AnimatedVisibility(visible = isExporting) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .obsidianGlassEffect(hazeState, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.exporting),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$exportProgress / $exportTotal",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { if (exportTotal > 0) exportProgress.toFloat() / exportTotal else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // Search Bar item
            item(key = "search_bar") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Search local or cached songs…",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Navigation Tabs (Only when not in a folder)
            if (selectedFolder == null) {
                item(key = "nav_tabs") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedTab == LocalTab.ALL,
                            onClick = { selectedTab = LocalTab.ALL },
                            label = { Text("All Tracks (${localTracks.size + cachedSongsList.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        FilterChip(
                            selected = selectedTab == LocalTab.FOLDERS,
                            onClick = { selectedTab = LocalTab.FOLDERS },
                            label = { Text("Folders (${folderGroups.size + if (cachedSongsList.isNotEmpty()) 1 else 0})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        FilterChip(
                            selected = selectedTab == LocalTab.CACHED,
                            onClick = { selectedTab = LocalTab.CACHED },
                            label = { Text("Cached (${cachedSongsList.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // Storage Permission Request Banner (if ungranted)
            if (!hasPermission) {
                item(key = "permission_banner") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.storage_permission_needed),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = { permissionLauncher.launch(requiredPermission) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Text(stringResource(R.string.grant_permission))
                            }
                        }
                    }
                }
            }
            // View a specific folder
            if (selectedFolder != null) {
                val folderName = selectedFolder!!
                if (folderName == "Mercury Cache") {
                    // Cached songs inside folder view
                    item {
                        FolderActionHeader(
                            title = "Mercury Cache",
                            count = filteredCachedSongs.size,
                            onPlayAll = {
                                if (filteredCachedSongs.isNotEmpty() && playerConnection != null) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "Mercury Cache",
                                            items = filteredCachedSongs.map { it.toMediaItem() }
                                        )
                                    )
                                }
                            },
                            onShuffle = {
                                if (filteredCachedSongs.isNotEmpty() && playerConnection != null) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = "Mercury Cache",
                                            items = filteredCachedSongs.shuffled().map { it.toMediaItem() }
                                        )
                                    )
                                }
                            }
                        )
                    }

                    itemsIndexed(
                        items = filteredCachedSongs,
                        key = { _, song -> song.id }
                    ) { index, song ->
                        val isPlaying = currentPlayingMediaMetadata?.id == song.id
                        CachedSongRow(
                            song = song,
                            isPlaying = isPlaying,
                            onClick = {
                                playerConnection?.playQueue(
                                    ListQueue(
                                        title = "Mercury Cache",
                                        items = filteredCachedSongs.map { it.toMediaItem() },
                                        startIndex = index
                                    )
                                )
                            }
                        )
                    }
                } else {
                    // Local folder tracks
                    val folderTracks = folderGroups[folderName].orEmpty().let { list ->
                        if (searchQuery.isBlank()) list
                        else list.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                            it.artist.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    item {
                        FolderActionHeader(
                            title = folderName,
                            count = folderTracks.size,
                            onPlayAll = {
                                if (folderTracks.isNotEmpty() && playerConnection != null) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = folderName,
                                            items = folderTracks.map { it.toMediaItem() }
                                        )
                                    )
                                }
                            },
                            onShuffle = {
                                if (folderTracks.isNotEmpty() && playerConnection != null) {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = folderName,
                                            items = folderTracks.shuffled().map { it.toMediaItem() }
                                        )
                                    )
                                }
                            }
                        )
                    }

                    itemsIndexed(
                        items = folderTracks,
                        key = { _, track -> track.id }
                    ) { index, track ->
                        val isPlaying = currentPlayingMediaMetadata?.id == "local_${track.id}"
                        LocalTrackRow(
                            track = track,
                            isPlaying = isPlaying,
                            onClick = {
                                playerConnection?.playQueue(
                                    ListQueue(
                                        title = folderName,
                                        items = folderTracks.map { it.toMediaItem() },
                                        startIndex = index
                                    )
                                )
                            }
                        )
                    }
                }
            } else {
                // Tabs rendering
                when (selectedTab) {
                    LocalTab.FOLDERS -> {
                        // Mercury Cache folder card
                        if (cachedSongsList.isNotEmpty()) {
                            item {
                                FolderCardItem(
                                    folderName = "Mercury Cache",
                                    songCount = cachedSongsList.size,
                                    subtitle = "Locally cached streams ready for offline & export",
                                    isCache = true,
                                    onClick = { selectedFolder = "Mercury Cache" }
                                )
                            }
                        }

                        // Local device folders
                        folderGroups.forEach { (name, tracks) ->
                            item(key = "folder_$name") {
                                FolderCardItem(
                                    folderName = name,
                                    songCount = tracks.size,
                                    subtitle = tracks.firstOrNull()?.path ?: "",
                                    isCache = false,
                                    onClick = { selectedFolder = name }
                                )
                            }
                        }
                    }

                    LocalTab.CACHED -> {
                        if (filteredCachedSongs.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = "No cached songs found",
                                    subtitle = "Songs streamed or downloaded into cache will appear here"
                                )
                            }
                        } else {
                            itemsIndexed(
                                items = filteredCachedSongs,
                                key = { _, song -> song.id }
                            ) { index, song ->
                                val isPlaying = currentPlayingMediaMetadata?.id == song.id
                                CachedSongRow(
                                    song = song,
                                    isPlaying = isPlaying,
                                    onClick = {
                                        playerConnection?.playQueue(
                                            ListQueue(
                                                title = "Mercury Cache",
                                                items = filteredCachedSongs.map { it.toMediaItem() },
                                                startIndex = index
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }

                    LocalTab.ALL -> {
                        // Header for Local Tracks
                        if (filteredLocalTracks.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Device Storage (${filteredLocalTracks.size})")
                            }

                            itemsIndexed(
                                items = filteredLocalTracks,
                                key = { _, track -> "local_${track.id}" }
                            ) { index, track ->
                                val isPlaying = currentPlayingMediaMetadata?.id == "local_${track.id}"
                                LocalTrackRow(
                                    track = track,
                                    isPlaying = isPlaying,
                                    onClick = {
                                        playerConnection?.playQueue(
                                            ListQueue(
                                                title = "Local Music",
                                                items = filteredLocalTracks.map { it.toMediaItem() },
                                                startIndex = index
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        // Header for Cached Songs
                        if (filteredCachedSongs.isNotEmpty()) {
                            item {
                                SectionHeader(title = "Cached Music (${filteredCachedSongs.size})")
                            }

                            itemsIndexed(
                                items = filteredCachedSongs,
                                key = { _, song -> "cached_${song.id}" }
                            ) { index, song ->
                                val isPlaying = currentPlayingMediaMetadata?.id == song.id
                                CachedSongRow(
                                    song = song,
                                    isPlaying = isPlaying,
                                    onClick = {
                                        playerConnection?.playQueue(
                                            ListQueue(
                                                title = "Cached Music",
                                                items = filteredCachedSongs.map { it.toMediaItem() },
                                                startIndex = index
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        if (filteredLocalTracks.isEmpty() && filteredCachedSongs.isEmpty()) {
                            item {
                                EmptyStateCard(
                                    title = if (isLoadingLocal) "Scanning device audio…" else "No local music found",
                                    subtitle = if (!hasPermission) "Grant storage permission to see device tracks" else "No audio tracks detected"
                                )
                            }
                        }
                    }
                }
            }

            // Bottom spacer for player pill
            item {
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

@Composable
fun FolderActionHeader(
    title: String,
    count: Int,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$count tracks",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPlayAll,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("Play All")
            }

            Button(
                onClick = onShuffle,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text("Shuffle", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun FolderCardItem(
    folderName: String,
    songCount: Int,
    subtitle: String,
    isCache: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isCache) Brush.linearGradient(
                            listOf(Color(0xFF8A2387), Color(0xFFE94057), Color(0xFFF27121))
                        )
                        else Brush.linearGradient(
                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f), MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(if (isCache) R.drawable.cached else R.drawable.storage),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "$songCount songs" + if (subtitle.isNotBlank()) " • $subtitle" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LocalTrackRow(
    track: LocalAudioTrack,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val albumArtUri = if (track.albumId > 0) {
                ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), track.albumId)
            } else null

            if (albumArtUri != null) {
                AsyncImage(
                    model = albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.volume_up),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${track.artist} • ${track.album}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = makeTimeString(track.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CachedSongRow(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = song.song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.volume_up),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.padding(end = 6.dp)
                ) {
                    Text(
                        text = "CACHED",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = song.artists.joinToString { it.name }.ifEmpty { "Unknown Artist" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Text(
            text = makeTimeString(song.song.duration * 1000L),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
fun EmptyStateCard(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.music_note),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// MediaStore query helper
fun queryDeviceAudio(context: Context): List<LocalAudioTrack> {
    val tracks = mutableListOf<LocalAudioTrack>()
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.BUCKET_DISPLAY_NAME,
        MediaStore.Audio.Media.DATA
    )

    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 10000"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

    try {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val bucketCol = cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown Track"
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                val album = cursor.getString(albumCol) ?: "Unknown Album"
                val albumId = if (albumIdCol != -1) cursor.getLong(albumIdCol) else 0L
                val duration = cursor.getLong(durationCol)
                val folder = if (bucketCol != -1) cursor.getString(bucketCol) ?: "Music" else "Music"
                val path = if (dataCol != -1) cursor.getString(dataCol).orEmpty() else ""
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                tracks.add(
                    LocalAudioTrack(
                        id = id,
                        contentUri = contentUri,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        durationMs = duration,
                        folderName = folder,
                        path = path
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return tracks
}

// Cached music exporter
suspend fun exportCachedSongsToDirectory(
    context: Context,
    targetDirUri: Uri,
    playerCache: androidx.media3.datasource.cache.Cache,
    songs: List<Song>,
    onProgress: (Int) -> Unit
): Int = withContext(Dispatchers.IO) {
    val targetDir = DocumentFile.fromTreeUri(context, targetDirUri) ?: return@withContext 0
    if (!targetDir.canWrite()) return@withContext 0

    var count = 0
    val cacheDataSource = CacheDataSource.Factory()
        .setCache(playerCache)
        .setUpstreamDataSourceFactory(null)
        .createDataSource()

    val buffer = ByteArray(16384)

    songs.forEachIndexed { index, song ->
        try {
            val artistString = song.artists.joinToString { it.name }.ifEmpty { "Unknown Artist" }
            val safeArtist = artistString.replace("[/\\\\:*?\"<>|]".toRegex(), "_").trim()
            val safeTitle = song.song.title.replace("[/\\\\:*?\"<>|]".toRegex(), "_").trim()
            val fileName = "$safeArtist - $safeTitle.mp3"

            val existing = targetDir.findFile(fileName)
            val targetFile = existing ?: targetDir.createFile("audio/mpeg", fileName)

            if (targetFile != null) {
                val dataSpec = DataSpec.Builder()
                    .setKey(song.id)
                    .setUri(Uri.EMPTY)
                    .build()

                try {
                    cacheDataSource.open(dataSpec)
                    context.contentResolver.openOutputStream(targetFile.uri)?.use { outputStream ->
                        var bytesRead: Int
                        while (cacheDataSource.read(buffer, 0, buffer.size).also { bytesRead = it } != C.RESULT_END_OF_INPUT) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        outputStream.flush()
                    }
                    count++
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        cacheDataSource.close()
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onProgress(index + 1)
    }

    count
}
