package com.example.data.repository

import com.example.data.model.CastMember
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.StreamInfo
import com.example.data.model.VideoStreamSource

object FallbackMediaCatalog {

    val sampleStreams = listOf(
        VideoStreamSource("1080p Full HD", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", isHls = false),
        VideoStreamSource("720p HD", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", isHls = false),
        VideoStreamSource("480p SD", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", isHls = false),
        VideoStreamSource("Auto (HLS)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", isHls = true)
    )

    // -------------------------------------------------------------
    // ANIME ITEMS
    // -------------------------------------------------------------
    val animeList = listOf(
        MediaItem(
            id = "anime_demon_slayer",
            tmdbId = 85937L,
            title = "Demon Slayer: Kimetsu no Yaiba",
            originalTitle = "鬼滅の刃",
            overview = "Tanjiro Kamado sets out on a perilous journey to become a demon slayer and find a cure to turn his sister Nezuko back into a human after their family is slaughtered by powerful demons.",
            posterPath = "https://image.tmdb.org/t/p/w500/xUfRZu2mi8jH6SzQEJGP6tjBuYj.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/nTvM4mhqZlHIkw296cvgpbgdaaz.jpg",
            mediaType = MediaType.ANIME,
            rating = 8.9,
            releaseYear = "2019",
            genres = listOf("Action", "Fantasy", "Animation", "Supernatural"),
            totalSeasons = 4,
            totalEpisodes = 55,
            trailerUrl = "https://www.youtube.com/watch?v=VQGCKyvzIM4"
        ),
        MediaItem(
            id = "anime_jujutsu_kaisen",
            tmdbId = 95479L,
            title = "Jujutsu Kaisen",
            originalTitle = "呪術廻戦",
            overview = "Yuji Itadori swallows a cursed talisman—the finger of a demon—and becomes cursed himself. He enters a shaman's school to locate the demon's other body parts and exorcise himself.",
            posterPath = "https://image.tmdb.org/t/p/w500/hFWP5HkbVEe40hrXgtCeQxQ6ohn.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/gmECXPyd596kIQx84i4K7Y0R92M.jpg",
            mediaType = MediaType.ANIME,
            rating = 8.8,
            releaseYear = "2020",
            genres = listOf("Action", "Supernatural", "Animation", "Dark Fantasy"),
            totalSeasons = 2,
            totalEpisodes = 47,
            trailerUrl = "https://www.youtube.com/watch?v=pkKu9hLT-t8"
        ),
        MediaItem(
            id = "anime_solo_leveling",
            tmdbId = 127532L,
            title = "Solo Leveling",
            originalTitle = "나 혼자만 레벨업",
            overview = "In a world where hunters must battle deadly monsters, weak hunter Sung Jinwoo is chosen by a mysterious program to be its sole player, granting him the unique ability to level up beyond all limits.",
            posterPath = "https://image.tmdb.org/t/p/w500/geCRueV3ElhRTr0xtJuPxJ8BGdM.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/4HodYYKEIsGOdinkGi2Ucz6X9i0.jpg",
            mediaType = MediaType.ANIME,
            rating = 8.7,
            releaseYear = "2024",
            genres = listOf("Action", "Fantasy", "Adventure", "Animation"),
            totalSeasons = 1,
            totalEpisodes = 12,
            trailerUrl = "https://www.youtube.com/watch?v=91b_B3A1_2U"
        ),
        MediaItem(
            id = "anime_attack_on_titan",
            tmdbId = 1429L,
            title = "Attack on Titan",
            originalTitle = "進撃の巨人",
            overview = "After his hometown is destroyed and his mother is killed, young Eren Jaeger vows to cleanse the earth of the giant humanoid Titans that have brought humanity to the brink of extinction.",
            posterPath = "https://image.tmdb.org/t/p/w500/hTP1DtLGFamjfu8WqjnuQdP1n4i.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/iKGhKk4U9hJ7f7F2QZ79lJ5M6nO.jpg",
            mediaType = MediaType.ANIME,
            rating = 9.1,
            releaseYear = "2013",
            genres = listOf("Action", "Dark Fantasy", "Animation", "Sci-Fi"),
            totalSeasons = 4,
            totalEpisodes = 89,
            trailerUrl = "https://www.youtube.com/watch?v=MGRm4IzK1SQ"
        ),
        MediaItem(
            id = "anime_chainsaw_man",
            tmdbId = 114410L,
            title = "Chainsaw Man",
            originalTitle = "チェンソーマン",
            overview = "Denji is a teenage boy living with a Chainsaw Devil named Pochita. Due to the debt his father left behind, he is living a rock-bottom life while repaying his debt by harvesting devil corpses with Pochita.",
            posterPath = "https://image.tmdb.org/t/p/w500/npdB6eFz44557p5NQgQ5b306w6P.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/yGY4Spb9u4c6sRj2jO7J3M3J5nB.jpg",
            mediaType = MediaType.ANIME,
            rating = 8.6,
            releaseYear = "2022",
            genres = listOf("Action", "Supernatural", "Animation", "Horror"),
            totalSeasons = 1,
            totalEpisodes = 12,
            trailerUrl = "https://www.youtube.com/watch?v=dFlDRhvM4b0"
        ),
        MediaItem(
            id = "anime_one_piece",
            tmdbId = 37854L,
            title = "One Piece",
            originalTitle = "ワンピース",
            overview = "Monkey D. Luffy sets out on his quest to find the legendary treasure 'One Piece' and become the King of the Pirates along with his eccentric crew.",
            posterPath = "https://image.tmdb.org/t/p/w500/fcXdJlbSdUEeMSJFsXKszvWWev6.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/2rmK7mnchw9Xr3XdiTFSxTTF5IS.jpg",
            mediaType = MediaType.ANIME,
            rating = 8.9,
            releaseYear = "1999",
            genres = listOf("Action", "Adventure", "Fantasy", "Animation"),
            totalSeasons = 21,
            totalEpisodes = 1100,
            trailerUrl = "https://www.youtube.com/watch?v=MCb13lbKpsE"
        ),
        MediaItem(
            id = "anime_naruto_shippuden",
            tmdbId = 31910L,
            title = "Naruto Shippuden",
            originalTitle = "ナルト 疾風伝",
            overview = "Naruto Uzumaki, is a loud, hyperactive, adolescent ninja who constantly searches for approval and recognition, as well as to become Hokage, who is acknowledged as the leader and strongest of all ninja in the village.",
            posterPath = "https://image.tmdb.org/t/p/w500/kV27j3Nz4d5z8uK3b7p4Y5B1nC.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/7eZgK49k2h4l9j9G3z7f6p3Q0nE.jpg",
            mediaType = MediaType.ANIME,
            rating = 8.7,
            releaseYear = "2007",
            genres = listOf("Action", "Adventure", "Animation", "Fantasy"),
            totalSeasons = 21,
            totalEpisodes = 500
        ),
        MediaItem(
            id = "anime_death_note",
            tmdbId = 13916L,
            title = "Death Note",
            originalTitle = "デスノート",
            overview = "An intelligent high school student goes on a secret crusade to eliminate criminals from the world after discovering a notebook capable of killing anyone whose name is written into it.",
            posterPath = "https://image.tmdb.org/t/p/w500/tC78Pq7nxqp5Fm2gqHjP4o5F3jO.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/96K4eT5j9hP5Q7m3l7x0c1v1nO.jpg",
            mediaType = MediaType.ANIME,
            rating = 9.0,
            releaseYear = "2006",
            genres = listOf("Mystery", "Psychological", "Supernatural", "Animation"),
            totalSeasons = 1,
            totalEpisodes = 37
        )
    )

    // -------------------------------------------------------------
    // MOVIES
    // -------------------------------------------------------------
    val moviesList = listOf(
        MediaItem(
            id = "movie_oppenheimer",
            tmdbId = 872585L,
            title = "Oppenheimer",
            originalTitle = "Oppenheimer",
            overview = "The story of J. Robert Oppenheimer's role in the development of the atomic bomb during World War II and the dramatic political fallout that followed.",
            posterPath = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/fm6K9v6I7vdFGw6OZT29OGHQvpq.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.9,
            releaseYear = "2023",
            genres = listOf("Drama", "History", "Biography"),
            runtimeMinutes = 180,
            trailerUrl = "https://www.youtube.com/watch?v=uYPbbksJxIg"
        ),
        MediaItem(
            id = "movie_dune_two",
            tmdbId = 693134L,
            title = "Dune: Part Two",
            originalTitle = "Dune: Part Two",
            overview = "Follow the mythic journey of Paul Atreides as he unites with Chani and the Fremen while on a path of revenge against the conspirators who destroyed his family.",
            posterPath = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/xOMo8BRK7PfcJv9JCnx7s520QIq.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.8,
            releaseYear = "2024",
            genres = listOf("Sci-Fi", "Adventure", "Action"),
            runtimeMinutes = 166,
            trailerUrl = "https://www.youtube.com/watch?v=Way9Dexny3w"
        ),
        MediaItem(
            id = "movie_interstellar",
            tmdbId = 157336L,
            title = "Interstellar",
            originalTitle = "Interstellar",
            overview = "The adventures of a group of explorers who make use of a newly discovered wormhole to surpass the limitations on human space travel and conquer the vast distances involved in an interstellar voyage.",
            posterPath = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/xJHokMbljvjADYdit5fK5VQsXEG.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.7,
            releaseYear = "2014",
            genres = listOf("Sci-Fi", "Drama", "Adventure"),
            runtimeMinutes = 169
        ),
        MediaItem(
            id = "movie_inception",
            tmdbId = 27205L,
            title = "Inception",
            originalTitle = "Inception",
            overview = "Cobb, a skilled thief who commits corporate espionage by infiltrating the subconscious of his targets, is offered a chance to regain his old life as payment for a task considered to be impossible.",
            posterPath = "https://image.tmdb.org/t/p/w500/oYuLEt3zVCKq57qu2F8dT7NIa6f.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/s3TBrRGB1iav7gFOCNx3H31MoES.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.8,
            releaseYear = "2010",
            genres = listOf("Action", "Sci-Fi", "Thriller"),
            runtimeMinutes = 148
        ),
        MediaItem(
            id = "movie_jawan",
            tmdbId = 872906L,
            title = "Jawan",
            originalTitle = "जवान",
            overview = "An emotional journey of a prison warden who is set on a mission to right the wrongs in society against corrupt politicians and billionaires.",
            posterPath = "https://image.tmdb.org/t/p/w500/jNQWv2H5kL8nB0Zg1p7r8b9o7K.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/4F2b20D2yOQeY6k3nN9p8g8t6R.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.4,
            releaseYear = "2023",
            genres = listOf("Action", "Thriller", "Bollywood"),
            runtimeMinutes = 169
        ),
        MediaItem(
            id = "movie_rrr",
            tmdbId = 579974L,
            title = "RRR",
            originalTitle = "రౌద్రం రణం రుధిరం",
            overview = "A fearless revolutionary and an officer in the British force, who once shared a deep bond, decide to join forces and chart out an inspiring path of freedom against the despotic rulers.",
            posterPath = "https://image.tmdb.org/t/p/w500/wE0noFUqzd2yE2wE0gY6zR0wW3.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/22z44LPfl4IR7t4h4m19eK1Zl9n.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.6,
            releaseYear = "2022",
            genres = listOf("Action", "Drama", "Regional"),
            runtimeMinutes = 187
        ),
        MediaItem(
            id = "movie_spider_verse",
            tmdbId = 569094L,
            title = "Spider-Man: Across the Spider-Verse",
            originalTitle = "Spider-Man: Across the Spider-Verse",
            overview = "Miles Morales catapults across the Multiverse, where he encounters a team of Spider-People charged with protecting its very existence.",
            posterPath = "https://image.tmdb.org/t/p/w500/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/4HodYYKEIsGOdinkGi2Ucz6X9i0.jpg",
            mediaType = MediaType.MOVIE,
            rating = 8.8,
            releaseYear = "2023",
            genres = listOf("Animation", "Action", "Sci-Fi"),
            runtimeMinutes = 140
        )
    )

    // -------------------------------------------------------------
    // WEB SERIES
    // -------------------------------------------------------------
    val seriesList = listOf(
        MediaItem(
            id = "tv_stranger_things",
            tmdbId = 66732L,
            title = "Stranger Things",
            originalTitle = "Stranger Things",
            overview = "When a young boy vanishes, a small town uncovers a mystery involving secret experiments, terrifying supernatural forces and one strange little girl.",
            posterPath = "https://image.tmdb.org/t/p/w500/49WJfeN0moxb9IPfGn8AIqMGskD.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/56v2KjBlU4XaOv9rVYEQypROD7P.jpg",
            mediaType = MediaType.TV,
            rating = 8.8,
            releaseYear = "2016",
            genres = listOf("Sci-Fi", "Drama", "Mystery", "Supernatural"),
            totalSeasons = 4,
            totalEpisodes = 34,
            trailerUrl = "https://www.youtube.com/watch?v=b9EkMc79ZSU"
        ),
        MediaItem(
            id = "tv_breaking_bad",
            tmdbId = 1396L,
            title = "Breaking Bad",
            originalTitle = "Breaking Bad",
            overview = "A chemistry teacher diagnosed with inoperable lung cancer turns to manufacturing and selling methamphetamine with a former student in order to secure his family's future.",
            posterPath = "https://image.tmdb.org/t/p/w500/ggFHVNu6YYI5L9pCfOacjizRGt.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/tsRy63Mu5cu8etL1X7ZLyf7UP1M.jpg",
            mediaType = MediaType.TV,
            rating = 9.5,
            releaseYear = "2008",
            genres = listOf("Drama", "Crime", "Thriller"),
            totalSeasons = 5,
            totalEpisodes = 62
        ),
        MediaItem(
            id = "tv_money_heist",
            tmdbId = 71446L,
            title = "Money Heist",
            originalTitle = "La Casa de Papel",
            overview = "To carry out the biggest heist in history, a mysterious man called The Professor recruits a band of eight robbers who have a single characteristic: none of them has anything to lose.",
            posterPath = "https://image.tmdb.org/t/p/w500/reEMsq1JoAnq9z5mEs59pHdWF9V.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/gFZri2YbFUzgQgnYn09VDGzgxt2.jpg",
            mediaType = MediaType.TV,
            rating = 8.7,
            releaseYear = "2017",
            genres = listOf("Crime", "Action", "Drama", "Thriller"),
            totalSeasons = 5,
            totalEpisodes = 41
        ),
        MediaItem(
            id = "tv_mirzapur",
            tmdbId = 84087L,
            title = "Mirzapur",
            originalTitle = "मिर्ज़ापुर",
            overview = "The iron-fisted Akhandanand Tripathi is a millionaire carpet exporter and the mafia don of Mirzapur. His son Munna is an unworthy, power-hungry heir who will stop at nothing to inherit his father's legacy.",
            posterPath = "https://image.tmdb.org/t/p/w500/4g76xW1V5s0Vq7F1b5R9Y9k3nB.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/m9f3k6J0nB5r1t8Q4s7L6v3Y5mG.jpg",
            mediaType = MediaType.TV,
            rating = 8.6,
            releaseYear = "2018",
            genres = listOf("Crime", "Action", "Drama", "Indian"),
            totalSeasons = 3,
            totalEpisodes = 29
        ),
        MediaItem(
            id = "tv_the_boys",
            tmdbId = 76479L,
            title = "The Boys",
            originalTitle = "The Boys",
            overview = "A fun and irreverent take on what happens when superheroes—who are as popular as celebrities, as influential as politicians, and as revered as gods—abuse their superpowers rather than use them for good.",
            posterPath = "https://image.tmdb.org/t/p/w500/2Zm8eaL420udRFN7mnqGHq3iNc8.jpg",
            backdropPath = "https://image.tmdb.org/t/p/w1280/n6bUvigpRFqSwmPp1m2YADZRGFs.jpg",
            mediaType = MediaType.TV,
            rating = 8.8,
            releaseYear = "2019",
            genres = listOf("Action", "Sci-Fi", "Comedy", "Dark Comedy"),
            totalSeasons = 4,
            totalEpisodes = 32
        )
    )

    fun getAllItems(): List<MediaItem> = (animeList + moviesList + seriesList).distinctBy { it.id }

    fun getHomeCategorySections(): List<Pair<String, List<MediaItem>>> {
        val sections = mutableListOf<Pair<String, List<MediaItem>>>()
        sections.add("Trending Movies" to moviesList)
        sections.add("Popular Web Series" to seriesList)
        sections.add("Trending Anime" to animeList)
        sections.add("Action Blockbusters" to (moviesList + seriesList + animeList).filter { it.genres.any { g -> g.contains("Action", ignoreCase = true) } })
        sections.add("Sci-Fi & Supernatural" to (moviesList + seriesList + animeList).filter { it.genres.any { g -> g.contains("Sci-Fi", ignoreCase = true) || g.contains("Supernatural", ignoreCase = true) } })
        sections.add("Top Rated Picks" to getAllItems().sortedByDescending { it.rating })
        return sections
    }

    fun getAnimeCategorySections(): List<Pair<String, List<MediaItem>>> {
        val sections = mutableListOf<Pair<String, List<MediaItem>>>()
        sections.add("Trending Anime" to animeList)
        sections.add("Action & Shonen Anime" to animeList.filter { it.genres.any { g -> g.contains("Action", ignoreCase = true) || g.contains("Adventure", ignoreCase = true) } })
        sections.add("Supernatural & Dark Fantasy" to animeList.filter { it.genres.any { g -> g.contains("Supernatural", ignoreCase = true) || g.contains("Fantasy", ignoreCase = true) } })
        sections.add("Top Rated Anime" to animeList.sortedByDescending { it.rating })
        sections.add("Latest Anime Releases" to animeList.sortedByDescending { it.releaseYear })
        return sections
    }

    fun getSeriesCategorySections(): List<Pair<String, List<MediaItem>>> {
        val sections = mutableListOf<Pair<String, List<MediaItem>>>()
        sections.add("Trending Web Series" to seriesList)
        sections.add("Crime & Thriller Series" to seriesList.filter { it.genres.any { g -> g.contains("Crime", ignoreCase = true) || g.contains("Thriller", ignoreCase = true) } })
        sections.add("Sci-Fi & Drama" to seriesList.filter { it.genres.any { g -> g.contains("Sci-Fi", ignoreCase = true) || g.contains("Drama", ignoreCase = true) } })
        sections.add("Top Rated Shows" to seriesList.sortedByDescending { it.rating })
        return sections
    }

    fun getHeroBanners(count: Int = 6): List<MediaItem> {
        return listOf(
            animeList[0], // Demon Slayer
            moviesList[0], // Oppenheimer
            seriesList[0], // Stranger Things
            animeList[1], // Jujutsu Kaisen
            moviesList[1], // Dune 2
            seriesList[1]  // Breaking Bad
        ).take(count)
    }

    fun getAnimeHeroBanners(count: Int = 5): List<MediaItem> {
        return animeList.take(count)
    }

    fun getSeriesHeroBanners(count: Int = 5): List<MediaItem> {
        return seriesList.take(count)
    }

    fun getMediaItemById(id: String): MediaItem? {
        val clean = id.removePrefix("movie_").removePrefix("tv_").removePrefix("anime_").lowercase()
        val all = getAllItems()
        val exact = all.firstOrNull { it.id.equals(id, ignoreCase = true) }
        if (exact != null) return exact

        val partial = all.firstOrNull { 
            it.id.lowercase().contains(clean) || 
            it.title.lowercase().replace(" ", "").contains(clean.replace("_", "").replace("-", "")) ||
            clean.contains(it.title.lowercase().replace(" ", ""))
        }
        if (partial != null) return partial

        val isAnime = id.startsWith("anime") || clean.contains("anime")
        val isTv = id.startsWith("tv") || clean.contains("series") || clean.contains("tv")
        val pool = if (isAnime) animeList else if (isTv) seriesList else moviesList
        val template = pool[Math.abs(id.hashCode()) % pool.size]
        val formattedTitle = clean.replace("_", " ").replace("-", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        return template.copy(
            id = id,
            title = if (formattedTitle.length > 2) formattedTitle else template.title
        )
    }

    fun getEpisodes(mediaId: String, seasonNumber: Int): List<Episode> {
        val item = getMediaItemById(mediaId)
        val title = item?.title ?: "Episode"
        val total = item?.totalEpisodes ?: 12
        val epCount = minOf(total, 12)
        return (1..epCount).map { epNum ->
            Episode(
                id = "${mediaId}_s${seasonNumber}_e$epNum",
                episodeNumber = epNum,
                seasonNumber = seasonNumber,
                title = "$title - Episode $epNum",
                overview = "Exciting continuation of $title in season $seasonNumber episode $epNum with thrilling twists and high stakes drama.",
                stillPath = item?.backdropPath ?: item?.posterPath,
                airDate = item?.releaseYear ?: "2024"
            )
        }
    }

    fun getCast(mediaId: String): List<CastMember> {
        return listOf(
            CastMember(1, "Lead Protagonist", "Main Character", "https://image.tmdb.org/t/p/w185/nRAcv1AUn284F49oX5tU1vD26XJ.jpg"),
            CastMember(2, "Co-Star", "Key Partner", "https://image.tmdb.org/t/p/w185/8hPsp3G4fL1lWj3zP0N1Z2p4X5A.jpg"),
            CastMember(3, "Special Appearance", "Iconic Mentor", "https://image.tmdb.org/t/p/w185/vOx62sA9JvR7m8pU4G8oW6z6G8c.jpg")
        )
    }

    fun getStreamInfo(mediaId: String, title: String, season: Int = 1, episode: Int = 1): StreamInfo {
        return StreamInfo(
            title = title,
            sources = sampleStreams,
            subtitles = emptyList(),
            mediaId = mediaId,
            season = season,
            episode = episode
        )
    }
}
