package com.example.toutiao.presentation.home

import com.example.toutiao.domain.model.HotListItem
import com.example.toutiao.domain.model.HotQuickAction

sealed class HomeUiState {
    data object Loading : HomeUiState()
    data class Success(
        val isRefreshing: Boolean = false,
        val currentTab: String = "recommend",
        val searchQuery: String = "",
        val isSearching: Boolean = false,
        val searchError: String? = null,
        /**
         * 「上次看到这里」状态。
         * - [lastSeenCardId] 持久化的上次阅读首条 id
         * - [lastSeenAt] 记录时间（毫秒），用于「2 小时前看过」等提示
         */
        val lastSeenCardId: String? = null,
        val lastSeenAt: Long = 0L,
        // ── 热榜频道专用 ──
        val hotQuickActions: List<HotQuickAction> = emptyList(),
        val hotListItems: List<HotListItem> = emptyList(),
        val hotListLoading: Boolean = false,
    ) : HomeUiState()
    data class Error(
        val message: String,
        val retryable: Boolean = true,
    ) : HomeUiState()
    data object Empty : HomeUiState()
}
