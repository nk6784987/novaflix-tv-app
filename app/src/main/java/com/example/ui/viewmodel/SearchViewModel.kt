package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val selectedMediaType: MediaType? = null, // null = All
    val selectedGenre: String? = null,
    val selectedYear: String? = null,
    val selectedMinRating: Double? = null,
    val searchResults: List<MediaItem> = emptyList(),
    val filteredResults: List<MediaItem> = emptyList(),
    val availableGenres: List<String> = listOf("Action", "Adventure", "Animation", "Comedy", "Crime", "Drama", "Fantasy", "Horror", "Mystery", "Sci-Fi", "Thriller"),
    val availableYears: List<String> = listOf("2026", "2025", "2024", "2023", "2022", "2021", "2020", "2019", "2018"),
    val availableRatings: List<Double> = listOf(5.0, 6.0, 7.0, 8.0, 9.0)
)

class SearchViewModel(
    private val repository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChanged(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
        searchJob?.cancel()

        if (newQuery.trim().isEmpty()) {
            _uiState.update { it.copy(searchResults = emptyList(), filteredResults = emptyList(), isLoading = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(400) // 400ms Debounce
            _uiState.update { it.copy(isLoading = true) }
            try {
                val results = repository.search(newQuery)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        searchResults = results
                    )
                }
                applyFilters()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, searchResults = emptyList(), filteredResults = emptyList()) }
            }
        }
    }

    fun selectMediaType(mediaType: MediaType?) {
        _uiState.update { it.copy(selectedMediaType = mediaType) }
        applyFilters()
    }

    fun selectGenre(genre: String?) {
        _uiState.update { it.copy(selectedGenre = if (it.selectedGenre == genre) null else genre) }
        applyFilters()
    }

    fun selectYear(year: String?) {
        _uiState.update { it.copy(selectedYear = if (it.selectedYear == year) null else year) }
        applyFilters()
    }

    fun selectRating(rating: Double?) {
        _uiState.update { it.copy(selectedMinRating = if (it.selectedMinRating == rating) null else rating) }
        applyFilters()
    }

    private fun applyFilters() {
        val state = _uiState.value
        var list = state.searchResults

        if (state.selectedMediaType != null) {
            list = list.filter { it.mediaType == state.selectedMediaType }
        }

        if (state.selectedGenre != null) {
            list = list.filter { item ->
                item.genres.any { it.contains(state.selectedGenre, ignoreCase = true) }
            }
        }

        if (state.selectedYear != null) {
            list = list.filter { it.releaseYear == state.selectedYear }
        }

        if (state.selectedMinRating != null) {
            list = list.filter { it.rating >= state.selectedMinRating }
        }

        _uiState.update { it.copy(filteredResults = list) }
    }
}
