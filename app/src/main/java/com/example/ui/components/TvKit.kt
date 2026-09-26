package com.example.ui.components

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.MediaItem
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------
// TV detection + 10-foot environment
// ---------------------------------------------------------------------------

/** True on Android TV / Google TV / Fire TV style devices (leanback or TV UI mode). */
fun isTelevisionDevice(context: Context): Boolean {
    val uiMode = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    val pm = context.packageManager
    return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        pm.hasSystemFeature("android.software.leanback_only")
}

/** true while the app runs on a TV (or when "TV mode" is forced from Profile for testing). */
val LocalIsTv = compositionLocalOf { false }

/** Which screen is drawing right now ("home", "detail/..." ...). Used for TV-only behaviour. */
val LocalTvScreen = compositionLocalOf { "" }

private const val TV_DESIGN_WIDTH_DP = 860f

/**
 * Wraps the whole app. On TV it scales density so the UI has the same physical size on every
 * TV (TVs report wildly different densities), which makes text and posters comfortable to read
 * from the sofa. On phones it does nothing.
 */
@Composable
fun TvEnvironment(isTv: Boolean, content: @Composable () -> Unit) {
    if (!isTv) {
        CompositionLocalProvider(LocalIsTv provides false) { content() }
        return
    }
    val density = LocalDensity.current
    val widthDp = LocalConfiguration.current.screenWidthDp
    val scale = (widthDp / TV_DESIGN_WIDTH_DP).coerceIn(1f, 2f)
    CompositionLocalProvider(
        LocalIsTv provides true,
        LocalDensity provides Density(density.density * scale, density.fontScale)
    ) { content() }
}

// ---------------------------------------------------------------------------
// Focus helpers
// ---------------------------------------------------------------------------

/** Tries to focus until the target is on screen (or gives up quietly). */
suspend fun FocusRequester.requestFocusWhenReady(attempts: Int = 15, gapMs: Long = 120L): Boolean {
    repeat(attempts) {
        val ok = runCatching { requestFocus() }.isSuccess
        if (ok) return true
        delay(gapMs)
    }
    return false
}

/**
 * TV only: give this element the focus when the screen opens, so the remote works from the very
 * first key press. Put it BEFORE clickable()/Button internals in the modifier chain.
 */
fun Modifier.tvAutoFocus(enabled: Boolean = true): Modifier = composed {
    val isTv = LocalIsTv.current
    val requester = remember { FocusRequester() }
    if (isTv && enabled) {
        LaunchedEffect(Unit) {
            delay(200)
            requester.requestFocusWhenReady()
        }
    }
    this.focusRequester(requester)
}

/**
 * Visible focus for D-pad / keyboard users: white outline + soft light overlay.
 * Invisible on touch screens (nothing gets focus there), so phones look exactly the same.
 * Put it BEFORE .clickable() (or inside the `modifier =` of Button / IconButton).
 */
fun Modifier.tvFocusRing(
    shape: Shape = RoundedCornerShape(12.dp),
    width: Dp = 2.5.dp
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val amount by animateFloatAsState(if (focused) 1f else 0f, tween(130), label = "tvFocusRing")
    this
        .onFocusChanged { focused = it.hasFocus }
        .drawWithContent {
            drawContent()
            if (amount > 0.01f) {
                val outline = shape.createOutline(size, layoutDirection, this)
                drawOutline(outline, color = Color.White, alpha = 0.10f * amount)
                drawOutline(
                    outline,
                    color = Color.White,
                    alpha = amount,
                    style = Stroke(width = width.toPx())
                )
            }
        }
}

// ---------------------------------------------------------------------------
// Left navigation rail (TV replaces the phone bottom bar)
// ---------------------------------------------------------------------------

data class TvRailItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun TvSideRail(
    items: List<TvRailItem>,
    selectedRoute: String?,
    onSelect: (TvRailItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(84.dp)
            .background(Brush.horizontalGradient(listOf(Color(0xFF14141A), Color(0xFF0B0B0B))))
            .padding(horizontal = 8.dp, vertical = 14.dp)
            .focusGroup(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFE50914), Color(0xFF8E0A12)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "NovaFlix", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            RailButton(item = item, selected = item.route == selectedRoute, onClick = { onSelect(item) })
        }
    }
}

@Composable
private fun RailButton(item: TvRailItem, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg by animateColorAsState(
        when {
            focused -> Color.White
            selected -> Color(0x40E50914)
            else -> Color.Transparent
        },
        animationSpec = tween(140),
        label = "railBg"
    )
    val fg = when {
        focused -> Color(0xFF0B0B0B)
        selected -> Color(0xFFFF4D5A)
        else -> Color(0xFF9A9AA8)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(item.icon, contentDescription = item.label, tint = fg, modifier = Modifier.size(22.dp))
        Text(
            text = item.label,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

// ---------------------------------------------------------------------------
// TV hero banner (Home / Series / Anime)
// ---------------------------------------------------------------------------

private val TvBg = Color(0xFF0B0B0B)
private val TvRed = Color(0xFFE50914)

@Composable
fun TvHeroBanner(
    items: List<MediaItem>,
    tagLabel: String,
    onPlay: (MediaItem) -> Unit,
    onDetails: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    autoFocusPlay: Boolean = true
) {
    if (items.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    var hasFocus by remember { mutableStateOf(false) }
    val safeIndex = index.coerceIn(0, items.lastIndex)
    val item = items[safeIndex]

    // rotate the featured titles, but never while the user is on a button
    LaunchedEffect(items.size, hasFocus) {
        if (items.size > 1 && !hasFocus) {
            while (true) {
                delay(7000)
                index = (index + 1) % items.size
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(285.dp)
            .onFocusChanged { hasFocus = it.hasFocus }
    ) {
        Crossfade(targetState = safeIndex, animationSpec = tween(700), label = "tvHeroFade") { i ->
            val m = items[i.coerceIn(0, items.lastIndex)]
            AsyncImage(
                model = m.getFullBackdropUrl(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // side scrim (text readability) + bottom fade into the page
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0xF20B0B0B),
                        0.5f to Color(0xA60B0B0B),
                        1f to Color.Transparent
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to TvBg
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.56f)
                .padding(start = 28.dp, bottom = 22.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(TvRed)
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(tagLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 30.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            val rating = if (item.rating > 0.0) String.format("%.1f", item.rating) else null
            val meta = listOfNotNull(
                rating?.let { "★ $it" },
                item.releaseYear.takeIf { it.isNotBlank() },
                item.genres.firstOrNull()
            ).joinToString("  •  ")
            if (meta.isNotEmpty()) {
                Text(meta, color = Color(0xFFD0D0DA), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            if (item.overview.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.overview,
                    color = Color(0xFFB8B8C4),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TvActionButton(
                    label = "Play",
                    icon = Icons.Default.PlayArrow,
                    primary = true,
                    onClick = { onPlay(item) },
                    modifier = Modifier.tvAutoFocus(autoFocusPlay)
                )
                TvActionButton(
                    label = "Details",
                    icon = Icons.Default.Info,
                    primary = false,
                    onClick = { onDetails(item) }
                )
            }
        }

        if (items.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                items.indices.forEach { i ->
                    Box(
                        Modifier
                            .height(4.dp)
                            .width(if (i == safeIndex) 20.dp else 6.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (i == safeIndex) TvRed else Color(0x66FFFFFF))
                    )
                }
            }
        }
    }
}

/** Big remote-friendly button: red / glassy at rest, solid white when focused. */
@Composable
fun TvActionButton(
    label: String,
    icon: ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(130), label = "tvBtnScale")
    val bg by animateColorAsState(
        when {
            focused -> Color.White
            primary -> TvRed
            else -> Color(0x40FFFFFF)
        },
        animationSpec = tween(130),
        label = "tvBtnBg"
    )
    val fg = if (focused) Color(0xFF0B0B0B) else Color.White
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

/** Round icon-only button with the same focus look (Back, Close ...). */
@Composable
fun TvIconAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (focused) Color.White else Color(0x99000000))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (focused) Color(0xFF0B0B0B) else Color.White,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
