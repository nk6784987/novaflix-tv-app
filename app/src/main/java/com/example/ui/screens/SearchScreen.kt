package com.example.ui.screens

import com.example.ui.components.tvAutoFocus
import com.example.ui.components.LocalIsTv
import androidx.compose.ui.focus.FocusDirection
import com.example.ui.components.tvFocusRing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.ui.components.pressScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.ui.theme.*
import com.example.ui.viewmodel.SearchViewModel

private val HaiFlixRed = Color(0xFFE50914)
private val DarkBackground = Color(0xFF0B0B0B)
private val CardBackground = Color(0xFF101010)
private val BorderColor = Color(0x1AFFFFFF)
private val TextSecondaryColor = Color(0xFFA0A0A0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onBackClick: () -> Unit,
    onNavigateToDetail: (mediaId: String, mediaType: MediaType) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val isTv = LocalIsTv.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = { viewModel.onQueryChanged(it) },
                        placeholder = { Text("Search Movies, Web Series, Anime...", color = TextSecondaryColor, fontSize = 14.sp) },
                        leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = HaiFlixRed) },
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(modifier = Modifier.tvFocusRing(CircleShape), onClick = { viewModel.onQueryChanged("") }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            // TV: hand the focus to the results so the remote can browse them
                            if (isTv) focusManager.moveFocus(FocusDirection.Down) else focusManager.clearFocus()
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HaiFlixRed,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = CardBackground,
                            unfocusedContainerColor = CardBackground,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .tvAutoFocus()
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("search_input_field")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick, modifier = Modifier.tvFocusRing(CircleShape).testTag("search_back_button")) {
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
            // Filter Chips Bar
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                // Media Type Filter Chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedMediaType == null,
                            onClick = { viewModel.selectMediaType(null) },
                            label = { Text("All Types") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HaiFlixRed,
                                selectedLabelColor = Color.White,
                                containerColor = CardBackground,
                                labelColor = TextSecondaryColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = uiState.selectedMediaType == null,
                                borderColor = BorderColor,
                                selectedBorderColor = HaiFlixRed
                            )
                        )
                    }
                    items(MediaType.values()) { type ->
                        val isSelected = uiState.selectedMediaType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectMediaType(if (isSelected) null else type) },
                            label = { Text(type.name) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HaiFlixRed,
                                selectedLabelColor = Color.White,
                                containerColor = CardBackground,
                                labelColor = TextSecondaryColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = BorderColor,
                                selectedBorderColor = HaiFlixRed
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Genre Filter Chips
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.availableGenres) { genre ->
                        val isSelected = uiState.selectedGenre == genre
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectGenre(genre) },
                            label = { Text(genre, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = HaiFlixRed,
                                selectedLabelColor = Color.White,
                                containerColor = CardBackground,
                                labelColor = TextSecondaryColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = BorderColor,
                                selectedBorderColor = HaiFlixRed
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Year and Rating Filter Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Year
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(uiState.availableYears) { year ->
                            val isSelected = uiState.selectedYear == year
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectYear(year) },
                                label = { Text(year, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HaiFlixRed,
                                    selectedLabelColor = Color.White,
                                    containerColor = CardBackground,
                                    labelColor = TextSecondaryColor
                                )
                            )
                        }
                    }

                    // Rating
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(uiState.availableRatings) { rating ->
                            val isSelected = uiState.selectedMinRating == rating
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectRating(rating) },
                                label = { Text("$rating+", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = HaiFlixRed,
                                    selectedLabelColor = Color.White,
                                    containerColor = CardBackground,
                                    labelColor = TextSecondaryColor
                                )
                            )
                        }
                    }
                }
            }

            // Results Section
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HaiFlixRed)
                }
            } else if (uiState.filteredResults.isEmpty() && uiState.query.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No media matches found.", color = TextSecondaryColor, fontSize = 15.sp)
                }
            } else if (uiState.query.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextSecondaryColor.copy(alpha = 0.4f), modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Search for your favorite titles", color = TextSecondaryColor, fontSize = 16.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.filteredResults, key = { it.id }) { item ->
                        SearchResultCard(
                            item = item,
                            onClick = { onNavigateToDetail(item.id, item.mediaType) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: MediaItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.pressScale(onClick)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
            .testTag("search_result_${item.id}")
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

                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(bottomStart = 6.dp),
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(text = String.format("%.1f", item.rating), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
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
                    text = "${item.releaseYear} • ${item.mediaType.name}",
                    color = TextSecondaryColor,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}
