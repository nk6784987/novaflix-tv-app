package com.example.ui.screens

import com.example.ui.components.LocalIsTv
import com.example.ui.components.tvFocusRing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.ui.components.pressScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.WatchItemEntity
import com.example.data.model.MediaType
import com.example.ui.theme.*
import com.example.ui.viewmodel.ProfileViewModel

private val HaiFlixRed = Color(0xFFE50914)
private val DarkBackground = Color(0xFF0B0B0B)
private val CardBackground = Color(0xFF101010)
private val BorderColor = Color(0x1AFFFFFF)
private val TextSecondaryColor = Color(0xFFA0A0A0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListScreen(
    profileViewModel: ProfileViewModel,
    onBackClick: () -> Unit,
    onNavigateToDetail: (mediaId: String, mediaType: MediaType) -> Unit,
    onNavigateToPlayer: (mediaId: String, mediaType: MediaType, title: String, posterPath: String?, backdropPath: String?, tmdbId: Long?, season: Int, episode: Int) -> Unit
) {
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredList = remember(uiState.myListItems, selectedFilter) {
        when (selectedFilter) {
            "MOVIE" -> uiState.myListItems.filter { it.mediaType == "MOVIE" }
            "TV" -> uiState.myListItems.filter { it.mediaType == "TV" }
            "ANIME" -> uiState.myListItems.filter { it.mediaType == "ANIME" }
            else -> uiState.myListItems
        }
    }

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
                        Text("My Watchlist", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.tvFocusRing(CircleShape).testTag("mylist_back_button")) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Filter Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == "ALL",
                    onClick = { selectedFilter = "ALL" },
                    label = { Text("All (${uiState.myListItems.size})") },
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
                    selected = selectedFilter == "MOVIE",
                    onClick = { selectedFilter = "MOVIE" },
                    label = { Text("Movies") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HaiFlixRed,
                        selectedLabelColor = Color.White,
                        containerColor = CardBackground,
                        labelColor = TextSecondaryColor
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == "MOVIE",
                        borderColor = BorderColor,
                        selectedBorderColor = HaiFlixRed
                    )
                )
                FilterChip(
                    selected = selectedFilter == "TV",
                    onClick = { selectedFilter = "TV" },
                    label = { Text("Web Series") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HaiFlixRed,
                        selectedLabelColor = Color.White,
                        containerColor = CardBackground,
                        labelColor = TextSecondaryColor
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == "TV",
                        borderColor = BorderColor,
                        selectedBorderColor = HaiFlixRed
                    )
                )
                FilterChip(
                    selected = selectedFilter == "ANIME",
                    onClick = { selectedFilter = "ANIME" },
                    label = { Text("Anime") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = HaiFlixRed,
                        selectedLabelColor = Color.White,
                        containerColor = CardBackground,
                        labelColor = TextSecondaryColor
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedFilter == "ANIME",
                        borderColor = BorderColor,
                        selectedBorderColor = HaiFlixRed
                    )
                )
            }

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Bookmark, contentDescription = null, tint = TextSecondaryColor.copy(alpha = 0.5f), modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No titles saved in this category", color = TextSecondaryColor, fontSize = 16.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        val mediaType = try { MediaType.valueOf(item.mediaType) } catch (e: Exception) { MediaType.MOVIE }
                        WatchLibraryCard(
                            item = item,
                            onClick = { onNavigateToDetail(item.mediaId, mediaType) },
                            onPlayClick = {
                                onNavigateToPlayer(
                                    item.mediaId,
                                    mediaType,
                                    item.title,
                                    item.posterPath,
                                    item.backdropPath,
                                    item.tmdbId,
                                    item.seasonNumber,
                                    item.episodeNumber
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchLibraryCard(
    item: WatchItemEntity,
    onClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Box(
        modifier = Modifier.pressScale(onClick)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .testTag("my_list_item_${item.id}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = item.posterPath ?: item.backdropPath,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // TV: OK on the card opens details; the extra play overlay is touch-only
                if (!LocalIsTv.current) IconButton(
                    onClick = onPlayClick,
                    modifier = Modifier.tvFocusRing(CircleShape)
                        .align(Alignment.Center)
                        .background(HaiFlixRed.copy(alpha = 0.9f), shape = CircleShape)
                        .size(38.dp)
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.mediaType,
                    color = HaiFlixRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
