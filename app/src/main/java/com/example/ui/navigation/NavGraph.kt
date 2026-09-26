package com.example.ui.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.ui.components.LocalIsTv
import com.example.ui.components.LocalTvScreen
import com.example.ui.components.TvRailItem
import com.example.ui.components.TvSideRail
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.data.local.CineStreamDatabase
import com.example.data.model.MediaType
import com.example.data.auth.AuthManager
import com.example.data.repository.AppPrefs
import com.example.data.repository.DownloadRepository
import com.example.ui.components.rememberFirebaseUser
import com.example.ui.components.ServerSelectionDialog
import com.example.data.repository.MediaRepository
import com.example.data.repository.UserRepository
import com.example.ui.screens.*
import com.example.ui.viewmodel.*

object Destinations {
    const val HOME = "home"
    const val SERIES = "series"
    const val ANIME = "anime"
    const val DETAIL = "detail/{mediaId}/{mediaType}"
    const val SEARCH = "search"
    const val MY_LIST = "my_list"
    const val DOWNLOADS = "downloads"
    const val PROFILE = "profile"
    const val PLAYER = "player/{mediaId}/{mediaType}/{title}/{posterPath}/{backdropPath}/{tmdbId}/{season}/{episode}"
    const val PLAYER_VIDUKI = "player_viduki/{mediaId}/{mediaType}/{title}/{tmdbId}/{season}/{episode}"
    const val PLAYER_DIRECT = "player_direct/{mediaId}/{mediaType}/{title}/{tmdbId}/{season}/{episode}"
}

/** Screens that show the TV navigation rail (order = order on screen). */
private val tvRailItems = listOf(
    TvRailItem(Destinations.SEARCH, "Search", Icons.Default.Search),
    TvRailItem(Destinations.HOME, "Home", Icons.Default.Home),
    TvRailItem(Destinations.SERIES, "Series", Icons.Default.Tv),
    TvRailItem(Destinations.ANIME, "Anime", Icons.Default.MovieFilter),
    TvRailItem(Destinations.MY_LIST, "My List", Icons.Default.Bookmark),
    TvRailItem(Destinations.DOWNLOADS, "Downloads", Icons.Default.Download),
    TvRailItem(Destinations.PROFILE, "Profile", Icons.Default.Person)
)

@Composable
fun AppNavGraph(
    navController: NavHostController,
    mediaRepository: MediaRepository
) {
    val context = LocalContext.current
    val database = CineStreamDatabase.getDatabase(context)
    val downloadDao = database.downloadDao()
    val userProfileDao = database.userProfileDao()

    val prefs = remember { AppPrefs.get(context) }
    val authManager = remember { AuthManager(context) }
    val downloadRepository = remember { DownloadRepository(context, downloadDao, mediaRepository.okHttpClient) }
    val userRepository = remember {
        UserRepository(database.watchItemDao(), mediaRepository.firebaseRepository, prefs)
    }

    // Real account session: whenever the Firebase user changes (guest -> Google, sign out ...)
    // the cloud data (My List, continue watching, watch time) is synced with this phone.
    val authUser = rememberFirebaseUser()
    LaunchedEffect(authUser?.uid, authUser?.isAnonymous) {
        userRepository.startSession(authUser)
    }
    val scope = rememberCoroutineScope()

    // ViewModels must survive recomposition (otherwise every recomposition re-creates
    // them and restarts all Firebase listeners).
    val homeViewModel = remember { HomeViewModel(mediaRepository) }
    val seriesViewModel = remember { SeriesViewModel(mediaRepository) }
    val animeViewModel = remember { AnimeViewModel(mediaRepository) }
    val detailViewModel = remember { DetailViewModel(mediaRepository) }
    val searchViewModel = remember { SearchViewModel(mediaRepository) }
    val playerViewModel = remember { PlayerViewModel(mediaRepository, downloadDao, prefs) }
    val downloadsViewModel = remember { DownloadsViewModel(downloadRepository) }
    val profileViewModel = remember { ProfileViewModel(userRepository, mediaRepository, authManager, prefs) }

    var showFirstTimeServerDialog by remember { mutableStateOf(!prefs.hasChosenServer) }

    if (showFirstTimeServerDialog) {
        ServerSelectionDialog(
            prefs = prefs,
            isFirstLaunch = true,
            onDismiss = { showFirstTimeServerDialog = false },
            onServerSelected = {
                showFirstTimeServerDialog = false
                homeViewModel.loadHomeData(force = true)
                seriesViewModel.loadSeriesData(force = true)
                animeViewModel.loadAnimeData(force = true)
            }
        )
    }

    val isTv = LocalIsTv.current
    val navEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navEntry?.destination?.route
    val showRail = isTv && tvRailItems.any { it.route == currentRoute }

    Row(modifier = Modifier.fillMaxSize()) {
    if (showRail) {
        TvSideRail(
            items = tvRailItems,
            selectedRoute = currentRoute,
            onSelect = { item ->
                if (item.route != currentRoute) {
                    navController.navigate(item.route) {
                        popUpTo(Destinations.HOME) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            }
        )
    }
    CompositionLocalProvider(LocalTvScreen provides (currentRoute ?: "")) {
    NavHost(
        navController = navController,
        modifier = Modifier.weight(1f).fillMaxHeight(),
        startDestination = Destinations.HOME,
        // smooth screen changes: soft fade + small slide (bottom-bar tabs only fade)
        enterTransition = { fadeIn(tween(280)) + slideInHorizontally(tween(320)) { it / 10 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(280)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 10 } }
    ) {
        composable(Destinations.HOME) {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToDetail = { id, type ->
                    navController.navigate("detail/$id/${type.name}")
                },
                onNavigateToPlayer = { id, type, title, poster, backdrop, tmdbId, season, episode ->
                    val encodedTitle = Uri.encode(title)
                    val encodedPoster = Uri.encode(poster ?: "none")
                    val encodedBackdrop = Uri.encode(backdrop ?: "none")
                    val tmdbIdVal = tmdbId ?: 0L
                    navController.navigate("player/$id/${type.name}/$encodedTitle/$encodedPoster/$encodedBackdrop/$tmdbIdVal/$season/$episode")
                },
                onNavigateToSeries = {
                    navController.navigate(Destinations.SERIES) {
                        launchSingleTop = true
                    }
                },
                onNavigateToAnime = {
                    navController.navigate(Destinations.ANIME) {
                        launchSingleTop = true
                    }
                },
                onNavigateToSearch = { navController.navigate(Destinations.SEARCH) },
                onNavigateToMyList = { navController.navigate(Destinations.MY_LIST) },
                onNavigateToDownloads = {
                    navController.navigate(Destinations.DOWNLOADS) {
                        launchSingleTop = true
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(Destinations.PROFILE) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(Destinations.SERIES) {
            SeriesScreen(
                viewModel = seriesViewModel,
                onNavigateToHome = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.HOME) { inclusive = true }
                    }
                },
                onNavigateToAnime = {
                    navController.navigate(Destinations.ANIME) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDownloads = {
                    navController.navigate(Destinations.DOWNLOADS) {
                        launchSingleTop = true
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(Destinations.PROFILE) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDetail = { id, type ->
                    navController.navigate("detail/$id/${type.name}")
                },
                onNavigateToPlayer = { id, type, title, poster, backdrop, tmdbId, season, episode ->
                    val encodedTitle = Uri.encode(title)
                    val encodedPoster = Uri.encode(poster ?: "none")
                    val encodedBackdrop = Uri.encode(backdrop ?: "none")
                    val tmdbIdVal = tmdbId ?: 0L
                    navController.navigate("player/$id/${type.name}/$encodedTitle/$encodedPoster/$encodedBackdrop/$tmdbIdVal/$season/$episode")
                }
            )
        }

        composable(Destinations.ANIME) {
            AnimeScreen(
                viewModel = animeViewModel,
                onNavigateToHome = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.HOME) { inclusive = true }
                    }
                },
                onNavigateToSeries = {
                    navController.navigate(Destinations.SERIES) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDownloads = {
                    navController.navigate(Destinations.DOWNLOADS) {
                        launchSingleTop = true
                    }
                },
                onNavigateToProfile = {
                    navController.navigate(Destinations.PROFILE) {
                        launchSingleTop = true
                    }
                },
                onNavigateToDetail = { id, type ->
                    navController.navigate("detail/$id/${type.name}")
                },
                onNavigateToPlayer = { id, type, title, poster, backdrop, tmdbId, season, episode ->
                    val encodedTitle = Uri.encode(title)
                    val encodedPoster = Uri.encode(poster ?: "none")
                    val encodedBackdrop = Uri.encode(backdrop ?: "none")
                    val tmdbIdVal = tmdbId ?: 0L
                    navController.navigate("player/$id/${type.name}/$encodedTitle/$encodedPoster/$encodedBackdrop/$tmdbIdVal/$season/$episode")
                }
            )
        }

        composable(
            route = Destinations.DETAIL,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("mediaType") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
            val mediaTypeStr = backStackEntry.arguments?.getString("mediaType") ?: "MOVIE"
            val mediaType = try { MediaType.valueOf(mediaTypeStr) } catch (e: Exception) { MediaType.MOVIE }

            DetailScreen(
                mediaId = mediaId,
                mediaType = mediaType,
                viewModel = detailViewModel,
                onBackClick = { navController.popBackStack() },
                onPlayClick = { season, episode ->
                    val item = detailViewModel.uiState.value.mediaItem
                    if (item != null) {
                        val encodedTitle = Uri.encode(item.title)
                        val encodedPoster = Uri.encode(item.posterPath ?: "none")
                        val encodedBackdrop = Uri.encode(item.backdropPath ?: "none")
                        val tmdbIdVal = item.tmdbId ?: 0L
                        navController.navigate("player/${item.id}/${item.mediaType.name}/$encodedTitle/$encodedPoster/$encodedBackdrop/$tmdbIdVal/$season/$episode")
                    }
                },
                onStartDownload = { id, tmdbId, title, poster, backdrop, type, season, episode, epTitle ->
                    // The old code passed an empty URL, so every download instantly FAILED.
                    scope.launch {
                        val url = mediaRepository.resolveDownloadUrl(id, type, season, episode)
                        downloadsViewModel.startOrResumeDownload(
                            mediaId = id,
                            tmdbId = tmdbId,
                            title = title,
                            posterPath = poster,
                            backdropPath = backdrop,
                            mediaType = type,
                            season = season,
                            episode = episode,
                            episodeTitle = epTitle,
                            streamUrl = url
                        )
                    }
                },
                onNavigateToDetail = { id, type ->
                    navController.navigate("detail/$id/${type.name}")
                },
                onWatchOnlineClick = { season, episode ->
                    val item = detailViewModel.uiState.value.mediaItem
                    if (item != null) {
                        val encodedTitle = Uri.encode(item.title)
                        val tmdbIdVal = item.tmdbId ?: 0L
                        navController.navigate("player_viduki/${item.id}/${item.mediaType.name}/$encodedTitle/$tmdbIdVal/$season/$episode")
                    }
                }
            )
        }

        composable(Destinations.SEARCH) {
            SearchScreen(
                viewModel = searchViewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToDetail = { id, type ->
                    navController.navigate("detail/$id/${type.name}")
                }
            )
        }

        composable(Destinations.MY_LIST) {
            MyListScreen(
                profileViewModel = profileViewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToDetail = { id, type ->
                    navController.navigate("detail/$id/${type.name}")
                },
                onNavigateToPlayer = { id, type, title, poster, backdrop, tmdbId, season, episode ->
                    val encodedTitle = Uri.encode(title)
                    val encodedPoster = Uri.encode(poster ?: "none")
                    val encodedBackdrop = Uri.encode(backdrop ?: "none")
                    val tmdbIdVal = tmdbId ?: 0L
                    navController.navigate("player/$id/${type.name}/$encodedTitle/$encodedPoster/$encodedBackdrop/$tmdbIdVal/$season/$episode")
                }
            )
        }

        composable(Destinations.DOWNLOADS) {
            DownloadsScreen(
                viewModel = downloadsViewModel,
                onBackClick = { navController.popBackStack() },
                onPlayOffline = { id, type, title, poster, backdrop, tmdbId, season, episode ->
                    val encodedTitle = Uri.encode(title)
                    val encodedPoster = Uri.encode(poster ?: "none")
                    val encodedBackdrop = Uri.encode(backdrop ?: "none")
                    val tmdbIdVal = tmdbId ?: 0L
                    navController.navigate("player/$id/${type.name}/$encodedTitle/$encodedPoster/$encodedBackdrop/$tmdbIdVal/$season/$episode")
                }
            )
        }

        composable(Destinations.PROFILE) {
            ProfileScreen(
                viewModel = profileViewModel,
                onBackClick = { navController.popBackStack() },
                onNavigateToMyList = { navController.navigate(Destinations.MY_LIST) },
                onNavigateToDownloads = { navController.navigate(Destinations.DOWNLOADS) },
                onNavigateToDetail = { id, type -> navController.navigate("detail/$id/${type.name}") }
            )
        }

        composable(
            route = Destinations.PLAYER,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("posterPath") { type = NavType.StringType },
                navArgument("backdropPath") { type = NavType.StringType },
                navArgument("tmdbId") { type = NavType.LongType },
                navArgument("season") { type = NavType.IntType },
                navArgument("episode") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
            val mediaTypeStr = backStackEntry.arguments?.getString("mediaType") ?: "MOVIE"
            val mediaType = try { MediaType.valueOf(mediaTypeStr) } catch (e: Exception) { MediaType.MOVIE }
            val title = Uri.decode(backStackEntry.arguments?.getString("title") ?: "")
            val rawPoster = Uri.decode(backStackEntry.arguments?.getString("posterPath") ?: "")
            val posterPath = if (rawPoster == "none") null else rawPoster
            val rawBackdrop = Uri.decode(backStackEntry.arguments?.getString("backdropPath") ?: "")
            val backdropPath = if (rawBackdrop == "none") null else rawBackdrop
            val tmdbIdVal = backStackEntry.arguments?.getLong("tmdbId")
            val tmdbId = if (tmdbIdVal == 0L) null else tmdbIdVal
            val season = backStackEntry.arguments?.getInt("season") ?: 1
            val episode = backStackEntry.arguments?.getInt("episode") ?: 1

            PlayerScreen(
                mediaId = mediaId,
                mediaType = mediaType,
                title = title,
                posterPath = posterPath,
                backdropPath = backdropPath,
                tmdbId = tmdbId,
                season = season,
                episode = episode,
                viewModel = playerViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable(
            route = Destinations.PLAYER_VIDUKI,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("tmdbId") { type = NavType.LongType },
                navArgument("season") { type = NavType.IntType },
                navArgument("episode") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
            val mediaTypeStr = backStackEntry.arguments?.getString("mediaType") ?: "MOVIE"
            val mediaType = try { MediaType.valueOf(mediaTypeStr) } catch (e: Exception) { MediaType.MOVIE }
            val title = Uri.decode(backStackEntry.arguments?.getString("title") ?: "")
            val tmdbIdVal = backStackEntry.arguments?.getLong("tmdbId") ?: 0L
            // Viduki takes a TMDB or IMDb id, used as-is — prefer the real tmdbId, fall back to mediaId.
            val vidukiId = if (tmdbIdVal != 0L) tmdbIdVal.toString() else mediaId
            val season = backStackEntry.arguments?.getInt("season") ?: 1
            val episode = backStackEntry.arguments?.getInt("episode") ?: 1

            VidukiPlayerScreen(
                title = title,
                mediaType = mediaType,
                mediaId = vidukiId,
                season = season,
                episode = episode,
                onBackClick = { navController.popBackStack() },
                onDirectPlayReady = { source ->
                    playerViewModel.initPlayerWithExternalSource(
                        mediaId = vidukiId,
                        mediaType = mediaType,
                        title = title,
                        posterPath = null,
                        backdropPath = null,
                        tmdbId = if (tmdbIdVal != 0L) tmdbIdVal else null,
                        season = season,
                        episode = episode,
                        source = source
                    )
                    val encodedTitle = Uri.encode(title)
                    navController.navigate("player_direct/$vidukiId/${mediaType.name}/$encodedTitle/$tmdbIdVal/$season/$episode") {
                        popUpTo(Destinations.PLAYER_VIDUKI) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Destinations.PLAYER_DIRECT,
            arguments = listOf(
                navArgument("mediaId") { type = NavType.StringType },
                navArgument("mediaType") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
                navArgument("tmdbId") { type = NavType.LongType },
                navArgument("season") { type = NavType.IntType },
                navArgument("episode") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val mediaId = backStackEntry.arguments?.getString("mediaId") ?: ""
            val mediaTypeStr = backStackEntry.arguments?.getString("mediaType") ?: "MOVIE"
            val mediaType = try { MediaType.valueOf(mediaTypeStr) } catch (e: Exception) { MediaType.MOVIE }
            val title = Uri.decode(backStackEntry.arguments?.getString("title") ?: "")
            val tmdbIdVal = backStackEntry.arguments?.getLong("tmdbId") ?: 0L
            val season = backStackEntry.arguments?.getInt("season") ?: 1
            val episode = backStackEntry.arguments?.getInt("episode") ?: 1

            // skipAutoInit = true: the source was already pushed into playerViewModel by
            // Viduki's extractor (onDirectPlayReady above) — this screen just renders it.
            PlayerScreen(
                mediaId = mediaId,
                mediaType = mediaType,
                title = title,
                posterPath = null,
                backdropPath = null,
                tmdbId = if (tmdbIdVal != 0L) tmdbIdVal else null,
                season = season,
                episode = episode,
                viewModel = playerViewModel,
                onBackClick = { navController.popBackStack() },
                skipAutoInit = true
            )
        }
    }
    }
    }
}
