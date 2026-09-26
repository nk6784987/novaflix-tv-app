package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.data.model.MediaType
import com.example.data.model.VideoStreamSource
import com.example.data.stream.VidukiProvider
import com.example.ui.components.LocalIsTv
import com.example.ui.components.tvAutoFocus
import com.example.ui.components.tvFocusRing
import org.json.JSONObject

private val Accent = Color(0xFFE50914)

// Requests whose URL looks like the actual video payload (not the page, not an ad pixel).
private val StreamUrlPattern = Regex("""\.(m3u8|mp4|mkv)(\?|$)""", RegexOption.IGNORE_CASE)
private val SkipHostHints = listOf("doubleclick", "googlesyndication", "google-analytics", "facebook", "adservice")

/**
 * WebView-based player for the Viduki.net provider. Viduki serves an iframe embed with its
 * own JS player, so it can't go through native ExoPlayer like the other providers — it lives
 * in its own screen, completely independent from ElitePlex / admin / scraper sources.
 *
 * While the embed plays, every sub-request the page makes is passively watched. If one of them
 * looks like the real .m3u8/.mp4/.mkv stream (i.e. the provider isn't wrapping it in DRM/EME),
 * a small "Native HD" pill appears — tapping it hands that URL to [onDirectPlayReady], which
 * switches playback into the native ExoPlayer screen for a proper quality/audio/subtitle UI.
 * If the provider never exposes a raw URL (DRM-protected), this embed keeps playing as-is —
 * nothing here ever interrupts it on its own.
 */
@Composable
fun VidukiPlayerScreen(
    title: String,
    mediaType: MediaType,
    mediaId: String,
    season: Int,
    episode: Int,
    onBackClick: () -> Unit,
    onDirectPlayReady: ((VideoStreamSource) -> Unit)? = null
) {
    val context = LocalContext.current
    val isTv = LocalIsTv.current

    var currentApi by remember { mutableStateOf(VidukiProvider.Api.API_1) }
    var manualOverride by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var allServersFailed by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var extractedSource by remember { mutableStateOf<VideoStreamSource?>(null) }
    var extractedForApi by remember { mutableStateOf<VidukiProvider.Api?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun currentUrl(api: VidukiProvider.Api) =
        VidukiProvider.buildUrl(api, mediaType, mediaId, season, episode)

    fun loadServer(api: VidukiProvider.Api, manual: Boolean) {
        currentApi = api
        manualOverride = manual
        allServersFailed = false
        isLoading = true
        extractedSource = null
        extractedForApi = null
        val url = currentUrl(api)
        val js = "document.getElementById('videoFrame').src = " + JSONObject.quote(url) + ";"
        webViewRef?.evaluateJavascript(js, null)
    }

    fun tryFallback(failedFrom: VidukiProvider.Api) {
        // Fallback only ever moves forward (API1→2→3→4) from wherever the current server is —
        // whether that's the default start or a server the user picked manually.
        val order = VidukiProvider.Api.fallbackOrder
        val nextIndex = order.indexOf(failedFrom) + 1
        if (nextIndex < order.size) {
            loadServer(order[nextIndex], manual = false)
        } else {
            allServersFailed = true
            isLoading = false
        }
    }

    // Landscape + immersive + keep screen on, restored on exit (same pattern as the native player)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
            webViewRef?.destroy()
        }
    }

    // Season/episode changed while this screen stays open (e.g. "Next episode") — keep the
    // currently selected API, just point the same iframe at the new episode.
    LaunchedEffect(mediaId, season, episode) {
        if (webViewRef != null) {
            loadServer(currentApi, manualOverride)
        }
    }

    BackHandler(onBack = onBackClick)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                try {
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }

                            // Passive network sniff — never blocks or redirects anything, just
                            // watches what the embed loads so we can offer native playback once
                            // a raw stream URL shows up (best-effort; nothing changes here if
                            // the provider never exposes one, e.g. behind DRM/EME).
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val url = request?.url?.toString()
                                if (url != null &&
                                    StreamUrlPattern.containsMatchIn(url) &&
                                    SkipHostHints.none { url.contains(it, ignoreCase = true) }
                                ) {
                                    val apiAtRequestTime = currentApi
                                    mainHandler.post {
                                        if (extractedForApi != apiAtRequestTime) {
                                            extractedForApi = apiAtRequestTime
                                            extractedSource = VideoStreamSource(
                                                quality = "Viduki ${apiAtRequestTime.displayName} (Direct)",
                                                url = url,
                                                isHls = url.contains(".m3u8", ignoreCase = true),
                                                headers = mapOf(
                                                    "User-Agent" to (view?.settings?.userAgentString ?: ""),
                                                    "Referer" to "https://viduki.net/"
                                                )
                                            )
                                        }
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onAllServersFailed(dataJson: String) {
                                    Log.d("Viduki", "all-servers-failed from ${currentApi.displayName}: $dataJson")
                                    tryFallback(currentApi)
                                }
                            },
                            "AndroidBridge"
                        )

                        val initialUrl = currentUrl(currentApi)
                        val vidukiOrigin = VidukiProvider.ORIGIN
                        val failureEventType = VidukiProvider.FAILURE_EVENT_TYPE
                        val wrapperHtml = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                              <style>
                                * { margin:0; padding:0; background-color:#000; }
                                html, body { width:100%; height:100%; overflow:hidden; }
                                iframe { width:100%; height:100%; border:none; display:block; }
                              </style>
                            </head>
                            <body>
                              <iframe id="videoFrame" src="$initialUrl"
                                allowfullscreen
                                allow="autoplay; fullscreen; picture-in-picture"></iframe>
                              <script>
                                window.addEventListener("message", function(event) {
                                  if (event.origin !== "$vidukiOrigin") { return; }
                                  if (event.data && event.data.type === "$failureEventType") {
                                    if (window.AndroidBridge) {
                                      AndroidBridge.onAllServersFailed(JSON.stringify(event.data));
                                    }
                                  }
                                });
                              </script>
                            </body>
                            </html>
                        """.trimIndent()

                        loadDataWithBaseURL(
                            "https://viduki.net",
                            wrapperHtml,
                            "text/html",
                            "utf-8",
                            null
                        )
                        webViewRef = this
                    }
                } catch (e: Exception) {
                    Log.e("Viduki", "WebView init failed: ${e.message}")
                    WebView(ctx)
                }
            }
        )

        if (isLoading && !allServersFailed) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
        }

        if (allServersFailed) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "All available servers are unavailable.",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { loadServer(VidukiProvider.Api.API_1, manual = false) },
                        colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        modifier = Modifier.tvFocusRing(RoundedCornerShape(50.dp))
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Retry")
                    }
                }
            }
        }

        // Top bar: back + title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier.tvAutoFocus(isTv).tvFocusRing(RoundedCornerShape(50))
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        // "Native HD available" pill — only shows once a raw stream URL has actually been
        // sniffed for the server currently playing; tapping it switches to native ExoPlayer.
        AnimatedVisibility(
            visible = onDirectPlayReady != null && extractedSource != null && extractedForApi == currentApi && !allServersFailed,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .tvFocusRing(RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(Accent)
                    .clickable {
                        extractedSource?.let { onDirectPlayReady?.invoke(it) }
                    }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Native HD — Play", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Bottom server selector
        LazyRow(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(VidukiProvider.Api.fallbackOrder) { api ->
                val active = api == currentApi
                Box(
                    modifier = Modifier
                        .tvFocusRing(RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Accent else Color.White.copy(alpha = 0.12f))
                        .clickable { if (!active) loadServer(api, manual = true) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        api.displayName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
