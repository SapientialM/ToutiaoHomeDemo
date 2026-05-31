package com.example.toutiao.domain.repository

import androidx.paging.PagingData
import com.example.toutiao.domain.model.FeedCard
import kotlinx.coroutines.flow.Flow

interface NewsRepository {
    fun getFeedPagingData(channel: String): Flow<PagingData<FeedCard>>
    suspend fun searchNews(query: String): List<FeedCard>
}
