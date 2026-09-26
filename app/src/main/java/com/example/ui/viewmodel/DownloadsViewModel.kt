package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.DownloadEntity
import com.example.data.model.MediaType
import com.example.data.repository.DownloadRepository
import com.example.data.repository.StorageInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadsUiState(
    val downloads: List<DownloadEntity> = emptyList(),
    val storageInfo: StorageInfo = StorageInfo(0L, 0L, 0L),
    val filterType: String = "ALL" // ALL, COMPLETED, DOWNLOADING
)

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val uiState: StateFlow<DownloadsUiState> = downloadRepository.allDownloads
        .map { list ->
            DownloadsUiState(
                downloads = list,
                storageInfo = downloadRepository.getStorageInfo()
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadsUiState()
        )

    fun startOrResumeDownload(
        mediaId: String,
        tmdbId: Long?,
        title: String,
        posterPath: String?,
        backdropPath: String?,
        mediaType: MediaType,
        season: Int = 1,
        episode: Int = 1,
        episodeTitle: String? = null,
        streamUrl: String
    ) {
        viewModelScope.launch {
            downloadRepository.startOrResumeDownload(
                mediaId = mediaId,
                tmdbId = tmdbId,
                title = title,
                posterPath = posterPath,
                backdropPath = backdropPath,
                mediaType = mediaType,
                season = season,
                episode = episode,
                episodeTitle = episodeTitle,
                streamUrl = streamUrl
            )
        }
    }

    fun pauseDownload(id: String) {
        downloadRepository.pauseDownload(id)
    }

    fun deleteDownload(id: String) {
        downloadRepository.deleteDownload(id)
    }
}
