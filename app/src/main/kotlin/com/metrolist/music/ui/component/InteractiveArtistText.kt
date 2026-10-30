package com.metrolist.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.models.MediaMetadata

/**
 * 1:1 port of Obsidian's InteractiveArtistText (PlayerScreen.kt:1829).
 * Parses artist lists into individual interactive chips with soft ripples
 * that navigate to artist pages, with an expandable "+N more" glass dropdown
 * when multiple artists are credited.
 */
@Composable
fun InteractiveArtistText(
    artists: List<MediaMetadata.Artist>,
    onArtistClick: (MediaMetadata.Artist) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.7f),
) {
    val validArtists = remember(artists) {
        artists.filter { it.name.isNotBlank() }
    }

    if (validArtists.isEmpty()) {
        Text(
            text = "Unknown",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = color,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }

    if (validArtists.size <= 2) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            validArtists.forEachIndexed { index, artist ->
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = color,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = Color.White),
                            onClick = { onArtistClick(artist) },
                        )
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                )

                if (index < validArtists.lastIndex) {
                    Text(
                        text = ", ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = color.copy(alpha = 0.45f),
                        ),
                    )
                }
            }
        }
    } else {
        var expanded by remember { mutableStateOf(false) }

        Box(modifier = modifier) {
            Row(
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // First artist
                val firstArtist = validArtists.first()
                Text(
                    text = firstArtist.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = color,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = Color.White),
                            onClick = { onArtistClick(firstArtist) },
                        )
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                )

                Text(
                    text = " & ",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        color = color.copy(alpha = 0.45f),
                    ),
                )

                // Expandable "+N more" pill
                Text(
                    text = "+${validArtists.size - 1} more",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true),
                            onClick = { expanded = true },
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .obsidianGlassEffect(blurRadius = 20.dp),
            ) {
                validArtists.forEach { artist ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                ),
                            )
                        },
                        onClick = {
                            expanded = false
                            onArtistClick(artist)
                        },
                        colors = MenuDefaults.itemColors(
                            textColor = Color.White,
                        ),
                    )
                }
            }
        }
    }
}
