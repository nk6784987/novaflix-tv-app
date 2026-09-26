package com.example.data.api

import com.example.data.model.*
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {

    @GET("trending/movie/day")
    suspend fun getTrendingMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbPageResponse<TmdbMediaDto>

    @GET("trending/tv/day")
    suspend fun getTrendingTvSeries(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbPageResponse<TmdbMediaDto>

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbPageResponse<TmdbMediaDto>

    @GET("movie/now_playing")
    suspend fun getNowPlayingMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
        @Query("region") region: String? = null
    ): TmdbPageResponse<TmdbMediaDto>

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("with_origin_country") originCountry: String? = null,
        @Query("with_original_language") originalLanguage: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1,
        @Query("region") region: String? = null
    ): TmdbPageResponse<TmdbMediaDto>

    @GET("discover/tv")
    suspend fun discoverTvSeries(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("with_origin_country") originCountry: String? = null,
        @Query("with_original_language") originalLanguage: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbPageResponse<TmdbMediaDto>

    @GET("tv/popular")
    suspend fun getPopularTvSeries(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbPageResponse<TmdbMediaDto>

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Long,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("append_to_response") appendToResponse: String = "credits,recommendations"
    ): TmdbMovieDetailsDto

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") tvId: Long,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("append_to_response") appendToResponse: String = "credits,recommendations"
    ): TmdbTvDetailsDto

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getSeasonDetails(
        @Path("tv_id") tvId: Long,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): TmdbSeasonDetailsDto

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1
    ): TmdbPageResponse<TmdbMediaDto>

    @GET("genre/movie/list")
    suspend fun getMovieGenres(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): TmdbGenreListDto

    @GET("genre/tv/list")
    suspend fun getTvGenres(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "en-US"
    ): TmdbGenreListDto
}
