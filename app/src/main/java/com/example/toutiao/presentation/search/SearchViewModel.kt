package com.example.toutiao.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.domain.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<FeedCard>>(emptyList())
    val searchResults: StateFlow<List<FeedCard>> = _searchResults.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _hasError = MutableStateFlow<String?>(null)
    val hasError: StateFlow<String?> = _hasError.asStateFlow()

    fun search(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }

        _isLoading.value = true
        _hasError.value = null

        viewModelScope.launch {
            try {
                val results = newsRepository.searchNews(query)
                _searchResults.value = results
                _isLoading.value = false
            } catch (e: Exception) {
                Timber.e(e, "search failed")
                _hasError.value = "搜索失败，请稍后重试"
                _isLoading.value = false
            }
        }
    }

    fun clearResults() {
        _searchResults.value = emptyList()
        _hasError.value = null
    }
}
