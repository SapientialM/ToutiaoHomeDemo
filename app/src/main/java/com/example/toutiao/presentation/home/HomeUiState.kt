package com.example.toutiao.presentation.home

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Success(
        val isRefreshing: Boolean = false,
        val currentTab: String = "recommend",
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val searchError: String? = null,
    ) : HomeUiState()
    data class Error(
        val message: String,
        val retryable: Boolean = true,
    ) : HomeUiState()
    data object Empty : HomeUiState()
}
