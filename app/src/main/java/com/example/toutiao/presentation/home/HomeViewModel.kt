package com.example.toutiao.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toutiao.domain.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var currentPage = 0

    init {
        Timber.d("HomeViewModel init — starting loadFeed for recommend")
        loadFeed("recommend", 0)
    }

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            is HomeUiEvent.OnTabSelected -> switchTab(event.tab)
            is HomeUiEvent.OnRefresh -> refresh()
            is HomeUiEvent.OnLoadMore -> loadMore()
            is HomeUiEvent.OnCardClick -> { /* TODO: navigate to detail */ }
            is HomeUiEvent.OnRetry -> refresh()
        }
    }

    private fun switchTab(tab: String) {
        currentPage = 0
        _uiState.update { HomeUiState.Loading }
        loadFeed(tab, 0)
    }

    private fun refresh() {
        val tab = (_uiState.value as? HomeUiState.Success)?.currentTab ?: "recommend"
        currentPage = 0
        _uiState.update {
            (it as? HomeUiState.Success)?.copy(isRefreshing = true) ?: HomeUiState.Loading
        }
        loadFeed(tab, 0, isRefresh = true)
    }

    private fun loadMore() {
        val state = _uiState.value as? HomeUiState.Success ?: return
        if (state.isLoadingMore || !state.hasMore) return
        _uiState.update { state.copy(isLoadingMore = true) }
        loadFeed(state.currentTab, currentPage + 1, isLoadMore = true)
    }

    private fun loadFeed(channel: String, page: Int, isRefresh: Boolean = false, isLoadMore: Boolean = false) {
        Timber.d("loadFeed called: channel=$channel, page=$page, isRefresh=$isRefresh, isLoadMore=$isLoadMore")
        viewModelScope.launch {
            try {
                val items = newsRepository.getNewsFeed(channel, page)
                Timber.d("loadFeed — got ${items.size} items from repository")
                val hasMore = newsRepository.hasMore(channel, page)
                currentPage = page
                _uiState.update { current ->
                    when {
                        items.isEmpty() && !isLoadMore -> {
                            Timber.d("loadFeed — items empty, transitioning to Empty")
                            HomeUiState.Empty
                        }
                        else -> {
                            Timber.d("loadFeed — transitioning to Success (items=${items.size}, hasMore=$hasMore)")
                            HomeUiState.Success(
                                feedItems = if (isLoadMore) {
                                    (current as? HomeUiState.Success)?.feedItems.orEmpty() + items
                                } else {
                                    items
                                },
                                hasMore = hasMore,
                                currentTab = channel,
                                isRefreshing = false,
                                isLoadingMore = false,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "loadFeed failed")
                _uiState.update {
                    HomeUiState.Error(
                        message = e.message ?: "加载失败，请重试",
                        retryable = true,
                    )
                }
            }
        }
    }
}
