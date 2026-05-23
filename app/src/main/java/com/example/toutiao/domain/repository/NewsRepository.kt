package com.example.toutiao.domain.repository

import com.example.toutiao.domain.model.FeedCard
import kotlinx.coroutines.flow.Flow

interface NewsRepository {
    suspend fun getNewsFeed(channel: String, page: Int, size: Int = 20): List<FeedCard>
    suspend fun hasMore(channel: String, page: Int): Boolean
    fun getCachedFeed(channel: String): Flow<List<FeedCard>>
}
