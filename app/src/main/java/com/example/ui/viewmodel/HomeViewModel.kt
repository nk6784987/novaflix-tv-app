package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.WatchItemEntity
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.MediaRepository
import com.example.data.repository.ServerConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

data class AdminCategorySection(
    val categoryName: String,
    val items: List<MediaItem>
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val selectedCategory: String = "All",
    val heroBannerItem: MediaItem? = null,
    val heroBannerItems: List<MediaItem> = emptyList(),
    val top10Items: List<MediaItem> = emptyList(),
    val categorySections: List<AdminCategorySection> = emptyList(),
    val categories: List<String> = listOf("All"),
    val error: String? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<MediaItem> = emptyList(),
    val searchFilter: MediaType? = null
)

class HomeViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private var allRawSearchResults: List<MediaItem> = emptyList()

    val continueWatching: StateFlow<List<WatchItemEntity>> = repository.continueWatchingList
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            ServerConfig.currentServer.collect {
                loadHomeData()
            }
        }

        // Real-time listener: automatically updates when admin uploads new content or categories in Firebase!
        viewModelScope.launch {
            repository.observeContentUpdates().debounce(400).collect {
                loadHomeData(force = true)
            }
        }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun loadHomeData(force: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // Only show the full-screen spinner the very first time. Later refreshes
            // (admin uploaded something, screen re-opened) happen silently in the background.
            val hasData = _uiState.value.categorySections.isNotEmpty()
            if (!hasData) _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val rawSections = repository.getAdminCategorySections()
                val sections = rawSections.map { (name, items) ->
                    AdminCategorySection(categoryName = name, items = items)
                }.filter { it.items.isNotEmpty() }

                val heroList = repository.getAdminFeaturedHeroBanners(6)
                val hero = heroList.firstOrNull() ?: sections.firstOrNull()?.items?.firstOrNull()

                val allItems = sections.flatMap { it.items }.distinctBy { it.id }
                val top10 = repository.getTopTen(MediaType.MOVIE, allItems)

                val configuredCategories = repository.getCategories()
                val categoryNames = (listOf("All") + configuredCategories + sections.map { it.categoryName }).distinct()

                if (sections.isEmpty() && hasData) {
                    // transient failure - keep what is already on screen
                    _uiState.update { it.copy(isLoading = false) }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = if (sections.isEmpty()) "No content found. Check your internet connection and try again." else null,
                        heroBannerItem = hero,
                        heroBannerItems = if (heroList.isNotEmpty()) heroList else listOfNotNull(hero),
                        top10Items = top10,
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
                        error = e.localizedMessage ?: "Failed to load content"
                    )
                }
            }
        }
    }

    fun deleteContinueWatching(id: String) {
        viewModelScope.launch {
            repository.deleteContinueWatchingItem(id)
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        _uiState.update { it.copy(searchQuery = newQuery) }
        searchJob?.cancel()

        if (newQuery.trim().isEmpty()) {
            allRawSearchResults = emptyList()
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = emptyList(),
                    searchFilter = null
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            delay(250) // 250ms debounce for real-time responsiveness
            try {
                val results = repository.search(newQuery.trim())
                allRawSearchResults = results
                val filtered = filterSearchResults(results, _uiState.value.searchFilter)
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = filtered
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSearching = false, searchResults = emptyList())
                }
            }
        }
    }

    fun setSearchFilter(mediaType: MediaType?) {
        val currentFilter = _uiState.value.searchFilter
        val nextFilter = if (currentFilter == mediaType) null else mediaType
        _uiState.update {
            it.copy(
                searchFilter = nextFilter,
                searchResults = filterSearchResults(allRawSearchResults, nextFilter)
            )
        }
    }

    private fun filterSearchResults(items: List<MediaItem>, filter: MediaType?): List<MediaItem> {
        if (filter == null) return items
        return items.filter { it.mediaType == filter }
    }

    fun clearSearch() {
        searchJob?.cancel()
        allRawSearchResults = emptyList()
        _uiState.update {
            it.copy(
                searchQuery = "",
                isSearching = false,
                searchResults = emptyList(),
                searchFilter = null
            )
        }
    }
}
