package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

// ---------------------------------------------------------------------------
// Shimmer skeletons (replace boring spinners)
// ---------------------------------------------------------------------------

fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shimmerX"
    )
    background(
        Brush.linearGradient(
            colors = listOf(Color(0xFF15151C), Color(0xFF262633), Color(0xFF15151C)),
            start = Offset(x, 0f),
            end = Offset(x + 500f, 260f)
        )
    )
}

@Composable
fun SkeletonScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .shimmer()
        )
        repeat(3) {
            Box(
                modifier = Modifier
                    .padding(start = 16.dp, top = 14.dp, bottom = 10.dp)
                    .width(140.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .shimmer()
            )
            LazyRow(
                userScrollEnabled = false,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(6) {
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .shimmer()
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Press feedback: card shrinks a little while pressed
// ---------------------------------------------------------------------------

/**
 * Put this at the START of a modifier chain (before clip/background/border).
 *  - touch: card shrinks a little while pressed
 *  - remote (D-pad): focused card grows, comes to the front and gets a white outline
 *  - [onLongClick]: optional long-press (OK button held on a remote)
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressScale(
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(12.dp),
    onLongClick: (() -> Unit)? = null
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.94f
            focused -> 1.07f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale"
    )
    val ring by animateFloatAsState(if (focused) 1f else 0f, tween(130), label = "focusRing")
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
    }
    this
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .drawWithContent {
            drawContent()
            if (ring > 0.01f) {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, color = Color.White, alpha = ring, style = Stroke(width = 2.5.dp.toPx()))
            }
        }
        .then(clickModifier)
}

// ---------------------------------------------------------------------------
// Auth user helper
// ---------------------------------------------------------------------------

@Composable
fun rememberFirebaseUser(): FirebaseUser? {
    var user by remember { mutableStateOf(runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()) }
    DisposableEffect(Unit) {
        val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
        val listener = FirebaseAuth.AuthStateListener { user = it.currentUser }
        auth?.addAuthStateListener(listener)
        onDispose { auth?.removeAuthStateListener(listener) }
    }
    return user
}

fun FirebaseUser.bestPhotoUrl(): String? =
    (photoUrl ?: providerData.firstOrNull { it.photoUrl != null }?.photoUrl)?.toString()

fun FirebaseUser.bestName(): String? =
    displayName?.takeIf { it.isNotBlank() }
        ?: providerData.firstOrNull { !it.displayName.isNullOrBlank() }?.displayName

/** Round avatar: Google photo when signed in, otherwise the first letter / a person icon. */
@Composable
fun UserAvatar(
    photoUrl: String?,
    name: String?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFE50914), Color(0xFF8E0A12))))
            .border(1.dp, Color(0x33FFFFFF), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (!name.isNullOrBlank()) {
            Text(
                text = name.trim().first().uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp
            )
        } else {
            Icon(Icons.Outlined.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
        }
    }
}

// ---------------------------------------------------------------------------
// Shared bottom bar (same look on Home / Series / Anime)
// ---------------------------------------------------------------------------

enum class BottomTab { HOME, SERIES, ANIME, DOWNLOADS, PROFILE }

@Composable
fun AppBottomBar(
    selected: BottomTab,
    onHome: () -> Unit,
    onSeries: () -> Unit,
    onAnime: () -> Unit,
    onDownloads: () -> Unit,
    onProfile: () -> Unit
) {
    // On TV the left navigation rail (see AppNavGraph) replaces this bar.
    if (LocalIsTv.current) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0B0B0B))))
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xF0141419))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(26.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarItem(Icons.Default.Home, "Home", selected == BottomTab.HOME, onHome)
            BarItem(Icons.Default.Tv, "Series", selected == BottomTab.SERIES, onSeries)
            BarItem(Icons.Default.MovieFilter, "Anime", selected == BottomTab.ANIME, onAnime)
            BarItem(Icons.Outlined.DownloadForOffline, "Downloads", selected == BottomTab.DOWNLOADS, onDownloads)
            BarItem(Icons.Outlined.Person, "Profile", selected == BottomTab.PROFILE, onProfile)
        }
    }
}

@Composable
private fun BarItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) Color(0xFFE50914) else Color.Transparent,
        animationSpec = tween(220),
        label = "barBg"
    )
    val fg by animateColorAsState(
        if (selected) Color.White else Color(0xFF9A9AA8),
        animationSpec = tween(220),
        label = "barFg"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .tvFocusRing(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (selected) 14.dp else 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(22.dp))
        if (selected) {
            Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
