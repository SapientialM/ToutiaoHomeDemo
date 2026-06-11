package com.example.toutiao.data.remote.datasource

import com.example.toutiao.data.remote.dto.NewsFeedResponse
import com.example.toutiao.domain.model.HotListItem
import com.example.toutiao.domain.model.HotQuickAction

/**
 * 远程数据源抽象接口。
 * Repository 层依赖此接口而非具体的 NewsApi，实现网络数据源的可替换性。
 */
interface RemoteDataSource {
    suspend fun getNewsFeed(channel: String, page: Int, size: Int = 20): NewsFeedResponse

    /**
     * 获取视频频道数据
     */
    suspend fun getVideoFeed(tab: String, page: Int, size: Int = 20): NewsFeedResponse

    /**
     * 搜索新闻
     */
    suspend fun searchNews(query: String, page: Int, size: Int = 20): NewsFeedResponse

    /**
     * 获取热榜频道数据：快捷入口胶囊 + Top N 榜单（带源 URL 用于跳详情页）。
     */
    suspend fun getHotList(): Pair<List<HotQuickAction>, List<HotListItem>>
}
