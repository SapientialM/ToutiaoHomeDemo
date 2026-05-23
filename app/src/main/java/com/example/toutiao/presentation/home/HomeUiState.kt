package com.example.toutiao.presentation.home

import com.example.toutiao.domain.model.FeedCard

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Success(
        val feedItems: List<FeedCard> = emptyList(),
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val currentTab: String = "recommend",
    ) : HomeUiState()
    data class Error(
        val message: String,
        val retryable: Boolean = true,
    ) : HomeUiState()
    data object Empty : HomeUiState()
}
