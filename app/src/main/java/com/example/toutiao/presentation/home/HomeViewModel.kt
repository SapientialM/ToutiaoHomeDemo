package com.example.toutiao.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.domain.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
) : ViewModel() {

    private val _currentTab = MutableStateFlow("recommend")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<FeedCard>>(emptyList())
    val searchResults: StateFlow<List<FeedCard>> = _searchResults.asStateFlow()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Success())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val feedPagingData: Flow<PagingData<FeedCard>> = _currentTab
        .flatMapLatest { tab ->
            Timber.d("feedPagingData — switching to tab=$tab")
            newsRepository.getFeedPagingData(tab)
        }
        .cachedIn(viewModelScope)

    init {
        Timber.d("HomeViewModel init")
    }

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.OnTabSelected -> switchTab(event.tab)
            is HomeUiEvent.OnRefresh -> {
                Timber.d("OnRefresh — will be handled by UI layer (lazyPagingItems.refresh())")
            }
            is HomeUiEvent.OnLoadMore -> {
                Timber.d("OnLoadMore — handled automatically by Paging3")
            }
            is HomeUiEvent.OnCardClick -> {
                Timber.d("Card clicked: ${event.cardId}")
            }
            is HomeUiEvent.OnRetry -> {
                Timber.d("OnRetry — will be handled by UI layer (lazyPagingItems.retry())")
            }
            is HomeUiEvent.OnSearchClicked -> {
                _uiState.update { (it as? HomeUiState.Success)?.copy(isSearching = true) ?: it }
            }
            is HomeUiEvent.OnSearchQueryChanged -> {
                _searchQuery.value = event.query
            }
            is HomeUiEvent.OnSearchSubmit -> {
                performSearch(_searchQuery.value)
            }
            is HomeUiEvent.OnSearchDismiss -> {
                _searchQuery.value = ""
                _searchResults.value = emptyList()
                _uiState.update { (it as? HomeUiState.Success)?.copy(isSearching = false) ?: it }
            }
        }
    }

    private fun switchTab(tab: String) {
        Timber.d("switchTab — tab=$tab")
        _currentTab.value = tab
        _uiState.update { HomeUiState.Success(currentTab = tab) }
    }

    private fun performSearch(query: String) {
        if (query.isBlank()) return
        Timber.d("performSearch — query=$query")
        viewModelScope.launch {
            try {
                val results = newsRepository.searchNews(query)
                _searchResults.value = results
            } catch (e: Exception) {
                Timber.e(e, "performSearch failed")
            }
        }
    }
}
