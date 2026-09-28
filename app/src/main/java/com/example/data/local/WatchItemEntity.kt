package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_items")
data class WatchItemEntity(
    @PrimaryKey val id: String, // e.g. "movie_550" or "tv_1399_s1_e1" or "anime_21_e1"
    val mediaId: String,
    val tmdbId: Long? = null,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val mediaType: String, // "MOVIE", "TV", "ANIME"
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val episodeTitle: String? = null,
    val progressMillis: Long = 0L,
    val durationMillis: Long = 0L,
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val isInMyList: Boolean = false
) {
    /** Build a proper TMDB image URL from the raw path stored in DB. */
    fun getFullPosterUrl(): String? {
        if (posterPath.isNullOrBlank() || posterPath.equals("none", ignoreCase = true)) return null
        val clean = posterPath.trim()
        return when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            clean.startsWith("/") -> "https://image.tmdb.org/t/p/w780$clean"
            else -> "https://image.tmdb.org/t/p/w780/$clean"
        }
    }

    fun getFullBackdropUrl(): String? {
        if (backdropPath.isNullOrBlank() || backdropPath.equals("none", ignoreCase = true)) return getFullPosterUrl()
        val clean = backdropPath.trim()
        return when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            clean.startsWith("/") -> "https://image.tmdb.org/t/p/w1280$clean"
            else -> "https://image.tmdb.org/t/p/w1280/$clean"
        }
    }
}
