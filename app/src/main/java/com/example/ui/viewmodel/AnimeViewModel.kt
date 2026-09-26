package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.MediaRepository
import com.example.data.repository.ServerConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class AnimeUiState(
    val isLoading: Boolean = true,
    val selectedCategory: String = "All",
    val heroBannerItem: MediaItem? = null,
    val heroBannerItems: List<MediaItem> = emptyList(),
    val top10Anime: List<MediaItem> = emptyList(),
    val categorySections: List<AdminCategorySection> = emptyList(),
    val categories: List<String> = listOf("All"),
    val error: String? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<MediaItem> = emptyList()
)

class AnimeViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnimeUiState())
    val uiState: StateFlow<AnimeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            ServerConfig.currentServer.collect {
                loadAnimeData()
            }
        }

        viewModelScope.launch {
            repository.observeContentUpdates().debounce(400).collect {
                loadAnimeData(force = true)
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun loadAnimeData(force: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val hasData = _uiState.value.categorySections.isNotEmpty()
            if (!hasData) _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val rawSections = repository.getAnimeCategorySections()
                val sections = rawSections.map { (name, items) ->
                    AdminCategorySection(categoryName = name, items = items)
                }.filter { it.items.isNotEmpty() }

                val heroList = repository.getAnimeHeroBanners(5)
                val hero = heroList.firstOrNull() ?: sections.firstOrNull()?.items?.firstOrNull()

                val allItems = sections.flatMap { it.items }.distinctBy { it.id }
                val top10 = repository.getTopTen(MediaType.ANIME, allItems)

                val categoryNames = (listOf("All") + sections.map { it.categoryName }).distinct()

                if (sections.isEmpty() && hasData) {
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        heroBannerItem = hero,
                        heroBannerItems = if (heroList.isNotEmpty()) heroList else listOfNotNull(hero),
                        top10Anime = top10,
                        categorySections = sections,
                        categories = categoryNames,
                        selectedCategory = if (it.selectedCategory in categoryNames) it.selectedCategory else "All"
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Failed to load anime content"
                    )
                }
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
        searchJob?.cancel()

        if (newQuery.trim().isEmpty()) {
            _uiState.update {
                it.copy(isSearching = false, searchResults = emptyList())
            }
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(250)
            try {
                val results = repository.search(newQuery.trim())
                    .filter { it.mediaType == MediaType.ANIME }
                _uiState.update {
                    it.copy(isSearching = false, searchResults = results)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSearching = false, searchResults = emptyList())
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(searchQuery = "", isSearching = false, searchResults = emptyList())
        }
    }
}
