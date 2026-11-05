/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.constants.AlbumSortType
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.AlbumEntity
import com.metrolist.music.db.entities.SongAlbumMap
import com.metrolist.music.extensions.matchesNormalizedQuery
import com.metrolist.music.extensions.normalizeForSearch
import com.metrolist.music.ui.component.AlbumListItem
import com.metrolist.music.ui.component.DefaultDialog
import com.metrolist.music.ui.component.ListDialog
import com.metrolist.music.ui.component.TextFieldDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.UUID

@Composable
fun AddToAlbumDialog(
    isVisible: Boolean,
    onGetSong: suspend () -> List<String>,
    onGetSongIds: (suspend () -> List<String>)? = null,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showCreateAlbumDialog by rememberSaveable { mutableStateOf(false) }

    val albumsFlow = remember {
        database.albums(AlbumSortType.NAME, false)
    }
    val albums by albumsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val normalizedQuery = remember(searchQuery) { searchQuery.normalizeForSearch() }
    val filteredAlbums = remember(albums, normalizedQuery) {
        val distinctAlbums = albums.distinctBy { it.id }.distinctBy { album ->
            val playlistPart = album.album.playlistId?.takeIf { it.isNotBlank() }
            val titleArtistPart = "${album.album.title.trim().lowercase()}_${album.artists.firstOrNull()?.name?.trim()?.lowercase().orEmpty()}"
            playlistPart ?: titleArtistPart
        }
        if (normalizedQuery.isBlank()) {
            distinctAlbums
        } else {
            distinctAlbums.filter { album ->
                val artistNames = album.artists.map { it.name }.toTypedArray()
                matchesNormalizedQuery(normalizedQuery, album.album.title, *artistNames)
            }
        }
    }

    suspend fun addSongsToTargetAlbum(targetAlbumId: String, targetAlbumTitle: String) {
        val songIds = onGetSongIds?.invoke() ?: onGetSong()
        if (songIds.isEmpty()) return

        withContext(Dispatchers.IO) {
            val existingAlbum = database.albumWithSongs(targetAlbumId).first()
            val existingSongs = existingAlbum?.songs ?: emptyList()
            val startIndex = existingSongs.size

            songIds.forEachIndexed { index, songId ->
                // Upsert SongAlbumMap
                database.upsert(
                    SongAlbumMap(
                        songId = songId,
                        albumId = targetAlbumId,
                        index = startIndex + index,
                    )
                )

                // Update SongEntity album association
                val song = database.song(songId).first()
                if (song != null) {
                    database.update(
                        song.song.copy(
                            albumId = targetAlbumId,
                            albumName = targetAlbumTitle,
                        )
                    )
                }
            }

            // Update AlbumEntity songCount and duration
            val updatedAlbumWithSongs = database.albumWithSongs(targetAlbumId).first()
            val finalSongs = updatedAlbumWithSongs?.songs ?: emptyList()
            val totalDuration = finalSongs.sumOf { if (it.song.duration > 0) it.song.duration else 0 }
            val albumEntity = database.albumEntity(targetAlbumId)
            if (albumEntity != null) {
                database.update(
                    albumEntity.copy(
                        songCount = finalSongs.size,
                        duration = totalDuration,
                        lastUpdateTime = LocalDateTime.now(),
                    )
                )
            }
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.added_to_album, targetAlbumTitle),
                Toast.LENGTH_SHORT
            ).show()
            onDismiss()
        }
    }

    ListDialog(onDismiss = onDismiss) {
        item {
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.7f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "createAlbumScale",
            )
            FilledTonalButton(
                onClick = { showCreateAlbumDialog = true },
                shape = RoundedCornerShape(50),
                interactionSource = interactionSource,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale },
            ) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp),
                )
                Text(
                    text = stringResource(R.string.create_album),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_albums)) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (filteredAlbums.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.library_album_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(
                items = filteredAlbums,
                key = { "add_to_album_${it.id}" },
            ) { album ->
                AlbumListItem(
                    album = album,
                    showLikedIcon = false,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            coroutineScope.launch {
                                addSongsToTargetAlbum(album.id, album.album.title)
                            }
                        },
                )
            }
        }
    }

    if (showCreateAlbumDialog) {
        TextFieldDialog(
            title = { Text(text = stringResource(R.string.create_album)) },
            placeholder = { Text(text = stringResource(R.string.album_title)) },
            onDismiss = { showCreateAlbumDialog = false },
            onDone = { title ->
                val cleanTitle = title.trim()
                if (cleanTitle.isNotEmpty()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val newAlbumId = "local_album_${UUID.randomUUID()}"
                        val newAlbum = AlbumEntity(
                            id = newAlbumId,
                            title = cleanTitle,
                            bookmarkedAt = LocalDateTime.now(),
                            inLibrary = LocalDateTime.now(),
                            isLocal = true,
                            songCount = 0,
                            duration = 0,
                        )
                        database.insert(newAlbum)
                        addSongsToTargetAlbum(newAlbumId, cleanTitle)
                    }
                }
            },
        )
    }
}
