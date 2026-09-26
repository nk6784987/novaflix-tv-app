package com.example.data.stream

import android.util.Log
import com.example.data.model.MediaType
import com.example.data.model.StreamInfo
import com.example.data.model.SubtitleTrack
import com.example.data.model.VideoStreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Direct Stream Resolver for Movies, TV Series & Anime.
 * Directly resolves native video stream URLs (.m3u8 / .mp4 / .mkv) for native ExoPlayer playback.
 */
class StreamExtractor(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36"

    private fun sanitizeTmdbId(id: String): String {
        val digitsOnly = id.replace(Regex("[^0-9]"), "")
        return if (digitsOnly.isNotEmpty()) digitsOnly else id
    }

    /**
     * Resolves the video stream with Admin Panel stream fallback.
     * If [adminStreamUrl] is provided, it creates a direct stream source and bypasses scrapers.
     * Otherwise, falls back to standard extraction mechanism [resolveStream].
     */
    suspend fun resolveStreamWithAdminFallback(
        adminStreamUrl: String?,
        mediaId: String,
        mediaType: MediaType,
        title: String,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null
    ): StreamInfo = withContext(Dispatchers.IO) {
        if (!adminStreamUrl.isNullOrBlank()) {
            Log.d("StreamDebug", "Using direct Admin Stream URL override: $adminStreamUrl")
            val isHlsStream = adminStreamUrl.contains(".m3u8", ignoreCase = true)
            val adminSource = VideoStreamSource(
                quality = "Admin Direct Stream (1080p HD)",
                url = adminStreamUrl,
                isHls = isHlsStream,
                headers = mapOf(
                    "User-Agent" to userAgent,
                    "Referer" to "https://google.com/"
                )
            )

            return@withContext StreamInfo(
                title = title,
                sources = listOf(adminSource),
                subtitles = emptyList(),
                mediaId = mediaId,
                season = season,
                episode = episode,
                embedUrl = null
            )
        }

        // Fall back to default extraction
        resolveStream(mediaId, mediaType, title, season, episode, imdbId)
    }

    suspend fun resolveStream(
        mediaId: String,
        mediaType: MediaType,
        title: String,
        season: Int = 1,
        episode: Int = 1,
        imdbId: String? = null
    ): StreamInfo = withContext(Dispatchers.IO) {
        val cleanId = sanitizeTmdbId(mediaId)
        Log.d("StreamDebug", "StreamExtractor resolveStream: title=$title, cleanId=$cleanId, s=$season, e=$episode, type=$mediaType")
        val sources = mutableListOf<VideoStreamSource>()
        val subtitles = mutableListOf<SubtitleTrack>()

        // Fetch direct video sources in parallel
        val animeTask = async { scrapeAnimeDirect(title, season, episode) }
        val animeResults = animeTask.await()

        sources.addAll(animeResults)

        val uniqueSources = sources.distinctBy { it.url }

        Log.d("StreamDebug", "StreamExtractor finished with ${uniqueSources.size} direct video sources.")

        StreamInfo(
            title = title,
            sources = uniqueSources,
            subtitles = subtitles,
            mediaId = mediaId,
            season = season,
            episode = episode,
            embedUrl = null
        )
    }

    private suspend fun scrapeAnimeDirect(
        title: String,
        season: Int,
        episode: Int
    ): List<VideoStreamSource> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoStreamSource>()
        val encodedTitle = URLEncoder.encode(title, "UTF-8")
        val animeEndpoints = listOf(
            "https://api.consumet.org/anime/gogoanime/$encodedTitle",
            "https://consumet-api.vercel.app/anime/gogoanime/$encodedTitle"
        )

        for (endpoint in animeEndpoints) {
            try {
                val req = okhttp3.Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", userAgent)
                    .build()

                val res = okHttpClient.newCall(req).execute()
                val json = res.body?.string() ?: ""
                res.close()

                if (json.contains(".m3u8")) {
                    val m3u8Regex = Regex("""(https?://[^\s"'<>]+\.m3u8[^\s"'<>]*)""")
                    m3u8Regex.findAll(json).forEach { match ->
                        val url = match.value
                        if (list.none { it.url == url }) {
                            list.add(
                                VideoStreamSource(
                                    quality = "Direct HD • Dual Audio (1080p)",
                                    url = url,
                                    isHls = true,
                                    headers = mapOf("User-Agent" to userAgent)
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d("StreamDebug", "Direct scraper note: ${e.message}")
            }
        }
        list
    }
}
