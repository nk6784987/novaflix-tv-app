package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadDao
import com.example.data.model.Episode
import com.example.data.model.MediaType
import com.example.data.model.StreamInfo
import com.example.data.model.SubtitleTrack
import com.example.data.model.VideoStreamSource
import com.example.data.repository.AppPrefs
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class PlayerUiState(
    val isLoading: Boolean = true,
    val streamInfo: StreamInfo? = null,
    val activeSource: VideoStreamSource? = null,
    val activeSubtitle: SubtitleTrack? = null,
    val autoPlayNext: Boolean = true,
    val mediaTitle: String = "",
    val mediaType: MediaType = MediaType.MOVIE,
    val mediaId: String = "",
    val tmdbId: Long? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val startPositionMs: Long = 0L,
    val isLocalFile: Boolean = false,
    val isControlsVisible: Boolean = true,
    val isLocked: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val resizeMode: Int = 0, // 0: FIT, 3: ZOOM, 4: FILL, 1: FIXED_WIDTH
    val availableEpisodes: List<Episode> = emptyList(),
    val nextEpisode: Episode? = null,
    /** Real intro range from the admin panel (introStart / introEnd in seconds). Null = unknown, no fake button. */
    val introStartMs: Long? = null,
    val introEndMs: Long? = null,
    val seasons: List<Int> = emptyList(),
    val panelSeason: Int = 1,
    val panelEpisodes: List<Episode> = emptyList(),
    /** Fatal error before any source could be chosen ("no source uploaded"). */
    val error: String? = null,
    /** Error while playing (all sources failed). Player screen shows a retry card. */
    val playbackError: String? = null,
    /** Short toast-like info, e.g. "Source failed - trying 720p". */
    val notice: String? = null,
    /** Bumped on every manual retry so the same source is prepared again. */
    val playbackAttempt: Int = 0
)

class PlayerViewModel(
    private val repository: MediaRepository,
    private val downloadDao: DownloadDao? = null,
    private val prefs: AppPrefs? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Sources that already failed for the current episode/movie.
    private val failedUrls = mutableSetOf<String>()

    // Latest playback position (kept out of UI state so we do not recompose every second).
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L
    private var lastLocalSaveAt = 0L
    private var lastRemoteSaveAt = 0L

    // real watched-time accounting
    private var lastTickAt = 0L
    private var watchAccumMs = 0L

    private fun trackWatchTime(force: Boolean) {
        val now = System.currentTimeMillis()
        if (lastTickAt > 0L) {
            val d = now - lastTickAt
            if (d in 1..3000) watchAccumMs += d
        }
        lastTickAt = now
        if (watchAccumMs >= 30_000L || (force && watchAccumMs > 0L)) {
            repository.addWatchTime(watchAccumMs)
            watchAccumMs = 0L
        }
    }

    /**
     * Plays an already-resolved source directly (used by the Viduki extractor once it sniffs a
     * real .m3u8/.mp4 out of the embed page) — skips the normal admin/scraper resolution in
     * [repository.extractStream] entirely since we already have a playable URL in hand.
     */
    fun initPlayerWithExternalSource(
        mediaId: String,
        mediaType: MediaType,
        title: String,
        posterPath: String?,
        backdropPath: String?,
        tmdbId: Long?,
        season: Int,
        episode: Int,
        source: VideoStreamSource
    ) {
        failedUrls.clear()
        lastPositionMs = 0L
        lastDurationMs = 0L
        val streamInfo = StreamInfo(
            title = title,
            sources = listOf(source),
            subtitles = emptyList(),
            mediaId = mediaId,
            season = season,
            episode = episode
        )
        _uiState.update {
            it.copy(
                isLoading = false,
                streamInfo = streamInfo,
                activeSource = source,
                activeSubtitle = null,
                mediaId = mediaId,
                mediaType = mediaType,
                mediaTitle = title,
                posterPath = posterPath,
                backdropPath = backdropPath,
                tmdbId = tmdbId,
                seasonNumber = season,
                episodeNumber = episode,
                startPositionMs = 0L,
                isLocalFile = false,
                introStartMs = null,
                introEndMs = null,
                error = null,
                playbackError = null,
                notice = null
            )
        }
    }

    /** Picks the first source according to Profile -> Default quality. */
    private fun pickInitialSource(sources: List<VideoStreamSource>): VideoStreamSource? {
        if (sources.isEmpty()) return null
        fun score(q: String): Int {
            Regex("(\\d{3,4})").find(q)?.value?.toIntOrNull()?.let { return it }
            val t = q.lowercase()
            return when {
                "4k" in t || "uhd" in t -> 2160
                "fhd" in t || "full" in t -> 1080
                "hd" in t -> 720
                "sd" in t -> 480
                else -> 0
            }
        }
        return when (prefs?.preferredQuality ?: AppPrefs.QualityMode.ADMIN_DEFAULT) {
            AppPrefs.QualityMode.HIGHEST -> sources.maxByOrNull { score(it.quality) } ?: sources.first()
            AppPrefs.QualityMode.DATA_SAVER ->
                sources.filter { score(it.quality) > 0 }.minByOrNull { score(it.quality) } ?: sources.first()
            else -> sources.first()
        }
    }

    fun initPlayer(
        mediaId: String,
        mediaType: MediaType,
        title: String,
        posterPath: String?,
        backdropPath: String?,
        tmdbId: Long?,
        season: Int = 1,
        episode: Int = 1,
        requestedStartPositionMs: Long? = null
    ) {
        failedUrls.clear()
        lastPositionMs = 0L
        lastDurationMs = 0L
        lastLocalSaveAt = 0L
        lastRemoteSaveAt = 0L
        if (watchAccumMs > 0L) { repository.addWatchTime(watchAccumMs); watchAccumMs = 0L }
        lastTickAt = 0L
        val isNewTitle = _uiState.value.mediaId != mediaId

        _uiState.update {
            it.copy(
                autoPlayNext = if (isNewTitle) (prefs?.autoPlayNext ?: it.autoPlayNext) else it.autoPlayNext,
                playbackSpeed = if (isNewTitle) (prefs?.defaultSpeed ?: it.playbackSpeed) else it.playbackSpeed,
                introStartMs = null,
                introEndMs = null,
                isLoading = true,
                mediaId = mediaId,
                mediaType = mediaType,
                mediaTitle = title,
                posterPath = posterPath,
                backdropPath = backdropPath,
                tmdbId = tmdbId,
                seasonNumber = season,
                episodeNumber = episode,
                activeSource = null,
                streamInfo = null,
                nextEpisode = null,
                isLocked = false,
                isLocalFile = false,
                error = null,
                playbackError = null,
                notice = null
            )
        }

        viewModelScope.launch {
            try {
                // Episode list + "next episode" target (TV / Anime)
                if (mediaType != MediaType.MOVIE) {
                    launch {
                        try {
                            val eps = repository.getSeasonEpisodes(mediaId, season)
                            val next = eps.filter { it.episodeNumber > episode }.minByOrNull { it.episodeNumber }
                                ?: repository.getSeasonEpisodes(mediaId, season + 1).minByOrNull { it.episodeNumber }
                            val seasonList = repository.getSeasonNumbers(mediaId, mediaType)
                            _uiState.update {
                                it.copy(
                                    availableEpisodes = eps,
                                    nextEpisode = next,
                                    seasons = seasonList,
                                    panelSeason = season,
                                    panelEpisodes = eps
                                )
                            }
                        } catch (e: Exception) {
                            Log.w("PlayerViewModel", "Error fetching season episodes: ${e.message}")
                        }
                    }
                } else {
                    _uiState.update { it.copy(availableEpisodes = emptyList()) }
                }

                val itemKey = "${mediaType.name.lowercase()}_${mediaId}_s${season}_e$episode"
                val existingWatchItem = repository.getWatchItemById(itemKey) ?: repository.getWatchItemByMediaId(mediaId)
                val savedPos = existingWatchItem?.progressMillis ?: 0L
                val totalDur = existingWatchItem?.durationMillis ?: 0L
                val initialPos = requestedStartPositionMs
                    ?: if (totalDur > 0 && savedPos > (totalDur * 0.95)) 0L else savedPos

                // Offline copy first
                val downloadId = "dl_${mediaType.name.lowercase()}_${mediaId}_s${season}_e$episode"
                val localDownload = downloadDao?.getDownloadById(downloadId)
                if (localDownload != null && localDownload.status == "COMPLETED" && !localDownload.localFilePath.isNullOrEmpty()) {
                    val file = File(localDownload.localFilePath)
                    if (file.exists()) {
                        val localSource = VideoStreamSource(
                            quality = "Offline",
                            url = file.absolutePath,
                            isHls = false
                        )
                        val offlineStreamInfo = StreamInfo(
                            title = title,
                            sources = listOf(localSource),
                            subtitles = emptyList(),
                            mediaId = mediaId,
                            season = season,
                            episode = episode
                        )
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                streamInfo = offlineStreamInfo,
                                activeSource = localSource,
                                startPositionMs = initialPos,
                                isLocalFile = true,
                                error = null
                            )
                        }
                        return@launch
                    }
                }

                val streamInfo = repository.extractStream(
                    mediaId = mediaId,
                    mediaType = mediaType,
                    title = title,
                    season = season,
                    episode = episode
                )

                val extras = repository.getPlaybackExtras(mediaId, mediaType, season, episode)
                val fullStreamInfo =
                    if (extras.subtitles.isNotEmpty() && streamInfo.subtitles.isEmpty()) streamInfo.copy(subtitles = extras.subtitles)
                    else streamInfo
                val primarySource = pickInitialSource(streamInfo.sources)

                if (primarySource != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            introStartMs = extras.introStartSec?.let { s0 -> s0 * 1000L },
                            introEndMs = extras.introEndSec?.let { e0 -> e0 * 1000L },
                            streamInfo = fullStreamInfo,
                            activeSource = primarySource,
                            activeSubtitle = streamInfo.subtitles.firstOrNull(),
                            startPositionMs = initialPos,
                            isLocalFile = false,
                            error = null,
                            playbackError = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            streamInfo = streamInfo,
                            startPositionMs = initialPos,
                            error = "No streaming video sources available for this title"
                        )
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.d("StreamDebug", "initPlayer exception: ${e.message}", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load video stream"
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // UI toggles
    // ------------------------------------------------------------------ //

    fun setPlaybackSpeed(speed: Float) {
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    fun cycleResizeMode() {
        val nextMode = when (_uiState.value.resizeMode) {
            0 -> 4 // Fit -> Zoom (RESIZE_MODE_ZOOM = 4)
            4 -> 3 // Zoom -> Stretch (RESIZE_MODE_FILL = 3)
            else -> 0 // -> Fit
        }
        _uiState.update { it.copy(resizeMode = nextMode) }
    }

    fun toggleScreenLock() {
        _uiState.update { it.copy(isLocked = !it.isLocked, isControlsVisible = it.isLocked) }
    }

    fun unlockScreen() {
        _uiState.update { it.copy(isLocked = false, isControlsVisible = true) }
    }

    fun setControlsVisible(visible: Boolean) {
        if (_uiState.value.isLocked) return
        _uiState.update { it.copy(isControlsVisible = visible) }
    }

    fun toggleControlsVisibility() {
        if (_uiState.value.isLocked) return
        _uiState.update { it.copy(isControlsVisible = !it.isControlsVisible) }
    }

    fun toggleAutoPlayNext() {
        val v = !_uiState.value.autoPlayNext
        prefs?.autoPlayNext = v
        _uiState.update { it.copy(autoPlayNext = v) }
    }

    fun selectSubtitle(subtitle: SubtitleTrack?) {
        _uiState.update { it.copy(activeSubtitle = subtitle) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    // ------------------------------------------------------------------ //
    // Sources / errors
    // ------------------------------------------------------------------ //

    /** User picked another quality/source from the admin list: continue from the same position. */
    fun selectSource(source: VideoStreamSource) {
        failedUrls.remove(source.url)
        _uiState.update {
            it.copy(
                activeSource = source,
                startPositionMs = if (lastPositionMs > 1000L) lastPositionMs else it.startPositionMs,
                playbackError = null,
                notice = null,
                error = null
            )
        }
    }

    /**
     * Called by the player when the current source can not be played.
     * Automatically moves to the next admin source that has not failed yet;
     * only when every source failed the retry card is shown.
     */
    fun onPlaybackError(reason: String? = null) {
        val state = _uiState.value
        val current = state.activeSource
        Log.d("StreamDebug", "onPlaybackError: ${current?.url} reason=$reason")
        if (current != null) failedUrls.add(current.url)

        val next = if (state.isLocalFile) null
        else state.streamInfo?.sources?.firstOrNull { it.url !in failedUrls }

        if (next != null) {
            _uiState.update {
                it.copy(
                    activeSource = next,
                    startPositionMs = if (lastPositionMs > 1000L) lastPositionMs else it.startPositionMs,
                    notice = "Source unavailable, switching to ${next.quality}…",
                    playbackError = null,
                    isLoading = false
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    playbackError = reason ?: "Unable to play video (stream link expired or server unreachable)"
                )
            }
        }
    }

    /** "Retry" button: start again from the first source at the last position. */
    fun retryPlayback() {
        failedUrls.clear()
        val state = _uiState.value
        val first = state.streamInfo?.sources?.firstOrNull()
        if (first == null) {
            initPlayer(
                mediaId = state.mediaId,
                mediaType = state.mediaType,
                title = state.mediaTitle,
                posterPath = state.posterPath,
                backdropPath = state.backdropPath,
                tmdbId = state.tmdbId,
                season = state.seasonNumber,
                episode = state.episodeNumber
            )
            return
        }
        _uiState.update {
            it.copy(
                activeSource = first,
                startPositionMs = if (lastPositionMs > 1000L) lastPositionMs else it.startPositionMs,
                playbackError = null,
                notice = null,
                error = null,
                playbackAttempt = it.playbackAttempt + 1
            )
        }
    }

    // ------------------------------------------------------------------ //
    // Progress
    // ------------------------------------------------------------------ //

    /** Called every second by the player. Only remembers the position and saves it
     *  every few seconds (the old version wrote to Firebase EVERY second). */
    fun updateProgress(currentMs: Long, durationMs: Long, force: Boolean = false) {
        trackWatchTime(force)
        if (currentMs > 0L) lastPositionMs = currentMs
        if (durationMs > 0L) lastDurationMs = durationMs
        if (durationMs <= 0L || currentMs <= 1000L) return

        val now = System.currentTimeMillis()
        val saveLocal = force || now - lastLocalSaveAt >= 5_000L
        if (!saveLocal) return
        lastLocalSaveAt = now
        val syncRemote = force || now - lastRemoteSaveAt >= 30_000L
        if (syncRemote) lastRemoteSaveAt = now

        val state = _uiState.value
        if (state.mediaId.isBlank()) return
        viewModelScope.launch {
            try {
                repository.saveWatchProgress(
                    mediaId = state.mediaId,
                    tmdbId = state.tmdbId,
                    title = state.mediaTitle,
                    posterPath = state.posterPath,
                    backdropPath = state.backdropPath,
                    mediaType = state.mediaType,
                    season = state.seasonNumber,
                    episode = state.episodeNumber,
                    episodeTitle = if (state.mediaType != MediaType.MOVIE) "S${state.seasonNumber} E${state.episodeNumber}" else null,
                    progressMillis = currentMs,
                    durationMillis = durationMs,
                    syncRemote = syncRemote
                )
            } catch (e: Exception) {
                Log.w("PlayerViewModel", "saveWatchProgress failed: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------ //
    // Episodes
    // ------------------------------------------------------------------ //

    /** Episodes panel: user switched the season chip. */
    fun loadPanelSeason(season: Int) {
        val state = _uiState.value
        if (state.mediaType == MediaType.MOVIE) return
        _uiState.update { it.copy(panelSeason = season) }
        viewModelScope.launch {
            val eps = try { repository.getSeasonEpisodes(state.mediaId, season) } catch (e: Exception) { emptyList() }
            _uiState.update { if (it.panelSeason == season) it.copy(panelEpisodes = eps) else it }
        }
    }

    fun selectEpisode(seasonNumber: Int, episodeNumber: Int) {
        val state = _uiState.value
        initPlayer(
            mediaId = state.mediaId,
            mediaType = state.mediaType,
            title = state.mediaTitle,
            posterPath = state.posterPath,
            backdropPath = state.backdropPath,
            tmdbId = state.tmdbId,
            season = seasonNumber,
            episode = episodeNumber
        )
    }

    fun selectEpisode(episodeNumber: Int) {
        val state = _uiState.value
        initPlayer(
            mediaId = state.mediaId,
            mediaType = state.mediaType,
            title = state.mediaTitle,
            posterPath = state.posterPath,
            backdropPath = state.backdropPath,
            tmdbId = state.tmdbId,
            season = state.seasonNumber,
            episode = episodeNumber
        )
    }

    fun loadNextEpisode() {
        val state = _uiState.value
        val next = state.nextEpisode ?: return
        Log.d("StreamDebug", "loadNextEpisode: S${next.seasonNumber}E${next.episodeNumber}")
        initPlayer(
            mediaId = state.mediaId,
            mediaType = state.mediaType,
            title = state.mediaTitle,
            posterPath = state.posterPath,
            backdropPath = state.backdropPath,
            tmdbId = state.tmdbId,
            season = next.seasonNumber,
            episode = next.episodeNumber
        )
    }
}
