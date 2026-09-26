package com.example.ui.screens

import com.example.ui.components.TvHeroBanner
import com.example.ui.components.LocalIsTv
import com.example.ui.components.tvFocusRing
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.ui.components.AppBottomBar
import com.example.ui.components.BottomTab
import com.example.ui.components.SkeletonScreen
import com.example.ui.components.UserAvatar
import com.example.ui.components.bestName
import com.example.ui.components.bestPhotoUrl
import com.example.ui.components.pressScale
import com.example.ui.components.rememberFirebaseUser
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.viewmodel.AnimeViewModel
import kotlinx.coroutines.delay

private val NovaFlixRed = Color(0xFFE50914)
private val NovaFlixRedGlow = Color(0x80E50914)
private val HaiFlixRed = NovaFlixRed
private val HaiFlixRedGlow = NovaFlixRedGlow
private val DarkBackground = Color(0xFF0B0B0B)
private val CardBackground = Color(0xFF101010)
private val BorderColor = Color(0x1AFFFFFF)
private val TextSecondaryColor = Color(0xFFA0A0A0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeScreen(
    viewModel: AnimeViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToSeries: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDetail: (mediaId: String, mediaType: MediaType) -> Unit,
    onNavigateToPlayer: (mediaId: String, mediaType: MediaType, title: String, posterPath: String?, backdropPath: String?, tmdbId: Long?, season: Int, episode: Int) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadAnimeData(force = true)
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
            ) {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Brush.linearGradient(listOf(NovaFlixRed, Color(0xFFFF4B8B)))),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = "NOVAFLIX",
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp,
                                letterSpacing = 1.sp,
                                color = Color.White
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NovaFlixRed)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "ANIME",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = onNavigateToProfile,
                            modifier = Modifier.tvFocusRing(CircleShape)
                                .testTag("anime_profile_button")
                                .padding(end = 8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CardBackground)
                                .border(1.dp, BorderColor, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Profile",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
                )

                // Search Bar
                AnimeSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onClear = { viewModel.clearSearch() },
                    isSearching = uiState.isSearching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }
        },
        bottomBar = {
            AppBottomBar(
                selected = BottomTab.ANIME,
                onHome = onNavigateToHome,
                onSeries = onNavigateToSeries,
                onAnime = { },
                onDownloads = onNavigateToDownloads,
                onProfile = onNavigateToProfile
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        if (uiState.searchQuery.isNotBlank()) {
            AnimeSearchLiveResultsView(
                query = uiState.searchQuery,
                isSearching = uiState.isSearching,
                results = uiState.searchResults,
                onClearSearch = { viewModel.clearSearch() },
                onMediaClick = { item ->
                    onNavigateToDetail(item.id, item.mediaType)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            )
        } else if (uiState.isLoading) {
            SkeletonScreen(modifier = Modifier.padding(paddingValues))
        } else if (uiState.error != null && uiState.categorySections.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = HaiFlixRed,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Unable to load Anime",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.error ?: "Please check your network and try again.",
                        color = TextSecondaryColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        modifier = Modifier.tvFocusRing(RoundedCornerShape(20.dp)),
                        onClick = { viewModel.loadAnimeData(force = true) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HaiFlixRed),
                        border = BorderStroke(1.dp, HaiFlixRed),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 64.dp)
            ) {
                // Anime Hero Banner
                if (uiState.heroBannerItems.isNotEmpty() && uiState.selectedCategory == "All") {
                    item {
                        AnimeHeroBanner(
                            items = uiState.heroBannerItems,
                            onPlayClick = { item ->
                                onNavigateToPlayer(
                                    item.id,
                                    item.mediaType,
                                    item.title,
                                    item.posterPath,
                                    item.backdropPath,
                                    item.tmdbId,
                                    1,
                                    1
                                )
                            },
                            onDetailClick = { item ->
                                onNavigateToDetail(item.id, item.mediaType)
                            }
                        )
                    }
                }

                // Category Chips
                if (uiState.categories.isNotEmpty()) {
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.categories) { category ->
                                val isSelected = uiState.selectedCategory == category
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSelected) HaiFlixRed else CardBackground)
                                        .border(
                                            1.dp,
                                            if (isSelected) HaiFlixRed else BorderColor,
                                            RoundedCornerShape(20.dp)
                                        )
                                        .tvFocusRing(RoundedCornerShape(20.dp)).clickable { viewModel.selectCategory(category) }
                                        .padding(horizontal = 18.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = category,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else TextSecondaryColor
                                    )
                                }
                            }
                        }
                    }
                }

                // Top 10 Anime Today
                if (uiState.top10Anime.isNotEmpty() && uiState.selectedCategory == "All") {
                    item {
                        Column(modifier = Modifier.padding(vertical = 12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .height(18.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(HaiFlixRed)
                                )
                                Text(
                                    text = "Top 10 Anime Today",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(uiState.top10Anime.take(10).withIndex().toList()) { (index, item) ->
                                    Top10MediaCard(
                                        rank = index + 1,
                                        media = item,
                                        onClick = { onNavigateToDetail(item.id, item.mediaType) }
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic Category Sections from Admin
                val filteredSections = if (uiState.selectedCategory == "All") {
                    uiState.categorySections
                } else {
                    val directMatch = uiState.categorySections.filter { it.categoryName.equals(uiState.selectedCategory, ignoreCase = true) }
                    if (directMatch.isNotEmpty()) {
                        directMatch
                    } else {
                        val selectedCat = uiState.selectedCategory.lowercase()
                        val filteredItems = uiState.categorySections.flatMap { it.items }.distinctBy { it.id }.filter { item ->
                            item.genres.any { it.equals(selectedCat, ignoreCase = true) }
                        }
                        if (filteredItems.isNotEmpty()) {
                            listOf(com.example.ui.viewmodel.AdminCategorySection(uiState.selectedCategory, filteredItems))
                        } else {
                            emptyList()
                        }
                    }
                }

                items(filteredSections) { section ->
                    Column(modifier = Modifier.padding(vertical = 10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(18.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(HaiFlixRed)
                            )
                            Text(
                                text = section.categoryName,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(section.items) { item ->
                                AnimeCard(
                                    media = item,
                                    onClick = { onNavigateToDetail(item.id, item.mediaType) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (query.isNotEmpty()) HaiFlixRed else TextSecondaryColor,
                modifier = Modifier.size(20.dp)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag("anime_search_input"),
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(HaiFlixRed),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "Search anime series, movies, genres...",
                            color = TextSecondaryColor,
                            fontSize = 14.sp
                        )
                    }
                    innerTextField()
                }
            )
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = HaiFlixRed
                )
            } else if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = TextSecondaryColor,
                    modifier = Modifier
                        .size(18.dp)
                        .tvFocusRing(RoundedCornerShape(10.dp)).clickable { onClear() }
                )
            }
        }
    }
}

@Composable
private fun AnimeHeroBanner(
    items: List<MediaItem>,
    onPlayClick: (MediaItem) -> Unit,
    onDetailClick: (MediaItem) -> Unit
) {
    if (LocalIsTv.current) {
        TvHeroBanner(items = items, tagLabel = "FEATURED ANIME", onPlay = onPlayClick, onDetails = onDetailClick)
        return
    }
    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(pagerState) {
        while (true) {
            delay(5000)
            if (items.size > 1) {
                val next = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(next)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
        ) { page ->
            val media = items[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .tvFocusRing(RoundedCornerShape(10.dp)).clickable { onDetailClick(media) }
            ) {
                AsyncImage(
                    model = media.backdropPath ?: media.posterPath,
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    DarkBackground.copy(alpha = 0.5f),
                                    DarkBackground
                                ),
                                startY = 100f
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(HaiFlixRed)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "ANIME",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        if (media.rating > 0) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFB800),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = String.format("%.1f", media.rating),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (!media.releaseYear.isNullOrBlank()) {
                            Text(
                                text = media.releaseYear,
                                color = TextSecondaryColor,
                                fontSize = 12.sp
                            )
                        }
                        if ((media.totalEpisodes ?: 0) > 0) {
                            Text(
                                text = "${media.totalEpisodes} Eps",
                                color = TextSecondaryColor,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = media.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (media.genres.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = media.genres.take(3).joinToString(" • "),
                            color = TextSecondaryColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onPlayClick(media) },
                            colors = ButtonDefaults.buttonColors(containerColor = HaiFlixRed),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(20.dp)).shadow(8.dp, RoundedCornerShape(20.dp), spotColor = HaiFlixRedGlow)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Play",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }

                        OutlinedButton(
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(20.dp)),
                            onClick = { onDetailClick(media) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = BorderStroke(1.dp, BorderColor),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Details",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }

        // Pager indicators
        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(items.size) { iteration ->
                    val color = if (pagerState.currentPage == iteration) HaiFlixRed else TextSecondaryColor.copy(alpha = 0.4f)
                    val width = if (pagerState.currentPage == iteration) 18.dp else 6.dp
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .clip(CircleShape)
                            .background(color)
                            .height(6.dp)
                            .width(width)
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimeCard(
    media: MediaItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.pressScale(onClick)
            .width(125.dp)
    ) {
        Box(
            modifier = Modifier
                .width(125.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CardBackground)
                .border(1.dp, BorderColor, RoundedCornerShape(8.dp))
        ) {
            AsyncImage(
                model = media.posterPath ?: media.backdropPath,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (media.rating > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFB800),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = String.format("%.1f", media.rating),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = media.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!media.releaseYear.isNullOrBlank() || (media.totalEpisodes ?: 0) > 0) {
            val subtitle = buildString {
                if (!media.releaseYear.isNullOrBlank()) append(media.releaseYear)
                if ((media.totalEpisodes ?: 0) > 0) {
                    if (isNotEmpty()) append(" • ")
                    append("${media.totalEpisodes} Eps")
                }
            }
            Text(
                text = subtitle,
                color = TextSecondaryColor,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AnimeSearchLiveResultsView(
    query: String,
    isSearching: Boolean,
    results: List<MediaItem>,
    onClearSearch: () -> Unit,
    onMediaClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (isSearching) "Searching '$query'..." else "${results.size} Results for '$query'",
                color = TextSecondaryColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "Clear",
                color = HaiFlixRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.tvFocusRing(RoundedCornerShape(10.dp)).clickable { onClearSearch() }
            )
        }

        if (results.isEmpty() && !isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = TextSecondaryColor,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No anime found matching \"$query\"",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = if (LocalIsTv.current) GridCells.Adaptive(140.dp) else GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 64.dp)
            ) {
                items(results) { media ->
                    AnimeCard(
                        media = media,
                        onClick = { onMediaClick(media) }
                    )
                }
            }
        }
    }
}
