package com.example.ui.screens

import com.example.ui.components.tvFocusRing
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.DownloadEntity
import com.example.data.model.MediaType
import com.example.ui.viewmodel.DownloadsViewModel
import java.util.Locale

private val HaiFlixRed = Color(0xFFE50914)
private val DarkBackground = Color(0xFF0B0B0B)
private val CardBackground = Color(0xFF101010)
private val BorderColor = Color(0x1AFFFFFF)
private val TextSecondaryColor = Color(0xFFA0A0A0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onBackClick: () -> Unit,
    onPlayOffline: (mediaId: String, mediaType: MediaType, title: String, posterPath: String?, backdropPath: String?, tmdbId: Long?, season: Int, episode: Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredList = remember(uiState.downloads, selectedFilter) {
        when (selectedFilter) {
            "COMPLETED" -> uiState.downloads.filter { it.status == "COMPLETED" }
            "DOWNLOADING" -> uiState.downloads.filter { it.status == "DOWNLOADING" || it.status == "QUEUED" || it.status == "PAUSED" }
            else -> uiState.downloads
        }
    }

    val totalStorageGb = String.format(Locale.US, "%.1f GB", uiState.storageInfo.totalSpaceBytes / (1024.0 * 1024.0 * 1024.0))
    val freeStorageGb = String.format(Locale.US, "%.1f GB", uiState.storageInfo.freeSpaceBytes / (1024.0 * 1024.0 * 1024.0))
    val appMb = String.format(Locale.US, "%.1f MB", uiState.storageInfo.appDownloadsBytes / (1024.0 * 1024.0))

    val storageRatio = if (uiState.storageInfo.totalSpaceBytes > 0) {
        (uiState.storageInfo.appDownloadsBytes.toFloat() / uiState.storageInfo.totalSpaceBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(HaiFlixRed)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Downloads & Storage",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.tvFocusRing(CircleShape).testTag("downloads_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Storage Overview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CardBackground)
                    .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.SdStorage,
                                contentDescription = null,
                                tint = Color(0xFFE2E8F0),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Device Storage",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }
                        Text(
                            text = "$freeStorageGb free of $totalStorageGb",
                            color = TextSecondaryColor,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { storageRatio.coerceAtLeast(0.02f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = HaiFlixRed,
                        trackColor = Color.White.copy(alpha = 0.10f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "HaiFlix Downloads: $appMb",
                            color = HaiFlixRed,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "${uiState.downloads.size} items",
                            color = TextSecondaryColor,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${uiState.downloads.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HaiFlixRed,
                        selectedLabelColor = Color.White,
                        containerColor = CardBackground,
                        labelColor = TextSecondaryColor
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == "ALL",
                        borderColor = BorderColor,
                        selectedBorderColor = HaiFlixRed
                    )
                )
                FilterChip(
                    selected = selectedFilter == "COMPLETED",
                    onClick = { selectedFilter = "COMPLETED" },
                    label = { Text("Downloaded") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HaiFlixRed,
                        selectedLabelColor = Color.White,
                        containerColor = CardBackground,
                        labelColor = TextSecondaryColor
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == "COMPLETED",
                        borderColor = BorderColor,
                        selectedBorderColor = HaiFlixRed
                    )
                )
                FilterChip(
                    selected = selectedFilter == "DOWNLOADING",
                    onClick = { selectedFilter = "DOWNLOADING" },
                    label = { Text("In Queue") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HaiFlixRed,
                        selectedLabelColor = Color.White,
                        containerColor = CardBackground,
                        labelColor = TextSecondaryColor
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == "DOWNLOADING",
                        borderColor = BorderColor,
                        selectedBorderColor = HaiFlixRed
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Download List or Empty State
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DownloadForOffline,
                            contentDescription = null,
                            tint = TextSecondaryColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Downloads Found",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Movies and episodes you download will appear here for offline viewing without internet.",
                            color = TextSecondaryColor,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredList, key = { it.id }) { download ->
                        DownloadItemRow(
                            download = download,
                            onPlayClick = {
                                val type = try {
                                    MediaType.valueOf(download.mediaType)
                                } catch (e: Exception) {
                                    MediaType.MOVIE
                                }
                                onPlayOffline(
                                    download.mediaId,
                                    type,
                                    download.title,
                                    download.posterPath,
                                    download.backdropPath,
                                    download.tmdbId,
                                    download.seasonNumber,
                                    download.episodeNumber
                                )
                            },
                            onPauseClick = { viewModel.pauseDownload(download.id) },
                            onDeleteClick = { viewModel.deleteDownload(download.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadItemRow(
    download: DownloadEntity,
    onPlayClick: () -> Unit,
    onPauseClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .testTag("download_item_${download.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster Image
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(115.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = download.posterPath ?: download.backdropPath,
                    contentDescription = download.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (download.status == "COMPLETED") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .tvFocusRing(RoundedCornerShape(10.dp)).clickable { onPlayClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = HaiFlixRed,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play Offline",
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(6.dp)
                                    .fillMaxSize()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info Column
            Column(
                modifier = Modifier
                    .weight(1f)
            ) {
                Text(
                    text = download.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                val subText = if (download.mediaType == "MOVIE") {
                    "Movie • Offline HD"
                } else {
                    "S${download.seasonNumber} E${download.episodeNumber} ${download.episodeTitle ?: ""}"
                }

                Text(
                    text = subText,
                    color = TextSecondaryColor,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Download Progress or Completed Status
                if (download.status == "DOWNLOADING") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { download.progressPercent / 100f },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape),
                            color = HaiFlixRed,
                            trackColor = Color.White.copy(alpha = 0.10f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${download.progressPercent}%",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (download.status == "PAUSED") {
                    Text(
                        text = "Paused • ${download.progressPercent}%",
                        color = Color(0xFFF59E0B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else if (download.status == "COMPLETED") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Downloaded • Ready Offline",
                            color = Color(0xFF10B981),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    Text(
                        text = "Queued for download",
                        color = TextSecondaryColor,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (download.status == "DOWNLOADING") {
                    IconButton(modifier = Modifier.tvFocusRing(CircleShape), onClick = onPauseClick) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = Color.White
                        )
                    }
                } else if (download.status == "COMPLETED") {
                    IconButton(modifier = Modifier.tvFocusRing(CircleShape), onClick = onPlayClick) {
                        Icon(
                            imageVector = Icons.Default.PlayCircle,
                            contentDescription = "Play",
                            tint = HaiFlixRed
                        )
                    }
                }

                IconButton(modifier = Modifier.tvFocusRing(CircleShape), onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = Color(0xFFEF4444)
                    )
                }
            }
        }
    }
}
