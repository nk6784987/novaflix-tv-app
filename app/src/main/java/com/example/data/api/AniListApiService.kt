package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.POST

interface AniListApiService {

    @POST("./")
    suspend fun queryAniList(
        @Body request: GraphQLRequest
    ): GraphQLResponse
}

@JsonClass(generateAdapter = true)
data class GraphQLRequest(
    val query: String,
    val variables: Map<String, Any?> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class GraphQLResponse(
    val data: AniListData?
)

@JsonClass(generateAdapter = true)
data class AniListData(
    val Page: AniListDataPage?,
    val Media: AniListMediaDto?
)

@JsonClass(generateAdapter = true)
data class AniListDataPage(
    val media: List<AniListMediaDto>?
)

@JsonClass(generateAdapter = true)
data class AniListMediaDto(
    val id: Long,
    val idMal: Long?,
    val title: AniListTitle?,
    val coverImage: AniListCoverImage?,
    val bannerImage: String?,
    val description: String?,
    val averageScore: Int?,
    val episodes: Int?,
    val status: String?,
    val format: String?,
    val genres: List<String>?,
    val startDate: AniListFuzzyDate?,
    val characters: AniListCharacterConnection?
)

@JsonClass(generateAdapter = true)
data class AniListTitle(
    val userPreferred: String?,
    val english: String?,
    val romaji: String?,
    val native: String?
)

@JsonClass(generateAdapter = true)
data class AniListCoverImage(
    val extraLarge: String?,
    val large: String?,
    val medium: String?,
    val color: String?
)

@JsonClass(generateAdapter = true)
data class AniListFuzzyDate(
    val year: Int?,
    val month: Int?,
    val day: Int?
)

@JsonClass(generateAdapter = true)
data class AniListCharacterConnection(
    val edges: List<AniListCharacterEdge>?
)

@JsonClass(generateAdapter = true)
data class AniListCharacterEdge(
    val role: String?,
    val node: AniListCharacterNode?
)

@JsonClass(generateAdapter = true)
data class AniListCharacterNode(
    val id: Long,
    val name: AniListCharacterName?,
    val image: AniListCharacterImage?
)

@JsonClass(generateAdapter = true)
data class AniListCharacterName(
    val full: String?
)

@JsonClass(generateAdapter = true)
data class AniListCharacterImage(
    val large: String?,
    val medium: String?
)
