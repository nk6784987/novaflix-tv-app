package com.example.ui.screens

import com.example.ui.components.tvAutoFocus
import com.example.ui.components.LocalIsTv
import com.example.ui.components.tvFocusRing
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.ui.components.pressScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.CastMember
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.theme.*
import com.example.ui.viewmodel.DetailViewModel

// Exact CSS-equivalent colors from HTML template
private val HaiFlixRed = Color(0xFFE50914)
private val HaiFlixRedGlow = Color(0x80E50914)
private val DarkBackground = Color(0xFF0B0B0B)
private val CardBackground = Color(0xFF101010)
private val BorderColor = Color(0x1AFFFFFF)
private val TextSecondaryColor = Color(0xFFA0A0A0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    mediaId: String,
    mediaType: MediaType,
    viewModel: DetailViewModel,
    onBackClick: () -> Unit,
    onPlayClick: (season: Int, episode: Int) -> Unit,
    onStartDownload: (mediaId: String, tmdbId: Long?, title: String, posterPath: String?, backdropPath: String?, mediaType: MediaType, season: Int, episode: Int, episodeTitle: String?) -> Unit,
    onNavigateToDetail: ((String, MediaType) -> Unit)? = null,
    onWatchOnlineClick: ((season: Int, episode: Int) -> Unit)? = null
) {
    val context = LocalContext.current
    val isTv = LocalIsTv.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showTrailerModal by remember { mutableStateOf(false) }

    LaunchedEffect(mediaId, mediaType) {
        viewModel.loadDetails(mediaId, mediaType)
    }

    val item = uiState.mediaItem

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = HaiFlixRed)
            }
        } else if (uiState.error != null || item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = HaiFlixRed,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = uiState.error ?: "Content details not found.",
                        color = Color.White,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 30.dp)
            ) {
                // 1. DETAIL BACKDROP (height: 250px with gradient to bottom)
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isTv) 220.dp else 260.dp)
                    ) {
                        AsyncImage(
                            model = item.getFullBackdropUrl() ?: item.getFullPosterUrl(),
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // .detail-backdrop::after gradient
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Transparent,
                                            0.55f to Color.Transparent,
                                            0.85f to DarkBackground.copy(alpha = 0.8f),
                                            1.0f to DarkBackground
                                        )
                                    )
                                )
                        )
                    }
                }

                // 2. DETAIL CONTENT (.detail-content padding: 15px)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isTv) 28.dp else 15.dp)
                    ) {
                        // Title: font-size 26px, font-weight 700
                        Text(
                            text = item.title,
                            fontSize = 26.sp,
                            fontWeight = FontWeight(700),
                            color = Color.White,
                            lineHeight = 31.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Meta info: ★ Rating ・ Year ・ Category
                        val ratingStr = if (item.rating > 0.0) String.format("%.1f", item.rating) else "N/A"
                        val yearStr = item.releaseYear.ifBlank { "Unknown Year" }
                        val genreStr = item.genres.firstOrNull()?.let { " ・ $it" } ?: ""

                        Text(
                            text = "★ $ratingStr ・ $yearStr$genreStr",
                            fontSize = 15.sp,
                            fontWeight = FontWeight(500),
                            color = TextSecondaryColor,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        // PLAY BUTTON (.btn-play-full: red rounded pill with glow)
                        Button(
                            onClick = { onPlayClick(uiState.selectedSeason, 1) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = HaiFlixRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(50.dp),
                            modifier = Modifier.tvAutoFocus(isTv).tvFocusRing(RoundedCornerShape(50.dp))
                                .then(if (isTv) Modifier.width(260.dp) else Modifier.fillMaxWidth())
                                .height(52.dp)
                                .shadow(
                                    elevation = 15.dp,
                                    shape = RoundedCornerShape(50.dp),
                                    ambientColor = HaiFlixRed,
                                    spotColor = HaiFlixRed
                                )
                                .testTag("play-btn")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "PLAY",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight(700),
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        if (onWatchOnlineClick != null) {
                            Spacer(modifier = Modifier.height(10.dp))

                            // WATCH ONLINE (Viduki servers) — independent embed provider,
                            // separate from the native ExoPlayer sources above.
                            OutlinedButton(
                                onClick = { onWatchOnlineClick(uiState.selectedSeason, 1) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(50.dp),
                                modifier = Modifier.tvFocusRing(RoundedCornerShape(50.dp))
                                    .then(if (isTv) Modifier.width(260.dp) else Modifier.fillMaxWidth())
                                    .height(48.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "WATCH ONLINE (SERVERS)",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight(600),
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(25.dp))

                        // STORYLINE / SYNOPSIS (.detail-storyline: 15px, color #a0a0a0, line-height 1.6)
                        Text(
                            text = item.overview.ifEmpty { "No storyline available." },
                            fontSize = 15.sp,
                            fontWeight = FontWeight(400),
                            color = TextSecondaryColor,
                            lineHeight = 24.sp,
                            maxLines = if (isTv) 4 else Int.MAX_VALUE,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .then(if (isTv) Modifier.widthIn(max = 640.dp) else Modifier)
                                .padding(bottom = 25.dp)
                        )

                        // SECONDARY ACTIONS (.secondary-actions-container: Trailer, My List, Share)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = if (isTv) Arrangement.spacedBy(20.dp) else Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Trailer Button
                            ActionIconBtn(
                                icon = Icons.Outlined.VideoLibrary,
                                label = "Trailer",
                                isActive = false,
                                onClick = {
                                    val trailer = item.trailerUrl
                                    if (!trailer.isNullOrBlank()) {
                                        showTrailerModal = true
                                    } else {
                                        Toast.makeText(context, "No trailer available for this title.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )

                            // 2. My List (Watchlist) Button
                            ActionIconBtn(
                                icon = if (uiState.isInMyList) Icons.Default.Check else Icons.Default.Add,
                                label = "My List",
                                isActive = uiState.isInMyList,
                                onClick = {
                                    viewModel.toggleMyList()
                                    val msg = if (uiState.isInMyList) "Removed from My List" else "Added to My List"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )

                            // 3. Share Button
                            ActionIconBtn(
                                icon = Icons.Default.Share,
                                label = "Share",
                                isActive = false,
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Check out \"${item.title}\" on NovaFlix!\nhttps://novaflix.app"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share \"${item.title}\""))
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                // 3. SEASONS & EPISODES (if TV or Anime with multiple episodes)
                if (item.mediaType != MediaType.MOVIE && uiState.episodes.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 25.dp)
                        ) {
                            // Category Header
                            CategoryHeaderSection(title = "Episodes")

                            // Season selector chips (.season-selector)
                            if (uiState.seasons.size > 1) {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(bottom = 15.dp)
                                ) {
                                    items(uiState.seasons) { s ->
                                        val isSelected = s.seasonNumber == uiState.selectedSeason
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(if (isSelected) HaiFlixRed else CardBackground)
                                                .border(
                                                    1.dp,
                                                    if (isSelected) HaiFlixRed else BorderColor,
                                                    RoundedCornerShape(20.dp)
                                                )
                                                .tvFocusRing(RoundedCornerShape(20.dp)).clickable { viewModel.selectSeason(s.seasonNumber) }
                                                .padding(horizontal = 18.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = s.name ?: "Season ${s.seasonNumber}",
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else TextSecondaryColor
                                            )
                                        }
                                    }
                                }
                            }

                            // Episode Cards Slider (.episode-slider-card: flex 0 0 200px, aspect-ratio 16/9)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.episodes) { ep ->
                                    EpisodeSliderCard(
                                        episode = ep,
                                        defaultPoster = item.getFullBackdropUrl() ?: item.getFullPosterUrl(),
                                        onClick = { onPlayClick(ep.seasonNumber, ep.episodeNumber) }
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. CAST SECTION (.cast-section: circle avatars 70x70)
                if (uiState.cast.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 30.dp)
                        ) {
                            CategoryHeaderSection(title = "Cast")

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(15.dp)
                            ) {
                                items(uiState.cast) { actor ->
                                    CastItemView(actor = actor)
                                }
                            }
                        }
                    }
                }

                // 5. MORE LIKE THIS (.category-section #similar-section)
                if (uiState.similarItems.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp)
                        ) {
                            CategoryHeaderSection(title = "More Like This")

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(uiState.similarItems) { similar ->
                                    ContentCardView(
                                        item = similar,
                                        onClick = {
                                            if (onNavigateToDetail != null) {
                                                onNavigateToDetail(similar.id, similar.mediaType)
                                            } else {
                                                viewModel.loadDetails(similar.id, similar.mediaType)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // FLOATING CIRCULAR BACK BUTTON (top: 15px, left: 15px, 40x40 circle, background rgba(0,0,0,0.5))
        // On TV the remote's BACK key does this, so no floating button that steals focus
        if (!isTv) {
            Box(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 15.dp, start = 15.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
                    .tvFocusRing(CircleShape).clickable(onClick = onBackClick)
                    .testTag("back-btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // TRAILER MODAL OVERLAY (.trailer-modal)
        if (showTrailerModal && !item?.trailerUrl.isNullOrBlank()) {
            TrailerDialog(
                trailerUrl = item!!.trailerUrl!!,
                onDismiss = { showTrailerModal = false }
            )
        }
    }
}

// -------------------------------------------------------------
// Component: Secondary Action Icon Button (.action-icon-btn)
// -------------------------------------------------------------
@Composable
private fun ActionIconBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(75.dp)
            .tvFocusRing(RoundedCornerShape(10.dp)).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) HaiFlixRed else TextSecondaryColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight(500),
            color = if (isActive) HaiFlixRed else TextSecondaryColor
        )
    }
}

// -------------------------------------------------------------
// Component: Section Header (.category-title with red left bar)
// -------------------------------------------------------------
@Composable
private fun CategoryHeaderSection(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 12.dp)
    ) {
        // Red accent bar: width: 5px, height: 24px, glow
        Box(
            modifier = Modifier
                .width(5.dp)
                .height(22.dp)
                .background(HaiFlixRed)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight(700),
            color = Color.White
        )
    }
}

// -------------------------------------------------------------
// Component: Cast Item (.cast-item circle 70x70)
// -------------------------------------------------------------
@Composable
private fun CastItemView(actor: CastMember) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(80.dp)
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(CircleShape)
                .background(CardBackground)
                .border(2.dp, BorderColor, CircleShape)
        ) {
            AsyncImage(
                model = actor.getFullProfileUrl(),
                contentDescription = actor.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = actor.name,
            fontSize = 13.sp,
            color = TextSecondaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// -------------------------------------------------------------
// Component: Episode Slider Card (.episode-slider-card flex 200px)
// -------------------------------------------------------------
@Composable
private fun EpisodeSliderCard(
    episode: Episode,
    defaultPoster: String?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.pressScale(onClick)
            .width(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = episode.getFullStillUrl() ?: defaultPoster,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = "E${episode.episodeNumber}: ${episode.title}",
                fontSize = 14.sp,
                fontWeight = FontWeight(500),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

// -------------------------------------------------------------
// Component: Content Card (.content-card flex 130px, ratio 2/3)
// -------------------------------------------------------------
@Composable
private fun ContentCardView(
    item: MediaItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.pressScale(onClick)
            .width(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = item.getFullPosterUrl(),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Overlay text gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                startY = 120f
                            )
                        )
                )
                Text(
                    text = item.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight(500),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------
// Component: Trailer Modal (.trailer-modal with close button)
// -------------------------------------------------------------
@Composable
private fun TrailerDialog(
    trailerUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val videoId = remember(trailerUrl) { extractYouTubeVideoId(trailerUrl) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            // Close Button (.trailer-close-btn top right)
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.tvAutoFocus().tvFocusRing(CircleShape)
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(15.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .border(1.dp, Color.White, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            // Video Wrapper (aspect ratio 16:9)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black)
            ) {
                if (!videoId.isNullOrBlank()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            // Some TVs ship without a WebView provider: don't crash, show a message instead
                            try {
                            android.webkit.WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                webChromeClient = android.webkit.WebChromeClient()
                                val embedHtml = """
                                    <!DOCTYPE html>
                                    <html>
                                    <head>
                                      <style>
                                        * { margin:0; padding:0; background-color:#000; }
                                        html, body { width:100%; height:100%; }
                                        iframe { width:100%; height:100%; border:none; }
                                      </style>
                                    </head>
                                    <body>
                                      <iframe src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&rel=0" allowfullscreen allow="autoplay; encrypted-media"></iframe>
                                    </body>
                                    </html>
                                """.trimIndent()
                                loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "utf-8", null)
                            } as android.view.View
                            } catch (e: Exception) {
                                android.widget.TextView(ctx).apply {
                                    text = "Trailer playback is not supported on this device"
                                    setTextColor(android.graphics.Color.WHITE)
                                    gravity = android.view.Gravity.CENTER
                                }
                            }
                        }
                    )
                } else {
                    // Fallback to open trailer URL in browser
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Trailer Link Available", color = Color.White, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(50.dp)),
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Cannot open trailer link", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = HaiFlixRed)
                        ) {
                            Text("Open in YouTube")
                        }
                    }
                }
            }
        }
    }
}

private fun extractYouTubeVideoId(url: String): String? {
    val regex = Regex("""(?:youtube\.com\/(?:[^\/\n\s]+\/\S+\/|(?:v|e(?:mbed)?)\/|\S*?[?&]v=)|youtu\.be\/)([a-zA-Z0-9_-]{11})""")
    return regex.find(url)?.groupValues?.getOrNull(1)
}
