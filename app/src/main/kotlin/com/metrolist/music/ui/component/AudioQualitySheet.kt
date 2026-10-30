package com.metrolist.music.ui.component

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R
import com.metrolist.music.utils.DolbyDetector
import com.metrolist.music.utils.DolbyHardwareReport
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioQualitySheet(
    currentFormatText: String,
    onDismissRequest: () -> Unit,
    onQualitySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val hazeState = LocalHazeState.current
    var dolbyReport by remember { mutableStateOf<DolbyHardwareReport?>(null) }
    var isCheckingDolby by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val qualityOptions = listOf(
        Triple("True Dolby Atmos", "Hardware Dolby Atmos object sound & spatial metadata", "DOLBY"),
        Triple("Mercury Atmos", "Spatial audio expansion for any stereo headphone", "DOLBY"),
        Triple("Lossless FLAC 24-bit", "Studio Master quality • up to 96kHz / 24-bit", "FLAC"),
        Triple("Lossless FLAC 16-bit", "Bit-perfect CD quality • 44.1kHz / 16-bit", "FLAC"),
        Triple("OPUS 160 kbps", "High quality transparent audio • default", "OPUS"),
        Triple("OPUS 96 kbps", "Balanced fidelity with minimal data consumption", "OPUS"),
        Triple("OPUS 48 kbps", "Extreme Data Saver mode • ultra low bandwidth", "SAVER"),
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF0C0D14).copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column {
                    Text(
                        text = "Audio Engine & Quality",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = "High-definition sound processing options",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp
                        )
                    )
                }
                ObsidianAudioBadge(
                    formatText = currentFormatText,
                    onClick = null
                )
            }

            // Quality Options List
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                qualityOptions.forEach { (title, subtitle, badgeType) ->
                    val isSelected = currentFormatText.contains(title.take(8), ignoreCase = true) ||
                            (title.contains("Dolby", ignoreCase = true) && currentFormatText.contains("Dolby", ignoreCase = true)) ||
                            (title.contains("48", ignoreCase = true) && currentFormatText.contains("48", ignoreCase = true))

                    val itemShape = RoundedCornerShape(16.dp)
                    val borderBrush = if (isSelected) {
                        when (badgeType) {
                            "DOLBY" -> Brush.horizontalGradient(listOf(Color(0xFF00F0FF), Color(0xFF8B5CF6), Color(0xFFFF007F)))
                            "FLAC" -> Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFF59E0B)))
                            "SAVER" -> Brush.horizontalGradient(listOf(Color(0xFF10B981), Color(0xFF059669)))
                            else -> Brush.horizontalGradient(listOf(Color.White, Color.White.copy(alpha = 0.5f)))
                        }
                    } else {
                        Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.04f)))
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(itemShape)
                            .background(if (isSelected) Color(0xFF161826) else Color(0xFF10121C).copy(alpha = 0.7f))
                            .border(width = if (isSelected) 1.5.dp else 1.dp, brush = borderBrush, shape = itemShape)
                            .clickable {
                                onQualitySelected(title)
                                scope.launch {
                                    sheetState.hide()
                                    onDismissRequest()
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
                                        fontSize = 15.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.White.copy(alpha = 0.50f),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Dolby Hardware Diagnostic Section
            val diagShape = RoundedCornerShape(18.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(diagShape)
                    .background(Color(0xFF131522).copy(alpha = 0.85f))
                    .border(width = 1.dp, color = Color.White.copy(alpha = 0.12f), shape = diagShape)
                    .padding(16.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(
                                text = "Dolby Hardware Diagnostics",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            )
                            Text(
                                text = "Inspect hardware decoders & spatial audio",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .clickable {
                                    isCheckingDolby = true
                                    dolbyReport = DolbyDetector.checkHardwareSupport(context)
                                    isCheckingDolby = false
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "RUN TEST",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = dolbyReport != null,
                        enter = fadeIn() + expandVertically()
                    ) {
                        dolbyReport?.let { report ->
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = report.details,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (report.isSupported) Color(0xFF34D399) else Color(0xFFFBBF24),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                )
                                if (report.matchedFeatures.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    report.matchedFeatures.forEach { feature ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF34D399))
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = feature,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 12.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
