package com.example.data.repository

import android.net.Uri
import android.util.Log
import com.example.data.model.CastMember
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.StreamInfo
import com.example.data.model.TmdbSeasonInfoDto
import com.example.data.model.VideoStreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * MovieBox & ElitePlex API client.
 * Connects to the user's server (default: https://eliteplex-api.vercel.app or custom URL).
 * Provides full catalog browsing (/mb/home), search (/mb/search),
 * title details with seasons & episodes (/mb/detail), and video streams (/mb/stream).
 */
class ElitePlexRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val itemCache = mutableMapOf<String, MediaItem>()
    private val detailCache = mutableMapOf<String, JSONObject>()

    @Volatile
    private var cachedTopMovies: List<MediaItem> = emptyList()
    @Volatile
    private var cachedTopSeries: List<MediaItem> = emptyList()
    @Volatile
    private var cachedTopAnime: List<MediaItem> = emptyList()
    @Volatile
    private var cachedHeroBanners: List<MediaItem> = emptyList()
    @Volatile
    private var cachedSections: List<Pair<String, List<MediaItem>>> = emptyList()

    private fun baseUrl(): String = ServerConfig.getActiveApiUrl().removeSuffix("/")

    private fun cleanId(id: String): String {
        return id.removePrefix("mb_")
            .removePrefix("movie_")
            .removePrefix("tv_")
            .removePrefix("anime_")
            .substringAfterLast("::")
            .trim()
    }

    private fun buildRequest(url: String): Request {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36")
            .header("Accept", "application/json")

        val apiKey = ServerConfig.movieBoxApiKey.value
        if (apiKey.isNotBlank()) {
            builder.header("X-API-Key", apiKey)
            builder.header("Authorization", "Bearer $apiKey")
        }
        return builder.build()
    }

    fun parseSubject(subject: JSONObject): MediaItem? {
        val subjectId = subject.optString("subjectId").ifBlank {
            subject.optString("subject_id").ifBlank { subject.optString("id") }
        }
        if (subjectId.isBlank()) return null

        val title = subject.optString("title").ifBlank {
            subject.optString("name", "Untitled")
        }

        // Cover can be an object {"url": "..."} or a direct string URL
        var coverUrl: String? = null
        val coverObj = subject.optJSONObject("cover")
        if (coverObj != null) {
            coverUrl = coverObj.optString("url")
        } else {
            coverUrl = subject.optString("cover").ifBlank {
                subject.optString("poster_url").ifBlank {
                    subject.optString("poster")
                }
            }
        }
        if (coverUrl.isNullOrBlank()) coverUrl = null

        val subjectType = subject.optInt("subjectType", 0)
        val rawType = subject.optString("type", "").lowercase()
        val genre = subject.optString("genre", "")
        val genres = if (genre.isNotBlank()) genre.split(",").map { it.trim() } else emptyList()
        val country = subject.optString("countryName", "")

        val isAnime = genres.any { it.equals("Anime", ignoreCase = true) } ||
            genre.contains("Anime", ignoreCase = true) ||
            rawType.contains("anime") ||
            title.contains("Anime", ignoreCase = true) ||
            (genres.any { it.equals("Animation", ignoreCase = true) } && (country.equals("Japan", ignoreCase = true) || country.equals("China", ignoreCase = true)))

        val mediaType = when {
            isAnime -> MediaType.ANIME
            subjectType == 2 || subjectType == 7 || rawType.contains("tv") || rawType.contains("series") -> MediaType.TV
            else -> MediaType.MOVIE
        }

        val rateStr = subject.optString("imdbRate").ifBlank {
            subject.optString("imdbRatingValue").ifBlank {
                subject.optString("rating", "0.0")
            }
        }
        val rating = rateStr.toDoubleOrNull() ?: subject.optDouble("rate", 0.0)

        val releaseDate = subject.optString("releaseDate").ifBlank {
            subject.optString("year", "")
        }
        val releaseYear = if (releaseDate.length >= 4) releaseDate.take(4) else releaseDate

        val description = subject.optString("description", "")

        val item = MediaItem(
            id = "mb_$subjectId",
            tmdbId = subjectId.toLongOrNull(),
            title = title,
            overview = description,
            posterPath = coverUrl,
            backdropPath = coverUrl,
            mediaType = mediaType,
            rating = rating,
            releaseYear = releaseYear,
            genres = genres
        )
        itemCache[item.id] = item
        itemCache[subjectId] = item
        return item
    }

    private fun fetchPageItems(path: String, page: Int): List<MediaItem> {
        return try {
            val url = "${baseUrl()}$path?page=$page"
            val request = buildRequest(url)
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val itemsArray = json.optJSONArray("items") ?: json.optJSONArray("movies") ?: json.optJSONArray("series") ?: return emptyList()
            val list = mutableListOf<MediaItem>()
            for (i in 0 until itemsArray.length()) {
                val itObj = itemsArray.optJSONObject(i) ?: continue
                parseSubject(itObj)?.let { list.add(it) }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchRankItems(): Pair<List<MediaItem>, List<MediaItem>> {
        return try {
            val url = "${baseUrl()}/mb/rank"
            val request = buildRequest(url)
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return Pair(emptyList(), emptyList())
            val json = JSONObject(body)
            val moviesArray = json.optJSONArray("movies")
            val seriesArray = json.optJSONArray("series")
            val movies = mutableListOf<MediaItem>()
            if (moviesArray != null) {
                for (i in 0 until moviesArray.length()) {
                    val itObj = moviesArray.optJSONObject(i) ?: continue
                    parseSubject(itObj)?.let { movies.add(it) }
                }
            }
            val series = mutableListOf<MediaItem>()
            if (seriesArray != null) {
                for (i in 0 until seriesArray.length()) {
                    val itObj = seriesArray.optJSONObject(i) ?: continue
                    parseSubject(itObj)?.let { series.add(it) }
                }
            }
            Pair(movies, series)
        } catch (e: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    /**
     * Fetches sections from MovieBox (/mb/home) and populates banners and ranking lists.
     */
    suspend fun getSections(): List<Pair<String, List<MediaItem>>> = withContext(Dispatchers.IO) {
        try {
            val url = "${baseUrl()}/mb/home"
            val request = buildRequest(url)
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext cachedSections

            val json = JSONObject(body)
            val dataObj = json.optJSONObject("data") ?: json
            val itemsArray = dataObj.optJSONArray("items") ?: JSONArray()

            val sections = mutableListOf<Pair<String, List<MediaItem>>>()
            val parsedBanners = mutableListOf<MediaItem>()
            var foundTopMovies = mutableListOf<MediaItem>()
            var foundTopSeries = mutableListOf<MediaItem>()
            var foundTopAnime = mutableListOf<MediaItem>()

            for (i in 0 until itemsArray.length()) {
                val secObj = itemsArray.getJSONObject(i)
                val secType = secObj.optString("type", "")
                val secTitle = secObj.optString("title", "").ifBlank { "Featured" }

                // Parse BANNER container
                if (secType == "BANNER") {
                    val bannerContainer = secObj.optJSONObject("banner")
                    val bannersArray = bannerContainer?.optJSONArray("banners")
                    if (bannersArray != null) {
                        for (b in 0 until bannersArray.length()) {
                            val bObj = bannersArray.getJSONObject(b)
                            val sObj = bObj.optJSONObject("subject")
                            val bannerItem = if (sObj != null) {
                                parseSubject(sObj)
                            } else {
                                val title = bObj.optString("title", "Featured")
                                val subId = bObj.optString("subjectId").ifBlank { bObj.optString("subject_id") }
                                val imgObj = bObj.optJSONObject("image")
                                val imgUrl = imgObj?.optString("url") ?: bObj.optString("imgUrl")
                                if (imgUrl.isNotBlank()) {
                                    MediaItem(
                                        id = if (subId.isNotBlank()) "mb_$subId" else "mb_banner_$b",
                                        tmdbId = subId.toLongOrNull(),
                                        title = title,
                                        overview = "Now streaming in HD on MovieBox",
                                        posterPath = imgUrl,
                                        backdropPath = imgUrl,
                                        mediaType = MediaType.MOVIE,
                                        rating = 8.5,
                                        releaseYear = "2026"
                                    )
                                } else null
                            }
                            if (bannerItem != null) {
                                parsedBanners.add(bannerItem)
                            }
                        }
                    }
                    continue
                }

                // Parse RANKING_LIST_MULTI_TAB
                if (secType == "RANKING_LIST_MULTI_TAB") {
                    val rdata = secObj.optJSONObject("rankingListData")
                    val rItems = rdata?.optJSONArray("items")
                    if (rItems != null) {
                        for (k in 0 until rItems.length()) {
                            val ritem = rItems.optJSONObject(k) ?: continue
                            val rtitle = ritem.optString("title", "").ifBlank { secTitle }
                            val rsubs = ritem.optJSONArray("subjects") ?: continue
                            val list = mutableListOf<MediaItem>()
                            for (m in 0 until rsubs.length()) {
                                parseSubject(rsubs.optJSONObject(m) ?: continue)?.let { list.add(it) }
                            }
                            if (list.isNotEmpty()) {
                                val isMovieRanking = rtitle.contains("Movie", ignoreCase = true) || secTitle.contains("Movie", ignoreCase = true)
                                val isSeriesRanking = rtitle.contains("Series", ignoreCase = true) || secTitle.contains("Series", ignoreCase = true)
                                val isAnimeRanking = rtitle.contains("Anime", ignoreCase = true) || list.any { it.mediaType == MediaType.ANIME }

                                if (isMovieRanking && foundTopMovies.isEmpty()) {
                                    foundTopMovies.addAll(list.filter { it.mediaType == MediaType.MOVIE })
                                }
                                if (isSeriesRanking && foundTopSeries.isEmpty()) {
                                    foundTopSeries.addAll(list.filter { it.mediaType == MediaType.TV })
                                }
                                if (isAnimeRanking && foundTopAnime.isEmpty()) {
                                    foundTopAnime.addAll(list.filter { it.mediaType == MediaType.ANIME })
                                }
                                sections.add(rtitle to list)
                            }
                        }
                    }
                    continue
                }

                val subjectsArray = secObj.optJSONArray("subjects")
                val mediaList = mutableListOf<MediaItem>()

                if (subjectsArray != null && subjectsArray.length() > 0) {
                    for (j in 0 until subjectsArray.length()) {
                        val subObj = subjectsArray.optJSONObject(j) ?: continue
                        parseSubject(subObj)?.let { mediaList.add(it) }
                    }
                } else {
                    // Check groups
                    val groupsArray = secObj.optJSONArray("groups")
                    if (groupsArray != null && groupsArray.length() > 0) {
                        for (g in 0 until groupsArray.length()) {
                            val groupObj = groupsArray.optJSONObject(g) ?: continue
                            val groupSubs = groupObj.optJSONArray("subjects") ?: continue
                            for (j in 0 until groupSubs.length()) {
                                val subObj = groupSubs.optJSONObject(j) ?: continue
                                parseSubject(subObj)?.let { mediaList.add(it) }
                            }
                        }
                    }
                }

                // Check customData items (e.g. Breaking Bad, Farzi, Indian Stars, Curated Collections)
                if (mediaList.isEmpty()) {
                    val customData = secObj.optJSONObject("customData")
                    val customItems = customData?.optJSONArray("items")
                    if (customItems != null && customItems.length() > 0) {
                        for (c in 0 until customItems.length()) {
                            val cObj = customItems.optJSONObject(c) ?: continue
                            val subObj = cObj.optJSONObject("subject") ?: continue
                            parseSubject(subObj)?.let { mediaList.add(it) }
                        }
                    }
                }

                // Check playListData items
                if (mediaList.isEmpty()) {
                    val playListData = secObj.optJSONObject("playListData")
                    val playListItems = playListData?.optJSONArray("items")
                    if (playListItems != null && playListItems.length() > 0) {
                        for (p in 0 until playListItems.length()) {
                            val pObj = playListItems.optJSONObject(p) ?: continue
                            val subObj = pObj.optJSONObject("subject") ?: continue
                            parseSubject(subObj)?.let { mediaList.add(it) }
                        }
                    }
                }

                if (mediaList.isNotEmpty()) {
                    if (secTitle.contains("Anime", ignoreCase = true) && foundTopAnime.isEmpty()) {
                        foundTopAnime.addAll(mediaList.filter { it.mediaType == MediaType.ANIME })
                    }
                    sections.add(secTitle to mediaList)
                }
            }

            // Fallback if /mb/home format had legacy sections
            if (sections.isEmpty()) {
                val legacySections = json.optJSONArray("sections")
                if (legacySections != null) {
                    for (i in 0 until legacySections.length()) {
                        val s = legacySections.getJSONObject(i)
                        val title = s.optString("section", "Trending")
                        val sItems = s.optJSONArray("items") ?: continue
                        val list = mutableListOf<MediaItem>()
                        for (j in 0 until sItems.length()) {
                            val itObj = sItems.getJSONObject(j)
                            parseSubject(itObj)?.let { list.add(it) }
                        }
                        if (list.isNotEmpty()) sections.add(title to list)
                    }
                }
            }

            // Fetch extra catalog data and rankings in parallel
            try {
                coroutineScope {
                    val extraMoviesJob = async {
                        val p1 = fetchPageItems("/mb/movies", 1)
                        val p2 = fetchPageItems("/mb/movies", 2)
                        (p1 + p2).distinctBy { it.id }
                    }
                    val extraSeriesJob = async {
                        val p1 = fetchPageItems("/mb/series", 1)
                        val p2 = fetchPageItems("/mb/series", 2)
                        (p1 + p2).distinctBy { it.id }
                    }
                    val rankJob = async {
                        fetchRankItems()
                    }

                    val extraMovies = extraMoviesJob.await()
                    val extraSeries = extraSeriesJob.await()
                    val rankPair = rankJob.await()

                    if (rankPair.first.isNotEmpty() && foundTopMovies.isEmpty()) {
                        foundTopMovies.addAll(rankPair.first.filter { it.mediaType == MediaType.MOVIE })
                    }
                    if (rankPair.second.isNotEmpty() && foundTopSeries.isEmpty()) {
                        foundTopSeries.addAll(rankPair.second.filter { it.mediaType == MediaType.TV })
                    }

                    if (extraMovies.isNotEmpty()) {
                        sections.add("New Releases" to extraMovies)
                    }
                    if (extraSeries.isNotEmpty()) {
                        sections.add("Popular Web Series" to extraSeries)
                    }
                }
            } catch (e: Exception) {
                Log.w("MovieBoxRepo", "Background catalog enrichment partial failure: ${e.message}")
            }

            if (parsedBanners.isNotEmpty()) {
                cachedHeroBanners = parsedBanners
            }
            if (foundTopMovies.isNotEmpty()) cachedTopMovies = foundTopMovies
            if (foundTopSeries.isNotEmpty()) cachedTopSeries = foundTopSeries
            if (foundTopAnime.isNotEmpty()) cachedTopAnime = foundTopAnime

            val allItems = sections.flatMap { it.second }.distinctBy { it.id }
            if (cachedTopMovies.isEmpty()) {
                cachedTopMovies = allItems.filter { it.mediaType == MediaType.MOVIE }.sortedByDescending { it.rating }
            }
            if (cachedTopSeries.isEmpty()) {
                cachedTopSeries = allItems.filter { it.mediaType == MediaType.TV }.sortedByDescending { it.rating }
            }
            if (cachedTopAnime.isEmpty()) {
                cachedTopAnime = allItems.filter { it.mediaType == MediaType.ANIME }.sortedByDescending { it.rating }
            }

            cachedSections = sections
            sections
        } catch (e: Exception) {
            Log.e("MovieBoxRepo", "Error getSections: ${e.message}", e)
            cachedSections
        }
    }

    /**
     * Returns top 10 items for the specified media type directly from API rankings.
     */
    suspend fun getTopTenFromApi(type: MediaType?): List<MediaItem> = withContext(Dispatchers.IO) {
        if (cachedSections.isEmpty()) {
            getSections()
        }
        val target = when (type) {
            MediaType.TV -> cachedTopSeries.filter { it.mediaType == MediaType.TV }
            MediaType.ANIME -> cachedTopAnime.filter { it.mediaType == MediaType.ANIME }
            MediaType.MOVIE, null -> cachedTopMovies.filter { it.mediaType == MediaType.MOVIE }
        }
        if (target.isNotEmpty()) return@withContext target.take(10)

        // Fallback: extract from cachedSections
        val all = cachedSections.flatMap { it.second }.distinctBy { it.id }
        val fallback = when (type) {
            MediaType.TV -> all.filter { it.mediaType == MediaType.TV }
            MediaType.ANIME -> all.filter { it.mediaType == MediaType.ANIME }
            MediaType.MOVIE, null -> all.filter { it.mediaType == MediaType.MOVIE }
        }
        fallback.sortedByDescending { it.rating }.take(10)
    }

    /**
     * Returns hero banners filtered by media type.
     */
    suspend fun getHeroBanners(count: Int = 6, type: MediaType? = null): List<MediaItem> = withContext(Dispatchers.IO) {
        if (cachedHeroBanners.isEmpty()) {
            getSections()
        }
        val banners = when (type) {
            MediaType.MOVIE -> cachedHeroBanners.filter { it.mediaType == MediaType.MOVIE }
            MediaType.TV -> cachedHeroBanners.filter { it.mediaType == MediaType.TV }
            MediaType.ANIME -> cachedHeroBanners.filter { it.mediaType == MediaType.ANIME }
            null -> cachedHeroBanners
        }
        if (banners.isNotEmpty()) {
            return@withContext banners.take(count)
        }
        // Fallback to top ten for that type
        getTopTenFromApi(type).take(count)
    }

    /**
     * Returns rich, complete Movie category sections with real API titles for all major genres.
     */
    suspend fun getMovieSections(): List<Pair<String, List<MediaItem>>> = withContext(Dispatchers.IO) {
        val allSections = getSections()
        val allMovies = allSections.flatMap { it.second }
            .filter { it.mediaType == MediaType.MOVIE }
            .distinctBy { it.id }

        val result = mutableListOf<Pair<String, List<MediaItem>>>()

        // 1. Trending / Top Movies
        val topMovies = cachedTopMovies.filter { it.mediaType == MediaType.MOVIE }.ifEmpty {
            allMovies.sortedByDescending { it.rating }.take(15)
        }
        if (topMovies.isNotEmpty()) {
            result.add("🔥 Trending Movies" to topMovies)
        }

        // 2. Curated sections from API
        for ((title, items) in allSections) {
            val movies = items.filter { it.mediaType == MediaType.MOVIE }
            if (movies.size >= 3) {
                val cleanTitle = when {
                    title.contains("Cinema", ignoreCase = true) -> "🎬 In Cinemas Now"
                    title.contains("Bollywood", ignoreCase = true) -> "🇮🇳 Bollywood Hits"
                    title.contains("Hollywood", ignoreCase = true) -> "🌟 Hollywood Blockbusters"
                    title.contains("South Indian", ignoreCase = true) -> "⚡ South Indian Action"
                    title.contains("Free", ignoreCase = true) -> "🎁 Free to Watch"
                    title.contains("Coming Soon", ignoreCase = true) -> "⏳ Coming Soon"
                    else -> null
                }
                if (cleanTitle != null && result.none { it.first == cleanTitle }) {
                    result.add(cleanTitle to movies)
                }
            }
        }

        // 3. High-demand Genre sections
        val actionMovies = allMovies.filter { m ->
            m.genres.any { it.contains("Action", ignoreCase = true) || it.contains("Adventure", ignoreCase = true) }
        }
        if (actionMovies.isNotEmpty()) {
            result.add("💥 Action & Adventure" to actionMovies)
        }

        val comedyMovies = allMovies.filter { m ->
            m.genres.any { it.contains("Comedy", ignoreCase = true) }
        }
        if (comedyMovies.isNotEmpty()) {
            result.add("😂 Comedy & Entertainment" to comedyMovies)
        }

        val crimeThriller = allMovies.filter { m ->
            m.genres.any { it.contains("Crime", ignoreCase = true) || it.contains("Thriller", ignoreCase = true) || it.contains("Mystery", ignoreCase = true) }
        }
        if (crimeThriller.isNotEmpty()) {
            result.add("🔍 Crime & Thriller" to crimeThriller)
        }

        val dramaMovies = allMovies.filter { m ->
            m.genres.any { it.contains("Drama", ignoreCase = true) }
        }
        if (dramaMovies.isNotEmpty()) {
            result.add("🎭 Drama & Emotional" to dramaMovies)
        }

        val sciFiFantasy = allMovies.filter { m ->
            m.genres.any { it.contains("Sci-Fi", ignoreCase = true) || it.contains("Science", ignoreCase = true) || it.contains("Fantasy", ignoreCase = true) }
        }
        if (sciFiFantasy.isNotEmpty()) {
            result.add("🚀 Sci-Fi & Fantasy" to sciFiFantasy)
        }

        val horrorMystery = allMovies.filter { m ->
            m.genres.any { it.contains("Horror", ignoreCase = true) || it.contains("Mystery", ignoreCase = true) }
        }
        if (horrorMystery.isNotEmpty()) {
            result.add("👻 Horror & Mystery" to horrorMystery)
        }

        val romanceMovies = allMovies.filter { m ->
            m.genres.any { it.contains("Romance", ignoreCase = true) }
        }
        if (romanceMovies.isNotEmpty()) {
            result.add("💖 Romance & Love" to romanceMovies)
        }

        val familyAnim = allMovies.filter { m ->
            m.genres.any { it.contains("Family", ignoreCase = true) || it.contains("Animation", ignoreCase = true) || it.contains("Cartoon", ignoreCase = true) }
        }
        if (familyAnim.isNotEmpty()) {
            result.add("🎈 Family & Animation" to familyAnim)
        }

        val topRated = allMovies.filter { it.rating >= 7.0 }.sortedByDescending { it.rating }
        if (topRated.isNotEmpty() && result.none { it.first == "⭐ Top Rated Masterpieces" }) {
            result.add("⭐ Top Rated Masterpieces" to topRated.take(15))
        }

        if (result.isEmpty()) {
            result.add("Trending Movies" to allMovies.take(15))
        }
        result
    }

    /**
     * Returns rich Series-only category sections (strictly TV series, no anime, no movies).
     */
    suspend fun getSeriesSections(): List<Pair<String, List<MediaItem>>> = withContext(Dispatchers.IO) {
        val allSections = getSections()
        val allSeries = allSections.flatMap { it.second }
            .filter { it.mediaType == MediaType.TV }
            .distinctBy { it.id }

        val result = mutableListOf<Pair<String, List<MediaItem>>>()

        // 1. Trending TV Series
        val topSeries = cachedTopSeries.filter { it.mediaType == MediaType.TV }.ifEmpty {
            allSeries.sortedByDescending { it.rating }.take(15)
        }
        if (topSeries.isNotEmpty()) {
            result.add("🔥 Trending TV Series" to topSeries)
        }

        // 2. Curated sections from API
        for ((title, items) in allSections) {
            val series = items.filter { it.mediaType == MediaType.TV }
            if (series.size >= 3) {
                val cleanTitle = when {
                    title.contains("Asian", ignoreCase = true) || title.contains("K-Drama", ignoreCase = true) -> "🇰🇷 Asian & K-Drama"
                    title.contains("Western", ignoreCase = true) -> "🗽 Western TV Hits"
                    title.contains("Short", ignoreCase = true) -> "⚡ Hot Short TV"
                    title.contains("Drama", ignoreCase = true) && !title.contains("Anime", ignoreCase = true) -> "🎭 Indian & Global Drama"
                    else -> null
                }
                if (cleanTitle != null && result.none { it.first == cleanTitle }) {
                    result.add(cleanTitle to series)
                }
            }
        }

        // 3. Genre sections for Series
        val actionSeries = allSeries.filter { s ->
            s.genres.any { it.contains("Action", ignoreCase = true) || it.contains("Adventure", ignoreCase = true) }
        }
        if (actionSeries.isNotEmpty()) {
            result.add("💥 Action & Adventure Series" to actionSeries)
        }

        val crimeSeries = allSeries.filter { s ->
            s.genres.any { it.contains("Crime", ignoreCase = true) || it.contains("Thriller", ignoreCase = true) || it.contains("Mystery", ignoreCase = true) }
        }
        if (crimeSeries.isNotEmpty()) {
            result.add("🔍 Crime & Thriller Series" to crimeSeries)
        }

        val dramaSeries = allSeries.filter { s ->
            s.genres.any { it.contains("Drama", ignoreCase = true) }
        }
        if (dramaSeries.isNotEmpty()) {
            result.add("🎭 Drama Series" to dramaSeries)
        }

        val comedySeries = allSeries.filter { s ->
            s.genres.any { it.contains("Comedy", ignoreCase = true) }
        }
        if (comedySeries.isNotEmpty()) {
            result.add("😂 Comedy Series" to comedySeries)
        }

        if (result.isEmpty()) {
            result.add("Popular Series" to allSeries.take(15))
        }
        result
    }

    /**
     * Returns rich Anime-only category sections (strictly anime, no live-action series or movies).
     */
    suspend fun getAnimeSections(): List<Pair<String, List<MediaItem>>> = withContext(Dispatchers.IO) {
        val allSections = getSections()
        val allAnime = allSections.flatMap { it.second }
            .filter { it.mediaType == MediaType.ANIME }
            .distinctBy { it.id }

        val result = mutableListOf<Pair<String, List<MediaItem>>>()

        // 1. Trending Anime
        val topAnime = cachedTopAnime.filter { it.mediaType == MediaType.ANIME }.ifEmpty {
            allAnime.sortedByDescending { it.rating }.take(15)
        }
        if (topAnime.isNotEmpty()) {
            result.add("🔥 Top Trending Anime" to topAnime)
        }

        // 2. Action & Shounen Anime
        val actionAnime = allAnime.filter { a ->
            a.genres.any { it.contains("Action", ignoreCase = true) || it.contains("Adventure", ignoreCase = true) }
        }
        if (actionAnime.isNotEmpty()) {
            result.add("⚔️ Action & Shounen Anime" to actionAnime)
        }

        // 3. Fantasy & Supernatural
        val fantasyAnime = allAnime.filter { a ->
            a.genres.any { it.contains("Fantasy", ignoreCase = true) || it.contains("Supernatural", ignoreCase = true) || it.contains("Sci-Fi", ignoreCase = true) }
        }
        if (fantasyAnime.isNotEmpty()) {
            result.add("🔮 Fantasy & Supernatural Anime" to fantasyAnime)
        }

        // 4. Comedy & Fun Anime
        val comedyAnime = allAnime.filter { a ->
            a.genres.any { it.contains("Comedy", ignoreCase = true) }
        }
        if (comedyAnime.isNotEmpty()) {
            result.add("😂 Comedy & Fun Anime" to comedyAnime)
        }

        // 5. Must-Watch Anime Masterpieces
        val topRatedAnime = allAnime.sortedByDescending { it.rating }
        if (topRatedAnime.isNotEmpty()) {
            result.add("⭐ Critically Acclaimed Anime" to topRatedAnime)
        }

        if (result.isEmpty()) {
            result.add("Top Anime" to allAnime.take(15))
        }
        result
    }

    suspend fun getTrendingMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        val sections = getSections()
        val moviesSec = sections.firstOrNull {
            it.first.contains("Movie", ignoreCase = true) || it.first.contains("Trending", ignoreCase = true)
        }
        moviesSec?.second ?: sections.firstOrNull()?.second ?: emptyList()
    }

    suspend fun getContentByCategory(category: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val sections = getSections()
        val match = sections.firstOrNull { it.first.contains(category, ignoreCase = true) }
        if (match != null && match.second.isNotEmpty()) return@withContext match.second

        // Collect all items across sections and filter by genre
        val all = sections.flatMap { it.second }.distinctBy { it.id }
        val filtered = all.filter { item ->
            item.genres.any { it.contains(category, ignoreCase = true) } ||
            (category.equals("TV Series", ignoreCase = true) && item.mediaType == MediaType.TV) ||
            (category.equals("Anime", ignoreCase = true) && item.mediaType == MediaType.ANIME) ||
            (category.equals("Latest", ignoreCase = true))
        }
        if (filtered.isNotEmpty()) filtered else all.take(20)
    }

    /**
     * MovieBox search (/mb/search?q=...)
     */
    suspend fun searchContent(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isBlank()) return@withContext emptyList()

        try {
            val encodedQ = URLEncoder.encode(q, "UTF-8")
            val url = "${baseUrl()}/mb/search?q=$encodedQ"
            val request = buildRequest(url)
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()

            val json = JSONObject(body)
            val itemsArray = json.optJSONArray("items") ?: json.optJSONArray("moviebox") ?: JSONArray()
            val results = mutableListOf<MediaItem>()

            for (i in 0 until itemsArray.length()) {
                val itObj = itemsArray.getJSONObject(i)
                parseSubject(itObj)?.let { results.add(it) }
            }

            // If empty, fallback to root /search endpoint which also searches MovieBox
            if (results.isEmpty()) {
                val rootUrl = "${baseUrl()}/search?q=$encodedQ"
                val rootResp = client.newCall(buildRequest(rootUrl)).execute()
                val rootBody = rootResp.body?.string()
                if (!rootBody.isNullOrBlank()) {
                    val rootJson = JSONObject(rootBody)
                    val mbItems = rootJson.optJSONArray("moviebox") ?: rootJson.optJSONArray("items")
                    if (mbItems != null) {
                        for (i in 0 until mbItems.length()) {
                            parseSubject(mbItems.getJSONObject(i))?.let { results.add(it) }
                        }
                    }
                }
            }

            results
        } catch (e: Exception) {
            Log.e("MovieBoxRepo", "Error searching '$query': ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetches detailed info about a title from /mb/detail/{subjectId}.
     */
    suspend fun getMediaDetail(mediaId: String): MediaItem? = withContext(Dispatchers.IO) {
        val cached = itemCache[mediaId]
        val cleanSubjectId = cleanId(mediaId)

        try {
            val url = "${baseUrl()}/mb/detail/$cleanSubjectId"
            val request = buildRequest(url)
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext cached

            val json = JSONObject(body)
            val data = json.optJSONObject("data") ?: json
            detailCache[cleanSubjectId] = data

            val title = data.optString("title").ifBlank {
                cached?.title ?: "Untitled"
            }
            val desc = data.optString("description").ifBlank {
                cached?.overview ?: ""
            }
            val releaseDate = data.optString("releaseDate")
            val releaseYear = if (releaseDate.length >= 4) releaseDate.take(4) else cached?.releaseYear ?: ""

            var poster: String? = null
            val coverObj = data.optJSONObject("cover")
            if (coverObj != null) {
                poster = coverObj.optString("url")
            }
            if (poster.isNullOrBlank()) poster = cached?.posterPath

            val subjectType = data.optInt("subjectType", 0)
            val isTv = subjectType == 2 || data.has("seasons") || (cached?.mediaType == MediaType.TV)
            val mediaType = if (isTv) MediaType.TV else (cached?.mediaType ?: MediaType.MOVIE)

            val rating = data.optString("imdbRatingValue").toDoubleOrNull()
                ?: data.optDouble("rate", cached?.rating ?: 0.0)

            val genreStr = data.optString("genre", "")
            val genres = if (genreStr.isNotBlank()) genreStr.split(",").map { it.trim() } else cached?.genres ?: emptyList()

            val item = MediaItem(
                id = "mb_$cleanSubjectId",
                tmdbId = cleanSubjectId.toLongOrNull(),
                title = title,
                overview = desc,
                posterPath = poster,
                backdropPath = poster,
                mediaType = mediaType,
                rating = rating,
                releaseYear = releaseYear,
                genres = genres
            )
            itemCache[item.id] = item
            itemCache[cleanSubjectId] = item
            item
        } catch (e: Exception) {
            Log.e("MovieBoxRepo", "Error getMediaDetail '$mediaId': ${e.message}")
            cached
        }
    }

    /**
     * Returns series details, seasons, and episodes.
     */
    suspend fun getTvDetails(mediaId: String): Triple<MediaItem, List<CastMember>, List<TmdbSeasonInfoDto>> =
        withContext(Dispatchers.IO) {
            val item = getMediaDetail(mediaId) ?: MediaItem(
                id = mediaId,
                title = "Unknown Series",
                overview = "",
                posterPath = null,
                backdropPath = null,
                mediaType = MediaType.TV,
                rating = 0.0,
                releaseYear = ""
            )

            val cleanSubjectId = cleanId(mediaId)
            val data = detailCache[cleanSubjectId]
            val seasonsList = mutableListOf<TmdbSeasonInfoDto>()

            if (data != null) {
                val seasonsRaw = data.opt("seasons")
                if (seasonsRaw is JSONObject) {
                    val arr = seasonsRaw.optJSONArray("seasons")
                    if (arr != null) {
                        for (s in 0 until arr.length()) {
                            val sObj = arr.getJSONObject(s)
                            val seNum = sObj.optInt("se", s + 1)
                            val maxEp = sObj.optInt("maxEp", 1)
                            seasonsList.add(
                                TmdbSeasonInfoDto(
                                    id = seNum.toLong(),
                                    seasonNumber = seNum,
                                    name = "Season $seNum",
                                    episodeCount = maxEp,
                                    posterPath = item.posterPath
                                )
                            )
                        }
                    }
                } else if (seasonsRaw is JSONArray) {
                    for (s in 0 until seasonsRaw.length()) {
                        val sObj = seasonsRaw.getJSONObject(s)
                        val seNum = sObj.optInt("se", s + 1)
                        val maxEp = sObj.optInt("maxEp", 1)
                        seasonsList.add(
                            TmdbSeasonInfoDto(
                                id = seNum.toLong(),
                                seasonNumber = seNum,
                                name = "Season $seNum",
                                episodeCount = maxEp,
                                posterPath = item.posterPath
                            )
                        )
                    }
                }
            }

            if (seasonsList.isEmpty()) {
                seasonsList.add(
                    TmdbSeasonInfoDto(
                        id = 1L,
                        seasonNumber = 1,
                        name = "Season 1",
                        episodeCount = 1,
                        posterPath = item.posterPath
                    )
                )
            }

            Triple(item, emptyList(), seasonsList)
        }

    /**
     * Generates episode list for a season.
     */
    suspend fun getSeasonEpisodes(mediaId: String, seasonNumber: Int): List<Episode> =
        withContext(Dispatchers.IO) {
            val cleanSubjectId = cleanId(mediaId)
            var data = detailCache[cleanSubjectId]
            if (data == null) {
                getMediaDetail(mediaId)
                data = detailCache[cleanSubjectId]
            }

            var episodeCount = 1
            if (data != null) {
                val seasonsRaw = data.opt("seasons")
                val arr = when (seasonsRaw) {
                    is JSONObject -> seasonsRaw.optJSONArray("seasons")
                    is JSONArray -> seasonsRaw
                    else -> null
                }
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val sObj = arr.getJSONObject(i)
                        if (sObj.optInt("se") == seasonNumber) {
                            episodeCount = sObj.optInt("maxEp", 1).coerceAtLeast(1)
                            break
                        }
                    }
                }
            }

            val episodes = (1..episodeCount).map { ep ->
                Episode(
                    id = "${cleanSubjectId}_s${seasonNumber}_e$ep",
                    episodeNumber = ep,
                    seasonNumber = seasonNumber,
                    title = "Episode $ep",
                    overview = "Watch Episode $ep on MovieBox HD stream",
                    stillPath = null,
                    airDate = null
                )
            }
            episodes
        }

    /**
     * Resolves MovieBox stream (/mb/stream/{id}?se={se}&ep={ep}).
     */
    suspend fun extractStream(
        mediaId: String,
        season: Int = 1,
        episode: Int = 1,
        isMovie: Boolean = false
    ): StreamInfo? = withContext(Dispatchers.IO) {
        val cleanSubjectId = cleanId(mediaId)
        val seParam = if (isMovie) 0 else season
        val epParam = if (isMovie) 0 else episode

        try {
            val streamUrl = "${baseUrl()}/mb/stream/$cleanSubjectId?se=$seParam&ep=$epParam"
            val request = buildRequest(streamUrl)
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null

            val json = JSONObject(body)
            val title = json.optString("title", "MovieBox Stream")
            val sources = mutableListOf<VideoStreamSource>()

            val apiKey = ServerConfig.movieBoxApiKey.value
            val baseHeaders = mutableMapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Referer" to "${baseUrl()}/"
            )
            if (apiKey.isNotBlank()) {
                baseHeaders["X-API-Key"] = apiKey
                baseHeaders["Authorization"] = "Bearer $apiKey"
            }

            // 1. Check play / proxy_mpd (best native DASH playback via the proxy!)
            val proxyMpd = json.optString("play").ifBlank {
                json.optString("proxy_mpd").ifBlank {
                    json.optString("proxy_url")
                }
            }
            if (proxyMpd.isNotBlank()) {
                val fullProxyUrl = if (proxyMpd.startsWith("http")) proxyMpd else "${baseUrl()}$proxyMpd"
                sources.add(
                    VideoStreamSource(
                        quality = "⚡ 1080p HD Auto Proxy",
                        url = fullProxyUrl,
                        isHls = false,
                        isDash = true,
                        headers = baseHeaders
                    )
                )
            }

            // 2. Parse direct sources array (DASH streams with cookies/headers)
            val sourcesArray = json.optJSONArray("sources")
            if (sourcesArray != null) {
                for (i in 0 until sourcesArray.length()) {
                    val sObj = sourcesArray.getJSONObject(i)
                    val rawRes = sObj.optString("resolution", "HD").split(",").firstOrNull() ?: "HD"
                    val res = when {
                        rawRes.contains("1080") -> "🎬 1080p Full HD"
                        rawRes.contains("720") -> "📺 720p HD"
                        rawRes.contains("480") -> "📱 480p SD"
                        rawRes.contains("360") -> "📶 360p Low"
                        else -> "🎬 $rawRes"
                    }
                    val format = sObj.optString("format", "DASH")
                    val rawUrl = sObj.optString("url")
                    val playUrl = sObj.optString("play_url").ifBlank { sObj.optString("proxy_url") }

                    val headersMap = HashMap(baseHeaders)
                    val headersObj = sObj.optJSONObject("headers")
                    if (headersObj != null) {
                        val keys = headersObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            headersMap[k] = headersObj.optString(k)
                        }
                    }

                    // If proxy play_url available for this specific source, add it
                    if (playUrl.isNotBlank()) {
                        val fullUrl = if (playUrl.startsWith("http")) playUrl else "${baseUrl()}$playUrl"
                        sources.add(
                            VideoStreamSource(
                                quality = "$res (Fast Proxy)",
                                url = fullUrl,
                                isHls = format.equals("HLS", ignoreCase = true),
                                isDash = format.equals("DASH", ignoreCase = true) || fullUrl.contains(".mpd"),
                                headers = baseHeaders
                            )
                        )
                    }

                    // Also add direct source with original headers
                    if (rawUrl.isNotBlank()) {
                        sources.add(
                            VideoStreamSource(
                                quality = "$res ($format Direct)",
                                url = rawUrl,
                                isHls = format.equals("HLS", ignoreCase = true) || rawUrl.contains("m3u8"),
                                isDash = format.equals("DASH", ignoreCase = true) || rawUrl.contains(".mpd"),
                                headers = headersMap
                            )
                        )
                    }
                }
            }

            // 3. Direct MP4 options
            val mp4Array = json.optJSONArray("mp4")
            if (mp4Array != null) {
                for (i in 0 until mp4Array.length()) {
                    val mObj = mp4Array.getJSONObject(i)
                    val url = mObj.optString("url")
                    val res = mObj.optString("resolution", "MP4")
                    if (url.isNotBlank()) {
                        sources.add(
                            VideoStreamSource(
                                quality = "$res MP4",
                                url = url,
                                isHls = false,
                                isDash = false
                            )
                        )
                    }
                }
            }

            if (sources.isNotEmpty()) {
                StreamInfo(
                    title = title,
                    sources = sources.distinctBy { it.url },
                    mediaId = mediaId,
                    season = season,
                    episode = episode
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("MovieBoxRepo", "Error extractStream: ${e.message}", e)
            null
        }
    }
}
