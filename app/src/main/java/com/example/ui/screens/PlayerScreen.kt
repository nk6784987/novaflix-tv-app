package com.example.ui.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.Rational
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.example.ui.components.LocalIsTv
import com.example.ui.components.requestFocusWhenReady
import com.example.ui.components.tvFocusRing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.example.data.repository.AppPrefs
import com.example.util.NetworkUtils
import coil.compose.AsyncImage
import com.example.data.model.Episode
import com.example.data.model.MediaType
import com.example.data.model.VideoStreamSource
import com.example.ui.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

// ---------------------------------------------------------------------------
// Palette
// ---------------------------------------------------------------------------
private val Accent = Color(0xFFE50914)
private val PanelBg = Color(0xF2101010)
private val PillBg = Color(0x33FFFFFF)
private val TextDim = Color(0xFFB3B3B3)

// ---------------------------------------------------------------------------
// Track option models
// ---------------------------------------------------------------------------
data class VideoTrackOption(
    val id: String,
    val label: String,
    val height: Int,
    val isAuto: Boolean,
    val isSelected: Boolean,
    val group: TrackGroup?,
    val trackIndex: Int
)

data class AudioTrackOption(
    val id: String,
    val label: String,
    val isSelected: Boolean,
    val group: TrackGroup,
    val trackIndex: Int
)

data class SubtitleTrackOption(
    val id: String,
    val label: String,
    val isSelected: Boolean,
    val group: TrackGroup,
    val trackIndex: Int
)

private enum class PlayerPanel { None, Quality, Audio, Subtitles, Speed, Episodes, Settings }

// ---------------------------------------------------------------------------
// Track naming - turns "hin", "HI", "und", "SoundHandler" into "Hindi", "English", ...
// ---------------------------------------------------------------------------
private object TrackNamer {

    private val languageByCode: Map<String, String> = mapOf(
        "hi" to "Hindi", "hin" to "Hindi",
        "en" to "English", "eng" to "English",
        "ta" to "Tamil", "tam" to "Tamil",
        "te" to "Telugu", "tel" to "Telugu",
        "ml" to "Malayalam", "mal" to "Malayalam",
        "kn" to "Kannada", "kan" to "Kannada",
        "bn" to "Bengali", "ben" to "Bengali",
        "mr" to "Marathi", "mar" to "Marathi",
        "pa" to "Punjabi", "pan" to "Punjabi",
        "gu" to "Gujarati", "guj" to "Gujarati",
        "ur" to "Urdu", "urd" to "Urdu",
        "or" to "Odia", "ori" to "Odia",
        "as" to "Assamese", "asm" to "Assamese",
        "ne" to "Nepali", "nep" to "Nepali",
        "sa" to "Sanskrit", "san" to "Sanskrit",
        "ja" to "Japanese", "jpn" to "Japanese", "jap" to "Japanese",
        "ko" to "Korean", "kor" to "Korean",
        "zh" to "Chinese", "zho" to "Chinese", "chi" to "Chinese", "cmn" to "Chinese", "yue" to "Cantonese",
        "es" to "Spanish", "spa" to "Spanish",
        "fr" to "French", "fra" to "French", "fre" to "French",
        "de" to "German", "deu" to "German", "ger" to "German",
        "it" to "Italian", "ita" to "Italian",
        "pt" to "Portuguese", "por" to "Portuguese",
        "ru" to "Russian", "rus" to "Russian",
        "ar" to "Arabic", "ara" to "Arabic",
        "th" to "Thai", "tha" to "Thai",
        "id" to "Indonesian", "ind" to "Indonesian",
        "ms" to "Malay", "msa" to "Malay", "may" to "Malay",
        "tr" to "Turkish", "tur" to "Turkish",
        "vi" to "Vietnamese", "vie" to "Vietnamese",
        "fil" to "Filipino", "tl" to "Filipino", "tgl" to "Filipino",
        "pl" to "Polish", "pol" to "Polish",
        "nl" to "Dutch", "nld" to "Dutch", "dut" to "Dutch",
        "sv" to "Swedish", "swe" to "Swedish",
        "da" to "Danish", "dan" to "Danish",
        "fi" to "Finnish", "fin" to "Finnish",
        "no" to "Norwegian", "nor" to "Norwegian", "nb" to "Norwegian", "nob" to "Norwegian",
        "he" to "Hebrew", "heb" to "Hebrew",
        "fa" to "Persian", "fas" to "Persian", "per" to "Persian",
        "el" to "Greek", "ell" to "Greek", "gre" to "Greek",
        "uk" to "Ukrainian", "ukr" to "Ukrainian",
        "cs" to "Czech", "ces" to "Czech", "cze" to "Czech",
        "hu" to "Hungarian", "hun" to "Hungarian",
        "ro" to "Romanian", "ron" to "Romanian", "rum" to "Romanian",
        "bg" to "Bulgarian", "bul" to "Bulgarian",
        "sr" to "Serbian", "srp" to "Serbian",
        "hr" to "Croatian", "hrv" to "Croatian",
        "si" to "Sinhala", "sin" to "Sinhala",
        "my" to "Burmese", "mya" to "Burmese", "bur" to "Burmese"
    )

    private val unknownCodes = setOf("und", "zxx", "mis", "mul", "qaa", "", "null", "unknown")

    // words that may appear inside a track title written by whoever muxed the file
    private val labelPatterns: List<Pair<String, Regex>> = listOf(
        "Hindi" to Regex("""\b(hindi|hin|hind)\b""", RegexOption.IGNORE_CASE),
        "English" to Regex("""\b(english|eng)\b""", RegexOption.IGNORE_CASE),
        "Tamil" to Regex("""\b(tamil|tam)\b""", RegexOption.IGNORE_CASE),
        "Telugu" to Regex("""\b(telugu|tel)\b""", RegexOption.IGNORE_CASE),
        "Malayalam" to Regex("""\b(malayalam|mal)\b""", RegexOption.IGNORE_CASE),
        "Kannada" to Regex("""\b(kannada|kan)\b""", RegexOption.IGNORE_CASE),
        "Bengali" to Regex("""\b(bengali|bangla|ben)\b""", RegexOption.IGNORE_CASE),
        "Marathi" to Regex("""\b(marathi|mar)\b""", RegexOption.IGNORE_CASE),
        "Punjabi" to Regex("""\b(punjabi|pan)\b""", RegexOption.IGNORE_CASE),
        "Gujarati" to Regex("""\b(gujarati|guj)\b""", RegexOption.IGNORE_CASE),
        "Urdu" to Regex("""\b(urdu|urd)\b""", RegexOption.IGNORE_CASE),
        "Japanese" to Regex("""\b(japanese|jpn|jap)\b""", RegexOption.IGNORE_CASE),
        "Korean" to Regex("""\b(korean|kor)\b""", RegexOption.IGNORE_CASE),
        "Chinese" to Regex("""\b(chinese|mandarin|chi|zho)\b""", RegexOption.IGNORE_CASE),
        "Spanish" to Regex("""\b(spanish|spa)\b""", RegexOption.IGNORE_CASE),
        "French" to Regex("""\b(french|fre|fra)\b""", RegexOption.IGNORE_CASE),
        "German" to Regex("""\b(german|ger|deu)\b""", RegexOption.IGNORE_CASE),
        "Italian" to Regex("""\b(italian|ita)\b""", RegexOption.IGNORE_CASE),
        "Portuguese" to Regex("""\b(portuguese|por)\b""", RegexOption.IGNORE_CASE),
        "Russian" to Regex("""\b(russian|rus)\b""", RegexOption.IGNORE_CASE),
        "Arabic" to Regex("""\b(arabic|ara)\b""", RegexOption.IGNORE_CASE),
        "Thai" to Regex("""\b(thai|tha)\b""", RegexOption.IGNORE_CASE),
        "Indonesian" to Regex("""\b(indonesian|ind)\b""", RegexOption.IGNORE_CASE),
        "Turkish" to Regex("""\b(turkish|tur)\b""", RegexOption.IGNORE_CASE)
    )

    private fun fromCode(code: String?): String? {
        val c = code?.trim()?.lowercase(Locale.ROOT)?.replace('_', '-') ?: return null
        if (c in unknownCodes) return null
        val base = c.substringBefore('-')
        languageByCode[base]?.let { return it }
        return try {
            val n = Locale.forLanguageTag(base).getDisplayLanguage(Locale.ENGLISH)
            if (n.isNotBlank() && !n.equals(base, ignoreCase = true)) n else null
        } catch (e: Exception) {
            null
        }
    }

    private fun fromLabel(label: String?): String? {
        if (label.isNullOrBlank()) return null
        val hits = mutableListOf<Pair<Int, String>>()
        for ((name, regex) in labelPatterns) {
            val m = regex.find(label) ?: continue
            hits.add(m.range.first to name)
        }
        if (hits.isEmpty()) return null
        return hits.sortedBy { it.first }.map { it.second }.distinct().joinToString(" + ")
    }

    private fun channelText(f: Format): String? = when {
        f.channelCount >= 8 -> "7.1"
        f.channelCount >= 6 -> "5.1"
        f.channelCount == 2 -> "Stereo"
        f.channelCount == 1 -> "Mono"
        else -> null
    }

    private fun codecText(f: Format): String? = when (f.sampleMimeType) {
        MimeTypes.AUDIO_AC3 -> "AC3"
        MimeTypes.AUDIO_E_AC3 -> "DD+"
        MimeTypes.AUDIO_E_AC3_JOC -> "Atmos"
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_MPEG -> "MP3"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        else -> null
    }

    /** Language name for a track, or null when the file does not say. */
    fun languageOf(f: Format): String? = fromLabel(f.label) ?: fromCode(f.language)

    /** Human readable, unique names for all audio tracks. */
    fun audioNames(formats: List<Format>): List<String> {
        val base = formats.mapIndexed { i, f ->
            val lang = languageOf(f)
            val ch = channelText(f)
            when {
                lang != null && ch != null -> "$lang  •  $ch"
                lang != null -> lang
                ch != null -> "Audio ${i + 1}  •  $ch"
                else -> "Audio ${i + 1}"
            }
        }
        return disambiguate(base, formats) { f -> codecText(f) }
    }

    fun subtitleNames(formats: List<Format>): List<String> {
        val base = formats.mapIndexed { i, f ->
            val lang = languageOf(f) ?: "Subtitle ${i + 1}"
            val forced = (f.selectionFlags and C.SELECTION_FLAG_FORCED) != 0
            val cc = (f.roleFlags and C.ROLE_FLAG_CAPTION) != 0 || (f.roleFlags and C.ROLE_FLAG_TRANSCRIBES_DIALOG) != 0
            lang + if (forced) " (Forced)" else if (cc) " (CC)" else ""
        }
        return disambiguate(base, formats) { null }
    }

    private fun disambiguate(base: List<String>, formats: List<Format>, extra: (Format) -> String?): List<String> {
        val counts = base.groupingBy { it }.eachCount()
        val seen = HashMap<String, Int>()
        return base.mapIndexed { i, name ->
            if ((counts[name] ?: 0) <= 1) {
                name
            } else {
                val e = extra(formats[i])
                val n = (seen[name] ?: 0) + 1
                seen[name] = n
                if (e != null) "$name  •  $e" else "$name ($n)"
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------------
@OptIn(UnstableApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlayerScreen(
    mediaId: String,
    mediaType: MediaType,
    title: String,
    posterPath: String?,
    backdropPath: String?,
    tmdbId: Long?,
    season: Int,
    episode: Int,
    viewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    skipAutoInit: Boolean = false
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(mediaId, season, episode) {
        // Viduki's extractor already pushed a resolved source straight into the ViewModel
        // (see initPlayerWithExternalSource) — re-running the normal admin/scraper resolution
        // here would overwrite it, so this screen just renders whatever is already there.
        if (!skipAutoInit) {
            viewModel.initPlayer(
                mediaId = mediaId,
                mediaType = mediaType,
                title = title,
                posterPath = posterPath,
                backdropPath = backdropPath,
                tmdbId = tmdbId,
                season = season,
                episode = episode
            )
        }
    }

    // Landscape + immersive + keep screen on (everything restored on exit)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.let {
                val lp = it.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.attributes = lp
            }
        }
    }

    val trackSelector = remember { DefaultTrackSelector(context) }

    // Disk cache: previously played segments (rewinds, replays, Continue Watching re-opens)
    // load instantly instead of hitting the network again. Capped at 400MB, oldest evicted first.
    val streamCache = remember {
        SimpleCache(
            File(context.cacheDir, "media_cache"),
            LeastRecentlyUsedCacheEvictor(400L * 1024 * 1024),
            StandaloneDatabaseProvider(context)
        )
    }
    DisposableEffect(Unit) {
        onDispose { streamCache.release() }
    }

    val loadControl = remember {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15000, // minBufferMs (steady-state target; lower than before so playback starts sooner)
                120000, // maxBufferMs
                1200,  // bufferForPlaybackMs (start playing as soon as ~1.2s is buffered)
                2500   // bufferForPlaybackAfterRebufferMs (resume quickly after a stall)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
    }

    val exoPlayer = remember {
        val appContext = context.applicationContext
        val renderersFactory = DefaultRenderersFactory(appContext).setEnableDecoderFallback(true)
        val audioAttrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()
        ExoPlayer.Builder(appContext, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttrs, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build().apply {
                playWhenReady = true
                // no random subtitle turning on by itself - user picks from the panel
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var playWhenReady by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isScrubbing by remember { mutableStateOf(false) }

    var hud by remember { mutableStateOf<Pair<ImageVector, String>?>(null) }
    var hudProgress by remember { mutableStateOf<Float?>(null) }

    var panel by remember { mutableStateOf(PlayerPanel.None) }

    // ---- TV / remote support ----
    val isTv = LocalIsTv.current
    var interactionTick by remember { mutableIntStateOf(0) }   // any key press resets the auto-hide timer
    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val errorFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    var videoOptions by remember { mutableStateOf<List<VideoTrackOption>>(emptyList()) }
    var audioOptions by remember { mutableStateOf<List<AudioTrackOption>>(emptyList()) }
    var subtitleOptions by remember { mutableStateOf<List<SubtitleTrackOption>>(emptyList()) }
    var showNextPrompt by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val prefs = remember { AppPrefs.get(context) }
    val prefState by prefs.state.collectAsStateWithLifecycle()
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var sleepEndAt by remember { mutableStateOf<Long?>(null) }
    var sleepAtEpisodeEnd by remember { mutableStateOf(false) }
    var speedBoost by remember { mutableStateOf(false) }
    var swipeTarget by remember { mutableStateOf<Long?>(null) }
    var autoTrackApplied by remember { mutableStateOf(false) }

    // language label without the "• 5.1" part, e.g. "Hindi  •  5.1" -> "Hindi"
    fun langOfLabel(label: String): String = label.substringBefore("  •").substringBefore(" (").trim()

    fun selectVideoOption(opt: VideoTrackOption) {
        val b = exoPlayer.trackSelectionParameters.buildUpon()
        if (opt.isAuto || opt.group == null) {
            b.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        } else {
            b.setOverrideForType(TrackSelectionOverride(opt.group, opt.trackIndex))
        }
        exoPlayer.trackSelectionParameters = b.build()
    }

    fun selectAudio(opt: AudioTrackOption, persist: Boolean) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(opt.group, opt.trackIndex))
            .build()
        val lang = langOfLabel(opt.label)
        if (persist && !lang.startsWith("Audio ")) prefs.lastAudioLang = lang
    }

    fun selectSubtitle(opt: SubtitleTrackOption?, persist: Boolean) {
        val b = exoPlayer.trackSelectionParameters.buildUpon()
        if (opt == null) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(opt.group, opt.trackIndex))
        }
        exoPlayer.trackSelectionParameters = b.build()
        if (persist) prefs.lastSubtitleLang = opt?.let { langOfLabel(it.label) } ?: ""
    }

    // subtitle size from Profile settings
    LaunchedEffect(playerViewRef, prefState.subtitleScale) {
        playerViewRef?.subtitleView?.let { sv ->
            sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * prefState.subtitleScale)
            sv.setStyle(
                CaptionStyleCompat(
                    android.graphics.Color.WHITE,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.TRANSPARENT,
                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                    android.graphics.Color.BLACK,
                    null
                )
            )
        }
    }

    // sleep timer
    LaunchedEffect(sleepEndAt) {
        val end = sleepEndAt ?: return@LaunchedEffect
        val wait = end - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        if (sleepEndAt == end) {
            exoPlayer.pause()
            sleepEndAt = null
            hud = Icons.Default.Bedtime to "Sleep timer: video pause"
        }
    }

    // per-source retry bookkeeping
    var sameSourceRetries by remember { mutableIntStateOf(0) }
    var hlsForcedFor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.playbackSpeed) {
        exoPlayer.setPlaybackSpeed(uiState.playbackSpeed)
    }

    // ---- prepare a source ------------------------------------------------
    fun prepareSource(source: VideoStreamSource, startMs: Long, forceHls: Boolean) {
        try {
            val headers = HashMap<String, String>()
            headers.putAll(source.headers)
            val userAgent = headers.remove("User-Agent")
                ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

            val httpFactory = DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(30_000)
                .setReadTimeoutMs(60_000)

            val reqProps = mutableMapOf<String, String>(
                "Accept" to "*/*",
                "Accept-Language" to "en-US,en;q=0.9",
                "Sec-Fetch-Mode" to "cors"
            )
            reqProps.putAll(headers)
            httpFactory.setDefaultRequestProperties(reqProps)

            val url = source.url.trim()
            val uri = if (url.startsWith("/")) Uri.fromFile(File(url)) else Uri.parse(url)
            val itemBuilder = MediaItem.Builder().setUri(uri)
            if (forceHls || source.isHls) {
                itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            } else if (source.isDash || url.contains(".mpd", ignoreCase = true)) {
                itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            }

            // subtitles uploaded by admin (subtitles: [{url, lang, label}])
            val externalSubs = if (uiState.isLocalFile) emptyList() else uiState.streamInfo?.subtitles.orEmpty()
            if (externalSubs.isNotEmpty()) {
                itemBuilder.setSubtitleConfigurations(
                    externalSubs.map { st ->
                        val clean = st.url.substringBefore('?').lowercase(Locale.ROOT)
                        val mime = when {
                            clean.endsWith(".vtt") -> MimeTypes.TEXT_VTT
                            clean.endsWith(".ass") || clean.endsWith(".ssa") -> MimeTypes.TEXT_SSA
                            else -> MimeTypes.APPLICATION_SUBRIP
                        }
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(st.url))
                            .setMimeType(mime)
                            .setLanguage(st.language.ifBlank { null })
                            .setLabel(st.label)
                            .build()
                    }
                )
            }

            // Wrap the network data source with the disk cache: repeat reads of the same
            // segment (seeking backward, replaying, resuming Continue Watching) come from disk.
            val cacheFactory = CacheDataSource.Factory()
                .setCache(streamCache)
                .setUpstreamDataSourceFactory(httpFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

            // Retry flaky proxy/CDN responses a few times with a short backoff instead of
            // failing the whole playback on one dropped chunk.
            val loadErrorHandlingPolicy = DefaultLoadErrorHandlingPolicy(4)

            val isHlsSource = forceHls || source.isHls || url.contains(".m3u8", ignoreCase = true)
            val mediaSource: MediaSource = if (isHlsSource) {
                HlsMediaSource.Factory(cacheFactory)
                    .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                    // Skip downloading the first media segment just to read its format - start
                    // playback from the playlist alone. This is the single biggest speed win for HLS.
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(itemBuilder.build())
            } else {
                DefaultMediaSourceFactory(cacheFactory)
                    .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
                    .createMediaSource(itemBuilder.build())
            }
            if (startMs > 1000L) exoPlayer.setMediaSource(mediaSource, startMs) else exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        } catch (e: Exception) {
            Log.d("StreamDebug", "prepareSource failed: ${e.message}", e)
            viewModel.onPlaybackError(e.message)
        }
    }

    LaunchedEffect(uiState.activeSource, uiState.playbackAttempt) {
        val source = uiState.activeSource ?: return@LaunchedEffect
        sameSourceRetries = 0
        hlsForcedFor = null
        autoTrackApplied = false
        videoOptions = emptyList()
        audioOptions = emptyList()
        subtitleOptions = emptyList()
        prepareSource(source, uiState.startPositionMs, forceHls = false)
    }

    // ---- player listener -------------------------------------------------
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                playWhenReady = ready
            }

            override fun onTracksChanged(tracks: Tracks) {
                class Raw(val group: TrackGroup, val index: Int, val format: Format, val selected: Boolean)

                val videoRaws = mutableListOf<Raw>()
                val audio = mutableListOf<Raw>()
                val text = mutableListOf<Raw>()
                for (group in tracks.groups) {
                    for (i in 0 until group.length) {
                        if (!group.isTrackSupported(i)) continue
                        val raw = Raw(group.mediaTrackGroup, i, group.getTrackFormat(i), group.isTrackSelected(i))
                        when (group.type) {
                            C.TRACK_TYPE_VIDEO -> videoRaws.add(raw)
                            C.TRACK_TYPE_AUDIO -> audio.add(raw)
                            C.TRACK_TYPE_TEXT -> text.add(raw)
                        }
                    }
                }

                // Build multi-quality video options
                val newVideo = mutableListOf<VideoTrackOption>()
                val isAnyVideoOverride = exoPlayer.trackSelectionParameters.overrides.keys.any { group -> group.type == C.TRACK_TYPE_VIDEO }
                newVideo.add(
                    VideoTrackOption(
                        id = "v_auto",
                        label = "Auto (Adaptive)",
                        height = 0,
                        isAuto = true,
                        isSelected = !isAnyVideoOverride,
                        group = null,
                        trackIndex = 0
                    )
                )

                val sortedVideo = videoRaws.distinctBy { r -> r.format.height }.sortedByDescending { r -> r.format.height }
                for (r in sortedVideo) {
                    val h = r.format.height
                    val label = when {
                        h >= 2160 -> "4K Ultra HD (2160p)"
                        h >= 1080 -> "1080p Full HD"
                        h >= 720 -> "720p HD"
                        h >= 480 -> "480p SD"
                        h >= 360 -> "360p"
                        h > 0 -> "${h}p"
                        else -> "HD Standard"
                    }
                    newVideo.add(
                        VideoTrackOption(
                            id = "v_${r.format.id}_$h",
                            label = label,
                            height = h,
                            isAuto = false,
                            isSelected = isAnyVideoOverride && r.selected,
                            group = r.group,
                            trackIndex = r.index
                        )
                    )
                }
                videoOptions = newVideo

                val audioNames = TrackNamer.audioNames(audio.map { it.format })
                val newAudio = audio.mapIndexed { i, r ->
                    AudioTrackOption("a_$i", audioNames[i], r.selected, r.group, r.index)
                }
                val textNames = TrackNamer.subtitleNames(text.map { it.format })
                val newText = text.mapIndexed { i, r ->
                    SubtitleTrackOption("s_$i", textNames[i], r.selected, r.group, r.index)
                }
                audioOptions = newAudio
                subtitleOptions = newText

                // remember my dub / subtitle language across episodes and titles
                if (!autoTrackApplied && newAudio.isNotEmpty()) {
                    autoTrackApplied = true
                    val wantAudio = prefs.lastAudioLang
                    if (wantAudio.isNotBlank()) {
                        val cur = newAudio.firstOrNull { it.isSelected }
                        val target = newAudio.firstOrNull { it.label.startsWith(wantAudio, ignoreCase = true) }
                        if (target != null && cur?.id != target.id) selectAudio(target, persist = false)
                    }
                    val wantSub = prefs.lastSubtitleLang
                    if (wantSub.isNotBlank() && newText.isNotEmpty()) {
                        val target = newText.firstOrNull { it.label.startsWith(wantSub, ignoreCase = true) }
                        if (target != null) selectSubtitle(target, persist = false)
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                    sameSourceRetries = 0
                } else if (state == Player.STATE_ENDED) {
                    viewModel.updateProgress(exoPlayer.duration.coerceAtLeast(0L), exoPlayer.duration.coerceAtLeast(0L), force = true)
                    if (sleepAtEpisodeEnd) {
                        sleepAtEpisodeEnd = false
                        viewModel.setControlsVisible(true)
                        hud = Icons.Default.Bedtime to "Sleep timer: End of episode"
                    } else if (uiState.autoPlayNext && uiState.nextEpisode != null) {
                        viewModel.loadNextEpisode()
                    } else {
                        viewModel.setControlsVisible(true)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.d("StreamDebug", "ExoPlayer error ${error.errorCodeName}: ${error.message}", error)
                val source = uiState.activeSource ?: return
                val code = error.errorCode

                // Check internet connectivity first
                if (!NetworkUtils.isNetworkAvailable(context)) {
                    hud = Icons.Default.WifiOff to "Checking internet connection..."
                    scope.launch {
                        delay(3000)
                        prepareSource(source, exoPlayer.currentPosition.coerceAtLeast(0L), forceHls = hlsForcedFor == source.url)
                    }
                    return
                }

                // 1) Transient network / cold start -> retry up to 4 times with exponential backoff!
                val transient = code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                    code == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                    code == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                    code == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ||
                    code == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
                if (transient && sameSourceRetries < 4 && !uiState.isLocalFile) {
                    sameSourceRetries++
                    val at = exoPlayer.currentPosition.coerceAtLeast(0L)
                    hud = Icons.Default.CloudSync to "Buffering stream... (${sameSourceRetries}/4)"
                    scope.launch {
                        delay(1200L * sameSourceRetries)
                        prepareSource(source, at, forceHls = hlsForcedFor == source.url)
                    }
                    return
                }

                // 2) Link has no .m3u8 in it but is really HLS -> try again as HLS
                val unknownFormat = code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
                if (unknownFormat && hlsForcedFor != source.url && !uiState.isLocalFile) {
                    hlsForcedFor = source.url
                    prepareSource(source, exoPlayer.currentPosition.coerceAtLeast(0L), forceHls = true)
                    return
                }

                // 3) give up on this source -> ViewModel tries the next stream source
                val reason = when (code) {
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "Server refused video request (link expired or unavailable)"
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                        "This video format is not supported on this device"
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Could not connect to streaming server. Check your internet connection"
                    else -> "Unable to play video (${error.errorCodeName})"
                }
                viewModel.onPlaybackError(reason)
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            try {
                val finalPos = exoPlayer.currentPosition
                val finalDur = exoPlayer.duration
                if (finalDur > 0 && finalPos > 1000L) viewModel.updateProgress(finalPos, finalDur, force = true)
                exoPlayer.removeListener(listener)
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                exoPlayer.release()
            } catch (e: Exception) {
                Log.d("StreamDebug", "ExoPlayer release exception: ${e.message}")
            }
        }
    }

    // pause when app goes to background (unless Picture-in-Picture)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                    (context as? Activity)?.isInPictureInPictureMode == true
                if (!inPip) exoPlayer.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // position ticker
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isScrubbing) positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            if (exoPlayer.isPlaying) viewModel.updateProgress(positionMs, durationMs)

            val remaining = durationMs - positionMs
            if (uiState.nextEpisode != null && durationMs > 60_000L && remaining in 1_000L..25_000L) {
                showNextPrompt = true
            } else if (remaining > 30_000L) {
                showNextPrompt = false
            }
            delay(500)
        }
    }

    // auto-hide controls
    LaunchedEffect(uiState.isControlsVisible, isPlaying, panel, hud, interactionTick) {
        if (uiState.isControlsVisible && isPlaying && panel == PlayerPanel.None) {
            delay(4000)
            viewModel.setControlsVisible(false)
        }
    }

    // toast-like notice from the ViewModel
    LaunchedEffect(uiState.notice) {
        if (uiState.notice != null) {
            delay(3500)
            viewModel.clearNotice()
        }
    }

    // gesture HUD auto-hide
    LaunchedEffect(hud) {
        if (hud != null) {
            delay(900)
            hud = null
            hudProgress = null
        }
    }

    fun seekBy(deltaMs: Long) {
        val dur = exoPlayer.duration
        val target = (exoPlayer.currentPosition + deltaMs).coerceAtLeast(0L)
        exoPlayer.seekTo(if (dur > 0) target.coerceAtMost(dur) else target)
    }

    fun togglePlayback() {
        if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0)
        if (exoPlayer.playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED) exoPlayer.pause() else exoPlayer.play()
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val size = exoPlayer.videoSize
                val ratio = if (size.width > 0 && size.height > 0) Rational(size.width, size.height) else Rational(16, 9)
                viewModel.setControlsVisible(false)
                panel = PlayerPanel.None
                (context as? Activity)?.enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(ratio).build()
                )
            } catch (e: Exception) {
                Log.w("PlayerScreen", "PiP failed: ${e.message}")
            }
        }
    }

    val resizeLabel = when (uiState.resizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Stretch"
        else -> "Fit"
    }
    val selectedAudioLabel = audioOptions.firstOrNull { it.isSelected }?.label?.substringBefore("  •")
    val sources = uiState.streamInfo?.sources ?: emptyList()
    val activeSource = uiState.activeSource
    val fatalError: String? = uiState.playbackError ?: if (activeSource == null && !uiState.isLoading) uiState.error else null
    val controlsShown = uiState.isControlsVisible && !uiState.isLocked && fatalError == null
    val skipIntroVisible = uiState.introEndMs != null && !uiState.isLocked && fatalError == null && panel == PlayerPanel.None &&
        positionMs >= (uiState.introStartMs ?: 0L) && positionMs < (uiState.introEndMs ?: 0L) - 1_000L
    val nextPromptVisible = showNextPrompt && uiState.nextEpisode != null && !uiState.isLocked && fatalError == null

    // TV: always keep the remote's focus somewhere sensible so every key press reaches us
    LaunchedEffect(isTv, controlsShown, panel, fatalError, skipIntroVisible, nextPromptVisible) {
        if (!isTv) return@LaunchedEffect
        delay(80)
        when {
            panel != PlayerPanel.None -> panelFocus.requestFocusWhenReady()
            fatalError != null -> errorFocus.requestFocusWhenReady()
            controlsShown -> playFocus.requestFocusWhenReady()
            skipIntroVisible -> skipFocus.requestFocusWhenReady()
            nextPromptVisible -> nextFocus.requestFocusWhenReady()
            else -> rootFocus.requestFocusWhenReady()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown) interactionTick++
                false
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
                val k = keyEvent.key
                val blocked = panel != PlayerPanel.None || fatalError != null || uiState.isLocked
                when {
                    k == Key.Back || k == Key.Escape -> {
                        if (panel != PlayerPanel.None) {
                            panel = PlayerPanel.None
                            true
                        } else if (uiState.isControlsVisible) {
                            viewModel.setControlsVisible(false)
                            true
                        } else false
                    }
                    k == Key.MediaStop -> { onBackClick(); true }
                    // while a panel / error card is open the D-pad just moves focus inside it
                    blocked -> false
                    k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter -> {
                        togglePlayback()
                        viewModel.setControlsVisible(true)
                        true
                    }
                    k == Key.MediaPlayPause -> { togglePlayback(); viewModel.setControlsVisible(true); true }
                    k == Key.MediaPlay -> { exoPlayer.play(); true }
                    k == Key.MediaPause -> { exoPlayer.pause(); viewModel.setControlsVisible(true); true }
                    k == Key.MediaFastForward -> {
                        seekBy(30_000L); hud = Icons.Default.FastForward to "+30s"; true
                    }
                    k == Key.MediaRewind -> {
                        seekBy(-30_000L); hud = Icons.Default.FastRewind to "-30s"; true
                    }
                    k == Key.MediaNext -> {
                        if (uiState.nextEpisode != null) { viewModel.loadNextEpisode(); true } else false
                    }
                    k == Key.Menu -> { panel = PlayerPanel.Settings; true }
                    k == Key.DirectionLeft -> {
                        // controls visible: Left/Right move focus between buttons, otherwise seek
                        if (controlsShown) false else {
                            seekBy(-10_000L)
                            hud = Icons.Default.Replay10 to "-10s"
                            true
                        }
                    }
                    k == Key.DirectionRight -> {
                        if (controlsShown) false else {
                            seekBy(10_000L)
                            hud = Icons.Default.Forward10 to "+10s"
                            true
                        }
                    }
                    k == Key.DirectionUp || k == Key.DirectionDown -> {
                        if (!controlsShown) {
                            viewModel.setControlsVisible(true)
                            true
                        } else false
                    }
                    else -> false
                }
            }
            .then(if (isTv) Modifier.focusRequester(rootFocus).focusable() else Modifier)
            .pointerInput(uiState.isLocked) {
                if (uiState.isLocked) {
                    detectTapGestures(onTap = { hud = Icons.Default.Lock to "Screen locked" })
                } else {
                    detectTapGestures(
                        onPress = {
                            tryAwaitRelease()
                            if (speedBoost) {
                                speedBoost = false
                                exoPlayer.setPlaybackSpeed(uiState.playbackSpeed)
                            }
                        },
                        onLongPress = {
                            speedBoost = true
                            exoPlayer.setPlaybackSpeed(2f)
                        },
                        onTap = {
                            if (panel != PlayerPanel.None) panel = PlayerPanel.None
                            else viewModel.toggleControlsVisibility()
                        },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2f) {
                                seekBy(-10_000L)
                                hud = Icons.Default.Replay10 to "-10s"
                            } else {
                                seekBy(10_000L)
                                hud = Icons.Default.Forward10 to "+10s"
                            }
                        }
                    )
                }
            }
            .pointerInput(uiState.isLocked) {
                if (!uiState.isLocked) {
                    var startBrightness = 0.5f
                    var startVolume = 0f
                    var accum = 0f
                    var leftSide = true
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            accum = 0f
                            leftSide = offset.x < size.width / 2f
                            val lp = (context as? Activity)?.window?.attributes
                            val cur = lp?.screenBrightness ?: -1f
                            startBrightness = if (cur < 0f) 0.5f else cur
                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            accum -= dragAmount
                            val fraction = accum / (size.height * 0.7f)
                            if (leftSide) {
                                val window = (context as? Activity)?.window
                                val nb = (startBrightness + fraction).coerceIn(0.02f, 1f)
                                if (window != null) {
                                    val lp = window.attributes
                                    lp.screenBrightness = nb
                                    window.attributes = lp
                                }
                                hud = Icons.Default.Brightness6 to "Brightness ${(nb * 100).toInt()}%"
                                hudProgress = nb
                            } else {
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val nv = (startVolume + fraction * maxVol).coerceIn(0f, maxVol.toFloat())
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nv.toInt(), 0)
                                val pct = if (maxVol > 0) nv / maxVol else 0f
                                hud = (if (nv.toInt() == 0) Icons.Default.VolumeMute else Icons.Default.VolumeUp) to
                                    "Volume ${(pct * 100).toInt()}%"
                                hudProgress = pct
                            }
                        }
                    )
                }
            }
            .pointerInput(uiState.isLocked) {
                if (!uiState.isLocked) {
                    var startPos = 0L
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            startPos = exoPlayer.currentPosition
                            total = 0f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            total += dragAmount
                            val dur = exoPlayer.duration
                            if (dur > 0) {
                                val deltaMs = (total / size.width * 90_000f).toLong()
                                val target = (startPos + deltaMs).coerceIn(0L, dur)
                                swipeTarget = target
                                val secs = deltaMs / 1000
                                hud = (if (deltaMs >= 0) Icons.Default.FastForward else Icons.Default.FastRewind) to
                                    "${formatTimeMs(target)}  (${if (secs >= 0) "+" else ""}${secs}s)"
                            }
                        },
                        onDragEnd = {
                            swipeTarget?.let { exoPlayer.seekTo(it) }
                            swipeTarget = null
                        },
                        onDragCancel = { swipeTarget = null }
                    )
                }
            }
    ) {
        // ---------------- video surface ----------------
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    playerViewRef = this
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { it.resizeMode = uiState.resizeMode },
            onRelease = { it.player = null; playerViewRef = null },
            modifier = Modifier.fillMaxSize()
        )

        // ---------------- loading ----------------
        if (uiState.isLoading) {
            LoadingOverlay(title = uiState.mediaTitle.ifBlank { title }, backdrop = backdropPath ?: posterPath)
        } else if (isBuffering && fatalError == null && activeSource != null) {
            Box(Modifier.align(Alignment.Center)) {
                CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(46.dp))
            }
        }

        // ---------------- controls ----------------
        AnimatedVisibility(visible = controlsShown && !(isTv && panel != PlayerPanel.None), enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xCC000000), Color(0x22000000), Color(0x22000000), Color(0xDD000000))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                // ---- top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoundIconButton(icon = Icons.AutoMirrored.Filled.ArrowBack, description = "Back", onClick = onBackClick)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.mediaTitle.ifBlank { title },
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (uiState.mediaType != MediaType.MOVIE) {
                            val epTitle = uiState.availableEpisodes
                                .firstOrNull { it.episodeNumber == uiState.episodeNumber }?.title
                                ?.takeIf { it.isNotBlank() && !it.startsWith("Episode ") }
                            Text(
                                text = "S${uiState.seasonNumber}  E${uiState.episodeNumber}" + (epTitle?.let { "  •  $it" } ?: ""),
                                color = TextDim,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (uiState.mediaType != MediaType.MOVIE && uiState.availableEpisodes.isNotEmpty()) {
                        PillButton(icon = Icons.Default.VideoLibrary, text = "Episodes") { panel = PlayerPanel.Episodes }
                    }
                }

                // ---- center transport
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(44.dp)
                ) {
                    RoundIconButton(icon = Icons.Default.Replay10, description = "Back 10 seconds", size = 52.dp) { seekBy(-10_000L) }
                    val playInteraction = remember { MutableInteractionSource() }
                    val playFocused by playInteraction.collectIsFocusedAsState()
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .focusRequester(playFocus)
                            .clip(CircleShape)
                            .background(if (playFocused) Color.White else Accent)
                            .border(if (playFocused) 3.dp else 0.dp, Accent, CircleShape)
                            .clickable(interactionSource = playInteraction, indication = null) { togglePlayback() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (playWhenReady && exoPlayer.playbackState != Player.STATE_ENDED) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play / Pause",
                            tint = if (playFocused) Accent else Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                    RoundIconButton(icon = Icons.Default.Forward10, description = "Forward 10 seconds", size = 52.dp) { seekBy(10_000L) }
                }

                // ---- bottom
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTimeMs(positionMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        SeekBar(
                            positionMs = positionMs,
                            bufferedMs = bufferedMs,
                            durationMs = durationMs,
                            onScrub = { isScrubbing = true; positionMs = it; hud = Icons.Default.Schedule to formatTimeMs(it) },
                            onScrubFinished = { target ->
                                isScrubbing = false
                                positionMs = target
                                exoPlayer.seekTo(target)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                                .tvFocusRing(RoundedCornerShape(8.dp))
                                .onKeyEvent { ev ->
                                    if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionLeft) {
                                        seekBy(-10_000L); hud = Icons.Default.Replay10 to "-10s"; true
                                    } else if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionRight) {
                                        seekBy(10_000L); hud = Icons.Default.Forward10 to "+10s"; true
                                    } else false
                                }
                                .focusable()
                        )
                        Text(formatTimeMs(durationMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (!isTv) PillButton(icon = Icons.Default.Lock, text = "Lock") { viewModel.toggleScreenLock() }
                            PillButton(icon = Icons.Default.Speed, text = if (uiState.playbackSpeed == 1f) "Speed" else "${uiState.playbackSpeed}x") {
                                panel = PlayerPanel.Speed
                            }
                            PillButton(icon = Icons.Default.AspectRatio, text = resizeLabel) {
                                viewModel.cycleResizeMode()
                            }
                            if (!isTv) PillButton(icon = Icons.Default.PictureInPictureAlt, text = "PiP") { enterPip() }
                        }
                        Box(modifier = Modifier.weight(1f).padding(start = 12.dp), contentAlignment = Alignment.CenterEnd) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (audioOptions.size > 1 || (audioOptions.size == 1 && selectedAudioLabel != null)) {
                                    PillButton(icon = Icons.Default.Audiotrack, text = selectedAudioLabel ?: "Audio") { panel = PlayerPanel.Audio }
                                }
                                if (subtitleOptions.isNotEmpty()) {
                                    PillButton(icon = Icons.Default.Subtitles, text = "Subtitles") { panel = PlayerPanel.Subtitles }
                                }
                                if (sources.size > 1 || (sources.size == 1 && !uiState.isLocalFile)) {
                                    PillButton(
                                        icon = Icons.Default.HighQuality,
                                        text = activeSource?.let { sourceLabel(it) } ?: "Quality"
                                    ) { panel = PlayerPanel.Quality }
                                }
                                if (uiState.mediaType != MediaType.MOVIE) {
                                    PillButton(icon = Icons.Default.FastForward, text = "+85s") { seekBy(85_000L) }
                                }
                                if (uiState.nextEpisode != null) {
                                    PillButton(icon = Icons.Default.SkipNext, text = "Next") { viewModel.loadNextEpisode() }
                                }
                                PillButton(icon = Icons.Default.Tune, text = "More") { panel = PlayerPanel.Settings }
                            }
                        }
                    }
                }
            }
        }

        // ---------------- skip intro (only when the admin set introStart / introEnd) ----------------
        val introStart = uiState.introStartMs
        val introEnd = uiState.introEndMs
        if (introEnd != null && skipIntroVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = if (controlsShown) 110.dp else 40.dp, end = 24.dp)
            ) {
                PillButton(icon = Icons.Default.SkipNext, text = "Skip intro", highlighted = true, modifier = Modifier.focusRequester(skipFocus)) { exoPlayer.seekTo(introEnd) }
            }
        }

        // ---------------- 2x speed indicator ----------------
        if (speedBoost) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.FastForward, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                    Text("2x", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // ---------------- next episode card ----------------
        val next = uiState.nextEpisode
        if (nextPromptVisible && next != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = if (controlsShown) 120.dp else 40.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PanelBg),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Accent.copy(alpha = 0.6f))
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("UP NEXT", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "S${next.seasonNumber}  E${next.episodeNumber}  •  ${next.title}",
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 260.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { showNextPrompt = false; viewModel.loadNextEpisode() },
                                modifier = Modifier.focusRequester(nextFocus).tvFocusRing(RoundedCornerShape(10.dp)),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                            ) { Text("Play now", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            OutlinedButton(
                                onClick = { showNextPrompt = false },
                                modifier = Modifier.tvFocusRing(RoundedCornerShape(10.dp)),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Color(0x55FFFFFF)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) { Text("Hide", fontSize = 13.sp) }
                        }
                    }
                }
            }
        }

        // ---------------- notice pill ----------------
        uiState.notice?.let { msg ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xE6222222))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 13.sp)
            }
        }

        // ---------------- gesture HUD ----------------
        hud?.let { (icon, text) ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                hudProgress?.let { p ->
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { p.coerceIn(0f, 1f) },
                        color = Accent,
                        trackColor = Color(0x44FFFFFF),
                        modifier = Modifier
                            .width(140.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }

        // ---------------- lock ----------------
        if (uiState.isLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 28.dp)
            ) {
                RoundIconButton(icon = Icons.Default.LockOpen, description = "Unlock", size = 50.dp, background = Accent) {
                    viewModel.unlockScreen()
                }
            }
        }

        // ---------------- error card ----------------
        if (fatalError != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xEE000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.VideocamOff, contentDescription = null, tint = Accent, modifier = Modifier.size(54.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Playback Error", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        fatalError,
                        color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 420.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { viewModel.retryPlayback() },
                            modifier = Modifier.focusRequester(errorFocus).tvFocusRing(RoundedCornerShape(22.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onBackClick,
                            shape = RoundedCornerShape(22.dp),
                            border = BorderStroke(1.dp, Color(0x55FFFFFF)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) { Text("Go back") }
                    }
                }
            }
        }

        // ---------------- side panel ----------------
        if (panel != PlayerPanel.None) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { panel = PlayerPanel.None } }
            )
        }
        AnimatedVisibility(
            visible = panel != PlayerPanel.None,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val panelTitle = when (panel) {
                PlayerPanel.Quality -> "Quality"
                PlayerPanel.Audio -> "Audio"
                PlayerPanel.Subtitles -> "Subtitles"
                PlayerPanel.Speed -> "Playback speed"
                PlayerPanel.Episodes -> "Episodes"
                PlayerPanel.Settings -> "Settings"
                PlayerPanel.None -> ""
            }
            SidePanel(title = panelTitle, onClose = { panel = PlayerPanel.None }, listFocus = panelFocus) {
                when (panel) {
                    PlayerPanel.Quality -> {
                        if (videoOptions.isNotEmpty()) {
                            item { PanelHeader("Video Resolution & Quality") }
                            items(videoOptions) { vOpt ->
                                PanelRow(
                                    label = vOpt.label,
                                    selected = vOpt.isSelected,
                                    onClick = {
                                        selectVideoOption(vOpt)
                                        panel = PlayerPanel.None
                                        hud = Icons.Default.HighQuality to "Resolution set to ${vOpt.label}"
                                    }
                                )
                            }
                        } else {
                            item { PanelHeader("Adaptive Quality Limits") }
                            val presetOptions = listOf(
                                "Auto" to "⚡ Auto (Adaptive Speed)",
                                "1080p" to "🎬 1080p Full HD",
                                "720p" to "📺 720p HD",
                                "480p" to "📱 480p SD (Data Saver)",
                                "360p" to "📶 360p Low"
                            )
                            items(presetOptions) { (key, label) ->
                                PanelRow(
                                    label = label,
                                    selected = true,
                                    onClick = {
                                        panel = PlayerPanel.None
                                        hud = Icons.Default.HighQuality to "Auto quality active"
                                    }
                                )
                            }
                        }
                        if (sources.isNotEmpty()) {
                            item { PanelHeader("Server Stream Links") }
                            items(sources) { src ->
                                PanelRow(
                                    label = sourceLabel(src),
                                    selected = src == activeSource,
                                    onClick = {
                                        panel = PlayerPanel.None
                                        if (src != activeSource) viewModel.selectSource(src)
                                    }
                                )
                            }
                        }
                    }
                    PlayerPanel.Audio -> {
                        items(audioOptions) { opt ->
                            PanelRow(label = opt.label, selected = opt.isSelected, onClick = {
                                selectAudio(opt, persist = true)
                                panel = PlayerPanel.None
                            })
                        }
                    }
                    PlayerPanel.Subtitles -> {
                        item {
                            PanelRow(label = "Off", selected = subtitleOptions.none { it.isSelected }, onClick = {
                                selectSubtitle(null, persist = true)
                                panel = PlayerPanel.None
                            })
                        }
                        items(subtitleOptions) { opt ->
                            PanelRow(label = opt.label, selected = opt.isSelected, onClick = {
                                selectSubtitle(opt, persist = true)
                                panel = PlayerPanel.None
                            })
                        }
                    }
                    PlayerPanel.Speed -> {
                        items(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)) { speed ->
                            PanelRow(
                                label = if (speed == 1.0f) "Normal" else "${speed}x",
                                selected = speed == uiState.playbackSpeed,
                                onClick = {
                                    viewModel.setPlaybackSpeed(speed)
                                    panel = PlayerPanel.None
                                }
                            )
                        }
                    }
                    PlayerPanel.Episodes -> {
                        if (uiState.seasons.size > 1) {
                            item {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    uiState.seasons.forEach { sn ->
                                        val sel = sn == uiState.panelSeason
                                        Text(
                                            text = "Season $sn",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(if (sel) Accent else Color(0x22FFFFFF))
                                                .tvFocusRing(RoundedCornerShape(16.dp))
                                                .clickable { viewModel.loadPanelSeason(sn) }
                                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                        )
                                    }
                                }
                            }
                        }
                        items(uiState.panelEpisodes.ifEmpty { uiState.availableEpisodes }) { ep ->
                            val current = ep.seasonNumber == uiState.seasonNumber && ep.episodeNumber == uiState.episodeNumber
                            EpisodeRow(
                                ep = ep,
                                isCurrent = current,
                                onClick = {
                                    panel = PlayerPanel.None
                                    if (!current) viewModel.selectEpisode(ep.seasonNumber, ep.episodeNumber)
                                }
                            )
                        }
                    }
                    PlayerPanel.Settings -> {
                        item {
                            SettingToggleRow(
                                title = "Auto-play next episode",
                                checked = uiState.autoPlayNext,
                                onToggle = { viewModel.toggleAutoPlayNext() }
                            )
                        }
                        item { PanelHeader("Subtitle size") }
                        item {
                            val labels = listOf("Small" to 0.8f, "Normal" to 1f, "Large" to 1.3f, "XL" to 1.6f)
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                labels.forEach { (name, value) ->
                                    val sel = prefState.subtitleScale == value
                                    Text(
                                        text = name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (sel) Accent else Color(0x22FFFFFF))
                                            .tvFocusRing(RoundedCornerShape(16.dp))
                                            .clickable { prefs.subtitleScale = value }
                                            .padding(horizontal = 14.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        item { PanelHeader("Sleep timer") }
                        val sleepOptions = listOf("Off" to 0, "15 min" to 15, "30 min" to 30, "45 min" to 45, "60 min" to 60)
                        items(sleepOptions) { (name, mins) ->
                            val active = if (mins == 0) (sleepEndAt == null && !sleepAtEpisodeEnd) else false
                            PanelRow(label = name, selected = active, onClick = {
                                sleepAtEpisodeEnd = false
                                sleepEndAt = if (mins == 0) null else System.currentTimeMillis() + mins * 60_000L
                                if (mins > 0) hud = Icons.Default.Bedtime to "Sleep timer: $mins min"
                                panel = PlayerPanel.None
                            })
                        }
                        if (uiState.mediaType != MediaType.MOVIE) {
                            item {
                                PanelRow(label = "End of this episode", selected = sleepAtEpisodeEnd, onClick = {
                                    sleepEndAt = null
                                    sleepAtEpisodeEnd = true
                                    hud = Icons.Default.Bedtime to "Playback will stop after this episode"
                                    panel = PlayerPanel.None
                                })
                            }
                        }
                    }
                    PlayerPanel.None -> {}
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Small UI pieces
// ---------------------------------------------------------------------------

private fun sourceLabel(src: VideoStreamSource): String {
    val q = src.quality.ifBlank { "Auto" }
    return if (src.language.isNotBlank()) "$q  •  ${src.language}" else q
}

@Composable
private fun LoadingOverlay(title: String, backdrop: String?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val url = backdrop?.takeIf { it.startsWith("http", ignoreCase = true) }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.22f,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("Loading stream…", color = TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    background: Color = Color(0x55000000),
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val focusSource = remember { MutableInteractionSource() }
    val isFocused by focusSource.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isFocused) Accent else background)
            .border(if (isFocused) 2.dp else 0.dp, Color.White, CircleShape)
            .clickable(interactionSource = focusSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun PillButton(
    icon: ImageVector,
    text: String,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val focusSource = remember { MutableInteractionSource() }
    val isFocused by focusSource.collectIsFocusedAsState()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (isFocused) Accent else if (highlighted) Accent else PillBg)
            .border(if (isFocused) 2.dp else 0.dp, Color.White, RoundedCornerShape(18.dp))
            .clickable(interactionSource = focusSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(
            text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 150.dp)
        )
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    bufferedMs: Long,
    durationMs: Long,
    onScrub: (Long) -> Unit,
    onScrubFinished: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction
        ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Box(
        modifier = modifier
            .height(30.dp)
            .pointerInput(durationMs) {
                detectTapGestures { offset ->
                    if (durationMs > 0) {
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrubFinished((f * durationMs).toLong())
                    }
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (durationMs > 0) {
                            val f = (offset.x / size.width).coerceIn(0f, 1f)
                            dragFraction = f
                            onScrub((f * durationMs).toLong())
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        if (durationMs > 0) {
                            val f = (change.position.x / size.width).coerceIn(0f, 1f)
                            dragFraction = f
                            onScrub((f * durationMs).toLong())
                        }
                    },
                    onDragEnd = {
                        val f = dragFraction
                        dragFraction = null
                        if (f != null && durationMs > 0) onScrubFinished((f * durationMs).toLong())
                    },
                    onDragCancel = { dragFraction = null }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackH = 4.dp.toPx()
            val top = (size.height - trackH) / 2f
            val radius = CornerRadius(trackH / 2f, trackH / 2f)
            drawRoundRect(Color(0x40FFFFFF), Offset(0f, top), Size(size.width, trackH), radius)
            drawRoundRect(Color(0x66FFFFFF), Offset(0f, top), Size(size.width * bufferedFraction, trackH), radius)
            drawRoundRect(Accent, Offset(0f, top), Size(size.width * fraction, trackH), radius)
            val thumbR = if (dragFraction != null) 9.dp.toPx() else 7.dp.toPx()
            drawCircle(Accent, thumbR, Offset(size.width * fraction, size.height / 2f))
        }
    }
}

@Composable
private fun SidePanel(
    title: String,
    onClose: () -> Unit,
    listFocus: FocusRequester? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
            .background(PanelBg)
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(top = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            RoundIconButton(icon = Icons.Default.Close, description = "Close", size = 34.dp, onClick = onClose)
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (listFocus != null) Modifier.focusRequester(listFocus).focusGroup() else Modifier),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
    }
}

@Composable
private fun PanelRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val focusSource = remember { MutableInteractionSource() }
    val isFocused by focusSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Accent.copy(alpha = 0.45f) else if (selected) Accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(if (isFocused) 1.5.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
            .clickable(interactionSource = focusSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected || isFocused) Color.White else Color(0xFFDDDDDD),
            fontSize = 15.sp,
            fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Accent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, isCurrent: Boolean, onClick: () -> Unit) {
    val focusSource = remember { MutableInteractionSource() }
    val isFocused by focusSource.collectIsFocusedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isFocused) Accent.copy(alpha = 0.45f) else if (isCurrent) Accent.copy(alpha = 0.18f) else Color(0x14FFFFFF))
            .border(if (isFocused) 1.5.dp else 0.dp, Color.White, RoundedCornerShape(10.dp))
            .clickable(interactionSource = focusSource, indication = null, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val thumb = ep.getFullStillUrl()
        Box(
            modifier = Modifier
                .width(88.dp)
                .height(50.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF222222)),
            contentAlignment = Alignment.Center
        ) {
            if (thumb != null) {
                AsyncImage(model = thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (isCurrent) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Episode ${ep.episodeNumber}",
                color = if (isCurrent) Accent else TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                ep.title.ifBlank { "Episode ${ep.episodeNumber}" },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PanelHeader(text: String) {
    Text(
        text,
        color = TextDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingToggleRow(title: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .tvFocusRing(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.White)
        )
    }
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
