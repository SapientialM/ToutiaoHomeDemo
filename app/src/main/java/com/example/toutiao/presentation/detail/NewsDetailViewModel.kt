package com.example.toutiao.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toutiao.data.remote.datasource.CommentDataSource
import com.example.toutiao.domain.model.NewsContent
import com.example.toutiao.domain.repository.NewsContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * 新闻详情页 ViewModel
 *
 * 状态机：UiState.Success.detailStage 跟随 NewsContentRepository.Stage
 *
 * 触发：Activity / Screen 拿到 sourceUrl 后调用 [load]
 */
@HiltViewModel
class NewsDetailViewModel @Inject constructor(
    private val newsContentRepository: NewsContentRepository,
    val commentDataSource: CommentDataSource,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NewsDetailUiState())
    val uiState: StateFlow<NewsDetailUiState> = _uiState.asStateFlow()

    /**
     * 加载新闻详情。
     * 如果之前未加载，会启动 fetch + parse 流程；
     * 如果已经在加载中（Loading），忽略重复触发。
     */
    fun load(sourceUrl: String, fallbackTitle: String?) {
        if (sourceUrl.isBlank()) {
            _uiState.update {
                it.copy(detailStage = NewsDetailStage.Error("URL 为空"))
            }
            return
        }
        if (_uiState.value.detailStage is NewsDetailStage.Loading) {
            Timber.d("NewsDetailViewModel — already loading, skip")
            return
        }
        _uiState.update {
            it.copy(
                sourceUrl = sourceUrl,
                fallbackTitle = fallbackTitle,
                detailStage = NewsDetailStage.Loading(NewsContentRepository.Stage.Fetching(sourceUrl)),
            )
        }
        viewModelScope.launch {
            newsContentRepository.loadContent(sourceUrl, fallbackTitle).collect { repoStage ->
                val newStage = when (repoStage) {
                    is NewsContentRepository.Stage.Fetching -> NewsDetailStage.Loading(repoStage)
                    is NewsContentRepository.Stage.ManualParsing -> NewsDetailStage.Loading(repoStage)
                    is NewsContentRepository.Stage.LlmParsing -> NewsDetailStage.Loading(repoStage)
                    is NewsContentRepository.Stage.MockParsing -> NewsDetailStage.Loading(repoStage)
                    is NewsContentRepository.Stage.ContentReady -> NewsDetailStage.ContentReady(
                        content = repoStage.content,
                        byLlm = repoStage.byLlm,
                    )
                    is NewsContentRepository.Stage.Error -> NewsDetailStage.Error(repoStage.message)
                }
                _uiState.update { it.copy(detailStage = newStage) }
            }
        }
    }

    /** 重试：清除当前 stage 重新加载 */
    fun retry() {
        val url = _uiState.value.sourceUrl
        val title = _uiState.value.fallbackTitle
        if (url.isBlank()) return
        _uiState.update { it.copy(detailStage = NewsDetailStage.Idle) }
        load(url, title)
    }
}

/**
 * 新闻详情 UI 状态（聚合 UiState.Success 的 detailStage）
 */
data class NewsDetailUiState(
    val sourceUrl: String = "",
    val fallbackTitle: String? = null,
    val detailStage: NewsDetailStage = NewsDetailStage.Idle,
)

/**
 * 新闻详情阶段：Idle → Loading → ContentReady / Error
 */
sealed class NewsDetailStage {
    data object Idle : NewsDetailStage()
    data class Loading(val repoStage: NewsContentRepository.Stage) : NewsDetailStage()
    data class ContentReady(
        val content: NewsContent,
        val byLlm: Boolean,
    ) : NewsDetailStage()
    data class Error(val message: String) : NewsDetailStage()
}
