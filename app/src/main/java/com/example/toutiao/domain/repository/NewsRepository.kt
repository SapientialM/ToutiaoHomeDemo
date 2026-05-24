package com.example.toutiao.domain.repository

import androidx.paging.PagingData
import com.example.toutiao.domain.model.FeedCard
import kotlinx.coroutines.flow.Flow

interface NewsRepository {
    suspend fun getNewsFeed(channel: String, page: Int, size: Int = 20): List<FeedCard>
    suspend fun hasMore(channel: String, page: Int): Boolean
    fun getCachedFeed(channel: String): Flow<List<FeedCard>>
    fun getFeedPagingData(channel: String): Flow<PagingData<FeedCard>>
    suspend fun searchNews(query: String): List<FeedCard>
}
