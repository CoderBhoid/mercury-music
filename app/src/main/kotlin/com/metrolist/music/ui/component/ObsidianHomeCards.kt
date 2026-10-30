/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.WatchEndpoint
import com.metrolist.innertube.models.YTItem
import com.metrolist.music.LocalArtistNameAliases
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.AutoRadioQueueKey
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.db.entities.SongEntity
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.playback.queues.YouTubeQueue
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.ArtistMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.menu.YouTubeArtistMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.ArtistNameAliases
import com.metrolist.music.utils.rememberPreference

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ObsidianSongCard(
    song: Song,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val artistNameAliases = LocalArtistNameAliases.current
    val autoRadioQueue by rememberPreference(AutoRadioQueueKey, defaultValue = true)

    val currentSong by database.song(song.id).collectAsStateWithLifecycle(initialValue = song)
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    val isPlaying by playerConnection?.isEffectivelyPlaying?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    val activeSong = currentSong ?: song
    val isActive = activeSong.id == mediaMetadata?.id
    val isLiked = activeSong.song.liked

    val artistText = remember(activeSong.orderedArtists, artistNameAliases) {
        activeSong.orderedArtists.joinToString { ArtistNameAliases.resolve(artistNameAliases, it.id, it.name) }
    }

    Column(
        modifier = modifier
            .width(160.dp)
            .combinedClickable(
                onClick = {
                    if (playerConnection != null) {
                        if (isActive) {
                            playerConnection.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                if (autoRadioQueue) {
                                    YouTubeQueue.radio(activeSong.toMediaMetadata())
                                } else {
                                    ListQueue(
                                        title = activeSong.title,
                                        items = listOf(activeSong.toMediaItem()),
                                    )
                                }
                            )
                        }
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        SongMenu(
                            originalSong = activeSong,
                            onDismiss = menuState::dismiss,
                        )
                    }
                }
            )
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(18.dp)),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(activeSong.thumbnailUrl?.resize(400, 400))
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            if (isActive && isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.equalizer),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = activeSong.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = artistText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.70f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    database.transaction {
                        update(activeSong.song.toggleLike())
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription = null,
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(
                onClick = {
                    menuState.show {
                        SongMenu(
                            originalSong = activeSong,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ObsidianSongCard(
    item: YTItem,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val artistNameAliases = LocalArtistNameAliases.current
    val autoRadioQueue by rememberPreference(AutoRadioQueueKey, defaultValue = true)

    val dbSong by database.song(item.id).collectAsStateWithLifecycle(initialValue = null)
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    val isPlaying by playerConnection?.isEffectivelyPlaying?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    val isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id)
    val isLiked = dbSong?.song?.liked == true

    val artistText = remember(item, artistNameAliases) {
        when (item) {
            is SongItem -> item.artists.joinToString { ArtistNameAliases.resolve(artistNameAliases, it.id, it.name) }
            is AlbumItem -> item.artists?.joinToString { ArtistNameAliases.resolve(artistNameAliases, it.id, it.name) }.orEmpty()
            else -> ""
        }
    }

    Column(
        modifier = modifier
            .width(160.dp)
            .combinedClickable(
                onClick = {
                    when (item) {
                        is SongItem -> {
                            if (playerConnection != null) {
                                if (isActive) {
                                    playerConnection.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        if (autoRadioQueue) {
                                            YouTubeQueue(
                                                item.endpoint ?: WatchEndpoint(videoId = item.id),
                                                item.toMediaMetadata(),
                                            )
                                        } else {
                                            ListQueue(
                                                title = item.title,
                                                items = listOf(item.toMediaItem()),
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        is AlbumItem -> navController.navigate("album/${item.id}")
                        is ArtistItem -> navController.navigate("artist/${item.id}")
                        is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                        else -> {}
                    }
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        when (item) {
                            is SongItem -> YouTubeSongMenu(song = item, onDismiss = menuState::dismiss)
                            is AlbumItem -> YouTubeAlbumMenu(albumItem = item, onDismiss = menuState::dismiss)
                            is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                            else -> {}
                        }
                    }
                }
            )
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(18.dp)),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(item.thumbnail?.resize(400, 400))
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            if (isActive && isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.equalizer),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = artistText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.70f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (item is SongItem) {
                        database.transaction {
                            if (dbSong == null) {
                                insert(item.toMediaMetadata(), SongEntity::toggleLike)
                            } else {
                                update(dbSong!!.song.toggleLike())
                            }
                        }
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription = null,
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(
                onClick = {
                    menuState.show {
                        when (item) {
                            is SongItem -> YouTubeSongMenu(song = item, onDismiss = menuState::dismiss)
                            is AlbumItem -> YouTubeAlbumMenu(albumItem = item, onDismiss = menuState::dismiss)
                            is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                            else -> {}
                        }
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ObsidianSongCard(
    album: Album,
    modifier: Modifier = Modifier,
) {
    val database = LocalDatabase.current
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val artistNameAliases = LocalArtistNameAliases.current

    val dbAlbum by database.album(album.id).collectAsStateWithLifecycle(initialValue = album)
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
    val isPlaying by playerConnection?.isEffectivelyPlaying?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) }

    val activeAlbum = dbAlbum ?: album
    val isActive = activeAlbum.id == mediaMetadata?.album?.id
    val isLiked = activeAlbum.album.bookmarkedAt != null

    val artistText = remember(activeAlbum.artists, artistNameAliases) {
        activeAlbum.artists.joinToString { ArtistNameAliases.resolve(artistNameAliases, it.id, it.name) }
    }

    Column(
        modifier = modifier
            .width(160.dp)
            .combinedClickable(
                onClick = {
                    navController.navigate("album/${activeAlbum.id}")
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        AlbumMenu(
                            originalAlbum = activeAlbum,
                            onDismiss = menuState::dismiss,
                        )
                    }
                }
            )
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(18.dp)),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(activeAlbum.thumbnailUrl?.resize(400, 400))
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            if (isActive && isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(10.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.equalizer),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = activeAlbum.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = artistText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.70f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    database.transaction {
                        update(activeAlbum.album.toggleLike())
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription = null,
                    tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.70f),
                    modifier = Modifier.size(20.dp),
                )
            }

            IconButton(
                onClick = {
                    menuState.show {
                        AlbumMenu(
                            originalAlbum = activeAlbum,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun ObsidianSongCard(
    item: LocalItem,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is Song -> ObsidianSongCard(song = item, modifier = modifier)
        is Album -> ObsidianSongCard(album = item, modifier = modifier)
        is Artist -> ObsidianArtistCard(artist = item, modifier = modifier)
        else -> {}
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ObsidianArtistCard(
    artist: Artist,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(120.dp)
            .combinedClickable(
                onClick = {
                    navController.navigate("artist/${artist.id}")
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        ArtistMenu(
                            originalArtist = artist,
                            coroutineScope = scope,
                            onDismiss = menuState::dismiss,
                        )
                    }
                }
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.artist.thumbnailUrl?.resize(240, 240))
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )

        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.70f),
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ObsidianArtistCard(
    artist: ArtistItem,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(120.dp)
            .combinedClickable(
                onClick = {
                    navController.navigate("artist/${artist.id}")
                },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menuState.show {
                        YouTubeArtistMenu(
                            artist = artist,
                            onDismiss = menuState::dismiss,
                        )
                    }
                }
            )
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(artist.thumbnail?.resize(240, 240))
                .crossfade(true)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = artist.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )

        Text(
            text = "Artist",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.70f),
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}
