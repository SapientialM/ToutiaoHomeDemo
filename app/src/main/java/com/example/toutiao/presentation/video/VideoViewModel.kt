package com.example.toutiao.presentation.video

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.domain.repository.NewsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class VideoViewModel @Inject constructor(
    private val newsRepository: NewsRepository,
) : ViewModel() {

    fun loadVideos(
        page: Int,
        onResult: (List<FeedCard.Video>, Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val videos = newsRepository.getVideoFeed(page, 10)
                onResult(videos, videos.isNotEmpty())
            } catch (e: Exception) {
                Timber.e(e, "loadVideos failed")
                onResult(emptyList(), false)
            }
        }
    }
}
