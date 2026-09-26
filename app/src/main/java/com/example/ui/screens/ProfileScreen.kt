package com.example.ui.screens

import androidx.compose.ui.focus.focusProperties
import com.example.ui.components.tvFocusRing
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.WatchItemEntity
import com.example.data.model.MediaType
import com.example.data.repository.AppPrefs
import com.example.data.repository.ServerConfig
import com.example.ui.components.ServerSelectionDialog
import com.example.ui.components.UserAvatar
import com.example.ui.components.pressScale
import com.example.ui.viewmodel.ProfileViewModel

private val Red = Color(0xFFE50914)
private val Bg = Color(0xFF0B0B0E)
private val Card = Color(0xFF14141B)
private val Line = Color(0x1AFFFFFF)
private val Dim = Color(0xFF9A9AA8)

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBackClick: () -> Unit,
    onNavigateToMyList: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToDetail: (String, MediaType) -> Unit = { _, _ -> }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var showServerSettingsDialog by remember { mutableStateOf(false) }
    val currentServerType by ServerConfig.currentServer.collectAsState()
    val customUrl by ServerConfig.customBaseUrl.collectAsState()
    val prefs = remember { AppPrefs.get(context) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it, duration = SnackbarDuration.Long)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        containerColor = Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(modifier = Modifier.tvFocusRing(CircleShape), onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---------------- account card ----------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF2A0A0E), Color(0xFF14141B), Color(0xFF0F1420))))
                    .border(1.dp, Line, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    UserAvatar(photoUrl = state.photoUrl, name = state.name, size = 88.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = if (state.isGuest) "Guest" else (state.name ?: "NovaFlix User"),
                        color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    if (!state.isGuest && !state.email.isNullOrBlank()) {
                        Text(state.email ?: "", color = Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(8.dp))
                    StatusChip(
                        text = if (state.isGuest) "Guest mode  •  data sirf is phone ke saath linked" else "Google account  •  cloud sync ON",
                        good = !state.isGuest
                    )
                    Spacer(Modifier.height(16.dp))

                    if (state.isGuest) {
                        Button(
                            onClick = { context.findActivity()?.let { viewModel.signInWithGoogle(it) } },
                            enabled = !state.isBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(28.dp))
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            if (state.isBusy) {
                                CircularProgressIndicator(color = Red, strokeWidth = 2.5.dp, modifier = Modifier.size(20.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4285F4)),
                                    contentAlignment = Alignment.Center
                                ) { Text("G", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                                Spacer(Modifier.width(10.dp))
                                Text("Continue with Google", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Sign in to sync your My List, Continue Watching progress, and watch time across all your devices.",
                            color = Dim, fontSize = 12.sp, textAlign = TextAlign.Center
                        )
                    } else {
                        OutlinedButton(
                            onClick = { confirmSignOut = true },
                            enabled = !state.isBusy,
                            shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(1.dp, Color(0x55FFFFFF)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(28.dp))
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign out", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // ---------------- real stats ----------------
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(Icons.Default.Timer, formatWatch(state.watchMs), "Watch time", Modifier.weight(1f))
                StatCard(Icons.Default.Bookmark, "${state.myListCount}", "My List", Modifier.weight(1f))
                StatCard(Icons.Default.History, "${state.historyCount}", "Watched", Modifier.weight(1f))
            }

            // ---------------- shortcuts ----------------
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                ActionTile(Icons.Default.Bookmark, "My List", Modifier.weight(1f), onNavigateToMyList)
                ActionTile(Icons.Default.Download, "Downloads", Modifier.weight(1f), onNavigateToDownloads)
            }

            // ---------------- continue watching ----------------
            if (state.recent.isNotEmpty()) {
                SectionTitle("Recently watched")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.recent, key = { it.id }) { item ->
                        RecentCard(item) {
                            val type = runCatching { MediaType.valueOf(item.mediaType) }.getOrDefault(MediaType.MOVIE)
                            onNavigateToDetail(item.mediaId, type)
                        }
                    }
                }
            }

            // ---------------- playback settings (all of these really work) ----------------
            SectionTitle("Playback")
            SettingsCard {
                SwitchRow(
                    icon = Icons.Default.SkipNext,
                    title = "Auto-play next episode",
                    subtitle = "Automatically plays the next episode",
                    checked = state.autoPlayNext,
                    onChange = viewModel::setAutoPlayNext
                )
                RowDivider()
                ChipRow(
                    icon = Icons.Default.HighQuality,
                    title = "Default quality",
                    options = AppPrefs.QualityMode.values().map { it.label },
                    selectedIndex = state.quality.ordinal,
                    onSelect = { viewModel.setQuality(AppPrefs.QualityMode.values()[it]) }
                )
                RowDivider()
                val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
                ChipRow(
                    icon = Icons.Default.Speed,
                    title = "Default speed",
                    options = speeds.map { if (it == 1f) "Normal" else "${it}x" },
                    selectedIndex = speeds.indexOf(state.speed).coerceAtLeast(1),
                    onSelect = { viewModel.setSpeed(speeds[it]) }
                )
                RowDivider()
                val scales = listOf(0.8f, 1f, 1.3f, 1.6f)
                ChipRow(
                    icon = Icons.Default.Subtitles,
                    title = "Subtitle size",
                    options = listOf("Small", "Normal", "Large", "XL"),
                    selectedIndex = scales.indexOf(state.subtitleScale).coerceAtLeast(1),
                    onSelect = { viewModel.setSubtitleScale(scales[it]) }
                )
            }

            // ---------------- server & sources ----------------
            SectionTitle("Server & Sources")
            SettingsCard {
                ClickRow(
                    icon = Icons.Default.Dns,
                    title = "Active Server",
                    subtitle = when (currentServerType) {
                        ServerConfig.ServerType.MERA_SERVER -> "Primary Server (Admin Uploads)"
                        ServerConfig.ServerType.MOVIEBOX -> "MovieBox Server (ElitePlex API)"
                        ServerConfig.ServerType.CUSTOM -> "Custom Server ($customUrl)"
                    },
                    onClick = { showServerSettingsDialog = true }
                )
                RowDivider()
                ClickRow(
                    icon = Icons.Default.SwapHoriz,
                    title = "Change Streaming Server",
                    subtitle = "Switch between Primary Server, MovieBox, or custom endpoints",
                    onClick = { showServerSettingsDialog = true }
                )
            }

            // ---------------- display ----------------
            SectionTitle("Display")
            SettingsCard {
                var forceTv by remember { mutableStateOf(prefs.forceTvMode) }
                SwitchRow(
                    icon = Icons.Default.Tv,
                    title = "Android TV mode",
                    subtitle = "Remote-friendly layout with side menu. Auto-on for TVs. Restart the app to apply.",
                    checked = forceTv,
                    onChange = { forceTv = it; prefs.forceTvMode = it }
                )
            }

            // ---------------- data ----------------
            SectionTitle("Data")
            SettingsCard {
                ClickRow(
                    icon = Icons.Default.DeleteSweep,
                    title = "Clear watch history",
                    subtitle = "Reset Continue Watching and watch time (My List remains saved)",
                    onClick = { confirmClear = true }
                )
            }

            Text(
                "NovaFlix  •  v1.0",
                color = Color(0xFF55555F), fontSize = 12.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showServerSettingsDialog) {
        ServerSelectionDialog(
            prefs = prefs,
            isFirstLaunch = false,
            onDismiss = { showServerSettingsDialog = false },
            onServerSelected = {
                showServerSettingsDialog = false
            }
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Card,
            title = { Text("Clear watch history?", color = Color.White) },
            text = { Text("This will reset your Continue Watching progress and total watch time.", color = Dim) },
            confirmButton = {
                TextButton(modifier = Modifier.tvFocusRing(RoundedCornerShape(50.dp)), onClick = { confirmClear = false; viewModel.clearHistory() }) { Text("Clear", color = Red) }
            },
            dismissButton = { TextButton(modifier = Modifier.tvFocusRing(RoundedCornerShape(50.dp)), onClick = { confirmClear = false }) { Text("Cancel", color = Color.White) } }
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            containerColor = Card,
            title = { Text("Sign out?", color = Color.White) },
            text = { Text("Your data is safely synced to the cloud and will be restored when you sign in again.", color = Dim) },
            confirmButton = {
                TextButton(modifier = Modifier.tvFocusRing(RoundedCornerShape(50.dp)), onClick = { confirmSignOut = false; viewModel.signOut() }) { Text("Sign out", color = Red) }
            },
            dismissButton = { TextButton(modifier = Modifier.tvFocusRing(RoundedCornerShape(50.dp)), onClick = { confirmSignOut = false }) { Text("Cancel", color = Color.White) } }
        )
    }
}

// ---------------------------------------------------------------------------

private fun formatWatch(ms: Long): String {
    val totalMin = ms / 60_000L
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

@Composable
private fun StatusChip(text: String, good: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (good) Color(0x2233D17A) else Color(0x22FFFFFF))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            if (good) Icons.Default.CloudDone else Icons.Default.CloudOff,
            contentDescription = null,
            tint = if (good) Color(0xFF33D17A) else Dim,
            modifier = Modifier.size(15.dp)
        )
        Text(text, color = if (good) Color(0xFF7BE3AB) else Dim, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun StatCard(icon: ImageVector, value: String, label: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(18.dp))
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Red, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Dim, fontSize = 11.sp)
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .pressScale(onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(4.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Red))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Card)
            .border(1.dp, Line, RoundedCornerShape(20.dp))
            .padding(vertical = 4.dp),
        content = content
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusRing(RoundedCornerShape(10.dp)).clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Dim, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Dim, fontSize = 12.sp)
        }
        Switch(
            // the whole row is the focus target on a TV remote, not the tiny switch
            modifier = Modifier.focusProperties { canFocus = false },
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Red, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun ChipRow(icon: ImageVector, title: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Dim, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEachIndexed { i, label ->
                val sel = i == selectedIndex
                Text(
                    text = label,
                    color = if (sel) Color.White else Dim,
                    fontSize = 12.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (sel) Red else Color(0x14FFFFFF))
                        .tvFocusRing(RoundedCornerShape(16.dp)).clickable { onSelect(i) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ClickRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusRing(RoundedCornerShape(10.dp)).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Red, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Dim, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Dim)
    }
}

@Composable
private fun RecentCard(item: WatchItemEntity, onClick: () -> Unit) {
    val progress = if (item.durationMillis > 0) (item.progressMillis.toFloat() / item.durationMillis).coerceIn(0f, 1f) else 0f
    val img = item.backdropPath?.takeIf { it.startsWith("http") } ?: item.posterPath?.takeIf { it.startsWith("http") }
    Column(
        modifier = Modifier
            .width(170.dp)
            .pressScale(onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1E1E28))
        ) {
            if (img != null) {
                AsyncImage(model = img, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color(0x55FFFFFF))
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Red))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(item.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (item.mediaType != "MOVIE") {
            Text("S${item.seasonNumber} E${item.episodeNumber}", color = Dim, fontSize = 11.sp)
        }
    }
}
