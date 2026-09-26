package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.TmdbSeasonInfoDto
import com.example.data.model.CastMember
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.MediaRepository
import com.example.data.repository.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val isLoading: Boolean = true,
    val mediaItem: MediaItem? = null,
    val cast: List<CastMember> = emptyList(),
    val seasons: List<TmdbSeasonInfoDto> = emptyList(),
    val selectedSeason: Int = 1,
    val episodes: List<Episode> = emptyList(),
    val similarItems: List<MediaItem> = emptyList(),
    val isInMyList: Boolean = false,
    val error: String? = null
)

class DetailViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadDetails(mediaId: String, mediaType: MediaType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val isSaved = repository.isMediaInMyList(mediaId)

                if (ServerConfig.isMovieBox() || mediaId.startsWith("mb_")) {
                    if (mediaType == MediaType.TV || mediaType == MediaType.ANIME) {
                        val (item, cast, seasons) = repository.getTvDetails(mediaId)
                        val defaultSeason = seasons.firstOrNull()?.seasonNumber ?: 1
                        val epList = repository.getSeasonEpisodes(mediaId, defaultSeason)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                mediaItem = item,
                                cast = cast,
                                seasons = seasons,
                                selectedSeason = defaultSeason,
                                episodes = epList,
                                isInMyList = isSaved
                            )
                        }
                    } else {
                        val (item, cast) = repository.getMovieDetails(mediaId)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                mediaItem = item,
                                cast = cast,
                                seasons = emptyList(),
                                episodes = emptyList(),
                                isInMyList = isSaved
                            )
                        }
                    }
                    return@launch
                }

                // mediaId is the real identifier (e.g. "movie_-NxAbCd123") - the repository
                // strips the prefix itself and looks it up as a Firebase key.
                when (mediaType) {
                    MediaType.MOVIE -> {
                        val (item, cast) = repository.getMovieDetails(mediaId)
                        // show the page immediately, fill "More Like This" right after
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                mediaItem = item,
                                cast = cast,
                                seasons = emptyList(),
                                episodes = emptyList(),
                                similarItems = emptyList(),
                                isInMyList = isSaved
                            )
                        }
                        val similar = repository.getSimilarItems(item, cast)
                        _uiState.update { if (it.mediaItem?.id == item.id) it.copy(similarItems = similar) else it }
                    }
                    MediaType.TV, MediaType.ANIME -> {
                        val (item, cast, seasons) =
                            if (mediaType == MediaType.TV) repository.getTvDetails(mediaId)
                            else repository.getAnimeFullDetails(mediaId)
                        val defaultSeason = seasons.firstOrNull()?.seasonNumber ?: 1
                        val epList = repository.getSeasonEpisodes(mediaId, defaultSeason)
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                mediaItem = item,
                                cast = cast,
                                seasons = seasons,
                                selectedSeason = defaultSeason,
                                episodes = epList,
                                similarItems = emptyList(),
                                isInMyList = isSaved
                            )
                        }
                        val similar = repository.getSimilarItems(item, cast)
                        _uiState.update { if (it.mediaItem?.id == item.id) it.copy(similarItems = similar) else it }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Failed to load details"
                    )
                }
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        val currentItem = _uiState.value.mediaItem ?: return
        if (currentItem.mediaType == MediaType.MOVIE) return

        viewModelScope.launch {
            val epList = repository.getSeasonEpisodes(currentItem.id, seasonNumber)
            _uiState.update {
                it.copy(selectedSeason = seasonNumber, episodes = epList)
            }
        }
    }

    fun toggleMyList() {
        val currentItem = _uiState.value.mediaItem ?: return
        val newStatus = !_uiState.value.isInMyList
        viewModelScope.launch {
            repository.toggleMyList(currentItem, newStatus)
            _uiState.update { it.copy(isInMyList = newStatus) }
        }
    }
}
