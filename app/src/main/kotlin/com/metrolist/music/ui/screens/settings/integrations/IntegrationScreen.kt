/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings.integrations

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import coil3.compose.AsyncImage
import com.metrolist.music.utils.YouTubeVideoDownloader
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.IntegrationCard
import com.metrolist.music.ui.component.IntegrationCardItem
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.PlaylistLinkImporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationScreen(
    navController: NavController,
) {
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Spotify, 1: YouTube Music
    var importUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }
    var successMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val spotifyColor = Color(0xFF1DB954)
    val youtubeColor = Color(0xFFFF0000)
    val activeColor = if (selectedTab == 0) spotifyColor else youtubeColor

    fun triggerImport() {
        if (importUrl.isBlank() || isLoading) return
        keyboardController?.hide()
        isLoading = true
        errorMessage = null
        successMessage = null
        progressStatus = "Connecting…"

        coroutineScope.launch {
            val result = if (selectedTab == 0) {
                PlaylistLinkImporter.importSpotifyPlaylist(
                    urlOrId = importUrl,
                    database = database,
                    onProgress = { current, total ->
                        progressStatus = "Searching tracks ($current/$total)…"
                    },
                )
            } else {
                PlaylistLinkImporter.importYouTubePlaylist(
                    urlOrId = importUrl,
                    database = database,
                    onProgress = { _, _ ->
                        progressStatus = "Loading playlist tracks…"
                    },
                )
            }

            isLoading = false
            result.onSuccess { res ->
                successMessage = "Imported ${res.importedCount} of ${res.totalCount} tracks into '${res.playlistTitle}'!"
                importUrl = ""
            }.onFailure { err ->
                errorMessage = err.localizedMessage ?: "Failed to import playlist"
            }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        // --- Import Playlist via Link Section (Obsidian Music Style) ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.import_playlist_via_link),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.import_playlist_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(18.dp))

                // Segmented Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // Spotify Tab
                    val spotifyTabBg by animateColorAsState(
                        targetValue = if (selectedTab == 0) spotifyColor else Color.Transparent,
                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                        label = "spotifyTabBg",
                    )
                    val spotifyTabContent by animateColorAsState(
                        targetValue = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(250),
                        label = "spotifyTabContent",
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(spotifyTabBg)
                            .clickable {
                                selectedTab = 0
                                errorMessage = null
                                successMessage = null
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_spotify),
                            contentDescription = null,
                            tint = if (selectedTab == 0) Color.White else spotifyColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.spotify),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = spotifyTabContent,
                        )
                    }

                    // YouTube Music Tab
                    val ytTabBg by animateColorAsState(
                        targetValue = if (selectedTab == 1) youtubeColor else Color.Transparent,
                        animationSpec = tween(250, easing = FastOutSlowInEasing),
                        label = "ytTabBg",
                    )
                    val ytTabContent by animateColorAsState(
                        targetValue = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = tween(250),
                        label = "ytTabContent",
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(ytTabBg)
                            .clickable {
                                selectedTab = 1
                                errorMessage = null
                                successMessage = null
                            }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_youtube),
                            contentDescription = null,
                            tint = if (selectedTab == 1) Color.White else youtubeColor,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.youtube_music),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = ytTabContent,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Input Field with Paste / Clear Buttons
                OutlinedTextField(
                    value = importUrl,
                    onValueChange = { newUrl ->
                        importUrl = newUrl
                        errorMessage = null
                        successMessage = null
                        if (newUrl.contains("open.spotify.com")) {
                            selectedTab = 0
                        } else if (newUrl.contains("youtube.com") || newUrl.contains("youtu.be")) {
                            selectedTab = 1
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    placeholder = {
                        Text(
                            text = if (selectedTab == 0) {
                                stringResource(R.string.spotify_link_hint)
                            } else {
                                stringResource(R.string.youtube_link_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.link),
                            contentDescription = null,
                            tint = activeColor,
                            modifier = Modifier.size(20.dp),
                        )
                    },
                    trailingIcon = {
                        if (importUrl.isNotEmpty()) {
                            androidx.compose.material3.IconButton(onClick = {
                                importUrl = ""
                                errorMessage = null
                                successMessage = null
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else {
                            androidx.compose.material3.IconButton(onClick = {
                                val clipText = clipboardManager.getText()?.text
                                if (!clipText.isNullOrBlank()) {
                                    importUrl = clipText.trim()
                                    if (importUrl.contains("open.spotify.com")) {
                                        selectedTab = 0
                                    } else if (importUrl.contains("youtube.com") || importUrl.contains("youtu.be")) {
                                        selectedTab = 1
                                    }
                                }
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.content_copy),
                                    contentDescription = "Paste",
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { triggerImport() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = activeColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                Spacer(Modifier.height(14.dp))

                // Action Button
                Button(
                    onClick = { triggerImport() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = activeColor,
                        contentColor = Color.White,
                    ),
                    enabled = importUrl.isNotBlank() && !isLoading,
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = progressStatus.ifBlank { stringResource(R.string.importing_playlist) },
                            fontWeight = FontWeight.SemiBold,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.download),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.import_playlist),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // Feedback: Success Message
                AnimatedVisibility(
                    visible = successMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    successMessage?.let { msg ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .border(1.dp, spotifyColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                            color = spotifyColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.check),
                                    contentDescription = null,
                                    tint = spotifyColor,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }

                // Feedback: Error Message
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    errorMessage?.let { err ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.error),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // --- YouTube Video Downloader Card (up to 4K UHD) ---
        var ytVideoUrl by remember { mutableStateOf("") }
        var isFetchingVideo by remember { mutableStateOf(false) }
        var isDownloadingVideo by remember { mutableStateOf(false) }
        var downloadProgress by remember { mutableFloatStateOf(0f) }
        var fetchedVideoInfo by remember { mutableStateOf<YouTubeVideoDownloader.YouTubeVideoInfo?>(null) }
        var selectedFormatIndex by remember { mutableIntStateOf(0) }
        var videoSuccessUri by remember { mutableStateOf<Uri?>(null) }
        var videoErrorMessage by remember { mutableStateOf<String?>(null) }
        val context = androidx.compose.ui.platform.LocalContext.current

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.youtube_video_downloader),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.youtube_video_downloader_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(16.dp))

                // Input field
                OutlinedTextField(
                    value = ytVideoUrl,
                    onValueChange = {
                        ytVideoUrl = it
                        videoErrorMessage = null
                    },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.paste_youtube_link),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    trailingIcon = {
                        if (ytVideoUrl.isNotEmpty()) {
                            androidx.compose.material3.IconButton(onClick = {
                                ytVideoUrl = ""
                                fetchedVideoInfo = null
                                videoSuccessUri = null
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            androidx.compose.material3.IconButton(onClick = {
                                val clipText = clipboardManager.getText()?.text
                                if (!clipText.isNullOrBlank()) {
                                    ytVideoUrl = clipText.trim()
                                }
                            }) {
                                Icon(
                                    painter = painterResource(R.drawable.content_copy),
                                    contentDescription = "Paste",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))

                // Fetch Button
                Button(
                    onClick = {
                        val videoId = YouTubeVideoDownloader.extractVideoId(ytVideoUrl)
                        if (videoId == null) {
                            videoErrorMessage = context.getString(R.string.error_invalid_youtube_link)
                            return@Button
                        }
                        keyboardController?.hide()
                        isFetchingVideo = true
                        videoErrorMessage = null
                        videoSuccessUri = null

                        coroutineScope.launch {
                            val res = YouTubeVideoDownloader.fetchVideoDetails(videoId)
                            isFetchingVideo = false
                            res.onSuccess { info ->
                                fetchedVideoInfo = info
                                selectedFormatIndex = 0
                            }.onFailure { err ->
                                videoErrorMessage = err.localizedMessage ?: "Failed to fetch video"
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = ytVideoUrl.isNotBlank() && !isFetchingVideo && !isDownloadingVideo,
                ) {
                    if (isFetchingVideo) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.fetching_video), fontWeight = FontWeight.SemiBold)
                    } else {
                        Icon(painter = painterResource(R.drawable.search), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.fetch_video), fontWeight = FontWeight.Bold)
                    }
                }

                // Video Info Preview & Resolution Selection
                fetchedVideoInfo?.let { info ->
                    Spacer(Modifier.height(16.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AsyncImage(
                                    model = info.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(72.dp, 48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = info.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = info.author,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.select_resolution),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))

                            // Resolution Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                info.formats.forEachIndexed { index, format ->
                                    val sizeMb = format.approximateSizeBytes / (1024 * 1024)
                                    val isSelected = index == selectedFormatIndex
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedFormatIndex = index },
                                        label = {
                                            Text("${format.qualityLabel}${if (format.fps != null && format.fps > 30) "p${format.fps}" else ""} (~${sizeMb}MB)")
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Download Button
                            Button(
                                onClick = {
                                    val format = info.formats.getOrNull(selectedFormatIndex) ?: return@Button
                                    isDownloadingVideo = true
                                    downloadProgress = 0f
                                    videoErrorMessage = null
                                    videoSuccessUri = null

                                    coroutineScope.launch {
                                        val res = YouTubeVideoDownloader.downloadVideo(
                                            context = context,
                                            videoInfo = info,
                                            selectedFormat = format,
                                            onProgress = { p -> downloadProgress = p }
                                        )
                                        isDownloadingVideo = false
                                        res.onSuccess { uri ->
                                            videoSuccessUri = uri
                                        }.onFailure { err ->
                                            videoErrorMessage = err.localizedMessage ?: "Failed to download video"
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                enabled = !isDownloadingVideo,
                            ) {
                                if (isDownloadingVideo) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text("${(downloadProgress * 100).toInt()}% Downloading…", fontWeight = FontWeight.SemiBold)
                                } else {
                                    Icon(painter = painterResource(R.drawable.download), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.download_video), fontWeight = FontWeight.Bold)
                                }
                            }

                            if (isDownloadingVideo) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Download Success Notice
                AnimatedVisibility(visible = videoSuccessUri != null) {
                    videoSuccessUri?.let { uri ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .border(1.dp, spotifyColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
                            color = spotifyColor.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(R.drawable.check),
                                        contentDescription = null,
                                        tint = spotifyColor,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.download_complete),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Button(
                                        onClick = {
                                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "video/mp4")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(viewIntent, "Open Video"))
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = spotifyColor, contentColor = Color.White),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.open_video), fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "video/mp4"
                                                putExtra(Intent.EXTRA_STREAM, uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(stringResource(R.string.share_video), fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Error Notice
                AnimatedVisibility(visible = videoErrorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.error),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = videoErrorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Existing General Integrations
        IntegrationCard(
            title = stringResource(R.string.general),
            items = listOf(
                IntegrationCardItem(
                    icon = painterResource(R.drawable.discord),
                    title = { Text(stringResource(R.string.discord_integration)) },
                    onClick = { navController.navigate("settings/integrations/discord") },
                ),
                IntegrationCardItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text(stringResource(R.string.lastfm_integration)) },
                    onClick = {
                        navController.navigate("settings/integrations/lastfm")
                    },
                ),
            ),
        )
    }

    TopAppBar(
        title = { Text(stringResource(R.string.integrations)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}
