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
)
