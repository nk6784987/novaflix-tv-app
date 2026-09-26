package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

enum class MediaType {
    MOVIE, TV, ANIME
}

data class MediaItem(
    val id: String,
    val tmdbId: Long? = null,
    val title: String,
    val originalTitle: String? = null,
    val overview: String,
    val posterPath: String?,
    val backdropPath: String?,
    val mediaType: MediaType,
    val rating: Double,
    val releaseYear: String,
    val genres: List<String> = emptyList(),
    val voteCount: Int = 0,
    val runtimeMinutes: Int? = null,
    val totalSeasons: Int? = null,
    val totalEpisodes: Int? = null,
    val trailerUrl: String? = null
) {
    fun getFullPosterUrl(): String? {
        if (posterPath.isNullOrBlank() || posterPath.equals("none", ignoreCase = true)) return null
        val clean = posterPath.trim()
        return when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            clean.startsWith("/") -> "https://image.tmdb.org/t/p/w500$clean"
            else -> "https://image.tmdb.org/t/p/w500/$clean"
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

data class CastMember(
    val id: Long,
    val name: String,
    val character: String,
    val profilePath: String?
) {
    fun getFullProfileUrl(): String? {
        if (profilePath.isNullOrBlank() || profilePath.equals("none", ignoreCase = true)) return null
        val clean = profilePath.trim()
        return when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            clean.startsWith("/") -> "https://image.tmdb.org/t/p/w185$clean"
            else -> "https://image.tmdb.org/t/p/w185/$clean"
        }
    }
}

data class Episode(
    val id: String,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val title: String,
    val overview: String?,
    val stillPath: String?,
    val airDate: String?
) {
    fun getFullStillUrl(): String? {
        if (stillPath.isNullOrBlank() || stillPath.equals("none", ignoreCase = true)) return null
        val clean = stillPath.trim()
        return when {
            clean.startsWith("http://") || clean.startsWith("https://") -> clean
            clean.startsWith("/") -> "https://image.tmdb.org/t/p/w300$clean"
            else -> "https://image.tmdb.org/t/p/w300/$clean"
        }
    }
}

data class VideoStreamSource(
    val quality: String, // "1080p", "720p", "480p", "Auto"
    val url: String,
    val isHls: Boolean = true,
    val headers: Map<String, String> = emptyMap(),
    val language: String = "",      // e.g. "Hindi", "English" (as set by admin, optional)
    val downloadUrl: String = "",    // direct download link set by admin (optional)
    val isDash: Boolean = false
)

data class SubtitleTrack(
    val language: String,
    val label: String,
    val url: String
)

data class StreamInfo(
    val title: String,
    val sources: List<VideoStreamSource>,
    val subtitles: List<SubtitleTrack> = emptyList(),
    val mediaId: String,
    val season: Int = 1,
    val episode: Int = 1,
    val embedUrl: String? = null
)

// TMDB Raw DTOs
@JsonClass(generateAdapter = true)
data class TmdbPageResponse<T>(
    val page: Int?,
    val results: List<T>?,
    @Json(name = "total_pages") val totalPages: Int?,
    @Json(name = "total_results") val totalResults: Int?
)

@JsonClass(generateAdapter = true)
data class TmdbMediaDto(
    val id: Long,
    val title: String?,
    val name: String?,
    @Json(name = "original_title") val originalTitle: String?,
    @Json(name = "original_name") val originalName: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "media_type") val mediaTypeRaw: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    @Json(name = "vote_count") val voteCount: Int?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "genre_ids") val genreIds: List<Int>?,
    val popularity: Double?
)

@JsonClass(generateAdapter = true)
data class TmdbMovieDetailsDto(
    val id: Long,
    val title: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    @Json(name = "vote_count") val voteCount: Int?,
    @Json(name = "release_date") val releaseDate: String?,
    val runtime: Int?,
    val genres: List<TmdbGenreDto>?,
    val credits: TmdbCreditsDto?,
    val recommendations: TmdbPageResponse<TmdbMediaDto>?
)

@JsonClass(generateAdapter = true)
data class TmdbTvDetailsDto(
    val id: Long,
    val name: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    @Json(name = "vote_count") val voteCount: Int?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "number_of_seasons") val numberOfSeasons: Int?,
    @Json(name = "number_of_episodes") val numberOfEpisodes: Int?,
    val genres: List<TmdbGenreDto>?,
    val credits: TmdbCreditsDto?,
    val seasons: List<TmdbSeasonInfoDto>?,
    val recommendations: TmdbPageResponse<TmdbMediaDto>?
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonInfoDto(
    val id: Long,
    @Json(name = "season_number") val seasonNumber: Int,
    val name: String?,
    @Json(name = "episode_count") val episodeCount: Int?,
    @Json(name = "poster_path") val posterPath: String?
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonDetailsDto(
    val id: Long,
    @Json(name = "season_number") val seasonNumber: Int,
    val name: String?,
    val episodes: List<TmdbEpisodeDto>?
)

@JsonClass(generateAdapter = true)
data class TmdbEpisodeDto(
    val id: Long,
    @Json(name = "episode_number") val episodeNumber: Int,
    @Json(name = "season_number") val seasonNumber: Int,
    val name: String?,
    val overview: String?,
    @Json(name = "still_path") val stillPath: String?,
    @Json(name = "air_date") val airDate: String?
)

@JsonClass(generateAdapter = true)
data class TmdbGenreDto(
    val id: Int,
    val name: String
)

@JsonClass(generateAdapter = true)
data class TmdbGenreListDto(
    val genres: List<TmdbGenreDto>?
)

@JsonClass(generateAdapter = true)
data class TmdbCreditsDto(
    val cast: List<TmdbCastDto>?
)

@JsonClass(generateAdapter = true)
data class TmdbCastDto(
    val id: Long,
    val name: String,
    val character: String?,
    @Json(name = "profile_path") val profilePath: String?
)
