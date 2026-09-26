package com.example.data.repository

import android.util.Log
import com.example.data.local.WatchItemEntity
import com.example.data.model.CastMember
import com.example.data.model.SubtitleTrack
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.VideoStreamSource
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Reads content from the Firebase Realtime Database written by the
 * NovaFlixAdmin panel / Telegram auto-upload bot:
 *
 *   movies/{key}              -> title, year, language, rating, desc, poster, backdrop,
 *                                trailerUrl, categories[] / category, flags, cast[], sources[]
 *   webseries/{key}, anime/{key} -> same, but `episodes` instead of `sources`
 *   categories/{id}           -> { name, type, order, enabled }
 *
 * v2 fixes:
 *  - EVERY field is parsed defensively. RTDB throws if you ask for a String but the
 *    value is a number (year / rating / timestamp are often numbers) - that used to
 *    make the whole list silently come back empty.
 *  - Each node (movies / webseries / anime) is read on its own, so if one node is
 *    permission-denied or broken the others still load.
 *  - Newly uploaded content is sorted FIRST inside every category row.
 *  - Categories that exist on content but are missing from the admin `categories`
 *    node still get their own row, so new content is never hidden.
 *  - "More like this" = shared categories + shared cast (never one repeated movie).
 *  - Sources keep language / downloadUrl; episodes support nested season layouts.
 */
class FirebaseRepository(
    private val databaseProvider: () -> FirebaseDatabase? = {
        try {
            FirebaseDatabase.getInstance(DB_URL)
        } catch (e: Throwable) {
            try { FirebaseDatabase.getInstance() } catch (e2: Throwable) { null }
        }
    }
) {

    private fun getDb(): FirebaseDatabase? = databaseProvider()

    // ------------------------------------------------------------------ //
    // Auth (once, not on every call)
    // ------------------------------------------------------------------ //

    private val authMutex = Mutex()
    private var lastAuthAttemptAt = 0L

    private suspend fun ensureAuth() {
        try {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (auth.currentUser != null) return
            authMutex.withLock {
                if (auth.currentUser != null) return@withLock
                val now = System.currentTimeMillis()
                if (now - lastAuthAttemptAt < 60_000L) return@withLock
                lastAuthAttemptAt = now
                auth.signInAnonymously().await()
            }
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "ensureAuth failed: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ //
    // Safe DataSnapshot readers (never throw on type mismatch)
    // ------------------------------------------------------------------ //

    private fun scalarToString(v: Any?): String = when (v) {
        null -> ""
        is String -> v.trim()
        is Boolean -> v.toString()
        is Long -> v.toString()
        is Int -> v.toString()
        is Double -> if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
        is Number -> v.toString()
        else -> ""
    }

    private fun anyToLong(v: Any?): Long = when (v) {
        is Number -> v.toLong()
        is String -> v.trim().toDoubleOrNull()?.toLong() ?: 0L
        else -> 0L
    }

    private fun DataSnapshot.str(field: String): String = scalarToString(child(field).value)

    private fun DataSnapshot.strOrNull(field: String): String? = str(field).ifBlank { null }

    private fun DataSnapshot.firstStr(vararg fields: String): String {
        for (f in fields) {
            val v = str(f)
            if (v.isNotBlank()) return v
        }
        return ""
    }

    private fun DataSnapshot.bool(field: String): Boolean = when (val v = child(field).value) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        is String -> v.equals("true", ignoreCase = true) || v == "1" || v.equals("yes", ignoreCase = true)
        else -> false
    }

    private fun DataSnapshot.longVal(field: String): Long = anyToLong(child(field).value)

    private fun DataSnapshot.intOrNull(field: String): Int? {
        val v = child(field).value ?: return null
        return when (v) {
            is Number -> v.toInt()
            is String -> v.trim().toDoubleOrNull()?.toInt()
            else -> null
        }
    }

    private fun norm(s: String): String = s.trim().lowercase()

    // ------------------------------------------------------------------ //
    // Parsing helpers
    // ------------------------------------------------------------------ //

    private fun normalizeQuality(q: String): String {
        val t = q.trim()
        if (t.isEmpty()) return "Auto"
        return if (t.all { it.isDigit() }) "${t}p" else t
    }

    private fun sourceFromNode(s: DataSnapshot): VideoStreamSource? {
        val url = s.firstStr("url", "link", "streamUrl", "src", "file").trim()
        if (url.isBlank()) return null
        return VideoStreamSource(
            quality = normalizeQuality(s.firstStr("quality", "label", "name")),
            url = url,
            isHls = url.contains(".m3u8", ignoreCase = true),
            language = s.firstStr("language", "lang", "audio"),
            downloadUrl = s.firstStr("downloadUrl", "download", "downloadLink")
        )
    }

    private fun parseSources(sourcesSnap: DataSnapshot): List<VideoStreamSource> {
        val list = mutableListOf<VideoStreamSource>()
        if (!sourcesSnap.exists()) return list

        // `sources` itself might be a single source object instead of a list
        if (sourcesSnap.hasChild("url") || sourcesSnap.hasChild("link")) {
            sourceFromNode(sourcesSnap)?.let { list.add(it) }
            return list
        }
        for (s in sourcesSnap.children) {
            if (s.hasChildren()) {
                sourceFromNode(s)?.let { list.add(it) }
            } else {
                val u = scalarToString(s.value)
                if (u.startsWith("http", ignoreCase = true)) {
                    list.add(
                        VideoStreamSource(
                            quality = "Auto",
                            url = u,
                            isHls = u.contains(".m3u8", ignoreCase = true)
                        )
                    )
                }
            }
        }
        return list.distinctBy { it.url }
    }

    /** Sources for a whole node (movie or episode): `sources` list, or a bare url field. */
    private fun parseNodeSources(node: DataSnapshot): List<VideoStreamSource> {
        val fromList = parseSources(node.child("sources"))
        if (fromList.isNotEmpty()) return fromList
        val direct = node.firstStr("url", "streamUrl", "videoUrl", "link")
        if (direct.startsWith("http", ignoreCase = true)) {
            return listOf(
                VideoStreamSource(
                    quality = normalizeQuality(node.str("quality")),
                    url = direct,
                    isHls = direct.contains(".m3u8", ignoreCase = true),
                    language = node.str("language"),
                    downloadUrl = node.firstStr("downloadUrl", "download")
                )
            )
        }
        return emptyList()
    }

    private fun parseCast(snap: DataSnapshot): List<CastMember> {
        val list = mutableListOf<CastMember>()
        for ((idx, c) in snap.child("cast").children.withIndex()) {
            val name = if (c.hasChildren()) c.str("name") else scalarToString(c.value)
            if (name.isBlank()) continue
            list.add(
                CastMember(
                    id = idx.toLong(),
                    name = name,
                    character = if (c.hasChildren()) c.str("character") else "",
                    profilePath = if (c.hasChildren()) c.firstStr("photo", "image", "profile").ifBlank { null } else null
                )
            )
        }
        return list
    }

    private fun parseCategoryList(snap: DataSnapshot): List<String> {
        val out = LinkedHashMap<String, String>() // normalized -> display
        fun addRaw(v: String) {
            v.split(',', '|', ';').map { it.trim() }.filter { it.isNotEmpty() }
                .forEach { out.putIfAbsent(norm(it), it) }
        }
        for (field in listOf("categories", "category", "genres", "genre")) {
            val node = snap.child(field)
            if (!node.exists()) continue
            if (node.hasChildren()) {
                for (c in node.children) {
                    val v = c.value
                    if (v is Boolean) {
                        if (v) c.key?.let { addRaw(it) }
                    } else {
                        addRaw(scalarToString(v))
                    }
                }
            } else {
                addRaw(scalarToString(node.value))
            }
        }
        return out.values.toList()
    }

    // --- episodes (supports flat list AND nested season containers) ------ //

    private data class RawEpisode(
        val season: Int,
        val number: Int,
        val title: String,
        val thumb: String?,
        val node: DataSnapshot
    )

    private fun collectEpisodes(episodesSnap: DataSnapshot): List<RawEpisode> {
        val out = mutableListOf<RawEpisode>()

        fun walk(snap: DataSnapshot, seasonHint: Int?) {
            var autoIndex = 0
            for (child in snap.children) {
                autoIndex++
                val looksLikeEpisode = child.hasChild("episodeNumber") || child.hasChild("episode") ||
                    child.hasChild("sources") || child.hasChild("url")
                if (looksLikeEpisode) {
                    val season = child.intOrNull("seasonNumber") ?: child.intOrNull("season") ?: seasonHint ?: 1
                    val keyNum = child.key?.toIntOrNull()?.takeIf { it > 0 }
                    val number = child.intOrNull("episodeNumber") ?: child.intOrNull("episode") ?: keyNum ?: autoIndex
                    out.add(
                        RawEpisode(
                            season = season,
                            number = number,
                            title = child.firstStr("title", "name").ifBlank { "Episode $number" },
                            thumb = child.firstStr("thumbnail", "thumb", "still", "image").ifBlank { null },
                            node = child
                        )
                    )
                } else if (child.hasChildren()) {
                    val keyDigits = child.key?.filter { it.isDigit() }?.toIntOrNull()
                    val hint = child.intOrNull("seasonNumber") ?: keyDigits ?: seasonHint
                    val inner = if (child.hasChild("episodes")) child.child("episodes") else child
                    walk(inner, hint)
                }
            }
        }

        walk(episodesSnap, null)
        return out.distinctBy { it.season to it.number }
    }

    // --- MediaItem builders ---------------------------------------------- //

    private fun movieToMediaItem(key: String, snap: DataSnapshot): MediaItem {
        return MediaItem(
            id = "movie_$key",
            title = snap.str("title").ifBlank { "Untitled" },
            overview = snap.firstStr("desc", "description", "overview"),
            posterPath = snap.strOrNull("poster"),
            backdropPath = snap.strOrNull("backdrop"),
            mediaType = MediaType.MOVIE,
            rating = snap.str("rating").toDoubleOrNull() ?: 0.0,
            releaseYear = snap.str("year"),
            genres = parseCategoryList(snap),
            runtimeMinutes = snap.intOrNull("runtime") ?: snap.intOrNull("duration"),
            trailerUrl = snap.strOrNull("trailerUrl")
        )
    }

    private fun seriesToMediaItem(key: String, snap: DataSnapshot, isAnime: Boolean): MediaItem {
        val episodes = collectEpisodes(snap.child("episodes"))
        val seasonNumbers = episodes.map { it.season }.distinct()
        return MediaItem(
            id = if (isAnime) "anime_$key" else "tv_$key",
            title = snap.str("title").ifBlank { "Untitled" },
            overview = snap.firstStr("desc", "description", "overview"),
            posterPath = snap.strOrNull("poster"),
            backdropPath = snap.strOrNull("backdrop"),
            mediaType = if (isAnime) MediaType.ANIME else MediaType.TV,
            rating = snap.str("rating").toDoubleOrNull() ?: 0.0,
            releaseYear = snap.str("year"),
            genres = parseCategoryList(snap),
            totalEpisodes = episodes.size,
            totalSeasons = if (seasonNumbers.isNotEmpty()) seasonNumbers.size else null,
            trailerUrl = snap.strOrNull("trailerUrl")
        )
    }

    private fun seriesNode(isAnime: Boolean) = if (isAnime) "anime" else "webseries"

    // ------------------------------------------------------------------ //
    // Raw items + cache
    // ------------------------------------------------------------------ //

    private data class RawItem(
        val item: MediaItem,
        val categories: List<String>,
        val cast: List<String>,
        val language: String,
        val addedAt: Long,
        val updatedAt: Long,
        val isTrending: Boolean,
        val isNew: Boolean,
        val isHeroBanner: Boolean,
        val isTopTen: Boolean,
        val isUpcoming: Boolean
    )

    private data class CategoryConfig(
        val enabled: List<String>,
        val disabled: Set<String>
    )

    private fun buildRaw(snap: DataSnapshot, item: MediaItem): RawItem {
        val key = snap.key ?: ""
        val added = listOf(snap.longVal("timestamp"), snap.longVal("createdAt"), snap.longVal("addedAt"))
            .firstOrNull { it > 0L } ?: 0L
        val updated = snap.longVal("updatedAt").takeIf { it > 0L } ?: added
        return RawItem(
            item = item,
            categories = item.genres,
            cast = snap.child("cast").children.mapNotNull {
                val n = if (it.hasChildren()) it.str("name") else scalarToString(it.value)
                n.ifBlank { null }
            },
            language = snap.str("language"),
            // when an item has no timestamp at all, Firebase push-keys are chronological -> use the key
            addedAt = if (added > 0L) added else 0L,
            updatedAt = updated,
            isTrending = snap.bool("isTrending"),
            isNew = snap.bool("isNew"),
            isHeroBanner = snap.bool("isHeroBanner"),
            isTopTen = snap.bool("isTopTen"),
            isUpcoming = snap.bool("isUpcoming")
        ).also { keyOrder[item.id] = key }
    }

    // push-key fallback ordering for items without timestamps
    private val keyOrder = java.util.concurrent.ConcurrentHashMap<String, String>()

    private val newestFirst: Comparator<RawItem> = Comparator { a, b ->
        val t = b.addedAt.compareTo(a.addedAt)
        if (t != 0) t else (keyOrder[b.item.id] ?: "").compareTo(keyOrder[a.item.id] ?: "")
    }

    private val cacheMutex = Mutex()
    @Volatile private var rawCache: List<RawItem>? = null
    @Volatile private var rawCacheAt = 0L
    @Volatile private var catCache: CategoryConfig? = null
    @Volatile private var catCacheAt = 0L
    private val cacheTtlMs = 30_000L

    fun invalidateCache() {
        rawCache = null
        catCache = null
    }

    private suspend fun readNode(path: String): DataSnapshot? {
        val db = getDb() ?: return null
        return try {
            db.getReference(path).get().await()
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Could not read '$path': ${e.message}")
            null
        }
    }

    private suspend fun loadAllRawItems(): List<RawItem> = coroutineScope {
        val moviesD = async { readNode("movies") }
        val seriesD = async { readNode("webseries") }
        val animeD = async { readNode("anime") }
        val result = mutableListOf<RawItem>()

        moviesD.await()?.children?.forEach { child ->
            val key = child.key ?: return@forEach
            try { result.add(buildRaw(child, movieToMediaItem(key, child))) }
            catch (e: Exception) { Log.w("FirebaseRepository", "Skipping bad movie $key: ${e.message}") }
        }
        seriesD.await()?.children?.forEach { child ->
            val key = child.key ?: return@forEach
            try { result.add(buildRaw(child, seriesToMediaItem(key, child, false))) }
            catch (e: Exception) { Log.w("FirebaseRepository", "Skipping bad series $key: ${e.message}") }
        }
        animeD.await()?.children?.forEach { child ->
            val key = child.key ?: return@forEach
            try { result.add(buildRaw(child, seriesToMediaItem(key, child, true))) }
            catch (e: Exception) { Log.w("FirebaseRepository", "Skipping bad anime $key: ${e.message}") }
        }
        result
    }

    private suspend fun fetchAllRawItems(force: Boolean = false): List<RawItem> {
        ensureAuth()
        val now = System.currentTimeMillis()
        val cached = rawCache
        if (!force && cached != null && now - rawCacheAt < cacheTtlMs) return cached
        return cacheMutex.withLock {
            val again = rawCache
            if (!force && again != null && System.currentTimeMillis() - rawCacheAt < cacheTtlMs) {
                return@withLock again
            }
            val fresh = loadAllRawItems()
            if (fresh.isEmpty() && again != null) {
                again // offline / temporary failure: keep showing what we had
            } else {
                rawCache = fresh
                rawCacheAt = System.currentTimeMillis()
                fresh
            }
        }
    }

    private suspend fun loadCategoryConfig(): CategoryConfig {
        val cached = catCache
        if (cached != null && System.currentTimeMillis() - catCacheAt < cacheTtlMs) return cached
        val snap = readNode("categories")
        val list = mutableListOf<Pair<Int, String>>()
        val disabled = HashSet<String>()
        snap?.children?.forEach { child ->
            val name = child.str("name").ifBlank { child.key.orEmpty() }.trim()
            if (name.isBlank()) return@forEach
            val enabledNode = child.child("enabled")
            val enabled = if (enabledNode.exists()) child.bool("enabled") else true
            if (!enabled) {
                disabled.add(norm(name))
                return@forEach
            }
            list.add((child.intOrNull("order") ?: 999) to name)
        }
        val cfg = CategoryConfig(
            enabled = list.sortedBy { it.first }.map { it.second }.distinctBy { norm(it) },
            disabled = disabled
        )
        catCache = cfg
        catCacheAt = System.currentTimeMillis()
        return cfg
    }

    // ------------------------------------------------------------------ //
    // Sections
    // ------------------------------------------------------------------ //

    private fun buildCategorySections(
        raw: List<RawItem>,
        cfg: CategoryConfig
    ): List<Pair<String, List<MediaItem>>> {
        val sorted = raw.filter { !it.isUpcoming }.sortedWith(newestFirst)
        val out = mutableListOf<Pair<String, List<MediaItem>>>()
        val used = HashSet<String>()

        // 1) admin-configured categories, in admin order
        for (cat in cfg.enabled) {
            val k = norm(cat)
            if (!used.add(k)) continue
            val items = sorted.filter { r -> r.categories.any { norm(it) == k } }.map { it.item }
            if (items.isNotEmpty()) out.add(cat to items)
        }

        // 2) categories that exist on content but were never added to the admin list
        val extra = LinkedHashMap<String, String>()
        for (r in sorted) {
            for (c in r.categories) {
                val k = norm(c)
                if (k in used || k in cfg.disabled || k in extra) continue
                extra[k] = c
            }
        }
        for ((k, display) in extra) {
            val items = sorted.filter { r -> r.categories.any { norm(it) == k } }.map { it.item }
            if (items.isNotEmpty()) out.add(display to items)
        }
        return out
    }

    private suspend fun buildSections(
        raw: List<RawItem>,
        trendingTitle: String,
        recentTitle: String,
        fallbackTitle: String?
    ): List<Pair<String, List<MediaItem>>> {
        if (raw.isEmpty()) return emptyList()
        val cfg = loadCategoryConfig()
        val visible = raw.filter { !it.isUpcoming }
        val sections = mutableListOf<Pair<String, List<MediaItem>>>()

        val newest = visible.sortedWith(newestFirst).take(20).map { it.item }
        if (newest.isNotEmpty()) sections.add(recentTitle to newest)

        val trending = visible.filter { it.isTrending }.sortedWith(newestFirst).map { it.item }
        if (trending.isNotEmpty()) sections.add(trendingTitle to trending)

        val cats = buildCategorySections(raw, cfg)
        sections.addAll(cats)

        val upcoming = raw.filter { it.isUpcoming }.sortedWith(newestFirst).map { it.item }
        if (upcoming.isNotEmpty()) sections.add("Coming Soon" to upcoming)

        if (cats.isEmpty() && fallbackTitle != null && visible.isNotEmpty()) {
            sections.add(fallbackTitle to visible.sortedWith(newestFirst).map { it.item })
        }
        return sections
    }

    // ------------------------------------------------------------------ //
    // Listing / discovery
    // ------------------------------------------------------------------ //

    suspend fun getAllMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            fetchAllRawItems().filter { it.item.mediaType == MediaType.MOVIE }
                .sortedWith(newestFirst).map { it.item }
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error fetching movies: ${e.message}")
            emptyList()
        }
    }

    suspend fun getAllSeries(isAnime: Boolean): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val type = if (isAnime) MediaType.ANIME else MediaType.TV
            fetchAllRawItems().filter { it.item.mediaType == type }
                .sortedWith(newestFirst).map { it.item }
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error fetching ${seriesNode(isAnime)}: ${e.message}")
            emptyList()
        }
    }

    suspend fun getTrendingMovies(): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val movies = fetchAllRawItems().filter { it.item.mediaType == MediaType.MOVIE && !it.isUpcoming }
            val trending = movies.filter { it.isTrending }.sortedWith(newestFirst)
            (if (trending.isNotEmpty()) trending else movies.sortedWith(newestFirst)).map { it.item }
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error fetching trending movies: ${e.message}")
            emptyList()
        }
    }

    suspend fun getContentByCategory(categoryName: String): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val k = norm(categoryName)
            fetchAllRawItems()
                .filter { r -> r.categories.any { norm(it) == k } }
                .sortedWith(newestFirst)
                .map { it.item }
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error fetching category '$categoryName': ${e.message}")
            emptyList()
        }
    }

    suspend fun getCategories(): List<String> = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            loadCategoryConfig().enabled
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error fetching categories: ${e.message}")
            emptyList()
        }
    }

    /** Home rows: Recently Added, Trending, every category (admin order first, then any
     *  category found on content), Coming Soon. New uploads are always at the FRONT. */
    suspend fun getAdminCategorySections(): List<Pair<String, List<MediaItem>>> = withContext(Dispatchers.IO) {
        try {
            buildSections(fetchAllRawItems(), "Trending Now", "Recently Added", null)
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error building category sections: ${e.message}")
            emptyList()
        }
    }

    suspend fun getSeriesCategorySections(): List<Pair<String, List<MediaItem>>> = withContext(Dispatchers.IO) {
        try {
            val raw = fetchAllRawItems().filter { it.item.mediaType == MediaType.TV }
            buildSections(raw, "Trending Web Series", "Recently Added", "Web Series")
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error building series sections: ${e.message}")
            emptyList()
        }
    }

    suspend fun getAnimeCategorySections(): List<Pair<String, List<MediaItem>>> = withContext(Dispatchers.IO) {
        try {
            val raw = fetchAllRawItems().filter { it.item.mediaType == MediaType.ANIME }
            buildSections(raw, "Trending Anime", "Recently Added", "Anime")
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error building anime sections: ${e.message}")
            emptyList()
        }
    }

    private fun heroFrom(raw: List<RawItem>, maxCount: Int): List<MediaItem> {
        val visible = raw.filter { !it.isUpcoming && (!it.item.backdropPath.isNullOrBlank() || !it.item.posterPath.isNullOrBlank()) }
        val hero = visible.filter { it.isHeroBanner }.sortedWith(newestFirst)
        val trending = visible.filter { it.isTrending }.sortedWith(newestFirst)
        val newest = visible.sortedWith(newestFirst)
        return (hero + trending + newest).map { it.item }.distinctBy { it.id }.take(maxCount)
    }

    suspend fun getFeaturedHeroBanners(maxCount: Int = 6): List<MediaItem> = withContext(Dispatchers.IO) {
        try { heroFrom(fetchAllRawItems().filter { it.item.mediaType == MediaType.MOVIE }, maxCount) } catch (e: Exception) { emptyList() }
    }

    suspend fun getFeaturedHeroBanner(): MediaItem? = getFeaturedHeroBanners(1).firstOrNull()

    suspend fun getSeriesHeroBanners(maxCount: Int = 5): List<MediaItem> = withContext(Dispatchers.IO) {
        try { heroFrom(fetchAllRawItems().filter { it.item.mediaType == MediaType.TV }, maxCount) } catch (e: Exception) { emptyList() }
    }

    suspend fun getAnimeHeroBanners(maxCount: Int = 5): List<MediaItem> = withContext(Dispatchers.IO) {
        try { heroFrom(fetchAllRawItems().filter { it.item.mediaType == MediaType.ANIME }, maxCount) } catch (e: Exception) { emptyList() }
    }

    /** Top 10: items flagged isTopTen in admin (latest updatedAt first); if none are
     *  flagged, falls back to best rated. [type] defaults to MediaType.MOVIE. */
    suspend fun getTopTen(type: MediaType? = null): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val targetType = type ?: MediaType.MOVIE
            val raw = fetchAllRawItems().filter { !it.isUpcoming && it.item.mediaType == targetType }
            val flagged = raw.filter { it.isTopTen }
                .sortedWith(compareByDescending<RawItem> { it.updatedAt }.then(newestFirst))
            val list = if (flagged.isNotEmpty()) flagged else raw.sortedByDescending { it.item.rating }
            list.take(10).map { it.item }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun searchContent(query: String): List<MediaItem> = withContext(Dispatchers.IO) {
        if (query.trim().isEmpty()) return@withContext emptyList()
        try {
            val key = norm(query)
            val squashed = key.filter { it.isLetterOrDigit() }
            fetchAllRawItems()
                .filter { r ->
                    val t = norm(r.item.title)
                    t.contains(key) ||
                        (squashed.length >= 3 && t.filter { it.isLetterOrDigit() }.contains(squashed)) ||
                        r.cast.any { norm(it).contains(key) }
                }
                .sortedWith(newestFirst)
                .map { it.item }
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error performing search: ${e.message}")
            emptyList()
        }
    }

    /**
     * "More Like This": scored by shared categories (x10) + shared cast (x6) +
     * same language / same type bonus. Never contains the title itself and never
     * repeats a title. Tops up with the newest of the same type if too few match.
     */
    suspend fun getSimilarItems(item: MediaItem, cast: List<CastMember>, limit: Int = 12): List<MediaItem> =
        withContext(Dispatchers.IO) {
            try {
                val all = fetchAllRawItems()
                val self = all.firstOrNull { it.item.id == item.id }
                val cats = (self?.categories ?: item.genres).map { norm(it) }.filter { it.isNotEmpty() }.toSet()
                val castNames = (cast.map { it.name } + (self?.cast ?: emptyList()))
                    .map { norm(it) }.filter { it.isNotEmpty() }.toSet()
                val lang = norm(self?.language ?: "")

                val scored = all.asSequence()
                    .filter { it.item.id != item.id && !it.isUpcoming }
                    .map { r ->
                        val sharedCats = r.categories.count { norm(it) in cats }
                        val sharedCast = r.cast.count { norm(it) in castNames }
                        var score = sharedCats * 10 + sharedCast * 6
                        if (score > 0) {
                            if (lang.isNotEmpty() && norm(r.language) == lang) score += 2
                            if (r.item.mediaType == item.mediaType) score += 3
                        }
                        r to score
                    }
                    .filter { it.second > 0 }
                    .sortedWith(compareByDescending<Pair<RawItem, Int>> { it.second }.then(Comparator { a, b -> newestFirst.compare(a.first, b.first) }))
                    .map { it.first.item }
                    .toList()

                val result = scored.toMutableList()
                if (result.size < 6) {
                    val topUp = all.filter { it.item.mediaType == item.mediaType && it.item.id != item.id && !it.isUpcoming }
                        .sortedWith(newestFirst).map { it.item }
                    for (t in topUp) {
                        if (result.size >= limit) break
                        if (result.none { it.id == t.id }) result.add(t)
                    }
                }
                result.distinctBy { it.id }.distinctBy { norm(it.title) }.take(limit)
            } catch (e: Exception) {
                Log.w("FirebaseRepository", "Error building similar items: ${e.message}")
                emptyList()
            }
        }

    /** Emits whenever movies/webseries/anime/categories change in the database. */
    fun observeContentUpdates(): Flow<Unit> = callbackFlow {
        val db = getDb()
        if (db == null) {
            close()
            return@callbackFlow
        }
        val refs = listOf("movies", "webseries", "anime", "categories").map { db.getReference(it) }
        val listeners = refs.map { ref ->
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    invalidateCache()
                    trySend(Unit)
                }
                override fun onCancelled(error: DatabaseError) {}
            }
            ref.addValueEventListener(listener)
            ref to listener
        }
        awaitClose {
            listeners.forEach { (ref, listener) -> ref.removeEventListener(listener) }
        }
    }.debounce(1200).flowOn(Dispatchers.IO)

    // ------------------------------------------------------------------ //
    // Detail screen
    // ------------------------------------------------------------------ //

    /** [key] is the raw Firebase push key (already stripped of the prefix). */
    suspend fun getMovieDetail(key: String): Pair<MediaItem, List<CastMember>>? = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val snap = readNode("movies/$key") ?: return@withContext null
            if (!snap.exists()) return@withContext null
            movieToMediaItem(key, snap) to parseCast(snap)
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error fetching movie detail for $key: ${e.message}")
            null
        }
    }

    suspend fun getSeriesDetail(key: String, isAnime: Boolean): Pair<MediaItem, List<CastMember>>? =
        withContext(Dispatchers.IO) {
            try {
                ensureAuth()
                val snap = readNode("${seriesNode(isAnime)}/$key") ?: return@withContext null
                if (!snap.exists()) return@withContext null
                seriesToMediaItem(key, snap, isAnime) to parseCast(snap)
            } catch (e: Exception) {
                Log.w("FirebaseRepository", "Error fetching series detail for $key: ${e.message}")
                null
            }
        }

    suspend fun getSeasonNumbers(key: String, isAnime: Boolean): List<Int> = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val snap = readNode("${seriesNode(isAnime)}/$key/episodes") ?: return@withContext emptyList()
            collectEpisodes(snap).map { it.season }.distinct().sorted()
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error fetching seasons for $key: ${e.message}")
            emptyList()
        }
    }

    suspend fun getSeasonEpisodes(key: String, isAnime: Boolean, seasonNumber: Int): List<Episode> =
        withContext(Dispatchers.IO) {
            try {
                ensureAuth()
                val snap = readNode("${seriesNode(isAnime)}/$key/episodes") ?: return@withContext emptyList()
                collectEpisodes(snap)
                    .filter { it.season == seasonNumber }
                    .sortedBy { it.number }
                    .map { ep ->
                        Episode(
                            id = "${key}_s${seasonNumber}_e${ep.number}",
                            episodeNumber = ep.number,
                            seasonNumber = seasonNumber,
                            title = ep.title,
                            overview = null,
                            stillPath = ep.thumb,
                            airDate = null
                        )
                    }
            } catch (e: Exception) {
                Log.w("FirebaseRepository", "Error fetching episodes for $key S$seasonNumber: ${e.message}")
                emptyList()
            }
        }

    // ------------------------------------------------------------------ //
    // Streaming sources
    // ------------------------------------------------------------------ //

    suspend fun getMovieSources(key: String): List<VideoStreamSource> = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val snap = readNode("movies/$key") ?: return@withContext emptyList()
            parseNodeSources(snap)
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error fetching movie sources for $key: ${e.message}")
            emptyList()
        }
    }

    suspend fun getEpisodeSources(
        key: String, isAnime: Boolean, seasonNumber: Int, episodeNumber: Int
    ): List<VideoStreamSource> = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val episodesSnap = readNode("${seriesNode(isAnime)}/$key/episodes") ?: return@withContext emptyList()
            val match = collectEpisodes(episodesSnap)
                .firstOrNull { it.season == seasonNumber && it.number == episodeNumber }
                ?: return@withContext emptyList()
            parseNodeSources(match.node)
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error fetching episode sources for $key S${seasonNumber}E$episodeNumber: ${e.message}")
            emptyList()
        }
    }

    // ------------------------------------------------------------------ //
    // User data (unrelated to content - untouched)
    // ------------------------------------------------------------------ //

    suspend fun saveUserProfileToFirestore(
        userId: String,
        displayName: String,
        email: String,
        avatarIcon: String,
        preferredGenre: String,
        totalWatchTimeMinutes: Long
    ) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val db = getDb() ?: return@withContext
            val data = mapOf(
                "userId" to userId,
                "displayName" to displayName,
                "email" to email,
                "avatarIcon" to avatarIcon,
                "preferredGenre" to preferredGenre,
                "totalWatchTimeMinutes" to totalWatchTimeMinutes,
                "updatedAt" to System.currentTimeMillis()
            )
            db.getReference("users").child(userId).updateChildren(data).await()
            Log.d("FirebaseRepository", "User profile synced to RTDB")
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error syncing user profile: ${e.message}")
        }
    }

    suspend fun saveContinueWatchingToFirestore(
        userId: String,
        itemId: String,
        mediaId: String,
        tmdbId: Long?,
        title: String,
        posterPath: String?,
        backdropPath: String?,
        mediaType: String,
        seasonNumber: Int,
        episodeNumber: Int,
        episodeTitle: String?,
        progressMillis: Long,
        durationMillis: Long
    ) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val db = getDb() ?: return@withContext
            val data = mapOf(
                "id" to itemId,
                "mediaId" to mediaId,
                "tmdbId" to tmdbId,
                "title" to title,
                "posterPath" to posterPath,
                "backdropPath" to backdropPath,
                "mediaType" to mediaType,
                "seasonNumber" to seasonNumber,
                "episodeNumber" to episodeNumber,
                "episodeTitle" to episodeTitle,
                "progressMillis" to progressMillis,
                "durationMillis" to durationMillis,
                "lastWatchedTimestamp" to System.currentTimeMillis()
            )
            db.getReference("users").child(userId).child("continue_watching").child(itemId).updateChildren(data).await()
            Log.d("FirebaseRepository", "Continue watching synced to RTDB")
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error syncing continue watching: ${e.message}")
        }
    }

    suspend fun deleteContinueWatchingFromFirestore(
        userId: String,
        itemId: String
    ) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val db = getDb() ?: return@withContext
            db.getReference("users").child(userId).child("continue_watching").child(itemId).removeValue().await()
            Log.d("FirebaseRepository", "Deleted continue watching from RTDB")
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "Error deleting continue watching: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ //
    // Real per-user cloud data:  users/{uid}/...
    //   my_list/{mediaId}          -> saved titles
    //   continue_watching/{itemId} -> resume positions
    //   stats/watchTimeMs          -> real watched time (atomic increments)
    // ------------------------------------------------------------------ //

    private fun parseWatchItem(node: DataSnapshot, inMyList: Boolean): WatchItemEntity? {
        val mediaId = node.str("mediaId").ifBlank { node.key.orEmpty() }
        if (mediaId.isBlank()) return null
        val stamp = node.longVal("lastWatchedTimestamp").takeIf { it > 0L } ?: node.longVal("addedAt")
        return WatchItemEntity(
            id = node.str("id").ifBlank { node.key.orEmpty() },
            mediaId = mediaId,
            tmdbId = node.longVal("tmdbId").takeIf { it > 0L },
            title = node.str("title"),
            posterPath = node.strOrNull("posterPath"),
            backdropPath = node.strOrNull("backdropPath"),
            mediaType = node.str("mediaType").ifBlank { "MOVIE" },
            seasonNumber = node.intOrNull("seasonNumber") ?: 1,
            episodeNumber = node.intOrNull("episodeNumber") ?: 1,
            episodeTitle = node.strOrNull("episodeTitle"),
            progressMillis = node.longVal("progressMillis"),
            durationMillis = node.longVal("durationMillis"),
            lastWatchedTimestamp = if (stamp > 0L) stamp else System.currentTimeMillis(),
            isInMyList = inMyList
        )
    }

    private fun parseUserCloud(snap: DataSnapshot): CloudUserData {
        val cont = snap.child("continue_watching").children.mapNotNull {
            try { parseWatchItem(it, false) } catch (e: Exception) { null }
        }
        val mine = snap.child("my_list").children.mapNotNull {
            try { parseWatchItem(it, true) } catch (e: Exception) { null }
        }
        val statsNode = snap.child("stats")
        val wt = if (statsNode.child("watchTimeMs").exists()) statsNode.longVal("watchTimeMs") else null
        return CloudUserData(cont, mine, wt)
    }

    suspend fun fetchUserCloud(uid: String): CloudUserData? = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val snap = readNode("users/$uid") ?: return@withContext null
            parseUserCloud(snap)
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "fetchUserCloud failed: ${e.message}")
            null
        }
    }

    fun observeUserCloud(uid: String): Flow<CloudUserData> = callbackFlow {
        val db = getDb()
        if (db == null) {
            close()
            return@callbackFlow
        }
        val ref = db.getReference("users").child(uid)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try { trySend(parseUserCloud(snapshot)) } catch (e: Exception) { Log.w("FirebaseRepository", "cloud parse: ${e.message}") }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w("FirebaseRepository", "observeUserCloud cancelled: ${error.message}")
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }.flowOn(Dispatchers.IO)

    /** Fire-and-forget: RTDB queues the write and sends it when the network is back. */
    fun saveMyListItem(uid: String, item: WatchItemEntity) {
        try {
            val db = getDb() ?: return
            val data = mapOf(
                "mediaId" to item.mediaId,
                "tmdbId" to item.tmdbId,
                "title" to item.title,
                "posterPath" to item.posterPath,
                "backdropPath" to item.backdropPath,
                "mediaType" to item.mediaType,
                "addedAt" to System.currentTimeMillis()
            )
            db.getReference("users").child(uid).child("my_list").child(item.mediaId).setValue(data)
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "saveMyListItem: ${e.message}")
        }
    }

    fun removeMyListItem(uid: String, mediaId: String) {
        try {
            getDb()?.getReference("users")?.child(uid)?.child("my_list")?.child(mediaId)?.removeValue()
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "removeMyListItem: ${e.message}")
        }
    }

    fun incrementWatchTime(uid: String, deltaMs: Long) {
        try {
            getDb()?.getReference("users")?.child(uid)?.child("stats")?.child("watchTimeMs")
                ?.setValue(ServerValue.increment(deltaMs))
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "incrementWatchTime: ${e.message}")
        }
    }

    fun saveAccountInfo(uid: String, name: String?, email: String?, photoUrl: String?, isAnonymous: Boolean) {
        try {
            val data = mapOf(
                "userId" to uid,
                "displayName" to (name ?: ""),
                "email" to (email ?: ""),
                "photoUrl" to (photoUrl ?: ""),
                "isGuest" to isAnonymous,
                "lastLoginAt" to System.currentTimeMillis()
            )
            getDb()?.getReference("users")?.child(uid)?.updateChildren(data)
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "saveAccountInfo: ${e.message}")
        }
    }

    suspend fun clearUserHistory(uid: String) = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            getDb()?.getReference("users")?.child(uid)?.child("continue_watching")?.removeValue()?.await()
            getDb()?.getReference("users")?.child(uid)?.child("stats")?.child("watchTimeMs")?.setValue(0L)?.await()
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "clearUserHistory: ${e.message}")
        }
    }

    // ------------------------------------------------------------------ //
    // Playback extras: external subtitles + intro range (both optional fields in admin)
    //   subtitles: [{ url, lang|language, label }]   introStart / introEnd (seconds)
    // ------------------------------------------------------------------ //

    suspend fun getPlaybackExtras(
        key: String, isMovie: Boolean, isAnime: Boolean, season: Int, episode: Int
    ): PlaybackExtras = withContext(Dispatchers.IO) {
        try {
            ensureAuth()
            val node: DataSnapshot? = if (isMovie) {
                readNode("movies/$key")
            } else {
                val eps = readNode("${seriesNode(isAnime)}/$key/episodes")
                eps?.let { e -> collectEpisodes(e).firstOrNull { it.season == season && it.number == episode }?.node }
            }
            if (node == null || !node.exists()) return@withContext PlaybackExtras()
            val subs = mutableListOf<SubtitleTrack>()
            for (s in node.child("subtitles").children) {
                val url = if (s.hasChildren()) s.firstStr("url", "src", "file", "link") else scalarToString(s.value)
                if (!url.startsWith("http", ignoreCase = true)) continue
                val lang = if (s.hasChildren()) s.firstStr("lang", "language") else ""
                val label = if (s.hasChildren()) s.firstStr("label", "name") else ""
                subs.add(SubtitleTrack(language = lang, label = label.ifBlank { lang.ifBlank { "Subtitle" } }, url = url))
            }
            var iStart = node.intOrNull("introStart")
            var iEnd = node.intOrNull("introEnd")
            if (iStart == null && iEnd == null && node.child("intro").hasChildren()) {
                iStart = node.child("intro").intOrNull("start")
                iEnd = node.child("intro").intOrNull("end")
            }
            PlaybackExtras(subs.distinctBy { it.url }, iStart, iEnd)
        } catch (e: Exception) {
            Log.w("FirebaseRepository", "getPlaybackExtras: ${e.message}")
            PlaybackExtras()
        }
    }

    companion object {
        const val DB_URL = "https://cineflix-c6302-default-rtdb.firebaseio.com"
    }
}

data class CloudUserData(
    val continueItems: List<WatchItemEntity>,
    val myList: List<WatchItemEntity>,
    val watchTimeMs: Long?
)

data class PlaybackExtras(
    val subtitles: List<SubtitleTrack> = emptyList(),
    val introStartSec: Int? = null,
    val introEndSec: Int? = null
)
