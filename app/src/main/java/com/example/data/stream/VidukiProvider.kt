package com.example.data.stream

import com.example.data.model.MediaType

/**
 * Viduki.net embed provider — independent from every other provider (ElitePlex, direct
 * scrapers, admin uploads). Viduki serves an HTML page with its own JS player inside an
 * iframe, so it is rendered through a WebView (see [com.example.ui.screens.VidukiPlayerScreen])
 * instead of native ExoPlayer sources.
 *
 * Do NOT invent undocumented endpoints here — only the 4 documented API numbers below.
 */
object VidukiProvider {

    private const val BASE = "https://viduki.net"

    /** One entry per documented Viduki API/server. Order below is also the fallback order. */
    enum class Api(val number: Int, val displayName: String) {
        API_1(1, "API 1 — Multi Server"),
        API_2(2, "API 2 — Multi Language"),
        API_3(3, "API 3 — Multi Embeds"),
        API_4(4, "API 4 — Premium");

        companion object {
            val fallbackOrder: List<Api> = listOf(API_1, API_2, API_3, API_4)

            fun fromSourceTag(source: String?): Api? {
                if (source.isNullOrBlank()) return null
                val digits = Regex("""(\d)""").find(source)?.groupValues?.get(1)
                return entries.firstOrNull { it.number.toString() == digits }
            }
        }
    }

    /**
     * Builds the documented Viduki URL for [api].
     * - movie: /{api}/movie/{id}
     * - tv (and anime, treated as TV — season/episode): /{api}/tv/{id}/{season}/{episode}
     * [id] is passed through untouched — never modify a TMDB/IMDb id.
     * [color] (hex, no leading '#') is only meaningful for API 1 movie requests.
     */
    fun buildUrl(
        api: Api,
        mediaType: MediaType,
        id: String,
        season: Int = 1,
        episode: Int = 1,
        color: String? = null
    ): String {
        val isTv = mediaType == MediaType.TV || mediaType == MediaType.ANIME
        val path = if (isTv) {
            "$BASE/${api.number}/tv/$id/$season/$episode"
        } else {
            "$BASE/${api.number}/movie/$id"
        }
        return if (!isTv && api == API_1_WITH_COLOR && !color.isNullOrBlank()) {
            "$path?color=${color.removePrefix("#")}"
        } else {
            path
        }
    }

    // Only API 1 documents the optional color parameter.
    private val API_1_WITH_COLOR = Api.API_1

    const val ORIGIN = "https://www.viduki.net"
    const val FAILURE_EVENT_TYPE = "viduki:all-servers-failed"
}
