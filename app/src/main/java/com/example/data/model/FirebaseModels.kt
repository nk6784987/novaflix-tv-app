package com.example.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class FirebaseMovie(
    val title: String = "",
    val year: Any? = "",
    val language: String = "",
    val rating: Any? = "",
    val director: String = "",
    val desc: String = "",
    val poster: String = "",
    val backdrop: String = "",
    val trailerUrl: String = "",
    val categories: List<String>? = null,
    val category: String? = null,
    val access: String = "Free",
    val isPremium: Boolean = false,
    val isNew: Boolean = false,
    val isUpcoming: Boolean = false,
    val isTopTen: Boolean = false,
    val isTrending: Boolean = false,
    val isHeroBanner: Boolean = false,
    val needsReview: Boolean = false,
    val cast: List<FirebaseCast>? = null,
    val sources: List<FirebaseSource>? = null,
    val timestamp: Any? = 0L
)

@IgnoreExtraProperties
data class FirebaseSeries(
    val title: String = "",
    val year: Any? = "",
    val language: String = "",
    val rating: Any? = "",
    val director: String = "",
    val desc: String = "",
    val poster: String = "",
    val backdrop: String = "",
    val trailerUrl: String = "",
    val category: String = "",
    val tmdbId: Any? = "",
    val access: String = "Free",
    val isPremium: Boolean = false,
    val isNew: Boolean = false,
    val isUpcoming: Boolean = false,
    val isTopTen: Boolean = false,
    val isTrending: Boolean = false,
    val isHeroBanner: Boolean = false,
    val needsReview: Boolean = false,
    val cast: List<FirebaseCast>? = null,
    val episodes: Any? = null,
    val episodesList: List<FirebaseEpisode>? = null,
    val timestamp: Any? = 0L
) {
    fun getEpisodes(): List<FirebaseEpisode> {
        return when (val eps = episodes) {
            is Map<*, *> -> eps.values.mapNotNull { it as? FirebaseEpisode }
            is List<*> -> eps.mapNotNull { it as? FirebaseEpisode }
            else -> episodesList ?: emptyList()
        }
    }
}

@IgnoreExtraProperties
data class FirebaseCast(
    val name: String = "",
    val photo: String = "",
    val character: String = ""
)

@IgnoreExtraProperties
data class FirebaseSource(
    val url: String = "",
    val downloadUrl: String = "",
    val quality: String = "",
    val language: String = ""
)

@IgnoreExtraProperties
data class FirebaseEpisode(
    val title: String = "",
    val episodeNumber: Int = 0,
    val seasonNumber: Int = 0,
    val thumbnail: String = "",
    val sources: List<FirebaseSource>? = null
)

@IgnoreExtraProperties
data class FirebaseCategory(
    val name: String = "",
    val type: String = "",
    val order: Int = 0,
    val enabled: Boolean = false
)

data class FirestoreCategory(
    val id: String = "",
    val name: String = "",
    val type: String? = null,
    val order: Int = 0,
    val enabled: Boolean = true
)

fun FirebaseMovie.toMediaItem(id: String): MediaItem {
    return MediaItem(
        id = id,
        tmdbId = null,
        title = title.ifBlank { "Movie $id" },
        overview = desc,
        posterPath = poster.takeIf { it.isNotBlank() },
        backdropPath = backdrop.takeIf { it.isNotBlank() },
        mediaType = MediaType.MOVIE,
        rating = rating?.toString()?.toDoubleOrNull() ?: 0.0,
        releaseYear = year?.toString() ?: "",
        genres = if (!categories.isNullOrEmpty()) categories else listOfNotNull(category.takeIf { it?.isNotBlank() == true }),
        trailerUrl = trailerUrl.takeIf { it.isNotBlank() }
    )
}

fun FirebaseSeries.toMediaItem(id: String, type: MediaType): MediaItem {
    return MediaItem(
        id = id,
        tmdbId = tmdbId?.toString()?.toLongOrNull(),
        title = title.ifBlank { "Series $id" },
        overview = desc,
        posterPath = poster.takeIf { it.isNotBlank() },
        backdropPath = backdrop.takeIf { it.isNotBlank() },
        mediaType = type,
        rating = rating?.toString()?.toDoubleOrNull() ?: 0.0,
        releaseYear = year?.toString() ?: "",
        genres = listOfNotNull(category.takeIf { it.isNotBlank() }),
        trailerUrl = trailerUrl.takeIf { it.isNotBlank() }
    )
}

fun Any?.toLongSafe(default: Long = 0L): Long {
    return when (this) {
        is Number -> this.toLong()
        is String -> this.toLongOrNull() ?: default
        else -> default
    }
}

fun FirebaseEpisode.toEpisode(id: String): Episode {
    return Episode(
        id = id,
        episodeNumber = episodeNumber,
        seasonNumber = seasonNumber,
        title = title.ifBlank { "Episode $episodeNumber" },
        overview = null,
        stillPath = thumbnail.takeIf { it.isNotBlank() },
        airDate = null
    )
}

fun FirebaseSource.toVideoStreamSource(): VideoStreamSource {
    return VideoStreamSource(
        quality = quality,
        url = url,
        isHls = false
    )
}
