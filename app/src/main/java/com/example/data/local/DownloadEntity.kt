package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_items")
data class DownloadEntity(
    @PrimaryKey val id: String, // e.g., "download_movie_550" or "download_tv_100_s1_e1"
    val mediaId: String,
    val tmdbId: Long? = null,
    val title: String,
    val posterPath: String?,
    val backdropPath: String?,
    val mediaType: String, // "MOVIE", "TV", "ANIME"
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val episodeTitle: String? = null,
    val streamUrl: String,
    val localFilePath: String? = null,
    val fileSizeBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: String = "QUEUED", // QUEUED, DOWNLOADING, COMPLETED, FAILED, PAUSED
    val progressPercent: Int = 0,
    val createdAtTimestamp: Long = System.currentTimeMillis()
)
