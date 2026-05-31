package com.example.toutiao.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData

import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.domain.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

// =============================================================================
// HomeViewModel — MVI 状态管理 + Paging3 数据管道
//
// 角色：连接 Repository（数据层）和 Screen（UI 层）的桥梁。
//       ViewModel 不关心数据来自 Mock 还是真实 API 还是 Room 缓存——
//       它只调用 NewsRepository 接口方法，由 DI 层决定注入什么实现。
//
// 核心数据管道：
//   _currentTab (MutableStateFlow<String>)
//     ↓ flatMapLatest — Tab 切换时自动取消旧流，启动新流
//   newsRepository.getFeedPagingData(tab)
//     ↓ 返回 Flow<PagingData<FeedCard>>
//   feedPagingData: Flow<PagingData<FeedCard>>
//     ↓ UI 层 collectAsLazyPagingItems() 消费
//   LazyPagingItems<FeedCard>
//     ↓ LazyColumn 渲染
//
// 为什么用 flatMapLatest（不用 cachedIn）：
//   Tab 切换 → _currentTab 变化 → flatMapLatest 自动取消旧的 getFeedPagingData 流
//   → 启动新的 getFeedPagingData(新 tab) → 新的 Pager → 新的 RemoteMediator
//   → 清空旧 tab 的 Room 数据 → 写入新 tab 的数据 → UI 自动更新
//   不使用 cachedIn 是为了避免 Tab 切换时，新订阅者先收到 SharedFlow replay
//   缓存里的旧 Tab PagingData，导致旧数据闪现（闪烁）且滚动位置错乱。
// =============================================================================
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
) : ViewModel() {

    // 当前选中的 Tab（recommend/hot/video/society）
    // 变化时通过 flatMapLatest 自动触发新 PagingData 流的创建
    private val _currentTab = MutableStateFlow("recommend")
    val currentTab: StateFlow<String> = _currentTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // 搜索结果用普通 List（不走 Paging3，数据量小）
    private val _searchResults = MutableStateFlow<List<FeedCard>>(emptyList())
    val searchResults: StateFlow<List<FeedCard>> = _searchResults.asStateFlow()

    // UiState 控制搜索 UI 状态（是否展开搜索框），不控制列表内容
    // 列表内容由 feedPagingData 和 LazyPagingItems.loadState 驱动
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Success())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // 这是整个数据流的核心管道：
    // Tab 变化 → 新 Pager → 新 PagingData → UI
    // flatMapLatest: 新 Tab 触发时自动取消旧 Tab 的流
    val feedPagingData: Flow<PagingData<FeedCard>> = _currentTab
        .flatMapLatest { tab ->
            Timber.d("feedPagingData — switching to tab=$tab")
            newsRepository.getFeedPagingData(tab)
        }

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
                _uiState.update { (it as? HomeUiState.Success)?.copy(isSearching = false, searchError = null) ?: it }
            }
            is HomeUiEvent.OnMoreChannelsClicked -> {
                Timber.d("OnMoreChannelsClicked — 预留更多频道入口")
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
                _uiState.update { (it as? HomeUiState.Success)?.copy(searchError = null) ?: it }
            } catch (e: Exception) {
                Timber.e(e, "performSearch failed")
                _searchResults.value = emptyList()
                _uiState.update { (it as? HomeUiState.Success)?.copy(searchError = "搜索失败，请稍后重试") ?: it }
            }
        }
    }
}
