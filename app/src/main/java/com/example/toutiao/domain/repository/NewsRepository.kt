package com.example.toutiao.domain.repository

import androidx.paging.PagingData
import com.example.toutiao.domain.model.FeedCard
import com.example.toutiao.domain.model.HotListItem
import com.example.toutiao.domain.model.HotQuickAction
import kotlinx.coroutines.flow.Flow

interface NewsRepository {
    fun getFeedPagingData(channel: String): Flow<PagingData<FeedCard>>
    suspend fun searchNews(query: String): List<FeedCard>
    suspend fun getVideoFeed(page: Int, size: Int): List<FeedCard.Video>
    /**
     * 获取热榜频道的快捷入口 + 排行列表
     */
    suspend fun getHotList(): Pair<List<HotQuickAction>, List<HotListItem>>
}
